package com.gole.api.order.adapter.out.settlement;

import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.NotFoundException;
import com.gole.api.order.application.port.in.GetSellerSettlementsUseCase.SellerSettlementSummary;
import com.gole.api.order.application.port.in.ManageSettlementsUseCase.FeeTotals;
import com.gole.api.order.application.port.in.ManageSettlementsUseCase.SettlementSummary;
import com.gole.api.order.application.port.out.AutomaticSettlementPort;
import com.gole.api.order.application.port.out.OrderRepositoryPort;
import com.gole.api.order.application.port.out.SettlementLedgerPort;
import com.gole.api.order.application.port.out.SettlementPort;
import com.gole.api.order.config.SettlementProperties;
import com.gole.api.order.domain.model.FeePolicy;
import com.gole.api.order.domain.model.ManualPayoutPolicy;
import com.gole.api.order.domain.model.Settlement;
import com.gole.api.order.domain.model.SettlementLedger;
import com.gole.api.order.domain.model.SettlementStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
public class MongoSettlementAdapter implements SettlementPort, SettlementLedgerPort, AutomaticSettlementPort {

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
        return ManualPayoutPolicy.payableAt(createdAt, properties.getPayoutHoldback());
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
        String actor = ManualPayoutPolicy.requireOperator(operatorId);
        Instant now = Instant.now(clock);
        requireAuthoritativePayableOrder(orderId);
        SettlementDocument beforeClaim = requireLedger(orderId);
        ManualPayoutPolicy.requireHoldbackElapsed(toLedger(beforeClaim), properties.getPayoutHoldback(), now);

        if (ManualPayoutPolicy.alreadyClaimedBy(toLedger(beforeClaim), actor)) {
            return toSummary(beforeClaim);
        }

        Query available =
                Query.query(Criteria.where("_id").is(orderId).and("status").in(names(ManualPayoutPolicy.CLAIMABLE)));
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
        throw ManualPayoutPolicy.claimRejected(toLedger(requireLedger(orderId)));
    }

    @Override
    public SettlementSummary reconcileManualPayout(String orderId, String operatorId, String reason) {
        String actor = ManualPayoutPolicy.requireOperator(operatorId);
        String detail = ManualPayoutPolicy.requireReason(reason);
        Instant now = Instant.now(clock);
        SettlementDocument current = requireLedger(orderId);
        String blockNote = ManualPayoutPolicy.reconcileBlockNote(
                toLedger(current), actor, detail, now, properties.getProviderClaimTimeout());

        Query inProgress = claimIdentityQuery(current);
        Update blocked = new Update()
                .set("status", SettlementStatus.PAYOUT_BLOCKED.name())
                .set("payoutError", blockNote)
                .unset("payoutNextAttemptAt")
                .unset("payoutOperatorId");
        SettlementDocument updated = mongoTemplate.findAndModify(
                inProgress, blocked, FindAndModifyOptions.options().returnNew(true), SettlementDocument.class);
        if (updated != null) {
            return toSummary(updated);
        }
        throw ManualPayoutPolicy.reconcileRejected();
    }

    @Override
    public SettlementSummary recoverBlockedPayout(
            String orderId, String operatorId, boolean alreadyPaid, String paymentReference, String reason) {
        String actor = ManualPayoutPolicy.requireOperator(operatorId);
        String detail = ManualPayoutPolicy.requireReason(reason);
        Instant now = Instant.now(clock);
        SettlementDocument current = requireLedger(orderId);
        Query blocked =
                Query.query(Criteria.where("_id").is(orderId).and("status").is(SettlementStatus.PAYOUT_BLOCKED.name()));

        switch (ManualPayoutPolicy.recovery(toLedger(current), alreadyPaid, paymentReference)) {
            case ALREADY_RECORDED -> {
                return toSummary(current);
            }
            case RECORD_PAID -> {
                Update paid = new Update()
                        .set("status", SettlementStatus.PAID.name())
                        .set("paymentReference", paymentReference.trim())
                        .set("paidAt", now)
                        .set("payoutError", ManualPayoutPolicy.externalPaidNote(detail))
                        .unset("payoutOperatorId")
                        .unset("payoutNextAttemptAt");
                try {
                    SettlementDocument updated = mongoTemplate.findAndModify(
                            blocked, paid, FindAndModifyOptions.options().returnNew(true), SettlementDocument.class);
                    if (updated != null) {
                        return toSummary(updated);
                    }
                } catch (DuplicateKeyException duplicateReference) {
                    throw ManualPayoutPolicy.duplicateReference();
                }
                throw ManualPayoutPolicy.recoveryRejected();
            }
            case RETRY -> {
                // 아래에서 정산 모드별로 되돌린다.
            }
        }

        requireAuthoritativePayableOrder(orderId);
        ManualPayoutPolicy.requireHoldbackElapsed(toLedger(current), properties.getPayoutHoldback(), now);
        if (properties.getMode() == SettlementProperties.Mode.PROVIDER) {
            ManualPayoutPolicy.requireVerifiedContract(properties.isPayoutContractVerified());
            Update retry = new Update()
                    .set("status", SettlementStatus.PAYOUT_FAILED.name())
                    // 외부 미지급을 운영자가 확인했으므로 새 자동 지급 주기에는 재시도
                    // 예산을 다시 부여한다. 확인 전에는 PAYOUT_BLOCKED에서 절대 나오지 않는다.
                    .set("payoutAttempts", 0)
                    .set("payoutAttemptedAt", now)
                    .set("payoutNextAttemptAt", now)
                    .set("payoutError", ManualPayoutPolicy.providerRetryNote(actor, detail))
                    .unset("payoutAttemptId")
                    .unset("payoutOperatorId");
            SettlementDocument updated = mongoTemplate.findAndModify(
                    blocked, retry, FindAndModifyOptions.options().returnNew(true), SettlementDocument.class);
            if (updated != null) {
                return toSummary(updated);
            }
            throw ManualPayoutPolicy.recoveryRejected();
        }

        requireManualPayoutEnabled();
        Update retry = new Update()
                .set("status", SettlementStatus.PAYOUT_IN_PROGRESS.name())
                .set("payoutAttemptId", "manual-" + UUID.randomUUID())
                .set("payoutOperatorId", actor)
                .set("payoutAttemptedAt", now)
                .set("payoutError", ManualPayoutPolicy.manualRetryNote(detail))
                .unset("payoutNextAttemptAt");
        SettlementDocument updated = mongoTemplate.findAndModify(
                blocked, retry, FindAndModifyOptions.options().returnNew(true), SettlementDocument.class);
        if (updated != null) {
            return toSummary(updated);
        }
        throw ManualPayoutPolicy.recoveryRejected();
    }

    @Override
    public SettlementSummary markPaid(String orderId, String operatorId, String paymentReference) {
        requireManualPayoutEnabled();
        String actor = ManualPayoutPolicy.requireOperator(operatorId);
        String reference = ManualPayoutPolicy.requirePaymentReference(paymentReference);
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
                .set("paymentReference", reference)
                .set("paidAt", now)
                .unset("payoutNextAttemptAt")
                .unset("payoutError");
        SettlementDocument updated;
        try {
            updated = mongoTemplate.findAndModify(
                    pending, paid, FindAndModifyOptions.options().returnNew(true), SettlementDocument.class);
        } catch (DuplicateKeyException duplicateReference) {
            throw ManualPayoutPolicy.duplicateReference();
        }
        if (updated != null) {
            return toSummary(updated);
        }
        SettlementDocument existing = mongoTemplate.findById(orderId, SettlementDocument.class);
        if (existing == null) {
            throw new NotFoundException("SETTLEMENT_NOT_FOUND", "정산 원장을 찾을 수 없습니다");
        }
        ManualPayoutPolicy.requireSamePaidEvidence(toLedger(existing), reference);
        return toSummary(existing);
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
        ManualPayoutPolicy.requireManualMode(
                properties.getMode() == SettlementProperties.Mode.MANUAL, properties.isPayoutContractVerified());
    }

    /** 자동 실행기는 운영자가 선점한 수동 지급을 절대 회수하지 않는다. */
    private static Criteria automaticClaimOwnerCriteria() {
        return new Criteria()
                .orOperator(
                        Criteria.where("payoutOperatorId").exists(false),
                        Criteria.where("payoutOperatorId").is(null));
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
        ManualPayoutPolicy.requirePayableOrder(orders.findById(orderId)
                .orElseThrow(() -> new NotFoundException("SETTLEMENT_ORDER_NOT_FOUND", "정산 대상 주문을 찾을 수 없습니다"))
                .getStatus());
    }

    private SettlementDocument requireLedger(String orderId) {
        SettlementDocument document = mongoTemplate.findById(orderId, SettlementDocument.class);
        if (document == null) {
            throw new NotFoundException("SETTLEMENT_NOT_FOUND", "정산 원장을 찾을 수 없습니다");
        }
        return document;
    }

    private static String sanitizeError(String error) {
        return SettlementLedger.payoutNote(error);
    }

    private static SettlementLedger toLedger(SettlementDocument document) {
        return new SettlementLedger(
                document.getOrderId(),
                knownStatus(document.getStatus()),
                document.getPayoutOperatorId(),
                document.getPayoutAttemptedAt(),
                document.getPaymentReference(),
                document.getCreatedAt());
    }

    /**
     * 저장된 상태값을 읽는다. 모르는 값이면 {@code null}을 돌려 정책이 어떤 전이도 허용하지 않게 한다 — 상태 문자열을 직접
     * 비교하던 때처럼 409 상태 충돌로 거부되고, 500 으로 새지 않는다.
     */
    private static SettlementStatus knownStatus(String stored) {
        if (stored == null) {
            return null;
        }
        try {
            return SettlementStatus.valueOf(stored);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static List<String> names(Set<SettlementStatus> statuses) {
        return statuses.stream().map(Enum::name).toList();
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
