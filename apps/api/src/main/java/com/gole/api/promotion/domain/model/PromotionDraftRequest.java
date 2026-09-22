package com.gole.api.promotion.domain.model;

import com.gole.api.promotion.domain.exception.DraftRequestLeaseLostException;
import com.gole.api.promotion.domain.exception.InvalidPromotionDraftRequestStateException;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 관리자가 "초안을 만들어 달라"고 남긴 요청 하나 — 콘솔과 에이전트 사이의 전달 수단이다.
 *
 * <p><b>이 애그리거트는 "이 릴리스를 홍보해도 되는가"를 판단하지 않는다.</b> 그 판단은
 * {@link PromotionPost} 의 점유({@code claimedSourceCommitSha})와 3회 상한이 단독으로 한다
 * (promotion-review D2/D11). 여기는 요청의 수명만 관리한다 — 둘을 겹쳐 놓으면 같은 규칙이
 * 두 군데 생기고 반드시 어긋난다.
 *
 * <p>실행 주체는 Python 에이전트이며 Java 는 그 프로세스를 띄울 수단이 없다(D20). 그래서
 * 이 요청은 "즉시 실행"이 아니라 <b>다음 에이전트 실행이 우선 처리할 대상</b>을 뜻한다.
 */
public final class PromotionDraftRequest {

    private static final Pattern COMMIT_SHA_PATTERN = Pattern.compile("[0-9a-f]{40}");
    private static final int MAX_FAILURE_CODE_LENGTH = 64;

    private final String id;

    /** 비어 있으면 에이전트가 지금처럼 자동 선정한다. 있으면 그 커밋만 돌린다. */
    private final String sourceCommitSha;

    private final String requestedBy;
    private final Instant createdAt;

    private PromotionDraftRequestStatus status;
    private int attempts;
    private String leaseToken;
    private Instant leaseUntil;
    private String promotionPostId;
    private String failureCode;
    private Instant finishedAt;

    public PromotionDraftRequest(
            String id,
            String sourceCommitSha,
            String requestedBy,
            Instant createdAt,
            PromotionDraftRequestStatus status,
            int attempts,
            String leaseToken,
            Instant leaseUntil,
            String promotionPostId,
            String failureCode,
            Instant finishedAt) {
        this.id = requireText(id, "id");
        this.sourceCommitSha = normalizeSha(sourceCommitSha);
        this.requestedBy = requireText(requestedBy, "requestedBy");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.status = Objects.requireNonNull(status, "status");
        this.attempts = attempts;
        this.leaseToken = leaseToken;
        this.leaseUntil = leaseUntil;
        this.promotionPostId = promotionPostId;
        this.failureCode = failureCode;
        this.finishedAt = finishedAt;
    }

    /** 접수: 항상 PENDING 으로 시작한다. */
    public static PromotionDraftRequest pending(String id, String sourceCommitSha, String requestedBy, Instant now) {
        return new PromotionDraftRequest(
                id,
                sourceCommitSha,
                requestedBy,
                now,
                PromotionDraftRequestStatus.PENDING,
                0,
                null,
                null,
                null,
                null,
                null);
    }

    /**
     * 점유: PENDING → IN_PROGRESS. 실제 경쟁은 저장소의 원자 갱신이 막고, 여기서는 전이만 본다.
     *
     * <p>lease 를 두는 이유는 에이전트가 중간에 죽었을 때다 — 만료되면 다음 실행이 다시 집는다.
     */
    public void claim(String leaseToken, Instant leaseUntil) {
        requireStatus(PromotionDraftRequestStatus.PENDING);
        this.status = PromotionDraftRequestStatus.IN_PROGRESS;
        this.leaseToken = requireText(leaseToken, "leaseToken");
        this.leaseUntil = Objects.requireNonNull(leaseUntil, "leaseUntil");
        this.attempts += 1;
    }

    /** 성공 회신: IN_PROGRESS → SUCCEEDED. 만들어진 초안 ID 를 남긴다. */
    public void succeed(String leaseToken, String promotionPostId, Instant now) {
        requireStatus(PromotionDraftRequestStatus.IN_PROGRESS);
        requireLease(leaseToken);
        this.status = PromotionDraftRequestStatus.SUCCEEDED;
        this.promotionPostId = requireText(promotionPostId, "promotionPostId");
        this.finishedAt = Objects.requireNonNull(now, "now");
        releaseLease();
    }

    /**
     * 실패 회신: IN_PROGRESS → FAILED.
     *
     * <p>사유는 <b>코드만</b> 받는다. 에이전트의 예외 원문에는 diff·캡션·경로가 섞일 수 있고,
     * 이 문서는 관리자 화면에 그대로 나가기 때문이다.
     */
    public void fail(String leaseToken, String failureCode, Instant now) {
        requireStatus(PromotionDraftRequestStatus.IN_PROGRESS);
        requireLease(leaseToken);
        this.status = PromotionDraftRequestStatus.FAILED;
        this.failureCode = requireCode(failureCode);
        this.finishedAt = Objects.requireNonNull(now, "now");
        releaseLease();
    }

    /** 시도 상한을 넘긴 요청을 닫는다. 에이전트 회신 없이 저장소가 부른다. */
    public void abandon(String failureCode, Instant now) {
        this.status = PromotionDraftRequestStatus.FAILED;
        this.failureCode = requireCode(failureCode);
        this.finishedAt = Objects.requireNonNull(now, "now");
        releaseLease();
    }

    public boolean isTerminal() {
        return status == PromotionDraftRequestStatus.SUCCEEDED || status == PromotionDraftRequestStatus.FAILED;
    }

    public boolean hasPinnedCommit() {
        return sourceCommitSha != null;
    }

    private void releaseLease() {
        this.leaseToken = null;
        this.leaseUntil = null;
    }

    private void requireStatus(PromotionDraftRequestStatus expected) {
        if (status != expected) {
            throw new InvalidPromotionDraftRequestStateException(id, expected, status);
        }
    }

    private void requireLease(String presented) {
        if (leaseToken == null || !leaseToken.equals(presented)) {
            throw new DraftRequestLeaseLostException(id);
        }
    }

    private static String requireCode(String value) {
        String text = requireText(value, "failureCode");
        if (text.length() > MAX_FAILURE_CODE_LENGTH) {
            throw new IllegalArgumentException("failureCode must be at most " + MAX_FAILURE_CODE_LENGTH + " chars");
        }
        return text;
    }

    private static String normalizeSha(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (!COMMIT_SHA_PATTERN.matcher(text).matches()) {
            throw new IllegalArgumentException("sourceCommitSha must be a 40-character hex string");
        }
        return text;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    public String getId() {
        return id;
    }

    public String getSourceCommitSha() {
        return sourceCommitSha;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public PromotionDraftRequestStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLeaseToken() {
        return leaseToken;
    }

    public Instant getLeaseUntil() {
        return leaseUntil;
    }

    public String getPromotionPostId() {
        return promotionPostId;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
