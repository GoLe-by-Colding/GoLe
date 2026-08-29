package com.gole.api.chat.application;

import com.gole.api.account.application.port.out.AccountRepositoryPort;
import com.gole.api.account.domain.model.Account;
import com.gole.api.chat.application.port.out.ChatBlockRepositoryPort;
import com.gole.api.chat.application.port.out.ChatReadStatePort;
import com.gole.api.chat.application.port.out.SocialChatRoomRepositoryPort;
import com.gole.api.chat.application.port.out.SupportTicketRepositoryPort;
import com.gole.api.chat.domain.model.ChatBlock;
import com.gole.api.chat.domain.model.ChatRoomType;
import com.gole.api.chat.domain.model.SocialChatRoom;
import com.gole.api.chat.domain.model.SupportTicket;
import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.common.exception.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 매물과 독립된 DIRECT·GROUP·SUPPORT 방의 생성과 접근 규칙을 담당한다. */
@Service
public class SocialChatService {

    private final SocialChatRoomRepositoryPort rooms;
    private final ChatBlockRepositoryPort blocks;
    private final SupportTicketRepositoryPort supportTickets;
    private final AccountRepositoryPort accounts;
    private final ChatReadStatePort readStates;
    private final Clock clock;

    public SocialChatService(
            SocialChatRoomRepositoryPort rooms,
            ChatBlockRepositoryPort blocks,
            SupportTicketRepositoryPort supportTickets,
            AccountRepositoryPort accounts,
            ChatReadStatePort readStates,
            Clock clock) {
        this.rooms = rooms;
        this.blocks = blocks;
        this.supportTickets = supportTickets;
        this.accounts = accounts;
        this.readStates = readStates;
        this.clock = clock;
    }

    public List<SocialChatRoom> mySocialRooms(String actorId, int limit) {
        requireAccount(actorId);
        return readableRooms(actorId, limit, rooms.findSocialByMember(actorId, limit));
    }

    /** 레거시 매물 방까지 합치되 SUPPORT 권한은 현재 티켓 담당자로 한 번에 재검사한다. */
    public List<SocialChatRoom> myReadableRooms(String actorId, int limit) {
        requireAccount(actorId);
        return readableRooms(actorId, limit, rooms.findByMember(actorId, limit));
    }

    /**
     * 방 문서의 SUPPORT 멤버 배열은 담당자 이관 직후 잠시 오래된 값일 수 있다. 따라서
     * 소셜 방 목록과 전체 읽기 목록 모두 현재 티켓을 권위 데이터로 사용해 이전 담당자를
     * 제거하고, 아직 방 문서에 반영되지 않은 현재 담당자는 보강한다.
     */
    private List<SocialChatRoom> readableRooms(String actorId, int limit, List<SocialChatRoom> memberRooms) {
        List<SupportTicket> participantTickets = supportTickets.findByParticipant(actorId, limit);
        LinkedHashSet<String> participantSupportRoomIds = participantTickets.stream()
                .map(SupportTicket::roomId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, SocialChatRoom> candidatesById = memberRooms.stream()
                .collect(Collectors.toMap(
                        SocialChatRoom::id,
                        Function.identity(),
                        (first, ignored) -> first,
                        java.util.LinkedHashMap::new));
        rooms.findByIds(List.copyOf(participantSupportRoomIds))
                .forEach(room -> candidatesById.putIfAbsent(room.id(), room));
        List<String> supportRoomIds = candidatesById.values().stream()
                .filter(room -> room.type() == ChatRoomType.SUPPORT)
                .map(SocialChatRoom::id)
                .toList();
        Map<String, SupportTicket> ticketsByRoom = new java.util.LinkedHashMap<>();
        participantTickets.forEach(ticket -> ticketsByRoom.put(ticket.roomId(), ticket));
        supportTickets.findByRoomIds(supportRoomIds).forEach(ticket -> ticketsByRoom.put(ticket.roomId(), ticket));
        return candidatesById.values().stream()
                .filter(room ->
                        room.type() != ChatRoomType.SUPPORT || canReadSupport(ticketsByRoom.get(room.id()), actorId))
                .sorted(java.util.Comparator.comparing(SocialChatRoom::lastMessageAt)
                        .reversed())
                .toList();
    }

    public SocialChatRoom createDirect(String actorId, String peerId) {
        requireCanStartPrivateConversation(actorId, peerId);

        String key = SocialChatRoom.directDedupeKey(actorId, peerId);
        var existing = rooms.findByDedupeKey(key);
        if (existing.isPresent()) {
            return existing.get();
        }

        SocialChatRoom room = SocialChatRoom.direct(UUID.randomUUID().toString(), actorId, peerId, Instant.now(clock));
        try {
            return rooms.save(room);
        } catch (ConflictException concurrentCreation) {
            return rooms.findByDedupeKey(key).orElseThrow(() -> concurrentCreation);
        }
    }

    public SocialChatRoom createGroup(String actorId, String title, List<String> inviteeIds) {
        requireRegularAccount(actorId);
        List<String> invitees = List.copyOf(new LinkedHashSet<>(inviteeIds == null ? List.of() : inviteeIds));
        for (String inviteeId : invitees) {
            requireRegularAccount(inviteeId);
        }
        LinkedHashSet<String> participantIds = new LinkedHashSet<>();
        participantIds.add(actorId);
        participantIds.addAll(invitees);
        List<String> participants = List.copyOf(participantIds);
        ensureNoBlockedPair(participants, participants);
        return rooms.save(
                SocialChatRoom.group(UUID.randomUUID().toString(), actorId, invitees, title, Instant.now(clock)));
    }

    @Transactional
    public SupportConversation createSupport(String actorId, String title) {
        requireAccount(actorId);
        Instant now = Instant.now(clock);
        SocialChatRoom room =
                rooms.save(SocialChatRoom.support(UUID.randomUUID().toString(), actorId, title, now));
        SupportTicket ticket = supportTickets.save(SupportTicket.opened(room.id(), actorId, now));
        return new SupportConversation(room, ticket);
    }

    @Transactional
    public SocialChatRoom invite(String roomId, String actorId, String inviteeId) {
        requireRegularAccount(actorId);
        requireRegularAccount(inviteeId);
        SocialChatRoom room = requireRoom(roomId);
        if (room.type() != ChatRoomType.GROUP) {
            throw new BadRequestException("CHAT_INVITE_NOT_ALLOWED", "그룹 대화방에만 초대할 수 있습니다");
        }
        ensureNoBlockedPair(List.of(inviteeId), room.memberIds());
        boolean joining = !room.isMember(inviteeId);
        Instant now = Instant.now(clock);
        SocialChatRoom updated = rooms.save(room.invite(actorId, inviteeId, now));
        if (joining) {
            readStates.initializeAtLatest(roomId, inviteeId, now);
        }
        return updated;
    }

    public SocialChatRoom leave(String roomId, String actorId) {
        SocialChatRoom room = requireRoom(roomId);
        if (room.type() != ChatRoomType.GROUP) {
            throw new BadRequestException("CHAT_LEAVE_NOT_ALLOWED", "그룹 대화방만 나갈 수 있습니다");
        }
        return rooms.save(room.leave(actorId, Instant.now(clock)));
    }

    public void block(String actorId, String targetId, String reason) {
        requireRegularAccount(actorId);
        requireRegularAccount(targetId);
        if (actorId.equals(targetId)) {
            throw new BadRequestException("CHAT_SELF_BLOCK", "자기 자신을 차단할 수 없습니다");
        }
        blocks.save(new ChatBlock(actorId, targetId, normalizeReason(reason), Instant.now(clock)));
    }

    public void unblock(String actorId, String targetId) {
        requireAccount(actorId);
        blocks.delete(actorId, targetId);
    }

    public List<String> myBlockedAccountIds(String actorId) {
        requireAccount(actorId);
        return blocks.blockedTargets(actorId);
    }

    public SocialChatRoom requireReadable(String roomId, String actorId) {
        SocialChatRoom room = requireRoom(roomId);
        if (room.type() != ChatRoomType.SUPPORT) {
            room.requireMember(actorId);
            return room;
        }

        // SUPPORT의 권위 데이터는 티켓이다. 방 멤버 배열이 이관 도중 오래된 값으로
        // 남더라도 이전 담당자가 본문이나 열린 SSE를 계속 읽을 수 없어야 한다.
        SupportTicket ticket = requireSupportTicket(room.id());
        if (!actorId.equals(ticket.requesterId()) && !actorId.equals(ticket.assigneeId())) {
            throw new ForbiddenException("SUPPORT_ACCESS_DENIED", "문의자와 현재 담당자만 대화를 볼 수 있습니다");
        }
        return room;
    }

    public SocialChatRoom requireSendable(String roomId, String actorId) {
        SocialChatRoom room = requireRoom(roomId);
        requireReadable(roomId, actorId);
        room.requireCanSend(actorId);
        Account actor = requireAccount(actorId);
        if (actor.isAdmin()) {
            // 관리자 답변은 감사 로그를 남기는 /api/admin/support 전용 경로만 쓴다.
            throw new ForbiddenException("CHAT_ADMIN_SEND_NOT_ALLOWED", "관리자 답변은 운영팀 문의 콘솔에서 보내야 합니다");
        }
        if (room.type() != ChatRoomType.SUPPORT && (!actor.isVerified() || actor.isSuspended())) {
            throw new ForbiddenException("CHAT_ACCOUNT_UNAVAILABLE", "현재 메시지를 보낼 수 없는 계정입니다");
        }
        // 차단은 1:1 대화를 양방향으로 멈춘다. 이미 존재하는 GROUP 전체를 한 쌍의
        // 차단 때문에 얼리지는 않는다. 대신 그룹 생성·초대 때 모든 멤버 쌍을 검사한다.
        if (room.type() == ChatRoomType.DIRECT || room.type() == ChatRoomType.LISTING) {
            ensureNotBlocked(room.memberIds().get(0), room.memberIds().get(1));
        }
        if (room.type() == ChatRoomType.SUPPORT) {
            SupportTicket ticket = requireSupportTicket(room.id());
            if (!actorId.equals(ticket.requesterId())) {
                throw new ForbiddenException("SUPPORT_REQUESTER_ONLY", "문의자는 사용자 채팅 화면에서 답변해야 합니다");
            }
        }
        return room;
    }

    /** 감사 로그가 묶이는 관리자 전용 경로에서만 호출하는 SUPPORT 발신 권한 검사. */
    public SocialChatRoom requireAdminSupportSendable(String roomId, String adminId) {
        SocialChatRoom room = requireRoom(roomId);
        if (room.type() != ChatRoomType.SUPPORT) {
            throw new BadRequestException("CHAT_NOT_SUPPORT_ROOM", "운영팀 문의방이 아닙니다");
        }
        Account admin = requireAccount(adminId);
        if (!admin.isAdmin() || admin.isSuspended()) {
            throw new ForbiddenException("ADMIN_ONLY", "관리자 권한이 필요합니다");
        }
        SupportTicket ticket = requireSupportTicket(roomId);
        if (!adminId.equals(ticket.assigneeId())) {
            throw new ForbiddenException("SUPPORT_ASSIGNEE_ONLY", "현재 담당 관리자만 답변할 수 있습니다");
        }
        if (!ticket.status().isOpen()) {
            throw new ForbiddenException("SUPPORT_ALREADY_RESOLVED", "완료된 문의를 재개한 뒤 답변해 주세요");
        }
        if (room.isClosed()) {
            throw new ForbiddenException("CHAT_ROOM_CLOSED", "종료된 대화방에는 메시지를 보낼 수 없습니다");
        }
        return room;
    }

    /** 메시지 저장 뒤 SUPPORT 상태를 발신자 역할에 맞게 갱신한다. */
    public void onMessageSent(SocialChatRoom room, String senderId) {
        if (room.type() != ChatRoomType.SUPPORT) {
            return;
        }
        SupportTicket ticket = supportTickets
                .findByRoomId(room.id())
                .orElseThrow(() -> new NotFoundException("SUPPORT_TICKET_NOT_FOUND", "운영팀 문의를 찾을 수 없습니다"));
        SupportTicket next = senderId.equals(ticket.requesterId())
                ? ticket.userReplied(Instant.now(clock))
                : ticket.agentReplied(Instant.now(clock));
        supportTickets.save(next);
    }

    public void touchActivity(String roomId, Instant occurredAt) {
        rooms.touchActivity(roomId, occurredAt);
    }

    public SocialChatRoom requireRoom(String roomId) {
        return rooms.findById(roomId).orElseThrow(() -> new NotFoundException("CHAT_ROOM_NOT_FOUND", "채팅방을 찾을 수 없습니다"));
    }

    /** 매물 문의를 포함한 모든 새 1:1 대화가 같은 계정·차단 정책을 쓰게 한다. */
    public void requireCanStartPrivateConversation(String actorId, String peerId) {
        requireRegularAccount(actorId);
        requireRegularAccount(peerId);
        ensureNotBlocked(actorId, peerId);
    }

    private SupportTicket requireSupportTicket(String roomId) {
        return supportTickets
                .findByRoomId(roomId)
                .orElseThrow(() -> new NotFoundException("SUPPORT_TICKET_NOT_FOUND", "운영팀 문의를 찾을 수 없습니다"));
    }

    private static boolean canReadSupport(SupportTicket ticket, String actorId) {
        return ticket != null && (actorId.equals(ticket.requesterId()) || actorId.equals(ticket.assigneeId()));
    }

    private Account requireAccount(String accountId) {
        return accounts.findById(accountId)
                .orElseThrow(() -> new NotFoundException("CHAT_ACCOUNT_NOT_FOUND", "대화 상대를 찾을 수 없습니다"));
    }

    private Account requireRegularAccount(String accountId) {
        Account account = requireAccount(accountId);
        if (account.isAdmin()) {
            throw new BadRequestException("CHAT_ADMIN_PRIVATE_ROOM_NOT_ALLOWED", "운영자에게는 운영팀 문의로 연락해 주세요");
        }
        if (!account.isVerified() || account.isSuspended()) {
            throw new BadRequestException("CHAT_ACCOUNT_UNAVAILABLE", "현재 대화할 수 없는 사용자입니다");
        }
        return account;
    }

    private void ensureNotBlocked(String a, String b) {
        if (blocks.blockedBetween(a, b)) {
            throw new ForbiddenException("CHAT_BLOCKED", "차단 관계인 사용자와는 대화할 수 없습니다");
        }
    }

    private void ensureNoBlockedPair(Collection<String> leftAccountIds, Collection<String> rightAccountIds) {
        if (blocks.blockedBetweenAny(leftAccountIds, rightAccountIds)) {
            throw new ForbiddenException("CHAT_BLOCKED", "차단 관계인 사용자와는 대화할 수 없습니다");
        }
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return reason.trim().substring(0, Math.min(reason.trim().length(), 200));
    }

    public record SupportConversation(SocialChatRoom room, SupportTicket ticket) {}
}
