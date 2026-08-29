package com.gole.api.order.adapter.out.settlement;

import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.NotFoundException;
import com.gole.api.order.application.port.in.GetSellerSettlementsUseCase;
import com.gole.api.order.application.port.in.GetSellerSettlementsUseCase.SellerSettlementSummary;
import com.gole.api.order.application.port.in.ManageSettlementsUseCase;
import com.gole.api.order.application.port.in.ManageSettlementsUseCase.FeeTotals;
import com.gole.api.order.application.port.in.ManageSettlementsUseCase.SettlementStatus;
import com.gole.api.order.application.port.in.ManageSettlementsUseCase.SettlementSummary;
import com.gole.api.order.application.port.out.AutomaticSettlementPort;
import com.gole.api.order.application.port.out.OrderRepositoryPort;
import com.gole.api.order.application.port.out.SettlementPort;
import com.gole.api.order.domain.model.FeePolicy;
import com.gole.api.order.domain.model.OrderStatus;
import com.gole.api.order.domain.model.Settlement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/** 완료 주문의 판매자 정산 원장을 멱등 생성하고 관리자 지급 확인을 원자 처리한다. */
@Component
public class MongoSettlementAdapter
        implements SettlementPort, ManageSettlementsUseCase, GetSellerSettlementsUseCase, AutomaticSettlementPort {

    private static final Logger log = LoggerFactory.getLogger(MongoSettlementAdapter.class);
    private static final int MAX_ROWS = 200;

    private final MongoTemplate mongoTemplate;
    private final Clock clock;
    private final FeePolicy feePolicy;
    private final SettlementProperties properties;
    private final OrderRepositoryPort orders;

    public MongoSettlementAdapter(
            MongoTemplate mongoTemplate,
            Clock clock,
            FeePolicy feePolicy,
            SettlementProperties properties,
            OrderRepositoryPort orders) {
        this.mongoTemplate = mongoTemplate;
        this.clock = clock;
        this.feePolicy = feePolicy;
        this.properties = properties;
        this.orders = orders;
    }

    /** 지급 유예가 끝나는 시각. 원장 적재 시각 + holdback. */
    private Instant payableAt(Instant createdAt) {
        return createdAt == null ? null : createdAt.plus(properties.getPayoutHoldback());
    }

    @Override
    public void settleOnce(String orderId, String sellerId, long amount) {
        Instant now = Instant.now(clock);
        Settlement settlement = Settlement.compute(orderId, sellerId, amount, feePolicy, now);
        Query query = Query.query(Criteria.where("_id").is(orderId));
        Update create = new Update()
                .setOnInsert("sellerId", sellerId)
                .setOnInsert("grossAmount", settlement.grossAmount())
                .setOnInsert("fee", settlement.fee())
                .setOnInsert("payout", settlement.payout())
                .setOnInsert("feeRate", settlement.feeRate())
                .setOnInsert("status", SettlementStatus.PENDING.name())
                .setOnInsert("createdAt", now);
        mongoTemplate.upsert(query, create, SettlementDocument.class);
        log.info("정산 원장 대기 등록 orderId={} sellerId={} payout={}", orderId, sellerId, settlement.payout());
    }

    @Override
    public List<SettlementSummary> list(SettlementStatus status, int limit) {
        Query query = new Query().limit(Math.max(1, Math.min(limit, MAX_ROWS)));
        if (status != null) {
            query.addCriteria(Criteria.where("status").is(status.name()));
        }
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"));
        return mongoTemplate.find(query, SettlementDocument.class).stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    public List<SellerSettlementSummary> listBySeller(String sellerId, int limit) {
        Query query = Query.query(Criteria.where("sellerId").is(sellerId))
                .limit(Math.max(1, Math.min(limit, MAX_ROWS)))
                .with(Sort.by(Sort.Direction.DESC, "createdAt"));
        return mongoTemplate.find(query, SettlementDocument.class).stream()
                .map(this::toSellerSummary)
                .toList();
    }

    @Override
    public long count(SettlementStatus status) {
        Query query = status == null
                ? new Query()
                : Query.query(Criteria.where("status").is(status.name()));
        return mongoTemplate.count(query, SettlementDocument.class);
    }

    @Override
    public FeeTotals totals(SettlementStatus status) {
        List<AggregationOperation> stages = new java.util.ArrayList<>();
        if (status != null) {
            stages.add(Aggregation.match(Criteria.where("status").is(status.name())));
        }
        stages.add(Aggregation.group()
                .count()
                .as("count")
                .sum("grossAmount")
                .as("grossTotal")
                .sum("fee")
                .as("feeTotal")
                .sum("payout")
                .as("payoutTotal"));
        var results =
                mongoTemplate.aggregate(Aggregation.newAggregation(stages), SettlementDocument.class, FeeTotals.class);
        FeeTotals totals = results.getUniqueMappedResult();
        return totals == null ? new FeeTotals(0, 0, 0, 0) : totals;
    }

    @Override
    public SettlementSummary claimManualPayout(String orderId, String operatorId) {
        requireManualPayoutEnabled();
        String actor = requireOperator(operatorId);
        Instant now = Instant.now(clock);
        requireAuthoritativePayableOrder(orderId);
        SettlementDocument beforeClaim = requireLedger(orderId);
        requireHoldbackElapsed(beforeClaim, now);

        if (SettlementStatus.PAYOUT_IN_PROGRESS.name().equals(beforeClaim.getStatus())
                && actor.equals(beforeClaim.getPayoutOperatorId())) {
            return toSummary(beforeClaim);
        }

        Query available = Query.query(Criteria.where("_id")
                .is(orderId)
                .and("status")
                .in(SettlementStatus.PENDING.name(), SettlementStatus.PAYOUT_FAILED.name()));
        Update claim = new Update()
                .set("status", SettlementStatus.PAYOUT_IN_PROGRESS.name())
                .set("payoutAttemptId", "manual-" + UUID.randomUUID())
                .set("payoutOperatorId", actor)
                .set("payoutAttemptedAt", now)
                .unset("payoutNextAttemptAt")
                .unset("payoutError");
        SettlementDocument claimed = mongoTemplate.findAndModify(
                available, claim, FindAndModifyOptions.options().returnNew(true), SettlementDocument.class);
        if (claimed != null) {
            return toSummary(claimed);
        }
        SettlementDocument existing = requireLedger(orderId);
        if (SettlementStatus.PAYOUT_IN_PROGRESS.name().equals(existing.getStatus())) {
            throw new ConflictException("SETTLEMENT_ALREADY_CLAIMED", "다른 운영자가 처리 중이거나 자동 지급 결과를 확인 중입니다");
        }
        throw new ConflictException("SETTLEMENT_STATE_CONFLICT", "정산 상태가 변경되어 다시 확인해야 합니다");
    }

    @Override
    public SettlementSummary reconcileManualPayout(String orderId, String operatorId, String reason) {
        String actor = requireOperator(operatorId);
        String detail = requireReason(reason);
        Instant now = Instant.now(clock);
        SettlementDocument current = requireLedger(orderId);
        if (!SettlementStatus.PAYOUT_IN_PROGRESS.name().equals(current.getStatus())) {
            throw new ConflictException("SETTLEMENT_STATE_CONFLICT", "진행 중인 정산만 재조정할 수 있습니다");
        }

        boolean ownedByActor = actor.equals(current.getPayoutOperatorId());
        if (!ownedByActor) {
            Instant attemptedAt = current.getPayoutAttemptedAt();
            Instant staleAt = attemptedAt == null ? null : attemptedAt.plus(properties.getProviderClaimTimeout());
            if (staleAt != null && now.isBefore(staleAt)) {
                throw new ConflictException(
                        "SETTLEMENT_CLAIM_STILL_ACTIVE",
                        "다른 운영자 또는 지급사의 작업이 아직 진행 중입니다 (차단 가능 시각 %s)".formatted(staleAt));
            }
        }

        Query inProgress = claimIdentityQuery(current);
        Update blocked = new Update()
                .set("status", SettlementStatus.PAYOUT_BLOCKED.name())
                .set("payoutError", sanitizeError((ownedByActor ? "담당자 지급 결과 확인 필요: " : "장기 정체 지급 확인 필요: ") + detail))
                .unset("payoutNextAttemptAt")
                .unset("payoutOperatorId");
        SettlementDocument updated = mongoTemplate.findAndModify(
                inProgress, blocked, FindAndModifyOptions.options().returnNew(true), SettlementDocument.class);
        if (updated != null) {
            return toSummary(updated);
        }
        throw new ConflictException("SETTLEMENT_STATE_CONFLICT", "선점 상태가 변경되어 목록을 다시 확인해야 합니다");
    }

    @Override
    public SettlementSummary recoverBlockedPayout(
            String orderId, String operatorId, boolean alreadyPaid, String paymentReference, String reason) {
        String actor = requireOperator(operatorId);
        String detail = requireReason(reason);
        Instant now = Instant.now(clock);
        SettlementDocument current = requireLedger(orderId);

        if (!SettlementStatus.PAYOUT_BLOCKED.name().equals(current.getStatus())) {
            if (alreadyPaid
                    && SettlementStatus.PAID.name().equals(current.getStatus())
                    && paymentReference != null
                    && paymentReference.trim().equals(current.getPaymentReference())) {
                return toSummary(current);
            }
            throw new ConflictException("SETTLEMENT_STATE_CONFLICT", "운영 확인 필요 상태의 정산만 복구할 수 있습니다");
        }

        if (alreadyPaid) {
            if (paymentReference == null || paymentReference.isBlank()) {
                throw new ConflictException("SETTLEMENT_REFERENCE_REQUIRED", "외부 지급을 확인한 증빙 번호를 입력해야 합니다");
            }
            Query blocked = Query.query(
                    Criteria.where("_id").is(orderId).and("status").is(SettlementStatus.PAYOUT_BLOCKED.name()));
            Update paid = new Update()
                    .set("status", SettlementStatus.PAID.name())
                    .set("paymentReference", paymentReference.trim())
                    .set("paidAt", now)
                    .set("payoutError", sanitizeError("외부 지급 확인 완료: " + detail))
                    .unset("payoutOperatorId")
                    .unset("payoutNextAttemptAt");
            try {
                SettlementDocument updated = mongoTemplate.findAndModify(
                        blocked, paid, FindAndModifyOptions.options().returnNew(true), SettlementDocument.class);
                if (updated != null) {
                    return toSummary(updated);
                }
            } catch (DuplicateKeyException duplicateReference) {
                throw new ConflictException("SETTLEMENT_REFERENCE_DUPLICATE", "이미 다른 정산에 사용된 지급 증빙 번호입니다");
            }
            throw new ConflictException("SETTLEMENT_STATE_CONFLICT", "복구 중 상태가 변경되어 목록을 다시 확인해야 합니다");
        }

        requireAuthoritativePayableOrder(orderId);
        requireHoldbackElapsed(current, now);
        if (properties.getMode() == SettlementProperties.Mode.PROVIDER) {
            requireVerifiedPayoutContract();
            Query blocked = Query.query(
                    Criteria.where("_id").is(orderId).and("status").is(SettlementStatus.PAYOUT_BLOCKED.name()));
            Update retry = new Update()
                    .set("status", SettlementStatus.PAYOUT_FAILED.name())
                    // 외부 미지급을 운영자가 확인했으므로 새 자동 지급 주기에는 재시도
                    // 예산을 다시 부여한다. 확인 전에는 PAYOUT_BLOCKED에서 절대 나오지 않는다.
                    .set("payoutAttempts", 0)
                    .set("payoutAttemptedAt", now)
                    .set("payoutNextAttemptAt", now)
                    .set("payoutError", sanitizeError("외부 미지급 확인 후 자동 재시도 요청 (%s): %s".formatted(actor, detail)))
                    .unset("payoutAttemptId")
                    .unset("payoutOperatorId");
            SettlementDocument updated = mongoTemplate.findAndModify(
                    blocked, retry, FindAndModifyOptions.options().returnNew(true), SettlementDocument.class);
            if (updated != null) {
                return toSummary(updated);
            }
            throw new ConflictException("SETTLEMENT_STATE_CONFLICT", "복구 중 상태가 변경되어 목록을 다시 확인해야 합니다");
        }

        requireManualPayoutEnabled();
        Query blocked =
                Query.query(Criteria.where("_id").is(orderId).and("status").is(SettlementStatus.PAYOUT_BLOCKED.name()));
        Update retry = new Update()
                .set("status", SettlementStatus.PAYOUT_IN_PROGRESS.name())
                .set("payoutAttemptId", "manual-" + UUID.randomUUID())
                .set("payoutOperatorId", actor)
                .set("payoutAttemptedAt", now)
                .set("payoutError", sanitizeError("외부 미지급 확인 후 수동 복구: " + detail))
                .unset("payoutNextAttemptAt");
        SettlementDocument updated = mongoTemplate.findAndModify(
                blocked, retry, FindAndModifyOptions.options().returnNew(true), SettlementDocument.class);
        if (updated != null) {
            return toSummary(updated);
        }
        throw new ConflictException("SETTLEMENT_STATE_CONFLICT", "복구 중 상태가 변경되어 목록을 다시 확인해야 합니다");
    }

    @Override
    public SettlementSummary markPaid(String orderId, String operatorId, String paymentReference) {
        requireManualPayoutEnabled();
        String actor = requireOperator(operatorId);
        if (paymentReference == null || paymentReference.isBlank()) {
            throw new ConflictException("SETTLEMENT_REFERENCE_REQUIRED", "지급 증빙 번호를 입력해야 합니다");
        }
        Instant now = Instant.now(clock);
        requireAuthoritativePayableOrder(orderId);
        Query pending = Query.query(Criteria.where("_id")
                .is(orderId)
                .and("status")
                .is(SettlementStatus.PAYOUT_IN_PROGRESS.name())
                .and("payoutOperatorId")
                .is(actor));
        Update paid = new Update()
                .set("status", SettlementStatus.PAID.name())
                .set("paymentReference", paymentReference.trim())
                .set("paidAt", now)
                .unset("payoutNextAttemptAt")
                .unset("payoutError");
        SettlementDocument updated;
        try {
            updated = mongoTemplate.findAndModify(
                    pending, paid, FindAndModifyOptions.options().returnNew(true), SettlementDocument.class);
        } catch (DuplicateKeyException duplicateReference) {
            throw new ConflictException("SETTLEMENT_REFERENCE_DUPLICATE", "이미 다른 정산에 사용된 지급 증빙 번호입니다");
        }
        if (updated != null) {
            return toSummary(updated);
        }
        SettlementDocument existing = mongoTemplate.findById(orderId, SettlementDocument.class);
        if (existing == null) {
            throw new NotFoundException("SETTLEMENT_NOT_FOUND", "정산 원장을 찾을 수 없습니다");
        }
        if (SettlementStatus.PAID.name().equals(existing.getStatus())) {
            if (paymentReference.trim().equals(existing.getPaymentReference())) {
                return toSummary(existing);
            }
            throw new ConflictException("SETTLEMENT_ALREADY_PAID", "이미 다른 지급 증빙 번호로 완료된 정산입니다");
        }
        if (SettlementStatus.PAYOUT_IN_PROGRESS.name().equals(existing.getStatus())) {
            throw new ConflictException("SETTLEMENT_CLAIM_OWNER_MISMATCH", "이 정산을 배정받은 운영자만 지급 완료할 수 있습니다");
        }
        throw new ConflictException("SETTLEMENT_CLAIM_REQUIRED", "외부 이체 전에 먼저 정산 작업을 배정받아야 합니다");
    }

    @Override
    public void blockExhaustedClaims(Instant now, Duration staleAfter, int maxAttempts) {
        Instant staleClaimAt = now.minus(staleAfter);
        Criteria exhausted = new Criteria()
                .orOperator(
                        new Criteria()
                                .andOperator(
                                        Criteria.where("status").is(SettlementStatus.PAYOUT_FAILED.name()),
                                        Criteria.where("payoutAttempts").gte(maxAttempts)),
                        new Criteria()
                                .andOperator(
                                        Criteria.where("status").is(SettlementStatus.PAYOUT_IN_PROGRESS.name()),
                                        Criteria.where("payoutAttempts").gte(maxAttempts),
                                        Criteria.where("payoutAttemptedAt").lte(staleClaimAt),
                                        automaticClaimOwnerCriteria()));
        Update blocked = new Update()
                .set("status", SettlementStatus.PAYOUT_BLOCKED.name())
                .set("payoutError", sanitizeError("지급대행 재시도 상한 도달 또는 원장 반영 실패 — 외부 지급 결과 확인 필요"))
                .unset("payoutNextAttemptAt")
                .unset("payoutOperatorId");
        mongoTemplate.updateMulti(Query.query(exhausted), blocked, SettlementDocument.class);
    }

    @Override
    public Optional<Candidate> claimNext(Instant now, Duration holdback, Duration staleAfter, String attemptId) {
        Instant eligibleCreatedAt = now.minus(holdback);
        Instant staleClaimAt = now.minus(staleAfter);
        Criteria retryReady = new Criteria()
                .orOperator(
                        Criteria.where("payoutNextAttemptAt").lte(now),
                        Criteria.where("payoutNextAttemptAt").exists(false),
                        Criteria.where("payoutNextAttemptAt").is(null));
        Criteria retriable = new Criteria()
                .orOperator(
                        Criteria.where("status").is(SettlementStatus.PENDING.name()),
                        new Criteria()
                                .andOperator(
                                        Criteria.where("status").is(SettlementStatus.PAYOUT_FAILED.name()),
                                        Criteria.where("payoutAttempts").lt(properties.getProviderMaxAttempts()),
                                        retryReady),
                        new Criteria()
                                .andOperator(
                                        Criteria.where("status").is(SettlementStatus.PAYOUT_IN_PROGRESS.name()),
                                        Criteria.where("payoutAttempts").lt(properties.getProviderMaxAttempts()),
                                        Criteria.where("payoutAttemptedAt").lte(staleClaimAt),
                                        automaticClaimOwnerCriteria()));
        Query query = Query.query(new Criteria()
                        .andOperator(Criteria.where("createdAt").ne(null).lte(eligibleCreatedAt), retriable))
                .with(Sort.by(Sort.Direction.ASC, "createdAt"));
        Update claim = new Update()
                .set("status", SettlementStatus.PAYOUT_IN_PROGRESS.name())
                .set("payoutAttemptId", attemptId)
                .unset("payoutOperatorId")
                .set("payoutAttemptedAt", now)
                .unset("payoutNextAttemptAt")
                .unset("payoutError")
                .inc("payoutAttempts", 1);
        SettlementDocument claimed = mongoTemplate.findAndModify(
                query, claim, FindAndModifyOptions.options().returnNew(true), SettlementDocument.class);
        if (claimed == null) {
            return Optional.empty();
        }
        return Optional.of(new Candidate(
                claimed.getOrderId(),
                claimed.getSellerId(),
                claimed.getPayout(),
                attemptId,
                claimed.getPayoutAttempts()));
    }

    @Override
    public void markPaid(String orderId, String attemptId, String paymentReference, Instant paidAt) {
        if (paymentReference == null || paymentReference.isBlank()) {
            throw new ConflictException("SETTLEMENT_REFERENCE_REQUIRED", "지급대행 증빙 번호가 비어 있어 완료 처리할 수 없습니다");
        }
        String reference = paymentReference.trim();
        Query claim = Query.query(Criteria.where("_id")
                .is(orderId)
                .and("status")
                .is(SettlementStatus.PAYOUT_IN_PROGRESS.name())
                .and("payoutAttemptId")
                .is(attemptId));
        Update paid = new Update()
                .set("status", SettlementStatus.PAID.name())
                .set("paymentReference", reference)
                .set("paidAt", paidAt)
                .unset("payoutNextAttemptAt")
                .unset("payoutError");
        SettlementDocument updated;
        try {
            updated = mongoTemplate.findAndModify(
                    claim, paid, FindAndModifyOptions.options().returnNew(true), SettlementDocument.class);
        } catch (DuplicateKeyException duplicateReference) {
            throw new ConflictException("SETTLEMENT_REFERENCE_DUPLICATE", "지급대행 증빙 번호가 다른 정산에 이미 사용됐습니다");
        }
        if (updated != null) {
            return;
        }
        SettlementDocument existing = mongoTemplate.findById(orderId, SettlementDocument.class);
        if (existing != null
                && SettlementStatus.PAID.name().equals(existing.getStatus())
                && reference.equals(existing.getPaymentReference())) {
            return;
        }
        throw new OptimisticLockingFailureException("정산 지급 결과를 기록하기 전에 선점 상태가 변경됐습니다: " + orderId);
    }

    @Override
    public void markFailed(String orderId, String attemptId, String error, Instant failedAt, Duration retryAfter) {
        updateClaimState(
                orderId,
                attemptId,
                SettlementStatus.PAYOUT_FAILED,
                sanitizeError(error),
                failedAt,
                failedAt.plus(retryAfter));
    }

    @Override
    public void markBlocked(String orderId, String attemptId, String reason, Instant blockedAt) {
        updateClaimState(orderId, attemptId, SettlementStatus.PAYOUT_BLOCKED, sanitizeError(reason), blockedAt, null);
    }

    private void updateClaimState(
            String orderId,
            String attemptId,
            SettlementStatus target,
            String error,
            Instant attemptedAt,
            Instant nextAttemptAt) {
        Query claim = Query.query(Criteria.where("_id")
                .is(orderId)
                .and("status")
                .is(SettlementStatus.PAYOUT_IN_PROGRESS.name())
                .and("payoutAttemptId")
                .is(attemptId));
        Update update = new Update()
                .set("status", target.name())
                .set("payoutError", error)
                .set("payoutAttemptedAt", attemptedAt);
        if (nextAttemptAt == null) {
            update.unset("payoutNextAttemptAt");
        } else {
            update.set("payoutNextAttemptAt", nextAttemptAt);
        }
        if (mongoTemplate.findAndModify(claim, update, SettlementDocument.class) == null) {
            throw new OptimisticLockingFailureException("정산 선점 상태가 변경됐습니다: " + orderId);
        }
    }

    private void requireManualPayoutEnabled() {
        if (properties.getMode() != SettlementProperties.Mode.MANUAL) {
            throw new ConflictException("SETTLEMENT_MANUAL_MODE_REQUIRED", "수동 지급은 MANUAL 정산 모드에서만 사용할 수 있습니다");
        }
        requireVerifiedPayoutContract();
    }

    private void requireVerifiedPayoutContract() {
        if (!properties.isPayoutContractVerified()) {
            throw new ConflictException("SETTLEMENT_CONTRACT_NOT_VERIFIED", "지급대행 계약 확인 전에는 판매자 지급을 처리할 수 없습니다");
        }
    }

    /** 자동 실행기는 운영자가 선점한 수동 지급을 절대 회수하지 않는다. */
    private static Criteria automaticClaimOwnerCriteria() {
        return new Criteria()
                .orOperator(
                        Criteria.where("payoutOperatorId").exists(false),
                        Criteria.where("payoutOperatorId").is(null));
    }

    private static String requireOperator(String operatorId) {
        if (operatorId == null || operatorId.isBlank()) {
            throw new ConflictException("SETTLEMENT_OPERATOR_REQUIRED", "정산 작업자를 확인할 수 없습니다");
        }
        return operatorId.trim();
    }

    private static String requireReason(String reason) {
        String detail = reason == null ? "" : reason.trim();
        if (detail.isBlank()) {
            throw new ConflictException("SETTLEMENT_RECONCILE_REASON_REQUIRED", "외부 지급 확인 근거와 조치 사유를 입력해야 합니다");
        }
        return detail;
    }

    private static Query claimIdentityQuery(SettlementDocument document) {
        return Query.query(Criteria.where("_id")
                .is(document.getOrderId())
                .and("status")
                .is(SettlementStatus.PAYOUT_IN_PROGRESS.name())
                .and("payoutAttemptId")
                .is(document.getPayoutAttemptId()));
    }

    private void requireAuthoritativePayableOrder(String orderId) {
        OrderStatus orderStatus = orders.findById(orderId)
                .orElseThrow(() -> new NotFoundException("SETTLEMENT_ORDER_NOT_FOUND", "정산 대상 주문을 찾을 수 없습니다"))
                .getStatus();
        if (orderStatus != OrderStatus.COMPLETED) {
            throw new ConflictException(
                    "SETTLEMENT_ORDER_NOT_COMPLETED", "구매 확정된 주문만 지급할 수 있습니다 (현재 상태 %s)".formatted(orderStatus));
        }
    }

    private SettlementDocument requireLedger(String orderId) {
        SettlementDocument document = mongoTemplate.findById(orderId, SettlementDocument.class);
        if (document == null) {
            throw new NotFoundException("SETTLEMENT_NOT_FOUND", "정산 원장을 찾을 수 없습니다");
        }
        return document;
    }

    private void requireHoldbackElapsed(SettlementDocument document, Instant now) {
        if (document.getCreatedAt() == null) {
            throw new ConflictException("SETTLEMENT_DATA_INVALID", "정산 원장의 생성 시각이 없어 지급을 잠갔습니다. 운영자 확인이 필요합니다");
        }
        Instant payable = payableAt(document.getCreatedAt());
        if (now.isBefore(payable)) {
            throw new ConflictException(
                    "SETTLEMENT_HOLDBACK_ACTIVE", "지급 유예 기간이 끝나지 않아 아직 지급할 수 없습니다 (지급 가능 시각 %s)".formatted(payable));
        }
    }

    private static String sanitizeError(String error) {
        String value = error == null || error.isBlank() ? "알 수 없는 지급대행 오류" : error.trim();
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    private SettlementSummary toSummary(SettlementDocument document) {
        return new SettlementSummary(
                document.getOrderId(),
                document.getSellerId(),
                document.getGrossAmount(),
                document.getFee(),
                document.getPayout(),
                document.getFeeRate(),
                SettlementStatus.valueOf(document.getStatus()),
                document.getPaymentReference(),
                document.getCreatedAt(),
                payableAt(document.getCreatedAt()),
                document.getPaidAt(),
                document.getPayoutAttempts(),
                document.getPayoutOperatorId(),
                document.getPayoutAttemptedAt(),
                document.getPayoutNextAttemptAt(),
                document.getPayoutError());
    }

    private SellerSettlementSummary toSellerSummary(SettlementDocument document) {
        return new SellerSettlementSummary(
                document.getOrderId(),
                document.getGrossAmount(),
                document.getFee(),
                document.getPayout(),
                document.getFeeRate(),
                SettlementStatus.valueOf(document.getStatus()),
                document.getCreatedAt(),
                payableAt(document.getCreatedAt()),
                document.getPaidAt(),
                document.getPayoutNextAttemptAt());
    }
}
