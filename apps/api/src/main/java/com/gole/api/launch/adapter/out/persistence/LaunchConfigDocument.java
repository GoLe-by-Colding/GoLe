package com.gole.api.launch.adapter.out.persistence;

import java.time.Instant;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 공개 설정 영속 모델. 문서는 항상 한 건이며 {@link #SINGLETON_ID} 로 고정한다.
 *
 * <p>_id 를 고정하면 "설정이 두 벌 생겨 어느 쪽이 진짜인지 모르는" 상태가 구조적으로 불가능하다.
 */
@Document(collection = "launch_config")
public class LaunchConfigDocument {

    public static final String SINGLETON_ID = "launch";

    @Id
    private String id;

    /** 0~3. enum 이름이 아니라 정수로 저장한다 — 공개 API 계약이 정수이고 순서 비교가 자연스럽다. */
    private int stage;

    /** 기능 apiName -> 개방 여부. 지정되지 않은 기능은 단계 기본을 따른다. */
    private Map<String, Boolean> overrides;

    private Instant updatedAt;
    private String updatedBy;

    @Version
    private Long version;

    protected LaunchConfigDocument() {
        // MongoDB 매핑용
    }

    public LaunchConfigDocument(
            String id, int stage, Map<String, Boolean> overrides, Instant updatedAt, String updatedBy) {
        this(id, stage, overrides, updatedAt, updatedBy, null);
    }

    public LaunchConfigDocument(
            String id, int stage, Map<String, Boolean> overrides, Instant updatedAt, String updatedBy, Long version) {
        this.id = id;
        this.stage = stage;
        this.overrides = overrides;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
        this.version = version;
    }

    public String getId() {
        return id;
    }

    public int getStage() {
        return stage;
    }

    public Map<String, Boolean> getOverrides() {
        return overrides;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Long getVersion() {
        return version;
    }
}
