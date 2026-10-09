package com.gole.api.chat.application.service;

import com.gole.api.chat.application.port.in.ChatMessagingUseCase;
import com.gole.api.chat.application.port.out.ChatConsentPort;
import com.gole.api.chat.application.port.out.ChatMessagePublisherPort;
import com.gole.api.chat.application.port.out.ChatMessageRepositoryPort;
import com.gole.api.chat.domain.model.ChatMessage;
import com.gole.api.chat.domain.model.ChatRoomType;
import com.gole.api.chat.domain.model.SocialChatRoom;
import com.gole.api.chat.domain.model.SupportTicket;
import com.gole.api.common.exception.BadRequestException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 모든 방 유형이 공유하는 메시지 이력·전송 유스케이스. */
@Service
public class ChatMessagingService implements ChatMessagingUseCase {

    private final ChatMessageRepositoryPort messages;
    private final ChatMessagePublisherPort publisher;
    private final SocialChatService socialChats;
    private final ChatConsentPort consents;
    private final SupportOperationalEventNotifier supportEvents;
    private final SupportAssistantAnalysisService supportAnalysis;
    private final Clock clock;

    public ChatMessagingService(
            ChatMessageRepositoryPort messages,
            ChatMessagePublisherPort publisher,
            SocialChatService socialChats,
            ChatConsentPort consents,
            SupportOperationalEventNotifier supportEvents,
            SupportAssistantAnalysisService supportAnalysis,
            Clock clock) {
        this.messages = messages;
        this.publisher = publisher;
        this.socialChats = socialChats;
        this.consents = consents;
        this.supportEvents = supportEvents;
        this.supportAnalysis = supportAnalysis;
        this.clock = clock;
    }

    public List<ChatMessage> recent(String roomId, String actorId) {
        return history(roomId, actorId, null, null, 60);
    }

    /** 가장 오래 본 메시지 앞쪽을 안정적인 {@code sentAt + _id} 커서로 가져온다. */
    @Override
    public List<ChatMessage> history(
            String roomId, String actorId, Instant beforeSentAt, String beforeId, int requestedLimit) {
        socialChats.requireReadable(roomId, actorId);
        int limit = Math.clamp(requestedLimit, 1, 100);
        boolean hasTime = beforeSentAt != null;
        boolean hasId = beforeId != null && !beforeId.isBlank();
        if (hasTime != hasId) {
            throw new BadRequestException("CHAT_CURSOR_INVALID", "메시지 커서가 올바르지 않습니다");
        }
        // 저장소는 커서에 가까운(최신) 것부터 주므로 화면 순서(오래된 순)로 뒤집는다.
        List<ChatMessage> rows = new ArrayList<>(
                hasTime
                        ? messages.findBefore(roomId, beforeSentAt, beforeId, limit)
                        : messages.findLatest(roomId, limit));
        Collections.reverse(rows);
        return List.copyOf(rows);
    }

    /** SSE 재연결 시 마지막으로 받은 이벤트 다음 메시지를 Mongo 이력에서 재생한다. */
    @Override
    public List<ChatMessage> after(String roomId, String actorId, String afterId, int requestedLimit) {
        socialChats.requireReadable(roomId, actorId);
        if (afterId == null || afterId.isBlank()) {
            return List.of();
        }
        ChatMessage cursor = messages.findById(afterId)
                .filter(message -> roomId.equals(message.roomId()))
                .orElseThrow(() -> new BadRequestException("CHAT_CURSOR_INVALID", "메시지 커서가 올바르지 않습니다"));
        int limit = Math.clamp(requestedLimit, 1, 200);
        return messages.findAfter(roomId, cursor.sentAt(), cursor.id(), limit);
    }

    @Override
    @Transactional
    public ChatMessage sendFromUser(String roomId, String actorId, String rawContent) {
        SocialChatRoom room = socialChats.requireReadable(roomId, actorId);
        if (room.type() != ChatRoomType.SUPPORT) {
            // 운영팀 문의는 운영 목적의 대화라 제3자 제공 동의 없이도 보낸다.
            consents.requireCurrent(actorId);
        }
        return send(roomId, actorId, rawContent);
    }

    @Transactional
    public ChatMessage send(String roomId, String actorId, String rawContent) {
        return sendUserMessage(roomId, actorId, rawContent, false);
    }

    /** 문의방 생성 트랜잭션에서만 쓰는 첫 메시지 경로. 일반 사용자 답변과 알림을 구분한다. */
    @Transactional
    public ChatMessage sendSupportOpening(String roomId, String actorId, String rawContent) {
        return sendUserMessage(roomId, actorId, rawContent, true);
    }

    private ChatMessage sendUserMessage(String roomId, String actorId, String rawContent, boolean supportOpening) {
        String content = normalizeContent(rawContent);
        SocialChatRoom room = socialChats.requireSendable(roomId, actorId);
        if (supportOpening && room.type() != ChatRoomType.SUPPORT) {
            throw new BadRequestException("CHAT_NOT_SUPPORT_ROOM", "운영팀 문의방이 아닙니다");
        }
        Instant now = Instant.now(clock);
        ChatMessage message =
                messages.save(new ChatMessage(UUID.randomUUID().toString(), roomId, actorId, content, now));
        Optional<SupportTicket> supportTicket = socialChats.onMessageSent(room, actorId);
        socialChats.touchActivity(roomId, now);
        publishAfterCommit(message);
        supportTicket.ifPresent(ticket -> {
            if (supportOpening) {
                supportEvents.opened(ticket);
                supportAnalysis.analyzeOpeningAfterCommit(ticket, room.title(), content, "ko-KR");
            } else {
                supportEvents.requesterReplied(ticket);
            }
        });
        return message;
    }

    /** 관리자 SUPPORT 답변. 일반 사용자 메시지 경로와 분리해 감사 우회를 막는다. */
    @Transactional
    @Override
    public ChatMessage sendAdminSupport(String roomId, String adminId, String rawContent) {
        String content = normalizeContent(rawContent);
        SocialChatRoom room = socialChats.requireAdminSupportSendable(roomId, adminId);
        Instant now = Instant.now(clock);
        ChatMessage message =
                messages.save(new ChatMessage(UUID.randomUUID().toString(), roomId, adminId, content, now));
        socialChats.onMessageSent(room, adminId);
        socialChats.touchActivity(roomId, now);
        publishAfterCommit(message);
        return message;
    }

    private void publishAfterCommit(ChatMessage message) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            publisher.publish(message);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publisher.publish(message);
            }
        });
    }

    private static String normalizeContent(String content) {
        if (content == null || content.isBlank()) {
            throw new BadRequestException("CHAT_MESSAGE_REQUIRED", "메시지를 입력해 주세요");
        }
        String normalized = content.strip();
        if (normalized.length() > 2000) {
            throw new BadRequestException("CHAT_MESSAGE_TOO_LONG", "메시지는 2,000자까지 입력할 수 있습니다");
        }
        return normalized;
    }
}
