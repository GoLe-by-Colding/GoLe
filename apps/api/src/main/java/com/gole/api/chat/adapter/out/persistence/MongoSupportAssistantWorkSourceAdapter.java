package com.gole.api.chat.adapter.out.persistence;

import com.gole.api.chat.application.port.out.SupportAssistantPort.Request;
import com.gole.api.chat.application.port.out.SupportAssistantWorkSourcePort;
import com.gole.api.chat.domain.model.ChatRoomType;
import com.gole.api.chat.domain.model.SupportCategory;
import com.gole.api.chat.domain.model.SupportStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/** 기존 문의방·티켓·첫 메시지를 읽어 재시작 가능한 분석 요청을 만든다. */
@Component
public class MongoSupportAssistantWorkSourceAdapter implements SupportAssistantWorkSourcePort {

    private final SupportTicketMongoRepository tickets;
    private final SocialChatRoomMongoRepository rooms;
    private final ChatMessageMongoRepository messages;

    public MongoSupportAssistantWorkSourceAdapter(
            SupportTicketMongoRepository tickets,
            SocialChatRoomMongoRepository rooms,
            ChatMessageMongoRepository messages) {
        this.tickets = tickets;
        this.rooms = rooms;
        this.messages = messages;
    }

    @Override
    public Optional<Request> findRequest(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            return Optional.empty();
        }
        var ticket = tickets.findById(roomId).orElse(null);
        var room = rooms.findById(roomId).orElse(null);
        if (ticket == null
                || room == null
                || !ChatRoomType.SUPPORT.name().equals(room.getType())
                || ticket.getRequesterId() == null) {
            return Optional.empty();
        }
        var opening = messages.findFirstByRoomIdAndSenderIdOrderBySentAtAscIdAsc(roomId, ticket.getRequesterId())
                .orElse(null);
        if (opening == null
                || opening.getContent() == null
                || opening.getContent().isBlank()) {
            return Optional.empty();
        }
        SupportCategory category = parseCategory(ticket.getCategory());
        String title = room.getTitle() == null || room.getTitle().isBlank() ? "운영팀 문의" : room.getTitle();
        return Optional.of(new Request(roomId, category, title, opening.getContent(), "ko-KR"));
    }

    @Override
    public List<String> findRecentRoomIds(int limit) {
        var page = PageRequest.of(
                0, Math.clamp(limit, 1, 500), Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("roomId")));
        return tickets.findByStatusNot(SupportStatus.RESOLVED.name(), page).stream()
                .map(SupportTicketDocument::getRoomId)
                .filter(roomId -> roomId != null && !roomId.isBlank())
                .toList();
    }

    private static SupportCategory parseCategory(String rawCategory) {
        if (rawCategory == null || rawCategory.isBlank()) {
            return SupportCategory.GENERAL;
        }
        try {
            return SupportCategory.valueOf(rawCategory);
        } catch (IllegalArgumentException invalidLegacyCategory) {
            return SupportCategory.GENERAL;
        }
    }
}
