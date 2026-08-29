package com.gole.api.chat.domain.model;

import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.exception.ForbiddenException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 유형을 가진 채팅방. 기존 {@link ChatRoom}(매물 전용)을 대체하지 않고 확장한다.
 *
 * <p>기존 레코드를 고치지 않고 새 모델을 둔 이유는 두 가지다. 매물 채팅 경로가 계속 살아 있어야
 * 하고(무중단), 매물 방의 필수 불변식({@code listingId != null})을 소셜 방에 강요할 수 없기 때문이다.
 *
 * <p>멤버십은 {@code memberIds} 하나로만 판단한다. 매물 방의 buyer/seller 도 결국 멤버 두 명이며,
 * 권한 질문("이 사람이 읽어도 되나")의 답이 유형마다 갈라지면 어딘가는 반드시 빠뜨린다.
 */
public final class SocialChatRoom {

    private final String id;
    private final ChatRoomType type;
    private final List<String> memberIds;
    private final String ownerId;
    private final String title;
    private final String listingId;
    private final Instant createdAt;
    private final Instant lastMessageAt;
    private final Instant closedAt;
    private final long version;

    public SocialChatRoom(
            String id,
            ChatRoomType type,
            List<String> memberIds,
            String ownerId,
            String title,
            String listingId,
            Instant createdAt,
            Instant closedAt) {
        this(id, type, memberIds, ownerId, title, listingId, createdAt, createdAt, closedAt, 0L);
    }

    public SocialChatRoom(
            String id,
            ChatRoomType type,
            List<String> memberIds,
            String ownerId,
            String title,
            String listingId,
            Instant createdAt,
            Instant lastMessageAt,
            Instant closedAt,
            long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.memberIds = List.copyOf(new LinkedHashSet<>(Objects.requireNonNull(memberIds, "memberIds")));
        this.ownerId = ownerId;
        this.title = title;
        this.listingId = listingId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.lastMessageAt = lastMessageAt == null ? createdAt : lastMessageAt;
        this.closedAt = closedAt;
        this.version = version;
        if (type == ChatRoomType.LISTING && listingId == null) {
            throw new IllegalArgumentException("매물 방에는 listingId 가 필요하다");
        }
        // 최소 인원은 생성 팩토리에서만 본다. 저장된 상태는 언제나 다시 읽을 수 있어야 하므로
        // 재구성 경로에서 검사하면, 사람이 나가 정원 미달이 된 방을 아예 못 읽게 된다.
        // 살아 있는 방에 멤버가 하나도 없는 것만 막는다 — 그건 아무도 닿을 수 없는 유령 방이다.
        if (this.memberIds.isEmpty() && closedAt == null) {
            throw new IllegalArgumentException("열려 있는 방에는 멤버가 최소 1명 있어야 한다");
        }
    }

    /** 두 사용자의 1:1 방. 자기 자신과는 만들 수 없다. */
    public static SocialChatRoom direct(String id, String userA, String userB, Instant createdAt) {
        requireParticipant(userA);
        requireParticipant(userB);
        if (Objects.equals(userA, userB)) {
            throw new BadRequestException("CHAT_SELF_DM", "자기 자신과는 대화방을 만들 수 없습니다");
        }
        return new SocialChatRoom(id, ChatRoomType.DIRECT, List.of(userA, userB), null, null, null, createdAt, null);
    }

    /** 방장과 초대 대상들로 구성된 그룹. 방장도 멤버다. */
    public static SocialChatRoom group(
            String id, String ownerId, List<String> invitees, String title, Instant createdAt) {
        Set<String> members = new LinkedHashSet<>();
        members.add(ownerId);
        members.addAll(invitees == null ? List.of() : invitees);
        if (title == null || title.isBlank()) {
            throw new BadRequestException("CHAT_GROUP_TITLE_REQUIRED", "그룹 대화방 제목을 입력해야 합니다");
        }
        members.forEach(SocialChatRoom::requireParticipant);
        // 2명짜리 그룹은 DIRECT 와 같은 것인데 규칙만 다르다(초대·나가기·방장). 두 갈래로
        // 표현되면 "이 둘의 대화"가 방 두 개로 갈라져 멱등이 깨진다.
        if (members.size() < ChatRoomType.GROUP.minMembers()) {
            throw new BadRequestException(
                    "CHAT_GROUP_TOO_SMALL",
                    "그룹 대화방은 %d명 이상이어야 합니다. 두 명이면 1:1 대화를 이용하세요".formatted(ChatRoomType.GROUP.minMembers()));
        }
        if (members.size() > ChatRoomType.GROUP.maxMembers()) {
            throw new BadRequestException(
                    "CHAT_GROUP_FULL", "그룹 대화방은 최대 %d명까지 참여할 수 있습니다".formatted(ChatRoomType.GROUP.maxMembers()));
        }
        return new SocialChatRoom(
                id, ChatRoomType.GROUP, List.copyOf(members), ownerId, title.trim(), null, createdAt, null);
    }

    /** 운영팀 문의방. 생성 시점에는 사용자 혼자이고 배정될 때 관리자가 합류한다. */
    public static SocialChatRoom support(String id, String requesterId, String title, Instant createdAt) {
        return new SocialChatRoom(
                id,
                ChatRoomType.SUPPORT,
                List.of(requesterId),
                requesterId,
                title == null || title.isBlank() ? "운영팀 문의" : title.trim(),
                null,
                createdAt,
                null);
    }

    /** 레거시 매물 방을 새 모델로 읽는다. 저장된 문서를 고치지 않고도 같은 규칙을 적용하기 위한 것이다. */
    public static SocialChatRoom listing(
            String id, String listingId, String buyerId, String sellerId, Instant createdAt) {
        return new SocialChatRoom(
                id, ChatRoomType.LISTING, List.of(buyerId, sellerId), null, null, listingId, createdAt, null);
    }

    public String id() {
        return id;
    }

    public ChatRoomType type() {
        return type;
    }

    public List<String> memberIds() {
        return memberIds;
    }

    public String ownerId() {
        return ownerId;
    }

    public String title() {
        return title;
    }

    public String listingId() {
        return listingId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant lastMessageAt() {
        return lastMessageAt;
    }

    public Instant closedAt() {
        return closedAt;
    }

    public long version() {
        return version;
    }

    public boolean isClosed() {
        return closedAt != null;
    }

    public boolean isMember(String accountId) {
        return accountId != null && memberIds.contains(accountId);
    }

    /**
     * 읽기 권한을 확인한다.
     *
     * <p>관리자라는 이유로 통과시키지 않는다 — 운영 권한은 조치를 위한 것이지 사생활 열람권이
     * 아니다. 신고로 인한 열람은 스냅샷 경로를 쓴다.
     */
    public void requireMember(String accountId) {
        if (!isMember(accountId)) {
            throw new ForbiddenException("CHAT_NOT_A_MEMBER", "이 대화방에 접근할 수 없습니다");
        }
    }

    /** 전송 권한. 닫힌 방에는 아무도 보내지 못한다. */
    public void requireCanSend(String accountId) {
        requireMember(accountId);
        if (isClosed()) {
            throw new ForbiddenException("CHAT_ROOM_CLOSED", "종료된 대화방에는 메시지를 보낼 수 없습니다");
        }
    }

    /** 직거래 완료 확인 가능 여부. 매물 방이 아니면 거부한다. */
    public void requireDirectTradeAllowed() {
        if (!type.allowsDirectTradeConfirmation()) {
            throw new BadRequestException("CHAT_DIRECT_TRADE_NOT_ALLOWED", "매물 대화방에서만 직거래 완료를 확인할 수 있습니다");
        }
    }

    public SocialChatRoom invite(String inviterId, String inviteeId, Instant now) {
        if (!type.allowsInvitation()) {
            throw new BadRequestException("CHAT_INVITE_NOT_ALLOWED", "이 대화방에는 초대할 수 없습니다");
        }
        requireMember(inviterId);
        requireParticipant(inviteeId);
        if (isMember(inviteeId)) {
            return this; // 이미 멤버면 그대로 — 초대는 멱등하다.
        }
        if (memberIds.size() >= type.maxMembers()) {
            throw new BadRequestException("CHAT_GROUP_FULL", "그룹 대화방 정원 %d명에 도달했습니다".formatted(type.maxMembers()));
        }
        List<String> next = new ArrayList<>(memberIds);
        next.add(inviteeId);
        return new SocialChatRoom(
                id, type, next, ownerId, title, listingId, createdAt, lastMessageAt, closedAt, version);
    }

    /**
     * 나가기.
     *
     * <p>방장이 나가면 가장 오래된 잔여 멤버가 승계한다. 남는 사람이 없으면 방을 닫는다 —
     * 멤버가 0인 방을 살려 두면 아무도 접근할 수 없는 채로 조회 결과에만 남는다.
     */
    public SocialChatRoom leave(String accountId, Instant now) {
        requireMember(accountId);
        if (type == ChatRoomType.LISTING || type == ChatRoomType.DIRECT) {
            throw new BadRequestException("CHAT_LEAVE_NOT_ALLOWED", "이 대화방은 나갈 수 없습니다");
        }
        List<String> next = new ArrayList<>(memberIds);
        next.remove(accountId);
        if (next.isEmpty()) {
            // 마지막 사람이 나가면 방을 닫고 멤버를 비운다. 나간 사람을 멤버로 남겨 두면
            // "나갔는데 계속 보이는" 방이 되고, 아무도 없는 방을 열어 두면 조회 결과에만
            // 남는 유령이 된다. 대화 원본은 신고 스냅샷·감사 경로에 그대로 보존된다.
            return new SocialChatRoom(
                    id, type, List.of(), ownerId, title, listingId, createdAt, lastMessageAt, now, version);
        }
        String nextOwner = Objects.equals(ownerId, accountId) ? next.getFirst() : ownerId;
        return new SocialChatRoom(
                id, type, next, nextOwner, title, listingId, createdAt, lastMessageAt, closedAt, version);
    }

    /** 배정된 관리자를 SUPPORT 방의 멤버로 넣는다. 배정돼야 대화 본문을 볼 수 있다. */
    public SocialChatRoom withSupportAgent(String previousAgentId, String agentId) {
        if (type != ChatRoomType.SUPPORT) {
            throw new BadRequestException("CHAT_NOT_SUPPORT_ROOM", "운영팀 문의방이 아닙니다");
        }
        requireParticipant(agentId);
        List<String> next = new ArrayList<>(memberIds);
        // 이관이면 이전 담당자는 멤버에서 빠진다 — 넘긴 뒤에도 계속 읽히면 이관이 아니라 공유다.
        // 단 문의자(ownerId)는 어떤 경우에도 빼지 않는다. 담당자 교체 때문에 문의한 사람이
        // 자기 문의를 못 보게 되는 건 명백한 사고다.
        if (previousAgentId != null && !previousAgentId.equals(ownerId)) {
            next.remove(previousAgentId);
        }
        if (!next.contains(agentId)) {
            next.add(agentId);
        }
        return new SocialChatRoom(
                id, type, next, ownerId, title, listingId, createdAt, lastMessageAt, closedAt, version);
    }

    /**
     * DIRECT 방 멱등 키. 참여자 순서와 무관하게 같은 값이 나오도록 정렬한다.
     *
     * <p>빈 값을 그대로 통과시키면 {@code DIRECT::} 같은 키가 생기고, 서로 다른 "미상" 사용자들이
     * 같은 방으로 합쳐진다. 키를 만들기 전에 막는다.
     */
    public static String directDedupeKey(String userA, String userB) {
        requireParticipant(userA);
        requireParticipant(userB);
        return userA.compareTo(userB) <= 0 ? "DIRECT:" + userA + ":" + userB : "DIRECT:" + userB + ":" + userA;
    }

    /** 이 방의 멱등 키. DIRECT 만 값을 갖는다(나머지는 중복 개념이 없다). */
    public String dedupeKey() {
        return type == ChatRoomType.DIRECT ? directDedupeKey(memberIds.get(0), memberIds.get(1)) : null;
    }

    private static void requireParticipant(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            throw new BadRequestException("CHAT_PARTICIPANT_REQUIRED", "대화 상대를 지정해야 합니다");
        }
    }
}
