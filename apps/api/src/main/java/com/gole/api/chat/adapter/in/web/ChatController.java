package com.gole.api.chat.adapter.in.web;

import com.gole.api.chat.application.port.in.ChatMessagingUseCase;
import com.gole.api.chat.application.port.in.ChatReadStateUseCase;
import com.gole.api.chat.application.port.in.DirectTradeUseCase;
import com.gole.api.chat.application.port.in.ListingChatRoomUseCase;
import com.gole.api.chat.application.port.in.SocialChatUseCase;
import com.gole.api.chat.domain.model.ChatMessage;
import com.gole.api.chat.domain.model.ChatRoom;
import com.gole.api.common.web.auth.AuthenticatedUser;
import com.gole.api.common.web.auth.RequiresOnboarding;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

/** 채팅 REST API. 방 생성/조회 + 메시지 전송 + SSE 스트림(Redis Pub/Sub). */
@Tag(name = "Chat", description = "실시간 채팅(SSE) — 방 관리·메시지 송수신")
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L;
    private static final int REPLAY_BATCH_SIZE = 200;
    private static final int MAX_REPLAY_MESSAGES_PER_CONNECTION = 5_000;

    private final ListingChatRoomUseCase rooms;
    private final SocialChatUseCase socialChats;
    private final ChatMessagingUseCase messaging;
    private final ChatReadStateUseCase reads;
    private final DirectTradeUseCase directTrades;
    private final RedisMessageListenerContainer listenerContainer;
    private final ObjectMapper objectMapper;

    public ChatController(
            ListingChatRoomUseCase rooms,
            SocialChatUseCase socialChats,
            ChatMessagingUseCase messaging,
            ChatReadStateUseCase reads,
            DirectTradeUseCase directTrades,
            RedisMessageListenerContainer listenerContainer,
            ObjectMapper objectMapper) {
        this.rooms = rooms;
        this.socialChats = socialChats;
        this.messaging = messaging;
        this.reads = reads;
        this.directTrades = directTrades;
        this.listenerContainer = listenerContainer;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "채팅방 생성 또는 조회", description = "listingId 기반 구매자↔판매자 1:1 채팅방. 이미 존재하면 기존 방을 반환합니다(멱등).")
    @PostMapping("/rooms")
    @RequiresOnboarding // onboarding D5, R9
    @ResponseStatus(HttpStatus.OK)
    public RoomResponse createOrGetRoom(@Valid @RequestBody CreateRoomRequest req, HttpServletRequest http) {
        return RoomResponse.from(rooms.open(AuthenticatedUser.id(http), req.listingId()));
    }

    @GetMapping("/rooms")
    public List<RoomResponse> myRooms(HttpServletRequest http) {
        return rooms.myRooms(AuthenticatedUser.id(http)).stream()
                .map(RoomResponse::from)
                .toList();
    }

    @Operation(summary = "읽을 수 있는 채팅방 단건 조회", description = "알림 딥링크가 최근 100개 밖의 방도 안전하게 열 수 있도록 멤버십을 다시 검사합니다.")
    @GetMapping("/rooms/{roomId}")
    public ResolvedRoomResponse room(@PathVariable String roomId, HttpServletRequest http) {
        var resolved = rooms.resolve(roomId, AuthenticatedUser.id(http));
        if (resolved.isListing()) {
            return ResolvedRoomResponse.listing(RoomResponse.from(resolved.listingRoom()));
        }
        return ResolvedRoomResponse.social(
                SocialChatController.SocialRoomResponse.from(resolved.socialRoom(), resolved.supportTicket()));
    }

    @GetMapping("/unread-counts")
    public Map<String, Long> unreadCounts(HttpServletRequest http) {
        return reads.unreadCounts(AuthenticatedUser.id(http));
    }

    @PostMapping("/rooms/{roomId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(
            @PathVariable String roomId, @Valid @RequestBody MarkReadRequest req, HttpServletRequest http) {
        reads.markRead(roomId, AuthenticatedUser.id(http), req.lastMessageId());
    }

    @GetMapping("/rooms/{roomId}/messages")
    public List<MessageResponse> messages(
            @PathVariable String roomId,
            @RequestParam(required = false) Instant beforeSentAt,
            @RequestParam(required = false) String beforeId,
            @RequestParam(defaultValue = "60") int limit,
            HttpServletRequest http) {
        return messaging.history(roomId, AuthenticatedUser.id(http), beforeSentAt, beforeId, limit).stream()
                .map(MessageResponse::from)
                .toList();
    }

    @Operation(summary = "직거래 완료 확인", description = "구매자와 판매자가 각각 확인하면 매물을 판매 완료로 전환합니다.")
    @PostMapping("/rooms/{roomId}/direct-trade/confirmation")
    public RoomResponse confirmDirectTrade(@PathVariable String roomId, HttpServletRequest http) {
        return RoomResponse.from(directTrades.confirm(roomId, AuthenticatedUser.id(http)));
    }

    @DeleteMapping("/rooms/{roomId}/direct-trade/confirmation")
    public RoomResponse cancelDirectTradeConfirmation(@PathVariable String roomId, HttpServletRequest http) {
        return RoomResponse.from(directTrades.cancelConfirmation(roomId, AuthenticatedUser.id(http)));
    }

    @PostMapping("/rooms/{roomId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse sendMessage(
            @PathVariable String roomId, @Valid @RequestBody SendMessageRequest req, HttpServletRequest http) {
        return MessageResponse.from(messaging.sendFromUser(roomId, AuthenticatedUser.id(http), req.content()));
    }

    @Operation(
            summary = "실시간 메시지 SSE 스트림",
            description = "Server-Sent Events로 채팅방의 새 메시지를 실시간 수신합니다. "
                    + "이벤트 이름: `message`, 데이터: JSON `{id, senderId, content, sentAt}`")
    @GetMapping(value = "/rooms/{roomId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @PathVariable String roomId,
            @RequestParam(required = false) String afterId,
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId,
            HttpServletRequest http) {
        String actorId = AuthenticatedUser.id(http);
        socialChats.requireReadable(roomId, actorId);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        String channel = "chat:" + roomId;
        AtomicReference<Runnable> cleanupRef = new AtomicReference<>(() -> {});
        AtomicBoolean replaying = new AtomicBoolean(true);
        Queue<PubSubMessage> liveDuringReplay = new ConcurrentLinkedQueue<>();
        Object deliveryLock = new Object();

        MessageListener listener = (Message rawMsg, byte[] pattern) -> {
            try {
                // 연결 뒤 그룹 탈퇴·상담 이관이 발생할 수 있다. 이벤트마다 다시 멤버십을
                // 확인하지 않으면 제거된 사용자의 열린 SSE 연결로 새 메시지가 계속 샌다.
                socialChats.requireReadable(roomId, actorId);
                PubSubMessage payload = objectMapper.readValue(rawMsg.getBody(), PubSubMessage.class);
                synchronized (deliveryLock) {
                    if (replaying.get()) {
                        liveDuringReplay.add(payload);
                    } else {
                        sendEvent(emitter, payload);
                    }
                }
            } catch (IOException | RuntimeException e) {
                // Jackson 예외 메시지에는 원문 payload 일부가 들어갈 수 있으므로 채팅 내용을
                // 운영 로그에 복사하지 않고 예외 종류만 남긴다.
                log.warn(
                        "Chat SSE payload handling failed roomId={} errorType={}",
                        roomId,
                        e.getClass().getSimpleName());
                cleanupRef.get().run();
                emitter.completeWithError(e);
            }
        };

        ChannelTopic topic = new ChannelTopic(channel);
        Runnable cleanup = () -> listenerContainer.removeMessageListener(listener, topic);
        cleanupRef.set(cleanup);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError((e) -> cleanup.run());
        listenerContainer.addMessageListener(listener, topic);
        String replayAfter = lastEventId == null || lastEventId.isBlank() ? afterId : lastEventId;
        try {
            String cursor = replayAfter;
            int replayed = 0;
            boolean backlogRemains;
            do {
                List<ChatMessage> missed = messaging.after(roomId, actorId, cursor, REPLAY_BATCH_SIZE);
                for (ChatMessage message : missed) {
                    sendEvent(emitter, PubSubMessage.from(message));
                    cursor = message.id();
                }
                replayed += missed.size();
                backlogRemains = missed.size() == REPLAY_BATCH_SIZE;
            } while (backlogRemains && replayed < MAX_REPLAY_MESSAGES_PER_CONNECTION);

            if (backlogRemains) {
                // 한 연결이 무제한 이력을 점유하지 않게 끊는다. EventSource는 마지막 event id로
                // 자동 재연결하므로 다음 연결에서 나머지를 이어 받아 메시지 공백은 생기지 않는다.
                cleanup.run();
                emitter.complete();
                return emitter;
            }

            synchronized (deliveryLock) {
                PubSubMessage queued;
                while ((queued = liveDuringReplay.poll()) != null) {
                    sendEvent(emitter, queued);
                }
                replaying.set(false);
            }
        } catch (IOException | RuntimeException replayFailure) {
            cleanup.run();
            emitter.completeWithError(replayFailure);
        }
        return emitter;
    }

    private static void sendEvent(SseEmitter emitter, PubSubMessage payload) throws IOException {
        emitter.send(SseEmitter.event().id(payload.id()).name("message").data(payload, MediaType.APPLICATION_JSON));
    }

    /** buyerId/sellerId는 구버전 클라이언트 호환 필드이며 서버는 신뢰하지 않는다. */
    public record CreateRoomRequest(@NotBlank String listingId, String buyerId, String sellerId) {}

    public record SendMessageRequest(
            String senderId,

            @NotBlank @jakarta.validation.constraints.Size(max = 2000)
            String content) {}

    public record MarkReadRequest(@NotBlank String lastMessageId) {}

    private record PubSubMessage(String id, String senderId, String content, String sentAt) {

        private static PubSubMessage from(ChatMessage message) {
            return new PubSubMessage(
                    message.id(),
                    message.senderId(),
                    message.content(),
                    message.sentAt().toString());
        }
    }

    public record RoomResponse(
            String id,
            String listingId,
            String buyerId,
            String sellerId,
            String createdAt,
            String lastMessageAt,
            String buyerConfirmedAt,
            String sellerConfirmedAt,
            String directTradeCompletedAt) {

        public static RoomResponse from(ChatRoom room) {
            return new RoomResponse(
                    room.id(),
                    room.listingId(),
                    room.buyerId(),
                    room.sellerId(),
                    room.createdAt().toString(),
                    room.lastMessageAt().toString(),
                    instant(room.buyerConfirmedAt()),
                    instant(room.sellerConfirmedAt()),
                    instant(room.directTradeCompletedAt()));
        }

        private static String instant(Instant value) {
            return value == null ? null : value.toString();
        }
    }

    public record ResolvedRoomResponse(
            String kind, RoomResponse listingRoom, SocialChatController.SocialRoomResponse socialRoom) {

        static ResolvedRoomResponse listing(RoomResponse room) {
            return new ResolvedRoomResponse("LISTING", room, null);
        }

        static ResolvedRoomResponse social(SocialChatController.SocialRoomResponse room) {
            return new ResolvedRoomResponse("SOCIAL", null, room);
        }
    }

    public record MessageResponse(String id, String roomId, String senderId, String content, String sentAt) {

        public static MessageResponse from(ChatMessage message) {
            return new MessageResponse(
                    message.id(),
                    message.roomId(),
                    message.senderId(),
                    message.content(),
                    message.sentAt().toString());
        }
    }
}
