package com.gole.api.chat.adapter.in.web;

import com.gole.api.admin.adapter.in.web.AdminActor;
import com.gole.api.admin.application.port.in.RecordAdminActionUseCase;
import com.gole.api.admin.application.port.in.RecordAdminActionUseCase.RecordAdminActionCommand;
import com.gole.api.admin.domain.model.AdminActionType;
import com.gole.api.admin.domain.model.AdminTargetType;
import com.gole.api.chat.application.ChatMessagingService;
import com.gole.api.chat.application.SocialChatService;
import com.gole.api.chat.application.SupportChatService;
import com.gole.api.chat.application.port.out.SupportInternalNotePort;
import com.gole.api.chat.domain.model.ChatMessage;
import com.gole.api.chat.domain.model.SupportStatus;
import com.gole.api.chat.domain.model.SupportTicket;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 운영자 전용 문의 인박스. 본문은 담당자로 배정된 뒤에만 읽고 답한다. */
@Tag(name = "Admin · Support", description = "운영팀 문의 배정·답변·이관·내부 메모")
@RestController
@RequestMapping("/api/admin/support")
public class AdminSupportController {

    private final SupportChatService support;
    private final SocialChatService rooms;
    private final ChatMessagingService messaging;
    private final RecordAdminActionUseCase audit;

    public AdminSupportController(
            SupportChatService support,
            SocialChatService rooms,
            ChatMessagingService messaging,
            RecordAdminActionUseCase audit) {
        this.support = support;
        this.rooms = rooms;
        this.messaging = messaging;
        this.audit = audit;
    }

    @Operation(summary = "문의 인박스", description = "메타데이터만 조회하며 대화 본문은 배정 후 별도 조회합니다.")
    @GetMapping
    public List<TicketResponse> inbox(
            @RequestParam(required = false) SupportStatus status,
            @RequestParam(defaultValue = "50") int limit,
            HttpServletRequest http) {
        AdminActor actor = AdminActor.of(http);
        return support.inbox(actor.id(), status, limit).stream()
                .map(this::response)
                .toList();
    }

    @PostMapping("/{roomId}/assign")
    @Transactional
    public TicketResponse assign(@PathVariable String roomId, HttpServletRequest http) {
        AdminActor actor = AdminActor.of(http);
        var conversation = support.assignToSelf(roomId, actor.id());
        record(actor, AdminActionType.SUPPORT_ASSIGN, roomId, null);
        return TicketResponse.from(conversation.ticket(), conversation.room().title());
    }

    @PostMapping("/{roomId}/transfer")
    @Transactional
    public TicketResponse transfer(
            @PathVariable String roomId, @Valid @RequestBody TransferRequest request, HttpServletRequest http) {
        AdminActor actor = AdminActor.of(http);
        var conversation = support.transfer(roomId, actor.id(), request.assigneeId());
        record(actor, AdminActionType.SUPPORT_TRANSFER, roomId, "assignee=" + request.assigneeId());
        return TicketResponse.from(conversation.ticket(), conversation.room().title());
    }

    @Operation(summary = "문의 강제 인수", description = "응답 불가 또는 오배정된 타인 담당 미해결 문의를 사유와 감사 기록을 남기고 인수합니다.")
    @PostMapping("/{roomId}/takeover")
    @Transactional
    public TicketResponse takeOver(
            @PathVariable String roomId, @Valid @RequestBody TakeoverRequest request, HttpServletRequest http) {
        AdminActor actor = AdminActor.of(http);
        var takeover = support.takeOver(roomId, actor.id(), request.reason());
        record(
                actor,
                AdminActionType.SUPPORT_TAKEOVER,
                roomId,
                "previousAssignee=%s; reason=%s".formatted(takeover.previousAssigneeId(), takeover.reason()));
        return TicketResponse.from(takeover.ticket(), takeover.room().title());
    }

    @PostMapping("/{roomId}/resolve")
    @Transactional
    public TicketResponse resolve(@PathVariable String roomId, HttpServletRequest http) {
        AdminActor actor = AdminActor.of(http);
        SupportTicket ticket = support.resolve(roomId, actor.id());
        record(actor, AdminActionType.SUPPORT_RESOLVE, roomId, null);
        return response(ticket);
    }

    @PostMapping("/{roomId}/reopen")
    @Transactional
    public TicketResponse reopen(@PathVariable String roomId, HttpServletRequest http) {
        AdminActor actor = AdminActor.of(http);
        SupportTicket ticket = support.reopen(roomId, actor.id());
        record(actor, AdminActionType.SUPPORT_REOPEN, roomId, null);
        return response(ticket);
    }

    @GetMapping("/{roomId}/messages")
    public List<ChatController.MessageResponse> messages(
            @PathVariable String roomId,
            @RequestParam(required = false) Instant beforeSentAt,
            @RequestParam(required = false) String beforeId,
            @RequestParam(defaultValue = "60") int limit,
            HttpServletRequest http) {
        AdminActor actor = AdminActor.of(http);
        support.requireAssignedTo(roomId, actor.id());
        return messaging.history(roomId, actor.id(), beforeSentAt, beforeId, limit).stream()
                .map(ChatController.MessageResponse::from)
                .toList();
    }

    @PostMapping("/{roomId}/messages")
    @Transactional
    public ChatController.MessageResponse reply(
            @PathVariable String roomId, @Valid @RequestBody MessageRequest request, HttpServletRequest http) {
        AdminActor actor = AdminActor.of(http);
        support.requireAssignedTo(roomId, actor.id());
        ChatMessage message = messaging.sendAdminSupport(roomId, actor.id(), request.content());
        record(actor, AdminActionType.SUPPORT_REPLY, roomId, null);
        return ChatController.MessageResponse.from(message);
    }

    @GetMapping("/{roomId}/notes")
    public List<NoteResponse> notes(@PathVariable String roomId, HttpServletRequest http) {
        AdminActor actor = AdminActor.of(http);
        return support.notes(roomId, actor.id(), 100).stream()
                .map(NoteResponse::from)
                .toList();
    }

    @PostMapping("/{roomId}/notes")
    @Transactional
    public ResponseEntity<Void> addNote(
            @PathVariable String roomId, @Valid @RequestBody NoteRequest request, HttpServletRequest http) {
        AdminActor actor = AdminActor.of(http);
        support.addNote(roomId, actor.id(), request.note());
        record(actor, AdminActionType.SUPPORT_INTERNAL_NOTE, roomId, null);
        return ResponseEntity.noContent().build();
    }

    private TicketResponse response(SupportTicket ticket) {
        return TicketResponse.from(ticket, rooms.requireRoom(ticket.roomId()).title());
    }

    private void record(AdminActor actor, AdminActionType type, String roomId, String reason) {
        audit.record(new RecordAdminActionCommand(
                actor.id(), actor.email(), type, AdminTargetType.SUPPORT_TICKET, roomId, reason));
    }

    public record TransferRequest(@NotBlank String assigneeId) {}

    public record TakeoverRequest(@NotBlank(message = "문의 인수 사유를 입력해야 합니다") @Size(max = 500) String reason) {}

    public record MessageRequest(@NotBlank @Size(max = 2000) String content) {}

    public record NoteRequest(@NotBlank @Size(max = 2000) String note) {}

    public record TicketResponse(
            String roomId,
            String requesterId,
            String title,
            String status,
            String assigneeId,
            String createdAt,
            String updatedAt,
            String resolvedAt) {

        static TicketResponse from(SupportTicket ticket, String title) {
            return new TicketResponse(
                    ticket.roomId(),
                    ticket.requesterId(),
                    title,
                    ticket.status().name(),
                    ticket.assigneeId(),
                    ticket.createdAt().toString(),
                    ticket.updatedAt().toString(),
                    ticket.resolvedAt() == null ? null : ticket.resolvedAt().toString());
        }
    }

    public record NoteResponse(String id, String authorId, String note, String createdAt) {

        static NoteResponse from(SupportInternalNotePort.InternalNote note) {
            return new NoteResponse(
                    note.id(), note.authorId(), note.note(), note.createdAt().toString());
        }
    }
}
