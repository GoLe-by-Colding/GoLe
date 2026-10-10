package com.gole.api.chat.application.service;

import com.gole.api.chat.application.port.in.ChatMessagingUseCase;
import com.gole.api.chat.application.port.in.SupportConsoleUseCase;
import com.gole.api.chat.application.port.out.ChatAccountPort;
import com.gole.api.chat.application.port.out.SocialChatRoomRepositoryPort;
import com.gole.api.chat.application.port.out.SupportAdminActionPort;
import com.gole.api.chat.application.port.out.SupportInternalNotePort;
import com.gole.api.chat.application.port.out.SupportTicketRepositoryPort;
import com.gole.api.chat.domain.model.ChatAccount;
import com.gole.api.chat.domain.model.ChatMessage;
import com.gole.api.chat.domain.model.ChatRoomType;
import com.gole.api.chat.domain.model.SocialChatRoom;
import com.gole.api.chat.domain.model.SupportAdminAction;
import com.gole.api.chat.domain.model.SupportCategory;
import com.gole.api.chat.domain.model.SupportInternalNote;
import com.gole.api.chat.domain.model.SupportOperator;
import com.gole.api.chat.domain.model.SupportStatus;
import com.gole.api.chat.domain.model.SupportTicket;
import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.common.exception.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 운영팀 문의 인박스의 배정·이관·상태·답변·내부 메모를 담당한다. 조치와 감사 기록을 한 트랜잭션에 묶는다. */
@Service
public class SupportChatService implements SupportConsoleUseCase {

    private static final int TAKEOVER_REASON_MAX_LENGTH = 500;

    private final SocialChatRoomRepositoryPort rooms;
    private final SupportTicketRepositoryPort tickets;
    private final SupportInternalNotePort notes;
    private final ChatAccountPort accounts;
    private final ChatMessagingUseCase messaging;
    private final SupportAdminActionPort audit;
    private final Clock clock;

    public SupportChatService(
            SocialChatRoomRepositoryPort rooms,
            SupportTicketRepositoryPort tickets,
            SupportInternalNotePort notes,
            ChatAccountPort accounts,
            ChatMessagingUseCase messaging,
            SupportAdminActionPort audit,
            Clock clock) {
        this.rooms = rooms;
        this.tickets = tickets;
        this.notes = notes;
        this.accounts = accounts;
        this.messaging = messaging;
        this.audit = audit;
        this.clock = clock;
    }

    public List<SupportTicket> inbox(String adminId, SupportStatus status, int limit) {
        return inbox(adminId, status, null, limit);
    }

    @Override
    public List<SupportTicket> inbox(String adminId, SupportStatus status, SupportCategory category, int limit) {
        requireAdmin(adminId);
        return tickets.findByStatusAndCategory(status, category, limit);
    }

    @Override
    public long countUnassigned() {
        return tickets.countByStatus(SupportStatus.UNASSIGNED);
    }

    @Transactional
    @Override
    public SupportConversation assignToSelf(String roomId, SupportOperator operator) {
        String adminId = operator.id();
        requireAdmin(adminId);
        SupportTicket ticket = requireTicket(roomId);
        SocialChatRoom room = requireSupportRoom(roomId);
        if (ticket.assigneeId() != null && !ticket.assigneeId().equals(adminId)) {
            throw new ConflictException("SUPPORT_ALREADY_ASSIGNED", "다른 관리자가 담당 중인 문의입니다");
        }
        if (ticket.assigneeId() != null) {
            boolean membershipRepaired = !room.isMember(adminId);
            SocialChatRoom reconciled = membershipRepaired ? rooms.save(room.withSupportAgent(null, adminId)) : room;
            if (membershipRepaired) {
                audit.record(operator, SupportAdminAction.SUPPORT_ASSIGN, roomId, null);
            }
            return new SupportConversation(reconciled, ticket, membershipRepaired);
        }

        Instant now = Instant.now(clock);
        SupportTicket assignedTicket = tickets.save(ticket.assignTo(adminId, now));
        SocialChatRoom assignedRoom = rooms.save(room.withSupportAgent(null, adminId));
        audit.record(operator, SupportAdminAction.SUPPORT_ASSIGN, roomId, null);
        return new SupportConversation(assignedRoom, assignedTicket, true);
    }

    @Transactional
    @Override
    public SupportConversation transfer(String roomId, SupportOperator operator, String targetAdminId) {
        String actorId = operator.id();
        requireAdmin(actorId);
        requireAdmin(targetAdminId);
        if (actorId.equals(targetAdminId)) {
            throw new BadRequestException("SUPPORT_ALREADY_OWNED", "이미 내가 담당 중인 문의입니다");
        }
        SupportTicket ticket = requireAssignedTo(roomId, actorId);
        SocialChatRoom room = requireSupportRoom(roomId);
        Instant now = Instant.now(clock);
        SupportTicket transferredTicket = tickets.save(ticket.transferTo(targetAdminId, now));
        SocialChatRoom transferredRoom = rooms.save(room.withSupportAgent(ticket.assigneeId(), targetAdminId));
        audit.record(operator, SupportAdminAction.SUPPORT_TRANSFER, roomId, "assignee=" + targetAdminId);
        return new SupportConversation(transferredRoom, transferredTicket, true);
    }

    /**
     * 응답 불가·오배정 등으로 고립된 문의를 다른 관리자가 직접 인수한다.
     *
     * <p>일반 이관은 기존 담당자만 할 수 있다는 원칙을 유지한다. 인수는 그 원칙의 운영상 탈출구인
     * 만큼 타인에게 이미 배정된 미해결 문의와 명시적인 사유가 모두 있어야 한다.
     */
    @Transactional
    @Override
    public SupportTakeover takeOver(String roomId, SupportOperator operator, String rawReason) {
        String actorId = operator.id();
        requireAdmin(actorId);
        String reason = normalizeTakeoverReason(rawReason);
        SupportTicket ticket = requireTicket(roomId);
        String previousAssigneeId = ticket.assigneeId();
        if (previousAssigneeId == null) {
            throw new BadRequestException("SUPPORT_NOT_ASSIGNED", "미배정 문의는 '내가 맡기'로 배정해 주세요");
        }
        if (previousAssigneeId.equals(actorId)) {
            throw new BadRequestException("SUPPORT_ALREADY_OWNED", "이미 내가 담당 중인 문의입니다");
        }

        Instant now = Instant.now(clock);
        // transferTo가 RESOLVED 상태를 거부한다. 완료된 티켓을 몰래 다시 여는 우회로가 되지 않는다.
        SupportTicket nextTicket = ticket.transferTo(actorId, now);
        SocialChatRoom room = requireSupportRoom(roomId);
        SupportTicket takenTicket = tickets.save(nextTicket);
        SocialChatRoom takenRoom = rooms.save(room.withSupportAgent(previousAssigneeId, actorId));
        audit.record(
                operator,
                SupportAdminAction.SUPPORT_TAKEOVER,
                roomId,
                "previousAssignee=%s; reason=%s".formatted(previousAssigneeId, reason));
        return new SupportTakeover(takenRoom, takenTicket, previousAssigneeId, reason);
    }

    @Transactional
    @Override
    public SupportTransition resolve(String roomId, SupportOperator operator) {
        SupportTicket current = requireAssignedTo(roomId, operator.id());
        SupportTicket next = current.resolve(Instant.now(clock));
        if (next == current) {
            return new SupportTransition(current, false);
        }
        SupportTicket saved = tickets.save(next);
        audit.record(operator, SupportAdminAction.SUPPORT_RESOLVE, roomId, null);
        return new SupportTransition(saved, true);
    }

    @Transactional
    @Override
    public SupportTransition reopen(String roomId, SupportOperator operator) {
        String actorId = operator.id();
        requireAdmin(actorId);
        SupportTicket ticket = requireTicket(roomId);
        if (ticket.assigneeId() != null && !ticket.assigneeId().equals(actorId)) {
            throw new ForbiddenException("SUPPORT_ASSIGNEE_ONLY", "담당 관리자만 문의를 재개할 수 있습니다");
        }
        SupportTicket next = ticket.reopen(Instant.now(clock));
        if (next == ticket) {
            return new SupportTransition(ticket, false);
        }
        SupportTicket saved = tickets.save(next);
        audit.record(operator, SupportAdminAction.SUPPORT_REOPEN, roomId, null);
        return new SupportTransition(saved, true);
    }

    @Transactional
    @Override
    public ChatMessage reply(String roomId, SupportOperator operator, String content) {
        requireAssignedTo(roomId, operator.id());
        ChatMessage message = messaging.sendAdminSupport(roomId, operator.id(), content);
        audit.record(operator, SupportAdminAction.SUPPORT_REPLY, roomId, null);
        return message;
    }

    @Transactional
    @Override
    public void addNote(String roomId, SupportOperator operator, String note) {
        requireAssignedTo(roomId, operator.id());
        notes.append(roomId, operator.id(), note.trim(), Instant.now(clock));
        audit.record(operator, SupportAdminAction.SUPPORT_INTERNAL_NOTE, roomId, null);
    }

    @Override
    public List<SupportInternalNote> notes(String roomId, String actorId, int limit) {
        requireAssignedTo(roomId, actorId);
        return notes.findByRoom(roomId, limit);
    }

    @Override
    public SupportTicket requireAssignedTo(String roomId, String actorId) {
        requireAdmin(actorId);
        SupportTicket ticket = requireTicket(roomId);
        if (!actorId.equals(ticket.assigneeId())) {
            throw new ForbiddenException("SUPPORT_ASSIGNEE_ONLY", "담당 관리자만 이 문의를 처리할 수 있습니다");
        }
        return ticket;
    }

    private SupportTicket requireTicket(String roomId) {
        return tickets.findByRoomId(roomId)
                .orElseThrow(() -> new NotFoundException("SUPPORT_TICKET_NOT_FOUND", "운영팀 문의를 찾을 수 없습니다"));
    }

    private SocialChatRoom requireSupportRoom(String roomId) {
        SocialChatRoom room = rooms.findById(roomId)
                .orElseThrow(() -> new NotFoundException("CHAT_ROOM_NOT_FOUND", "채팅방을 찾을 수 없습니다"));
        if (room.type() != ChatRoomType.SUPPORT) {
            throw new NotFoundException("SUPPORT_TICKET_NOT_FOUND", "운영팀 문의를 찾을 수 없습니다");
        }
        return room;
    }

    private ChatAccount requireAdmin(String accountId) {
        ChatAccount account = accounts.findById(accountId)
                .orElseThrow(() -> new NotFoundException("SUPPORT_ADMIN_NOT_FOUND", "관리자 계정을 찾을 수 없습니다"));
        if (!account.isAdmin() || account.isSuspended()) {
            throw new ForbiddenException("ADMIN_ONLY", "관리자 권한이 필요합니다");
        }
        return account;
    }

    private static String normalizeTakeoverReason(String rawReason) {
        String reason = rawReason == null ? "" : rawReason.trim();
        if (reason.isEmpty()) {
            throw new BadRequestException("SUPPORT_TAKEOVER_REASON_REQUIRED", "문의 인수 사유를 입력해야 합니다");
        }
        if (reason.length() > TAKEOVER_REASON_MAX_LENGTH) {
            throw new BadRequestException(
                    "SUPPORT_TAKEOVER_REASON_TOO_LONG", "문의 인수 사유는 %d자 이하여야 합니다".formatted(TAKEOVER_REASON_MAX_LENGTH));
        }
        return reason;
    }
}
