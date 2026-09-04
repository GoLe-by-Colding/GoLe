package com.gole.api.chat.application;

import com.gole.api.chat.adapter.out.persistence.ChatMessageDocument;
import com.gole.api.chat.adapter.out.persistence.ChatMessageMongoRepository;
import com.gole.api.chat.application.port.out.ChatBlockRepositoryPort;
import com.gole.api.chat.application.port.out.ChatReadStatePort;
import com.gole.api.chat.application.port.out.SupportConversationPrivacyRepositoryPort;
import com.gole.api.chat.domain.model.ChatRoomType;
import com.gole.api.common.exception.BadRequestException;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 모든 채팅 유형의 읽음 커서와 방별 안 읽음 수를 한 계약으로 제공한다. */
@Service
public class ChatReadService {

    private static final int MAX_ROOMS = 100;

    private final ChatMessageMongoRepository messages;
    private final ChatReadStatePort readStates;
    private final ChatBlockRepositoryPort blocks;
    private final SocialChatService socialChats;
    private final SupportConversationPrivacyRepositoryPort supportPrivacy;
    private final Clock clock;

    public ChatReadService(
            ChatMessageMongoRepository messages,
            ChatReadStatePort readStates,
            ChatBlockRepositoryPort blocks,
            SocialChatService socialChats,
            SupportConversationPrivacyRepositoryPort supportPrivacy,
            Clock clock) {
        this.messages = messages;
        this.readStates = readStates;
        this.blocks = blocks;
        this.socialChats = socialChats;
        this.supportPrivacy = supportPrivacy;
        this.clock = clock;
    }

    public Map<String, Long> unreadCounts(String actorId) {
        List<String> readableRoomIds = socialChats.myReadableRooms(actorId, MAX_ROOMS).stream()
                .map(room -> room.id())
                .distinct()
                .toList();
        List<String> blockedSenderIds = blocks.blockedTargets(actorId);
        Map<String, Long> storedCounts = readStates.countUnread(actorId, readableRoomIds, blockedSenderIds);
        LinkedHashMap<String, Long> response = new LinkedHashMap<>();
        for (String roomId : readableRoomIds) {
            response.put(roomId, Math.max(0L, storedCounts.getOrDefault(roomId, 0L)));
        }
        return Collections.unmodifiableMap(response);
    }

    @Transactional
    public void markRead(String roomId, String actorId, String lastMessageId) {
        var room = socialChats.requireReadable(roomId, actorId);
        ChatMessageDocument cursor = messages.findById(lastMessageId)
                .filter(message -> roomId.equals(message.getRoomId()))
                .orElseThrow(() -> new BadRequestException("CHAT_READ_CURSOR_INVALID", "읽음 위치가 올바르지 않습니다"));
        Instant now = Instant.now(clock);
        if (room.type() == ChatRoomType.SUPPORT) {
            supportPrivacy.fenceSupportConversation(roomId, now);
        }
        readStates.advance(roomId, actorId, cursor.getId(), cursor.getSentAt(), now);
    }
}
