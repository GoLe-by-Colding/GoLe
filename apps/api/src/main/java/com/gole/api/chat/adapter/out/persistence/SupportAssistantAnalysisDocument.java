package com.gole.api.chat.adapter.out.persistence;

import com.gole.api.chat.application.port.out.SupportAssistantPort;
import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/** 문의 원문은 저장하지 않고 관리자 검토에 필요한 분석 결과만 보관한다. */
@Document(collection = "support_assistant_analyses")
@CompoundIndex(
        name = "support_analysis_recovery_idx",
        def = "{'state': 1, 'nextAttemptAt': 1, 'leaseUntil': 1, 'attempts': 1, 'requestedAt': 1}")
public class SupportAssistantAnalysisDocument {

    static final String PENDING = "PENDING";
    static final String PROCESSING = "PROCESSING";
    static final String RETRY = "RETRY";
    static final String COMPLETED = "COMPLETED";
    static final String FAILED = "FAILED";

    @Id
    private String roomId;

    private String state;
    private String category;
    private String priority;
    private String summary;
    private String draft;
    private List<String> risk;
    private Boolean humanReview;
    private Boolean externalModel;
    private String engine;
    private int attempts;
    private Instant requestedAt;
    private Instant startedAt;
    private Instant leaseUntil;
    private String leaseToken;
    private Instant nextAttemptAt;
    private Instant lastFailureAt;
    private Instant completedAt;

    protected SupportAssistantAnalysisDocument() {}

    private SupportAssistantAnalysisDocument(
            String roomId,
            String state,
            String category,
            String priority,
            String summary,
            String draft,
            List<String> risk,
            Boolean humanReview,
            Boolean externalModel,
            String engine,
            int attempts,
            Instant requestedAt,
            Instant startedAt,
            Instant leaseUntil,
            String leaseToken,
            Instant nextAttemptAt,
            Instant lastFailureAt,
            Instant completedAt) {
        this.roomId = roomId;
        this.state = state;
        this.category = category;
        this.priority = priority;
        this.summary = summary;
        this.draft = draft;
        this.risk = risk;
        this.humanReview = humanReview;
        this.externalModel = externalModel;
        this.engine = engine;
        this.attempts = attempts;
        this.requestedAt = requestedAt;
        this.startedAt = startedAt;
        this.leaseUntil = leaseUntil;
        this.leaseToken = leaseToken;
        this.nextAttemptAt = nextAttemptAt;
        this.lastFailureAt = lastFailureAt;
        this.completedAt = completedAt;
    }

    static SupportAssistantAnalysisDocument pending(String roomId, Instant requestedAt) {
        return new SupportAssistantAnalysisDocument(
                roomId,
                PENDING,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                0,
                requestedAt,
                null,
                null,
                null,
                requestedAt,
                null,
                null);
    }

    SupportAssistantAnalysisDocument completed(SupportAssistantPort.Analysis analysis, Instant at) {
        return new SupportAssistantAnalysisDocument(
                roomId,
                COMPLETED,
                analysis.recommendedCategory().name(),
                analysis.priority().name(),
                analysis.summary(),
                analysis.draftReply(),
                analysis.riskFlags(),
                analysis.humanReviewRequired(),
                analysis.externalModelUsed(),
                analysis.engineVersion(),
                attempts,
                requestedAt,
                startedAt,
                null,
                null,
                null,
                lastFailureAt,
                at);
    }

    SupportAssistantAnalysisDocument failed(Instant at) {
        return new SupportAssistantAnalysisDocument(
                roomId,
                FAILED,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                attempts,
                requestedAt,
                startedAt,
                null,
                null,
                null,
                at,
                at);
    }

    public String getRoomId() {
        return roomId;
    }

    public String getState() {
        return state;
    }

    public String getCategory() {
        return category;
    }

    public String getPriority() {
        return priority;
    }

    public String getSummary() {
        return summary;
    }

    public String getDraft() {
        return draft;
    }

    public List<String> getRisk() {
        return risk == null ? List.of() : List.copyOf(risk);
    }

    public Boolean getHumanReview() {
        return humanReview;
    }

    public Boolean getExternalModel() {
        return externalModel;
    }

    public String getEngine() {
        return engine;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getLeaseUntil() {
        return leaseUntil;
    }

    public String getLeaseToken() {
        return leaseToken;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getLastFailureAt() {
        return lastFailureAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
