package com.gole.api.order.adapter.out.settlement;

import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.NotFoundException;
import com.gole.api.order.application.port.in.ManageSettlementsUseCase;
import com.gole.api.order.application.port.in.ManageSettlementsUseCase.SettlementStatus;
import com.gole.api.order.application.port.in.ManageSettlementsUseCase.SettlementSummary;
import com.gole.api.order.application.port.out.SettlementPort;
import com.gole.api.order.domain.model.FeePolicy;
import com.gole.api.order.domain.model.Settlement;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/** 완료 주문의 판매자 정산 원장을 멱등 생성하고 관리자 지급 확인을 원자 처리한다. */
@Component
public class MongoSettlementAdapter implements SettlementPort, ManageSettlementsUseCase {

    private static final Logger log = LoggerFactory.getLogger(MongoSettlementAdapter.class);
    private static final int MAX_ROWS = 200;

    private final MongoTemplate mongoTemplate;
    private final Clock clock;
    private final FeePolicy feePolicy;

    public MongoSettlementAdapter(MongoTemplate mongoTemplate, Clock clock, FeePolicy feePolicy) {
        this.mongoTemplate = mongoTemplate;
        this.clock = clock;
        this.feePolicy = feePolicy;
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
                .map(MongoSettlementAdapter::toSummary)
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
    public SettlementSummary markPaid(String orderId, String paymentReference) {
        if (paymentReference == null || paymentReference.isBlank()) {
            throw new ConflictException("SETTLEMENT_REFERENCE_REQUIRED", "지급 증빙 번호를 입력해야 합니다");
        }
        Instant now = Instant.now(clock);
        Query pending =
                Query.query(Criteria.where("_id").is(orderId).and("status").is(SettlementStatus.PENDING.name()));
        Update paid = new Update()
                .set("status", SettlementStatus.PAID.name())
                .set("paymentReference", paymentReference.trim())
                .set("paidAt", now);
        SettlementDocument updated = mongoTemplate.findAndModify(
                pending, paid, FindAndModifyOptions.options().returnNew(true), SettlementDocument.class);
        if (updated != null) {
            return toSummary(updated);
        }
        SettlementDocument existing = mongoTemplate.findById(orderId, SettlementDocument.class);
        if (existing == null) {
            throw new NotFoundException("SETTLEMENT_NOT_FOUND", "정산 원장을 찾을 수 없습니다");
        }
        if (SettlementStatus.PAID.name().equals(existing.getStatus())) {
            return toSummary(existing);
        }
        throw new ConflictException("SETTLEMENT_STATE_CONFLICT", "정산 상태가 변경되어 다시 확인해야 합니다");
    }

    private static SettlementSummary toSummary(SettlementDocument document) {
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
                document.getPaidAt());
    }
}
