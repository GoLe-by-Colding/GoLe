package com.gole.api.chat.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.chat.application.port.out.SupportAssistantPort.Analysis;
import com.gole.api.chat.application.port.out.SupportAssistantPort.Priority;
import com.gole.api.chat.domain.model.SupportCategory;
import com.mongodb.client.result.UpdateResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.util.ReflectionTestUtils;

class MongoSupportAssistantAnalysisAdapterTest {

    private static final Instant STARTED_AT = Instant.parse("2026-09-04T01:00:00Z");
    private static final Instant LEASE_UNTIL = Instant.parse("2026-09-04T01:00:30Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-09-04T01:00:01Z");

    private final SupportAssistantAnalysisMongoRepository repository =
            mock(SupportAssistantAnalysisMongoRepository.class);
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final MongoSupportAssistantAnalysisAdapter adapter =
            new MongoSupportAssistantAnalysisAdapter(repository, mongoTemplate);

    @Test
    void enqueueUsesIdempotentUpsertWithoutInquirySourceText() {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(SupportTicketDocument.class)))
                .thenReturn(UpdateResult.acknowledged(1L, 1L, null));
        when(mongoTemplate.upsert(any(Query.class), any(Update.class), eq(SupportAssistantAnalysisDocument.class)))
                .thenReturn(UpdateResult.acknowledged(0L, null, new BsonString("room-1")));

        assertThat(adapter.enqueue("room-1", STARTED_AT)).isTrue();
        verify(mongoTemplate).upsert(any(Query.class), any(Update.class), eq(SupportAssistantAnalysisDocument.class));

        assertThat(SupportAssistantAnalysisDocument.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("title", "message", "requesterId", "email", "phone");
    }

    @Test
    void missingPurgedTicketPreventsLateAnalysisRecreation() {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(SupportTicketDocument.class)))
                .thenReturn(UpdateResult.acknowledged(0L, 0L, null));

        assertThat(adapter.enqueue("room-1", STARTED_AT)).isFalse();

        verify(mongoTemplate, never())
                .upsert(any(Query.class), any(Update.class), eq(SupportAssistantAnalysisDocument.class));
    }

    @Test
    void atomicClaimReturnsLeaseTokenAndIncrementedAttempt() {
        SupportAssistantAnalysisDocument claimed = SupportAssistantAnalysisDocument.pending("room-1", STARTED_AT);
        ReflectionTestUtils.setField(claimed, "attempts", 2);
        when(mongoTemplate.findAndModify(
                        any(Query.class),
                        any(Update.class),
                        any(FindAndModifyOptions.class),
                        eq(SupportAssistantAnalysisDocument.class)))
                .thenReturn(claimed);

        var claim = adapter.tryClaim("room-1", STARTED_AT, LEASE_UNTIL, 5).orElseThrow();

        assertThat(claim.roomId()).isEqualTo("room-1");
        assertThat(claim.leaseToken()).isNotBlank();
        assertThat(claim.attempt()).isEqualTo(2);
    }

    @Test
    void unclaimableRoomSkipsAnalysisLease() {
        when(mongoTemplate.findAndModify(
                        any(Query.class),
                        any(Update.class),
                        any(FindAndModifyOptions.class),
                        eq(SupportAssistantAnalysisDocument.class)))
                .thenReturn(null);

        assertThat(adapter.tryClaim("room-1", STARTED_AT, LEASE_UNTIL, 5)).isEmpty();
    }

    @Test
    void completedResultRoundTripsWithoutInquirySourceText() {
        Analysis analysis = result();
        SupportAssistantAnalysisDocument completed =
                SupportAssistantAnalysisDocument.pending("room-1", STARTED_AT).completed(analysis, COMPLETED_AT);
        when(repository.findById("room-1")).thenReturn(Optional.of(completed));

        assertThat(adapter.findCompletedByRoomId("room-1"))
                .contains(
                        new com.gole.api.chat.application.port.out.SupportAssistantAnalysisRepositoryPort
                                .StoredAnalysis("room-1", analysis, COMPLETED_AT));
    }

    @Test
    void completionAndRetryAreGuardedByLeaseOwnership() {
        Analysis analysis = result();

        adapter.complete("room-1", "lease-1", analysis, COMPLETED_AT);
        adapter.retry("room-1", "lease-1", COMPLETED_AT, COMPLETED_AT.plusSeconds(5));
        adapter.fail("room-1", "lease-1", COMPLETED_AT);

        verify(mongoTemplate, org.mockito.Mockito.times(3))
                .updateFirst(any(Query.class), any(Update.class), eq(SupportAssistantAnalysisDocument.class));
    }

    private static Analysis result() {
        return new Analysis(
                SupportCategory.TRADE,
                Priority.HIGH,
                "거래 검토 필요",
                "확인 후 안내드리겠습니다.",
                List.of("ESCROW_REVIEW"),
                true,
                false,
                "rules-v1");
    }
}
