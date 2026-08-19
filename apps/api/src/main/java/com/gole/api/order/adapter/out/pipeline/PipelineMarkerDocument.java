package com.gole.api.order.adapter.out.pipeline;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/** 파이프라인 1회성 액션 마커. (rule, refId) 유니크로 중복 실행을 막는다. */
@Document(collection = "pipeline_markers")
@CompoundIndex(name = "pipeline_marker_rule_ref_uq", def = "{'rule': 1, 'refId': 1}", unique = true)
public class PipelineMarkerDocument {

    @Id
    private String id;

    private String rule;
    private String refId;
    private Instant markedAt;

    protected PipelineMarkerDocument() {
        // MongoDB 매핑용
    }

    public PipelineMarkerDocument(String id, String rule, String refId, Instant markedAt) {
        this.id = id;
        this.rule = rule;
        this.refId = refId;
        this.markedAt = markedAt;
    }

    public String getId() {
        return id;
    }

    public String getRule() {
        return rule;
    }

    public String getRefId() {
        return refId;
    }

    public Instant getMarkedAt() {
        return markedAt;
    }
}
