package com.gole.api.chat.adapter.in.web;

import com.gole.api.account.adapter.in.web.AuthenticatedUser;
import com.gole.api.chat.application.ChatMessagingService;
import com.gole.api.chat.application.SocialChatService;
import com.gole.api.chat.application.port.out.SupportTicketRepositoryPort;
import com.gole.api.chat.domain.model.ChatRoomType;
import com.gole.api.chat.domain.model.SocialChatRoom;
import com.gole.api.chat.domain.model.SupportCategory;
import com.gole.api.chat.domain.model.SupportTicket;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 매물과 독립된 1:1·그룹·운영팀 문의 채팅 API. */
@Tag(name = "Social Chat", description = "사용자 1:1·그룹·운영팀 문의")
@RestController
@RequestMapping("/api/v1/chat/social")
public class SocialChatController {

    private final SocialChatService chats;
    private final ChatMessagingService messaging;
    private final SupportTicketRepositoryPort supportTickets;

    public SocialChatController(
            SocialChatService chats, ChatMessagingService messaging, SupportTicketRepositoryPort supportTickets) {
        this.chats = chats;
        this.messaging = messaging;
        this.supportTickets = supportTickets;
    }

    @Operation(summary = "내 소셜 채팅방", description = "1:1·그룹·운영팀 문의방을 최근 생성순으로 조회합니다.")
    @GetMapping("/rooms")
    public List<SocialRoomResponse> myRooms(HttpServletRequest http) {
        List<SocialChatRoom> rooms = chats.mySocialRooms(AuthenticatedUser.id(http), 100);
        List<String> supportRoomIds = rooms.stream()
                .filter(room -> room.type() == ChatRoomType.SUPPORT)
                .map(SocialChatRoom::id)
                .toList();
        Map<String, SupportTicket> ticketByRoom = supportTickets.findByRoomIds(supportRoomIds).stream()
                .collect(Collectors.toUnmodifiableMap(SupportTicket::roomId, Function.identity()));
        return rooms.stream()
                .map(room -> SocialRoomResponse.from(room, ticketByRoom.get(room.id())))
                .toList();
    }

    @Operation(summary = "1:1 대화 시작", description = "같은 상대와는 기존 방을 반환합니다.")
    @PostMapping("/rooms/direct")
    public SocialRoomResponse createDirect(@Valid @RequestBody CreateDirectRequest request, HttpServletRequest http) {
        return response(chats.createDirect(AuthenticatedUser.id(http), request.peerId()));
    }

    @Operation(summary = "그룹 대화방 만들기", description = "방장 포함 3명 이상이어야 합니다.")
    @PostMapping("/rooms/group")
    @ResponseStatus(HttpStatus.CREATED)
    public SocialRoomResponse createGroup(@Valid @RequestBody CreateGroupRequest request, HttpServletRequest http) {
        return response(chats.createGroup(AuthenticatedUser.id(http), request.title(), request.memberIds()));
    }

    @Operation(summary = "운영팀 문의 시작", description = "문의방과 티켓을 만들고 첫 메시지를 함께 저장합니다.")
    @PostMapping("/rooms/support")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public SocialRoomResponse createSupport(@Valid @RequestBody CreateSupportRequest request, HttpServletRequest http) {
        String actorId = AuthenticatedUser.id(http);
        var conversation = chats.createSupport(actorId, request.title(), request.category());
        messaging.send(conversation.room().id(), actorId, request.message());
        return SocialRoomResponse.from(conversation.room(), conversation.ticket());
    }

    @Operation(summary = "그룹 멤버 초대")
    @PostMapping("/rooms/{roomId}/members")
    public SocialRoomResponse invite(
            @PathVariable String roomId, @Valid @RequestBody InviteMemberRequest request, HttpServletRequest http) {
        return response(chats.invite(roomId, AuthenticatedUser.id(http), request.accountId()));
    }

    @Operation(summary = "그룹 대화방 나가기")
    @DeleteMapping("/rooms/{roomId}/members/me")
    public SocialRoomResponse leave(@PathVariable String roomId, HttpServletRequest http) {
        return response(chats.leave(roomId, AuthenticatedUser.id(http)));
    }

    @Operation(summary = "사용자 차단", description = "기존 대화는 보존하고 1:1 새 전송을 양방향으로 막습니다. 공동 그룹에서는 차단 대상 메시지만 숨깁니다.")
    @PostMapping("/blocks/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(
            @PathVariable String accountId,
            @RequestBody(required = false) BlockRequest request,
            HttpServletRequest http) {
        chats.block(AuthenticatedUser.id(http), accountId, request == null ? null : request.reason());
    }

    @Operation(summary = "내 차단 목록", description = "현재 사용자가 직접 차단한 계정 ID만 반환합니다.")
    @GetMapping("/blocks")
    public List<String> myBlocks(HttpServletRequest http) {
        return chats.myBlockedAccountIds(AuthenticatedUser.id(http));
    }

    @Operation(summary = "사용자 차단 해제")
    @DeleteMapping("/blocks/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@PathVariable String accountId, HttpServletRequest http) {
        chats.unblock(AuthenticatedUser.id(http), accountId);
    }

    private SocialRoomResponse response(SocialChatRoom room) {
        SupportTicket ticket = supportTickets.findByRoomId(room.id()).orElse(null);
        return SocialRoomResponse.from(room, ticket);
    }

    public record CreateDirectRequest(@NotBlank String peerId) {}

    public record CreateGroupRequest(
            @NotBlank @Size(max = 80) String title, @Size(min = 2, max = 49) List<@NotBlank String> memberIds) {}

    public record CreateSupportRequest(
            @NotBlank @Size(max = 100) String title,
            @NotBlank @Size(max = 2000) String message,
            SupportCategory category) {

        public CreateSupportRequest(String title, String message) {
            this(title, message, SupportCategory.GENERAL);
        }
    }

    public record InviteMemberRequest(@NotBlank String accountId) {}

    public record BlockRequest(@Size(max = 200) String reason) {}

    public record SocialRoomResponse(
            String id,
            String type,
            List<String> memberIds,
            String ownerId,
            String title,
            String listingId,
            String createdAt,
            String lastMessageAt,
            String closedAt,
            String supportStatus,
            String assigneeId,
            String supportCategory,
            String responseDueAt) {

        static SocialRoomResponse from(SocialChatRoom room, SupportTicket ticket) {
            return new SocialRoomResponse(
                    room.id(),
                    room.type().name(),
                    room.memberIds(),
                    room.ownerId(),
                    room.title(),
                    room.listingId(),
                    room.createdAt().toString(),
                    room.lastMessageAt().toString(),
                    instant(room.closedAt()),
                    ticket == null ? null : ticket.status().name(),
                    ticket == null ? null : ticket.assigneeId(),
                    ticket == null ? null : ticket.category().name(),
                    ticket == null ? null : instant(ticket.responseDueAt()));
        }

        private static String instant(Instant value) {
            return value == null ? null : value.toString();
        }
    }
}
