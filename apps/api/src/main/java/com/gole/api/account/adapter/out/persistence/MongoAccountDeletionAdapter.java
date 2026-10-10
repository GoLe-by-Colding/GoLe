package com.gole.api.account.adapter.out.persistence;

import com.gole.api.account.application.port.out.AccountDeletionRepositoryPort;
import com.gole.api.account.application.port.out.AccountLinkedRecordsPort;
import com.gole.api.account.domain.model.AccountDeletionBlocker;
import com.gole.api.account.domain.model.AccountDeletionHoldReason;
import com.gole.api.account.domain.model.AccountDeletionRequest;
import com.gole.api.account.domain.model.AccountDeletionStatus;
import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.NotFoundException;
import com.mongodb.client.result.DeleteResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Mongo 기반 보존 매트릭스와 계정 연계 파기 어댑터. */
@Component
public class MongoAccountDeletionAdapter implements AccountDeletionRepositoryPort {

    private final AccountDeletionRequestMongoRepository requests;
    private final MongoTemplate mongo;
    private final AccountLinkedRecordsPort linkedRecords;

    public MongoAccountDeletionAdapter(
            AccountDeletionRequestMongoRepository requests,
            MongoTemplate mongo,
            AccountLinkedRecordsPort linkedRecords) {
        this.requests = requests;
        this.mongo = mongo;
        this.linkedRecords = linkedRecords;
    }

    @Override
    public AccountDeletionRequest save(AccountDeletionRequest request) {
        try {
            return toDomain(requests.save(toDocument(request)));
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("ACCOUNT_DELETION_REQUEST_CONFLICT", "이미 처리 중인 탈퇴 요청이 있거나 멱등성 키가 재사용되었습니다");
        }
    }

    @Override
    public Optional<AccountDeletionRequest> findById(String requestId) {
        return requests.findById(requestId).map(MongoAccountDeletionAdapter::toDomain);
    }

    @Override
    public Optional<AccountDeletionRequest> findActiveByAccountId(String accountId) {
        return requests.findByAccountId(accountId).map(MongoAccountDeletionAdapter::toDomain);
    }

    @Override
    public List<AccountDeletionRequest> findRecent(AccountDeletionStatus status, int limit) {
        Query query =
                new Query().with(Sort.by(Sort.Direction.DESC, "updatedAt")).limit(Math.max(1, Math.min(limit, 200)));
        if (status != null) {
            query.addCriteria(Criteria.where("status").is(status.name()));
        }
        return mongo.find(query, AccountDeletionRequestDocument.class).stream()
                .map(MongoAccountDeletionAdapter::toDomain)
                .toList();
    }

    /** 다른 컨텍스트에 남은 기록의 차단 사유는 각 소유 컨텍스트가 판정한다(account-deletion-participants D1). */
    @Override
    public List<AccountDeletionBlocker> evaluateBlockers(String accountId, boolean explicitHold) {
        List<AccountDeletionBlocker> blockers = new ArrayList<>(linkedRecords.blockers(accountId));
        if (explicitHold) {
            blockers.add(AccountDeletionBlocker.EXPLICIT_RETENTION_HOLD);
        }
        return List.copyOf(blockers);
    }

    @Override
    @Transactional
    public AccountDeletionRequest complete(
            String requestId,
            String expectedAccountId,
            String actorId,
            String completionKeyHash,
            String completionFingerprint,
            Instant completedAt) {
        AccountDeletionRequest request = findById(requestId)
                .orElseThrow(() -> new NotFoundException("ACCOUNT_DELETION_REQUEST_NOT_FOUND", "탈퇴 요청을 찾을 수 없습니다"));
        if (request.getStatus() == AccountDeletionStatus.COMPLETED) {
            if (request.completionMatches(completionKeyHash, completionFingerprint)) {
                return request;
            }
            throw new ConflictException("IDEMPOTENCY_KEY_REUSED", "동일한 탈퇴 요청에 다른 멱등성 요청을 사용할 수 없습니다");
        }
        if (!expectedAccountId.equals(request.getAccountId())) {
            throw new ConflictException("ACCOUNT_DELETION_REQUEST_CHANGED", "탈퇴 요청 대상이 변경되었습니다");
        }

        List<AccountDeletionBlocker> blockers = evaluateBlockers(expectedAccountId, request.isHeld());
        request.review(blockers, completedAt);
        if (!blockers.isEmpty()) {
            return save(request);
        }

        Query accountQuery = Query.query(Criteria.where("_id")
                .is(expectedAccountId)
                .and("status")
                .is("SUSPENDED")
                .and("suspendedReason")
                .is(AccountDeletionRequest.suspensionReason(requestId)));
        if (!mongo.exists(accountQuery, "accounts")) {
            throw new ConflictException("ACCOUNT_DELETION_SUSPENSION_MISSING", "탈퇴 전용 계정 잠금이 유지되지 않아 파기를 중단했습니다");
        }

        String anonymousSubject = "withdrawn-" + UUID.randomUUID();
        // 다른 컨텍스트의 기록은 각 소유 컨텍스트가 지우거나 익명화한다. 같은 트랜잭션에 묶여 함께 되돌려진다.
        Map<String, Long> counts =
                new LinkedHashMap<>(linkedRecords.erase(expectedAccountId, anonymousSubject, requestId));
        counts.put(
                "policyAcceptances",
                remove("policy_acceptances", Criteria.where("accountId").is(expectedAccountId)));
        counts.put(
                "thirdPartyConsents",
                remove(
                        "third_party_provision_consent_events",
                        Criteria.where("accountId").is(expectedAccountId)));
        counts.put("accounts", mongo.remove(accountQuery, "accounts").getDeletedCount());
        if (counts.get("accounts") != 1L) {
            throw new ConflictException("ACCOUNT_DELETION_RACE", "계정 상태가 동시에 변경되어 파기를 중단했습니다");
        }

        request.complete(actorId, completionKeyHash, completionFingerprint, counts, completedAt);
        return save(request);
    }

    private long remove(String collection, Criteria criteria) {
        DeleteResult result = mongo.remove(Query.query(criteria), collection);
        return result.getDeletedCount();
    }

    private static AccountDeletionRequestDocument toDocument(AccountDeletionRequest request) {
        return new AccountDeletionRequestDocument(
                request.getId(),
                request.getAccountId(),
                request.getStatus().name(),
                request.getRequestIdempotencyKeyHash(),
                request.getRequestFingerprint(),
                request.getBlockers().stream().map(Enum::name).toList(),
                request.getHoldReason() == null ? null : request.getHoldReason().name(),
                request.getHoldPlacedBy(),
                request.getHoldPlacedAt(),
                request.getRequestedAt(),
                request.getUpdatedAt(),
                request.getCompletedAt(),
                request.getCompletedBy(),
                request.getCompletionIdempotencyKeyHash(),
                request.getCompletionFingerprint(),
                request.getDeletionCounts(),
                request.getVersion());
    }

    private static AccountDeletionRequest toDomain(AccountDeletionRequestDocument document) {
        return new AccountDeletionRequest(
                document.getId(),
                document.getAccountId(),
                AccountDeletionStatus.valueOf(document.getStatus()),
                document.getRequestIdempotencyKeyHash(),
                document.getRequestFingerprint(),
                document.getBlockers() == null
                        ? List.of()
                        : document.getBlockers().stream()
                                .map(AccountDeletionBlocker::valueOf)
                                .toList(),
                document.getHoldReason() == null ? null : AccountDeletionHoldReason.valueOf(document.getHoldReason()),
                document.getHoldPlacedBy(),
                document.getHoldPlacedAt(),
                document.getRequestedAt(),
                document.getUpdatedAt(),
                document.getCompletedAt(),
                document.getCompletedBy(),
                document.getCompletionIdempotencyKeyHash(),
                document.getCompletionFingerprint(),
                document.getDeletionCounts(),
                document.getVersion());
    }
}
