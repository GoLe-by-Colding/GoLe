package com.gole.api.account.adapter.out.persistence;

import com.gole.api.account.application.port.out.AccountDeletionRepositoryPort;
import com.gole.api.account.domain.model.AccountDeletionBlocker;
import com.gole.api.account.domain.model.AccountDeletionHoldReason;
import com.gole.api.account.domain.model.AccountDeletionRequest;
import com.gole.api.account.domain.model.AccountDeletionStatus;
import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.NotFoundException;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
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
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Mongo 기반 보존 매트릭스와 계정 연계 파기 어댑터. */
@Component
public class MongoAccountDeletionAdapter implements AccountDeletionRepositoryPort {

    private static final List<String> ACTIVE_ORDER_STATUSES =
            List.of("PAYMENT_PENDING", "PAYMENT_REVIEW", "FUNDS_HELD", "DISPUTED", "REFUND_PENDING");

    private final AccountDeletionRequestMongoRepository requests;
    private final MongoTemplate mongo;

    public MongoAccountDeletionAdapter(AccountDeletionRequestMongoRepository requests, MongoTemplate mongo) {
        this.requests = requests;
        this.mongo = mongo;
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

    @Override
    public List<AccountDeletionBlocker> evaluateBlockers(String accountId, boolean explicitHold) {
        List<AccountDeletionBlocker> blockers = new ArrayList<>();
        Criteria party = new Criteria()
                .orOperator(
                        Criteria.where("buyerId").is(accountId),
                        Criteria.where("sellerId").is(accountId));
        if (exists(
                "orders",
                new Criteria().andOperator(party, Criteria.where("status").in(ACTIVE_ORDER_STATUSES)))) {
            blockers.add(AccountDeletionBlocker.ACTIVE_ORDER);
        }
        if (exists(
                "settlements",
                Criteria.where("sellerId").is(accountId).and("status").ne("PAID"))) {
            blockers.add(AccountDeletionBlocker.UNSETTLED_PAYOUT);
        }
        Criteria pendingReport = new Criteria()
                .andOperator(
                        Criteria.where("status").is("PENDING"),
                        new Criteria()
                                .orOperator(
                                        Criteria.where("reporterId").is(accountId),
                                        new Criteria()
                                                .andOperator(
                                                        Criteria.where("targetType")
                                                                .is("ACCOUNT"),
                                                        Criteria.where("targetId")
                                                                .is(accountId))));
        if (exists("reports", pendingReport)) {
            blockers.add(AccountDeletionBlocker.PENDING_REPORT);
        }
        if (exists("support_tickets", Criteria.where("requesterId").is(accountId))) {
            blockers.add(AccountDeletionBlocker.SUPPORT_RECORDS_REQUIRE_PURGE);
        }
        Criteria activeListing =
                Criteria.where("sellerId").is(accountId).and("status").in("ACTIVE", "RESERVED");
        Criteria activePost =
                Criteria.where("authorId").is(accountId).and("status").ne("DELETED");
        Criteria visibleComment =
                Criteria.where("authorId").is(accountId).and("hiddenAt").is(null);
        Criteria visibleListingComment =
                Criteria.where("authorId").is(accountId).and("deleted").is(false);
        if (exists("listings", activeListing)
                || exists("posts", activePost)
                || exists("comments", visibleComment)
                || exists("listing_comments", visibleListingComment)) {
            blockers.add(AccountDeletionBlocker.PUBLIC_CONTENT_REQUIRES_LIFECYCLE_REVIEW);
        }
        if (exists(
                "media_assets",
                Criteria.where("ownerId").is(accountId).and("status").ne("REVOKED"))) {
            blockers.add(AccountDeletionBlocker.MEDIA_REQUIRES_LIFECYCLE_REVIEW);
        }
        if (exists(
                "social_chat_rooms",
                Criteria.where("ownerId").is(accountId).and("closedAt").is(null))) {
            blockers.add(AccountDeletionBlocker.OWNED_GROUP_REQUIRES_TRANSFER);
        }
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
        Map<String, Long> counts = new LinkedHashMap<>();

        counts.put(
                "notifications",
                remove("notifications", Criteria.where("recipientId").is(expectedAccountId)));
        counts.put(
                "wishlistEntries",
                remove("wishlist_entries", Criteria.where("userId").is(expectedAccountId)));
        counts.put(
                "collectionItems",
                remove("collection_items", Criteria.where("userId").is(expectedAccountId)));
        counts.put(
                "follows",
                remove(
                        "follows",
                        new Criteria()
                                .orOperator(
                                        Criteria.where("userId").is(expectedAccountId),
                                        Criteria.where("sellerId").is(expectedAccountId))));
        counts.put(
                "chatReadCursors",
                remove("chat_read_cursors", Criteria.where("accountId").is(expectedAccountId)));
        counts.put(
                "chatBlocks",
                remove(
                        "chat_blocks",
                        new Criteria()
                                .orOperator(
                                        Criteria.where("blockerId").is(expectedAccountId),
                                        Criteria.where("blockedId").is(expectedAccountId))));
        counts.put(
                "chatMessages",
                remove("chat_messages", Criteria.where("senderId").is(expectedAccountId)));

        counts.put(
                "marketChatRooms",
                update(
                                "chat_rooms",
                                Criteria.where("buyerId").is(expectedAccountId),
                                new Update().set("buyerId", anonymousSubject))
                        + update(
                                "chat_rooms",
                                Criteria.where("sellerId").is(expectedAccountId),
                                new Update().set("sellerId", anonymousSubject)));
        counts.put(
                "socialChatRooms",
                update(
                                "social_chat_rooms",
                                Criteria.where("memberIds").is(expectedAccountId),
                                new Update()
                                        .pull("memberIds", expectedAccountId)
                                        .unset("dedupeKey"))
                        + update(
                                "social_chat_rooms",
                                Criteria.where("ownerId").is(expectedAccountId),
                                new Update().set("ownerId", anonymousSubject).unset("dedupeKey")));

        counts.put(
                "reviews",
                update(
                                "reviews",
                                Criteria.where("reviewerId").is(expectedAccountId),
                                new Update()
                                        .set("reviewerId", anonymousSubject)
                                        .set("content", "")
                                        .unset("reply"))
                        + update(
                                "reviews",
                                Criteria.where("revieweeId").is(expectedAccountId),
                                new Update().set("revieweeId", anonymousSubject).unset("reply")));
        counts.put(
                "reports",
                update(
                                "reports",
                                Criteria.where("reporterId").is(expectedAccountId),
                                new Update().set("reporterId", anonymousSubject).unset("detail"))
                        + update(
                                "reports",
                                Criteria.where("targetType")
                                        .is("ACCOUNT")
                                        .and("targetId")
                                        .is(expectedAccountId),
                                new Update().set("targetId", anonymousSubject).unset("detail")));
        counts.put(
                "chatReportSnapshots",
                update(
                                "chat_report_snapshots",
                                Criteria.where("reporterId").is(expectedAccountId),
                                new Update().set("reporterId", anonymousSubject))
                        + updateSnapshotSenders(expectedAccountId, anonymousSubject));

        counts.put(
                "retiredListings",
                update(
                        "listings",
                        Criteria.where("sellerId")
                                .is(expectedAccountId)
                                .and("status")
                                .in("SOLD", "DELETED"),
                        new Update()
                                .set("sellerId", anonymousSubject)
                                .set("title", "탈퇴한 사용자의 매물")
                                .set("description", "")
                                .set("photoUrls", List.of())));
        counts.put(
                "deletedPosts",
                update(
                        "posts",
                        Criteria.where("authorId")
                                .is(expectedAccountId)
                                .and("status")
                                .is("DELETED"),
                        new Update()
                                .set("authorId", anonymousSubject)
                                .set("content", "")
                                .set("imageUrls", List.of())
                                .set("likedBy", java.util.Set.of())));
        counts.put(
                "hiddenComments",
                update(
                        "comments",
                        Criteria.where("authorId")
                                .is(expectedAccountId)
                                .and("hiddenAt")
                                .ne(null),
                        new Update().set("authorId", anonymousSubject).set("content", "")));
        counts.put(
                "deletedListingComments",
                update(
                        "listing_comments",
                        Criteria.where("authorId")
                                .is(expectedAccountId)
                                .and("deleted")
                                .is(true),
                        new Update().set("authorId", anonymousSubject).set("content", "")));
        counts.put(
                "revokedMediaAssets",
                update(
                        "media_assets",
                        Criteria.where("ownerId")
                                .is(expectedAccountId)
                                .and("status")
                                .is("REVOKED"),
                        new Update().set("ownerId", anonymousSubject)));

        counts.put(
                "adminAuditTargets",
                update(
                        "admin_actions",
                        Criteria.where("targetType")
                                .is("ACCOUNT")
                                .and("targetId")
                                .is(expectedAccountId),
                        new Update().set("targetId", requestId).unset("reason")));
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

    private boolean exists(String collection, Criteria criteria) {
        return mongo.exists(Query.query(criteria), collection);
    }

    private long remove(String collection, Criteria criteria) {
        DeleteResult result = mongo.remove(Query.query(criteria), collection);
        return result.getDeletedCount();
    }

    private long update(String collection, Criteria criteria, Update update) {
        UpdateResult result = mongo.updateMulti(Query.query(criteria), update, collection);
        return result.getModifiedCount();
    }

    private long updateSnapshotSenders(String accountId, String anonymousSubject) {
        Update update = new Update()
                .set("messages.$[message].senderId", anonymousSubject)
                .filterArray(Criteria.where("message.senderId").is(accountId));
        return update(
                "chat_report_snapshots", Criteria.where("messages.senderId").is(accountId), update);
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
