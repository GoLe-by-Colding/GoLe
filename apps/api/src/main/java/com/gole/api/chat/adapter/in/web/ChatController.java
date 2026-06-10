package com.gole.api.chat.adapter.in.web;

import com.gole.api.chat.adapter.out.persistence.ChatMessageDocument;
import com.gole.api.chat.adapter.out.persistence.ChatMessageMongoRepository;
import com.gole.api.chat.adapter.out.persistence.ChatRoomDocument;
import com.gole.api.chat.adapter.out.persistence.ChatRoomMongoRepository;
import com.gole.api.chat.adapter.out.pubsub.ChatRedisPublisher;
import com.gole.api.chat.domain.model.ChatMessage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L;

    // 간단한 JSON 값 추출 패턴 (채팅 메시지 페이로드 전용)
    private static final Pattern JSON_VAL = Pattern.compile("\"(\\w+)\"\\s*:\\s*\"([^\"]*)\"");

    private final ChatRoomMongoRepository roomRepo;
    private final ChatMessageMongoRepository messageRepo;
    private final ChatRedisPublisher publisher;
    private final RedisMessageListenerContainer listenerContainer;

    public ChatController(
            ChatRoomMongoRepository roomRepo,
            ChatMessageMongoRepository messageRepo,
            ChatRedisPublisher publisher,
            RedisMessageListenerContainer listenerContainer) {
        this.roomRepo = roomRepo;
        this.messageRepo = messageRepo;
        this.publisher = publisher;
        this.listenerContainer = listenerContainer;
    }

    @PostMapping("/rooms")
    @ResponseStatus(HttpStatus.OK)
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

    @GetMapping("/rooms")
    public List<RoomResponse> myRooms(@RequestParam String userId) {
        return roomRepo.findByBuyerIdOrSellerIdOrderByCreatedAtDesc(userId, userId)
                .stream().map(RoomResponse::from).toList();
    }

    @GetMapping("/rooms/{roomId}/messages")
    public List<MessageResponse> messages(@PathVariable String roomId) {
        List<ChatMessageDocument> all = messageRepo.findByRoomIdOrderBySentAtAsc(roomId);
        int from = Math.max(0, all.size() - 60);
        return all.subList(from, all.size()).stream().map(MessageResponse::from).toList();
    }

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

    @GetMapping(value = "/rooms/{roomId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String roomId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        String channel = "chat:" + roomId;
        CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
        emitters.add(emitter);

        MessageListener listener = (Message rawMsg, byte[] pattern) -> {
            try {
                String json = new String(rawMsg.getBody());
                // 최소 JSON 파싱: {"id":"...","senderId":"...","content":"...","sentAt":"..."}
                java.util.Map<String, String> vals = new java.util.HashMap<>();
                Matcher m = JSON_VAL.matcher(json);
                while (m.find()) vals.put(m.group(1), m.group(2));

                String ssePayload = "{\"id\":\"" + vals.getOrDefault("id", "") + "\","
                        + "\"senderId\":\"" + vals.getOrDefault("senderId", "") + "\","
                        + "\"content\":\"" + vals.getOrDefault("content", "") + "\","
                        + "\"sentAt\":\"" + vals.getOrDefault("sentAt", "") + "\"}";
                emitter.send(SseEmitter.event().name("message").data(ssePayload));
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
