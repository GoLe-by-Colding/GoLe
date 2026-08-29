package com.gole.api.launch.adapter.out.persistence;

import com.gole.api.launch.application.port.out.LaunchConfigHistoryPort;
import com.gole.api.launch.application.port.out.LaunchConfigRepositoryPort;
import com.gole.api.launch.domain.model.LaunchConfig;
import com.gole.api.launch.domain.model.LaunchConfigChange;
import com.gole.api.launch.domain.model.LaunchFeature;
import com.gole.api.launch.domain.model.LaunchStage;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/** 공개 설정과 변경 이력의 영속성 어댑터. 도메인과 도큐먼트를 양방향 매핑한다. */
@Component
public class LaunchConfigPersistenceAdapter implements LaunchConfigRepositoryPort, LaunchConfigHistoryPort {

    private static final Logger log = LoggerFactory.getLogger(LaunchConfigPersistenceAdapter.class);

    private final LaunchConfigMongoRepository configs;
    private final LaunchConfigHistoryMongoRepository changes;
    private final MongoTemplate mongoTemplate;

    public LaunchConfigPersistenceAdapter(
            LaunchConfigMongoRepository configs,
            LaunchConfigHistoryMongoRepository changes,
            MongoTemplate mongoTemplate) {
        this.configs = configs;
        this.changes = changes;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<LaunchConfig> load() {
        return configs.findById(LaunchConfigDocument.SINGLETON_ID)
                .map(this::initializeLegacyVersion)
                .map(LaunchConfigPersistenceAdapter::toDomain);
    }

    @Override
    public void save(LaunchConfig config) {
        Map<String, Boolean> overrides = new LinkedHashMap<>();
        config.overrides().forEach((feature, enabled) -> overrides.put(feature.apiName(), enabled));
        configs.save(new LaunchConfigDocument(
                LaunchConfigDocument.SINGLETON_ID,
                config.stage().level(),
                overrides,
                config.updatedAt(),
                config.updatedBy(),
                config.version()));
    }

    @Override
    public void append(LaunchConfigChange change) {
        changes.save(new LaunchConfigHistoryDocument(
                change.id(),
                change.type().name(),
                change.target(),
                change.before(),
                change.after(),
                change.reason(),
                change.actorId(),
                change.actorEmail(),
                change.occurredAt()));
    }

    @Override
    public java.util.List<LaunchConfigChange> findRecent(int limit) {
        return changes.findBy(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "occurredAt"))).stream()
                .map(LaunchConfigPersistenceAdapter::toDomain)
                .toList();
    }

    private static LaunchConfig toDomain(LaunchConfigDocument document) {
        Map<LaunchFeature, Boolean> overrides = new EnumMap<>(LaunchFeature.class);
        if (document.getOverrides() != null) {
            document.getOverrides().forEach((name, enabled) -> {
                if (enabled == null) {
                    return;
                }
                try {
                    overrides.put(LaunchFeature.of(name), enabled);
                } catch (IllegalArgumentException unknownFeature) {
                    // 기능이 제거된 뒤 남은 옛 override. 설정 전체를 못 읽게 만드는 대신 무시한다 —
                    // 공개 API가 이것 때문에 500이 되면 프론트가 통째로 Stage 0 으로 닫힌다.
                    log.warn("알 수 없는 기능 override 를 무시함: {}", name);
                }
            });
        }
        return new LaunchConfig(
                stageOf(document.getStage()),
                overrides,
                document.getUpdatedAt(),
                document.getUpdatedBy(),
                document.getVersion());
    }

    /** @Version 도입 전 문서는 최초 읽기에서 버전 0으로 승격해 중복 insert를 막는다. */
    private LaunchConfigDocument initializeLegacyVersion(LaunchConfigDocument document) {
        if (document.getVersion() != null) {
            return document;
        }
        Query legacy = Query.query(Criteria.where("_id")
                .is(LaunchConfigDocument.SINGLETON_ID)
                .orOperator(
                        Criteria.where("version").exists(false),
                        Criteria.where("version").is(null)));
        mongoTemplate.updateFirst(legacy, new Update().set("version", 0L), LaunchConfigDocument.class);
        return configs.findById(LaunchConfigDocument.SINGLETON_ID).orElse(document);
    }

    /** 저장된 정수가 범위를 벗어나면 가장 닫힌 단계로 읽는다 — 손상된 값으로 기능을 열지 않는다. */
    private static LaunchStage stageOf(int level) {
        try {
            return LaunchStage.ofLevel(level);
        } catch (IllegalArgumentException outOfRange) {
            log.error("저장된 공개 단계가 범위를 벗어남({}) — PREPARING 으로 읽는다", level);
            return LaunchStage.PREPARING;
        }
    }

    private static LaunchConfigChange toDomain(LaunchConfigHistoryDocument document) {
        return new LaunchConfigChange(
                document.getId(),
                LaunchConfigChange.Type.valueOf(document.getType()),
                document.getTarget(),
                document.getBefore(),
                document.getAfter(),
                document.getReason(),
                document.getActorId(),
                document.getActorEmail(),
                document.getOccurredAt());
    }
}
