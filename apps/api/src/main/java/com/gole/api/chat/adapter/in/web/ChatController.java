package com.gole.api.chat.adapter.in.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gole.api.chat.adapter.out.persistence.ChatMessageDocument;
import com.gole.api.chat.adapter.out.persistence.ChatMessageMongoRepository;
import com.gole.api.chat.adapter.out.persistence.ChatRoomDocument;
import com.gole.api.chat.adapter.out.persistence.ChatRoomMongoRepository;
import com.gole.api.chat.adapter.out.pubsub.ChatRedisPublisher;
import com.gole.api.chat.adapter.out.pubsub.ChatRedisPublisher.MessagePayload;
import com.gole.api.chat.domain.model.ChatMessage;
import com.gole.api.chat.domain.model.ChatRoom;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 채팅 REST API. 방 생성/조회 + 메시지 전송 + SSE 스트림(Redis Pub/Sub).
 *
 * <p>SSE 연결은 브라우저 당 1개. Redis 구독을 통해 다중 인스턴스에서도 모든 클라이언트에 전달한다.
 */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L; // 5분

    private final ChatRoomMongoRepository roomRepo;
    private final ChatMessageMongoRepository messageRepo;
    private final ChatRedisPublisher publisher;
    private final RedisMessageListenerContainer listenerContainer;
    private final ObjectMapper objectMapper;

    public ChatController(
            ChatRoomMongoRepository roomRepo,
            ChatMessageMongoRepository messageRepo,
            ChatRedisPublisher publisher,
            RedisMessageListenerContainer listenerContainer,
            ObjectMapper objectMapper) {
        this.roomRepo = roomRepo;
        this.messageRepo = messageRepo;
        this.publisher = publisher;
        this.listenerContainer = listenerContainer;
        this.objectMapper = objectMapper;
    }

    /** 방 생성(listingId 기반 buyerId+sellerId 조합, 중복 방지). */
    @PostMapping("/rooms")
    @ResponseStatus(HttpStatus.OK) // 멱등 — 이미 있으면 기존 방 반환
    public RoomResponse createOrGetRoom(@Valid @RequestBody CreateRoomRequest req) {
        return roomRepo
                .findByBuyerIdAndSellerIdAndListingId(req.buyerId(), req.sellerId(), req.listingId())
                .map(RoomResponse::from)
                .orElseGet(() -> {
                    ChatRoomDocument doc = new ChatRoomDocument(
                            UUID.randomUUID().toString(),
                            req.listingId(), req.buyerId(), req.sellerId(), Instant.now());
                    return RoomResponse.from(roomRepo.save(doc));
                });
    }

    /** 내 채팅방 목록(buyerId 또는 sellerId 기준). */
    @GetMapping("/rooms")
    public List<RoomResponse> myRooms(@RequestParam String userId) {
        return roomRepo.findByBuyerIdOrSellerIdOrderByCreatedAtDesc(userId, userId)
                .stream().map(RoomResponse::from).toList();
    }

    /** 방의 메시지 이력(최신 60개). */
    @GetMapping("/rooms/{roomId}/messages")
    public List<MessageResponse> messages(@PathVariable String roomId) {
        List<ChatMessageDocument> all = messageRepo.findByRoomIdOrderBySentAtAsc(roomId);
        int from = Math.max(0, all.size() - 60);
        return all.subList(from, all.size()).stream().map(MessageResponse::from).toList();
    }

    /** 메시지 전송: MongoDB 저장 → Redis Pub/Sub 브로드캐스트. */
    @PostMapping("/rooms/{roomId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse sendMessage(
            @PathVariable String roomId, @Valid @RequestBody SendMessageRequest req) {
        ChatMessageDocument doc = new ChatMessageDocument(
                UUID.randomUUID().toString(), roomId, req.senderId(), req.content(), Instant.now());
        ChatMessageDocument saved = messageRepo.save(doc);
        publisher.publish(new ChatMessage(
                saved.getId(), roomId, saved.getSenderId(), saved.getContent(), saved.getSentAt()));
        return MessageResponse.from(saved);
    }

    /**
     * SSE 스트림. 클라이언트가 연결하면 Redis 채널({@code chat:<roomId>})을 구독하고
     * 새 메시지가 오면 이벤트를 전송한다. 연결 해제 시 구독 해제.
     */
    @GetMapping(value = "/rooms/{roomId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String roomId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        String channel = "chat:" + roomId;

        // 활성 emitter 목록(cleanup용)
        CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
        emitters.add(emitter);

        MessageListener listener = (Message rawMsg, byte[] pattern) -> {
            try {
                String json = new String(rawMsg.getBody());
                MessagePayload payload = objectMapper.readValue(json, MessagePayload.class);
                emitter.send(SseEmitter.event()
                        .name("message")
                        .data(payload, MediaType.APPLICATION_JSON));
            } catch (JsonProcessingException e) {
                log.warn("SSE deserialization failed: {}", e.getMessage());
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        };

        ChannelTopic topic = new ChannelTopic(channel);
        listenerContainer.addMessageListener(listener, topic);

        Runnable cleanup = () -> listenerContainer.removeMessageListener(listener, topic);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError((e) -> cleanup.run());

        return emitter;
    }

    public record CreateRoomRequest(
            @NotBlank String listingId, @NotBlank String buyerId, @NotBlank String sellerId) {}

    public record SendMessageRequest(@NotBlank String senderId, @NotBlank String content) {}

    public record RoomResponse(
            String id, String listingId, String buyerId, String sellerId, String createdAt) {

        public static RoomResponse from(ChatRoomDocument d) {
            return new RoomResponse(d.getId(), d.getListingId(), d.getBuyerId(), d.getSellerId(),
                    d.getCreatedAt().toString());
        }
    }

    public record MessageResponse(
            String id, String roomId, String senderId, String content, String sentAt) {

        public static MessageResponse from(ChatMessageDocument d) {
            return new MessageResponse(d.getId(), d.getRoomId(), d.getSenderId(), d.getContent(),
                    d.getSentAt().toString());
        }
    }
}
