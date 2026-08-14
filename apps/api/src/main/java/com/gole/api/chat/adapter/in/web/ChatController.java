package com.gole.api.chat.adapter.in.web;

import com.gole.api.chat.adapter.out.persistence.ChatMessageDocument;
import com.gole.api.chat.adapter.out.persistence.ChatMessageMongoRepository;
import com.gole.api.chat.adapter.out.persistence.ChatRoomDocument;
import com.gole.api.chat.adapter.out.persistence.ChatRoomMongoRepository;
import com.gole.api.chat.adapter.out.pubsub.ChatMessageCodec;
import com.gole.api.chat.adapter.out.pubsub.ChatRedisPublisher;
import com.gole.api.chat.domain.model.ChatMessage;
import com.gole.api.chat.domain.model.ChatRoom;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.common.exception.NotFoundException;
import com.gole.api.common.web.CurrentUser;
import com.gole.api.common.web.CurrentUserArgumentResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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

    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L;

    private final ChatRoomMongoRepository roomRepo;
    private final ChatMessageMongoRepository messageRepo;
    private final ChatRedisPublisher publisher;
    private final ChatMessageCodec codec;
    private final RedisMessageListenerContainer listenerContainer;
    private final CurrentUserArgumentResolver currentUserResolver;

    public ChatController(
            ChatRoomMongoRepository roomRepo,
            ChatMessageMongoRepository messageRepo,
            ChatRedisPublisher publisher,
            ChatMessageCodec codec,
            RedisMessageListenerContainer listenerContainer,
            CurrentUserArgumentResolver currentUserResolver) {
        this.roomRepo = roomRepo;
        this.messageRepo = messageRepo;
        this.publisher = publisher;
        this.codec = codec;
        this.listenerContainer = listenerContainer;
        this.currentUserResolver = currentUserResolver;
    }

    @Operation(summary = "채팅방 생성 또는 조회", description = "listingId 기반 구매자↔판매자 1:1 채팅방. 이미 존재하면 기존 방을 반환합니다(멱등).")
    @PostMapping("/rooms")
    @ResponseStatus(HttpStatus.OK)
    public RoomResponse createOrGetRoom(CurrentUser user, @Valid @RequestBody CreateRoomRequest req) {
        // 남의 대화방을 대신 만들어 줄 이유가 없다. 요청자는 당사자 중 하나여야 한다.
        if (!user.accountId().equals(req.buyerId()) && !user.accountId().equals(req.sellerId())) {
            throw new ForbiddenException("CHAT_NOT_PARTICIPANT", "본인이 참여하는 대화만 만들 수 있습니다");
        }
        return roomRepo.findByBuyerIdAndSellerIdAndListingId(req.buyerId(), req.sellerId(), req.listingId())
                .map(RoomResponse::from)
                .orElseGet(() -> {
                    ChatRoomDocument doc = new ChatRoomDocument(
                            UUID.randomUUID().toString(),
                            req.listingId(),
                            req.buyerId(),
                            req.sellerId(),
                            Instant.now());
                    return RoomResponse.from(roomRepo.save(doc));
                });
    }

    /** 내 채팅방 목록. 대상은 쿼리 파라미터가 아니라 세션에서 정한다(남의 목록 조회 차단). */
    @GetMapping("/rooms")
    public List<RoomResponse> myRooms(CurrentUser user) {
        String userId = user.accountId();
        return roomRepo.findByBuyerIdOrSellerIdOrderByCreatedAtDesc(userId, userId).stream()
                .map(RoomResponse::from)
                .toList();
    }

    @GetMapping("/rooms/{roomId}/messages")
    public List<MessageResponse> messages(@PathVariable String roomId, CurrentUser user) {
        requireParticipant(roomId, user.accountId());
        List<ChatMessageDocument> all = messageRepo.findByRoomIdOrderBySentAtAsc(roomId);
        int from = Math.max(0, all.size() - 60);
        return all.subList(from, all.size()).stream().map(MessageResponse::from).toList();
    }

    @PostMapping("/rooms/{roomId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse sendMessage(
            @PathVariable String roomId, CurrentUser user, @Valid @RequestBody SendMessageRequest req) {
        requireParticipant(roomId, user.accountId());
        // 보낸 사람은 세션에서 정한다. 본문의 senderId를 믿으면 누구나 남을 사칭할 수 있다.
        ChatMessageDocument doc = new ChatMessageDocument(
                UUID.randomUUID().toString(), roomId, user.accountId(), req.content(), Instant.now());
        ChatMessageDocument saved = messageRepo.save(doc);
        publisher.publish(
                new ChatMessage(saved.getId(), roomId, saved.getSenderId(), saved.getContent(), saved.getSentAt()));
        return MessageResponse.from(saved);
    }

    @Operation(
            summary = "실시간 메시지 SSE 스트림",
            description = "Server-Sent Events로 채팅방의 새 메시지를 실시간 수신합니다. "
                    + "이벤트 이름: `message`, 데이터: JSON `{id, senderId, content, sentAt}`")
    @GetMapping(value = "/rooms/{roomId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @PathVariable String roomId,
            @RequestParam(name = "token", required = false, defaultValue = "") String token) {
        // 브라우저 EventSource는 Authorization 헤더를 붙일 수 없어 이 엔드포인트만 쿼리로 받는다.
        // 다른 곳으로 번지지 않도록 여기 한 곳에 가둔다.
        CurrentUser user = currentUserResolver.require(token);
        requireParticipant(roomId, user.accountId());

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        String channel = "chat:" + roomId;

        MessageListener listener = (Message rawMsg, byte[] pattern) -> {
            // 페이로드는 이미 정상 JSON이다. 값을 뜯어 다시 조립하지 않고 그대로 흘려보낸다 —
            // 재조립이 바로 따옴표 포함 메시지가 사라지던 원인이었다.
            codec.decode(new String(rawMsg.getBody(), StandardCharsets.UTF_8)).ifPresent(message -> {
                try {
                    emitter.send(SseEmitter.event().name("message").data(codec.encode(message)));
                } catch (IOException e) {
                    // 구독자가 이미 끊겼다. 정리는 onCompletion/onError가 맡는다.
                    emitter.completeWithError(e);
                }
            });
        };

        ChannelTopic topic = new ChannelTopic(channel);
        listenerContainer.addMessageListener(listener, topic);

        Runnable cleanup = () -> listenerContainer.removeMessageListener(listener, topic);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError((e) -> cleanup.run());
        return emitter;
    }

    /**
     * 요청자가 이 방의 당사자인지 확인한다. 아니면 403, 방이 없으면 404.
     *
     * <p>조회·전송·스트림 세 곳이 같은 규칙을 쓰므로 한 곳에 모은다. 규칙 자체는
     * {@link ChatRoom#isParticipant(String)}가 갖는다.
     */
    private ChatRoomDocument requireParticipant(String roomId, String accountId) {
        ChatRoomDocument room = roomRepo.findById(roomId)
                .orElseThrow(() -> new NotFoundException("CHAT_ROOM_NOT_FOUND", "채팅방을 찾을 수 없습니다"));
        ChatRoom domain = new ChatRoom(
                room.getId(), room.getListingId(), room.getBuyerId(), room.getSellerId(), room.getCreatedAt());
        if (!domain.isParticipant(accountId)) {
            throw new ForbiddenException("CHAT_NOT_PARTICIPANT", "이 대화의 참여자가 아닙니다");
        }
        return room;
    }

    public record CreateRoomRequest(@NotBlank String listingId, @NotBlank String buyerId, @NotBlank String sellerId) {}

    /** 보낸 사람은 세션에서 정하므로 본문에는 내용만 받는다. */
    public record SendMessageRequest(@NotBlank String content) {}

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
