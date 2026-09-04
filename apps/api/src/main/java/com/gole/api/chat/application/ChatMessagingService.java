package com.gole.api.chat.application;

import com.gole.api.chat.adapter.out.persistence.ChatMessageDocument;
import com.gole.api.chat.adapter.out.persistence.ChatMessageMongoRepository;
import com.gole.api.chat.adapter.out.pubsub.ChatRedisPublisher;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 모든 방 유형이 공유하는 메시지 이력·전송 유스케이스. */
@Service
public class ChatMessagingService {

    private final ChatMessageMongoRepository messages;
    private final ChatRedisPublisher publisher;
    private final SocialChatService socialChats;
    private final SupportOperationalEventNotifier supportEvents;
    private final SupportAssistantAnalysisService supportAnalysis;
    private final Clock clock;

    public ChatMessagingService(
            ChatMessageMongoRepository messages,
            ChatRedisPublisher publisher,
            SocialChatService socialChats,
            SupportOperationalEventNotifier supportEvents,
            SupportAssistantAnalysisService supportAnalysis,
            Clock clock) {
        this.messages = messages;
        this.publisher = publisher;
        this.socialChats = socialChats;
        this.supportEvents = supportEvents;
        this.supportAnalysis = supportAnalysis;
        this.clock = clock;
    }

    public List<ChatMessage> recent(String roomId, String actorId) {
        return history(roomId, actorId, null, null, 60);
    }

    /** 가장 오래 본 메시지 앞쪽을 안정적인 {@code sentAt + _id} 커서로 가져온다. */
    public List<ChatMessage> history(
            String roomId, String actorId, Instant beforeSentAt, String beforeId, int requestedLimit) {
        socialChats.requireReadable(roomId, actorId);
        int limit = Math.clamp(requestedLimit, 1, 100);
        boolean hasTime = beforeSentAt != null;
        boolean hasId = beforeId != null && !beforeId.isBlank();
        if (hasTime != hasId) {
            throw new BadRequestException("CHAT_CURSOR_INVALID", "메시지 커서가 올바르지 않습니다");
        }
        PageRequest page = PageRequest.of(0, limit, Sort.by(Sort.Order.desc("sentAt"), Sort.Order.desc("id")));
        List<ChatMessageDocument> rows = new ArrayList<>(
                hasTime
                        ? messages.findContextBefore(roomId, beforeSentAt, beforeId, page)
                        : messages.findByRoomId(roomId, page));
        Collections.reverse(rows);
        return rows.stream().map(ChatMessagingService::toDomain).toList();
    }

    /** SSE 재연결 시 마지막으로 받은 이벤트 다음 메시지를 Mongo 이력에서 재생한다. */
    public List<ChatMessage> after(String roomId, String actorId, String afterId, int requestedLimit) {
        socialChats.requireReadable(roomId, actorId);
        if (afterId == null || afterId.isBlank()) {
            return List.of();
        }
        ChatMessageDocument cursor = messages.findById(afterId)
                .filter(message -> roomId.equals(message.getRoomId()))
                .orElseThrow(() -> new BadRequestException("CHAT_CURSOR_INVALID", "메시지 커서가 올바르지 않습니다"));
        int limit = Math.clamp(requestedLimit, 1, 200);
        PageRequest page = PageRequest.of(0, limit, Sort.by(Sort.Order.asc("sentAt"), Sort.Order.asc("id")));
        return messages.findContextAfter(roomId, cursor.getSentAt(), cursor.getId(), page).stream()
                .map(ChatMessagingService::toDomain)
                .toList();
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
        ChatMessageDocument saved =
                messages.save(new ChatMessageDocument(UUID.randomUUID().toString(), roomId, actorId, content, now));
        ChatMessage message = toDomain(saved);
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
    public ChatMessage sendAdminSupport(String roomId, String adminId, String rawContent) {
        String content = normalizeContent(rawContent);
        SocialChatRoom room = socialChats.requireAdminSupportSendable(roomId, adminId);
        Instant now = Instant.now(clock);
        ChatMessageDocument saved =
                messages.save(new ChatMessageDocument(UUID.randomUUID().toString(), roomId, adminId, content, now));
        ChatMessage message = toDomain(saved);
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

    private static ChatMessage toDomain(ChatMessageDocument document) {
        return new ChatMessage(
                document.getId(),
                document.getRoomId(),
                document.getSenderId(),
                document.getContent(),
                document.getSentAt());
    }
}
