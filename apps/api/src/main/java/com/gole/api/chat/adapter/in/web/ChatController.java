package com.gole.api.chat.adapter.in.web;

import com.gole.api.account.adapter.in.web.AuthenticatedUser;
import com.gole.api.chat.adapter.out.persistence.ChatMessageDocument;
import com.gole.api.chat.adapter.out.persistence.ChatMessageMongoRepository;
import com.gole.api.chat.adapter.out.persistence.ChatRoomDocument;
import com.gole.api.chat.adapter.out.persistence.ChatRoomMongoRepository;
import com.gole.api.chat.adapter.out.pubsub.ChatRedisPublisher;
import com.gole.api.chat.domain.model.ChatMessage;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.common.exception.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
@Tag(name = "Chat", description = "실시간 채팅(SSE) — 방 관리·메시지 송수신")
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

    @Operation(summary = "채팅방 생성 또는 조회", description = "listingId 기반 구매자↔판매자 1:1 채팅방. 이미 존재하면 기존 방을 반환합니다(멱등).")
    @PostMapping("/rooms")
    @ResponseStatus(HttpStatus.OK)
    public RoomResponse createOrGetRoom(@Valid @RequestBody CreateRoomRequest req, HttpServletRequest http) {
        String buyerId = AuthenticatedUser.id(http);
        return roomRepo.findByBuyerIdAndSellerIdAndListingId(buyerId, req.sellerId(), req.listingId())
                .map(RoomResponse::from)
                .orElseGet(() -> {
                    ChatRoomDocument doc = new ChatRoomDocument(
                            UUID.randomUUID().toString(), req.listingId(), buyerId, req.sellerId(), Instant.now());
                    return RoomResponse.from(roomRepo.save(doc));
                });
    }

    @GetMapping("/rooms")
    public List<RoomResponse> myRooms(@RequestParam String userId, HttpServletRequest http) {
        String actorId = AuthenticatedUser.id(http);
        return roomRepo.findByBuyerIdOrSellerIdOrderByCreatedAtDesc(actorId, actorId).stream()
                .map(RoomResponse::from)
                .toList();
    }

    @GetMapping("/rooms/{roomId}/messages")
    public List<MessageResponse> messages(@PathVariable String roomId, HttpServletRequest http) {
        requireParticipant(roomId, http);
        List<ChatMessageDocument> all = messageRepo.findByRoomIdOrderBySentAtAsc(roomId);
        int from = Math.max(0, all.size() - 60);
        return all.subList(from, all.size()).stream().map(MessageResponse::from).toList();
    }

    @PostMapping("/rooms/{roomId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse sendMessage(
            @PathVariable String roomId, @Valid @RequestBody SendMessageRequest req, HttpServletRequest http) {
        String senderId = requireParticipant(roomId, http);
        ChatMessageDocument doc =
                new ChatMessageDocument(UUID.randomUUID().toString(), roomId, senderId, req.content(), Instant.now());
        ChatMessageDocument saved = messageRepo.save(doc);
        publisher.publish(
                new ChatMessage(saved.getId(), roomId, saved.getSenderId(), saved.getContent(), saved.getSentAt()));
        return MessageResponse.from(saved);
    }

    private String requireParticipant(String roomId, HttpServletRequest http) {
        String actorId = AuthenticatedUser.id(http);
        ChatRoomDocument room = roomRepo.findById(roomId)
                .orElseThrow(() -> new NotFoundException("CHAT_ROOM_NOT_FOUND", "채팅방을 찾을 수 없습니다"));
        if (!actorId.equals(room.getBuyerId()) && !actorId.equals(room.getSellerId())) {
            throw new ForbiddenException("CHAT_ROOM_ACCESS_DENIED", "참여 중인 채팅방만 볼 수 있습니다");
        }
        return actorId;
    }

    @Operation(
            summary = "실시간 메시지 SSE 스트림",
            description = "Server-Sent Events로 채팅방의 새 메시지를 실시간 수신합니다. "
                    + "이벤트 이름: `message`, 데이터: JSON `{id, senderId, content, sentAt}`")
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

    public record CreateRoomRequest(@NotBlank String listingId, @NotBlank String buyerId, @NotBlank String sellerId) {}

    public record SendMessageRequest(@NotBlank String senderId, @NotBlank String content) {}

    public record RoomResponse(String id, String listingId, String buyerId, String sellerId, String createdAt) {

        public static RoomResponse from(ChatRoomDocument d) {
            return new RoomResponse(
                    d.getId(),
                    d.getListingId(),
                    d.getBuyerId(),
                    d.getSellerId(),
                    d.getCreatedAt().toString());
        }
    }

    public record MessageResponse(String id, String roomId, String senderId, String content, String sentAt) {

        public static MessageResponse from(ChatMessageDocument d) {
            return new MessageResponse(
                    d.getId(),
                    d.getRoomId(),
                    d.getSenderId(),
                    d.getContent(),
                    d.getSentAt().toString());
        }
    }
}
