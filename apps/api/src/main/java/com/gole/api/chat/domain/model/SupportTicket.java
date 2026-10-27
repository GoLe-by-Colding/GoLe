package com.gole.api.chat.domain.model;

import com.gole.api.common.exception.BadRequestException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 운영팀 문의방의 처리 상태 묶음. 방({@link SocialChatRoom})과 분리한 이유는, 배정·상태는
 * 운영 데이터라 사용자에게 나가는 방 정보와 수명도 노출 범위도 다르기 때문이다.
 */
public record SupportTicket(
        String roomId,
        String requesterId,
        SupportCategory category,
        SupportStatus status,
        String assigneeId,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt,
        long version,
        List<SupportNotice> notices) {

    /** 구버전 문의에는 실제 안내 근거를 추정해서 채우지 않는다. */
    public SupportTicket(
            String roomId,
            String requesterId,
            SupportCategory category,
            SupportStatus status,
            String assigneeId,
            Instant createdAt,
            Instant updatedAt,
            Instant resolvedAt,
            long version) {
        this(roomId, requesterId, category, status, assigneeId, createdAt, updatedAt, resolvedAt, version, List.of());
    }

    /** 영속성 버전이 없던 호출부와 테스트를 위한 신규 티켓 생성 생성자. */
    public SupportTicket(
            String roomId,
            String requesterId,
            SupportStatus status,
            String assigneeId,
            Instant createdAt,
            Instant updatedAt,
            Instant resolvedAt) {
        this(roomId, requesterId, SupportCategory.GENERAL, status, assigneeId, createdAt, updatedAt, resolvedAt, 0L);
    }

    public SupportTicket {
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(requesterId, "requesterId");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        notices = notices == null ? List.of() : List.copyOf(notices);
    }

    public static SupportTicket opened(String roomId, String requesterId, Instant now) {
        return opened(roomId, requesterId, SupportCategory.GENERAL, now);
    }

    public static SupportTicket opened(String roomId, String requesterId, SupportCategory category, Instant now) {
        return new SupportTicket(roomId, requesterId, category, SupportStatus.UNASSIGNED, null, now, now, null, 0L);
    }

    /**
     * 모든 소비자 불만·분쟁 문의의 진행 경과 안내를 놓치지 않기 위한 내부 목표다.
     *
     * <p>법정 기준은 3영업일이지만 공휴일 달력을 임의로 구현하지 않고 접수 후 72시간으로 더 이르게
     * 잡는다. 이 값은 자동 해결·삭제 기한이 아니다.
     */
    public Instant progressDueAt() {
        return SupportNoticeKind.PROGRESS.dueAt(createdAt);
    }

    /**
     * 모든 소비자 불만·분쟁 문의의 조사 결과 또는 처리방안 안내를 위한 내부 목표다.
     *
     * <p>법정 기준은 10영업일이지만 접수 후 240시간으로 더 이르게 잡는다. 개인정보 권리 요청에도
     * 같은 보수적 목표를 적용하며, 실제 해결 완료를 보장하는 시각은 아니다.
     */
    public Instant responseDueAt() {
        return SupportNoticeKind.RESULT.dueAt(createdAt);
    }

    /** 결과 안내도 진행 상황을 포함한다. 반복 안내가 최초 이행 시각을 덮어쓰지 않는다. */
    public Instant firstNoticeAt(SupportNoticeKind kind) {
        return notices.stream()
                .filter(notice -> kind == SupportNoticeKind.PROGRESS || notice.kind() == kind)
                .map(SupportNotice::sentAt)
                .min(Instant::compareTo)
                .orElse(null);
    }

    public SupportTicket recordNotice(SupportNoticeKind kind, ChatMessage message) {
        Objects.requireNonNull(kind, "kind");
        if (!roomId.equals(message.roomId())
                || !Objects.equals(assigneeId, message.senderId())
                || message.sentAt().isBefore(createdAt)) {
            throw new BadRequestException("SUPPORT_NOTICE_MESSAGE_MISMATCH", "현재 담당자가 이 문의에 보낸 답변만 안내로 기록할 수 있습니다");
        }
        if (notices.stream().anyMatch(notice -> notice.messageId().equals(message.id()))) return this;
        List<SupportNotice> next = new ArrayList<>(notices);
        next.add(new SupportNotice(kind, message.id(), message.senderId(), message.sentAt()));
        return new SupportTicket(
                roomId, requesterId, category, status, assigneeId, createdAt, updatedAt, resolvedAt, version, next);
    }

    /** 관리자가 가져간다. 완료된 문의는 재개 후에 배정한다. */
    public SupportTicket assignTo(String agentId, Instant now) {
        if (status != SupportStatus.UNASSIGNED || assigneeId != null) {
            throw new BadRequestException("SUPPORT_ALREADY_ASSIGNED", "미배정 문의만 담당자를 지정할 수 있습니다");
        }
        return new SupportTicket(
                roomId,
                requesterId,
                category,
                SupportStatus.IN_PROGRESS,
                agentId,
                createdAt,
                now,
                resolvedAt,
                version,
                notices);
    }

    /** 진행 중인 문의를 다른 관리자에게 넘긴다. 이관은 담당자만 바꾸고 처리 상태는 보존한다. */
    public SupportTicket transferTo(String agentId, Instant now) {
        if (!status.canAssign()) {
            throw new BadRequestException("SUPPORT_ALREADY_RESOLVED", "완료된 문의는 재개한 뒤에 이관할 수 있습니다");
        }
        if (assigneeId == null) {
            throw new BadRequestException("SUPPORT_NOT_ASSIGNED", "먼저 문의를 담당자에게 배정해야 합니다");
        }
        return new SupportTicket(
                roomId, requesterId, category, status, agentId, createdAt, now, resolvedAt, version, notices);
    }

    /** 관리자가 답했다 — 이제 사용자 차례다. */
    public SupportTicket agentReplied(Instant now) {
        if (assigneeId == null || status == SupportStatus.UNASSIGNED) {
            throw new BadRequestException("SUPPORT_NOT_ASSIGNED", "담당자를 배정한 뒤 답변할 수 있습니다");
        }
        if (status == SupportStatus.RESOLVED) {
            throw new BadRequestException("SUPPORT_ALREADY_RESOLVED", "완료된 문의를 재개한 뒤 답변해 주세요");
        }
        return new SupportTicket(
                roomId,
                requesterId,
                category,
                SupportStatus.WAITING_USER,
                assigneeId,
                createdAt,
                now,
                resolvedAt,
                version,
                notices);
    }

    /** 사용자가 답했다 — 다시 우리 차례다. 미배정 상태는 그대로 둔다(아무도 안 가져갔으므로). */
    public SupportTicket userReplied(Instant now) {
        if (status == SupportStatus.UNASSIGNED) {
            // 상태는 그대로여도 활동 시각은 올려야 새 문의가 관리자 인박스 아래로
            // 가라앉지 않는다.
            return new SupportTicket(
                    roomId, requesterId, category, status, assigneeId, createdAt, now, resolvedAt, version, notices);
        }
        if (status == SupportStatus.RESOLVED) {
            return reopen(now);
        }
        return new SupportTicket(
                roomId,
                requesterId,
                category,
                SupportStatus.IN_PROGRESS,
                assigneeId,
                createdAt,
                now,
                resolvedAt,
                version,
                notices);
    }

    public SupportTicket resolve(Instant now) {
        if (status == SupportStatus.RESOLVED) {
            return this;
        }
        if (assigneeId == null || status == SupportStatus.UNASSIGNED) {
            throw new BadRequestException("SUPPORT_NOT_ASSIGNED", "담당자를 배정한 뒤 완료할 수 있습니다");
        }
        return new SupportTicket(
                roomId,
                requesterId,
                category,
                SupportStatus.RESOLVED,
                assigneeId,
                createdAt,
                now,
                now,
                version,
                notices);
    }

    public SupportTicket reopen(Instant now) {
        if (status != SupportStatus.RESOLVED) {
            return this;
        }
        return new SupportTicket(
                roomId,
                requesterId,
                category,
                assigneeId == null ? SupportStatus.UNASSIGNED : SupportStatus.IN_PROGRESS,
                assigneeId,
                createdAt,
                now,
                null,
                version,
                notices);
    }
}
