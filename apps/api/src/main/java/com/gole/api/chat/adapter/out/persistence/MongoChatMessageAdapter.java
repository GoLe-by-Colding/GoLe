package com.gole.api.chat.adapter.out.persistence;

import com.gole.api.chat.application.port.out.ChatMessageRepositoryPort;
import com.gole.api.chat.domain.model.ChatMessage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/** 채팅 메시지({@code chat_messages}) 영속성 어댑터. 커서 정렬은 {@code (sentAt, _id)} 한 쌍으로 건다. */
@Component
public class MongoChatMessageAdapter implements ChatMessageRepositoryPort {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("sentAt"), Sort.Order.desc("id"));
    private static final Sort OLDEST_FIRST = Sort.by(Sort.Order.asc("sentAt"), Sort.Order.asc("id"));

    private final ChatMessageMongoRepository repository;

    public MongoChatMessageAdapter(ChatMessageMongoRepository repository) {
        this.repository = repository;
    }

    @Override
    public ChatMessage save(ChatMessage message) {
        return toDomain(repository.save(new ChatMessageDocument(
                message.id(), message.roomId(), message.senderId(), message.content(), message.sentAt())));
    }

    @Override
    public Optional<ChatMessage> findById(String messageId) {
        return repository.findById(messageId).map(MongoChatMessageAdapter::toDomain);
    }

    @Override
    public List<ChatMessage> findLatest(String roomId, int limit) {
        return toDomain(repository.findByRoomId(roomId, PageRequest.of(0, limit, NEWEST_FIRST)));
    }

    @Override
    public List<ChatMessage> findBefore(String roomId, Instant sentAt, String messageId, int limit) {
        return toDomain(
                repository.findContextBefore(roomId, sentAt, messageId, PageRequest.of(0, limit, NEWEST_FIRST)));
    }

    @Override
    public List<ChatMessage> findAfter(String roomId, Instant sentAt, String messageId, int limit) {
        return toDomain(repository.findContextAfter(roomId, sentAt, messageId, PageRequest.of(0, limit, OLDEST_FIRST)));
    }

    private static List<ChatMessage> toDomain(List<ChatMessageDocument> documents) {
        return documents.stream().map(MongoChatMessageAdapter::toDomain).toList();
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
