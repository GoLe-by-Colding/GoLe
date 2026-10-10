package com.gole.api.design.adapter.out.persistence;

import com.gole.api.common.exception.ConflictException;
import com.gole.api.design.application.port.out.MascotRepositoryPort;
import com.gole.api.design.domain.model.MascotAsset;
import com.gole.api.design.domain.model.MascotSelection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

/** 마스코트 적용 리비전({@code mascot_selections})과 업로드 에셋({@code mascot_assets}). */
@Repository
public class MongoMascotAdapter implements MascotRepositoryPort {
    private static final int HISTORY_PAGE = 25;

    private final MongoTemplate mongo;

    public MongoMascotAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Document("mascot_selections")
    public record SelectionDocument(
            @Id long revision,
            String assetId,
            String assetName,
            String actorId,
            String reason,
            String action,
            Instant publishedAt) {
        MascotSelection model() {
            return new MascotSelection(revision, assetId, assetName, actorId, reason, action, publishedAt);
        }

        static SelectionDocument of(MascotSelection s) {
            return new SelectionDocument(
                    s.revision(), s.assetId(), s.assetName(), s.actorId(), s.reason(), s.action(), s.publishedAt());
        }
    }

    @Document("mascot_assets")
    public record AssetDocument(
            @Id String id,
            String name,
            String description,
            String imageKey,
            String imageUrl,
            String darkImageKey,
            String darkImageUrl,
            int width,
            int height,
            String createdBy,
            Instant createdAt) {
        MascotAsset model() {
            return new MascotAsset(
                    id,
                    name,
                    description,
                    imageKey,
                    imageUrl,
                    darkImageKey,
                    darkImageUrl,
                    width,
                    height,
                    createdBy,
                    createdAt);
        }

        static AssetDocument of(MascotAsset a) {
            return new AssetDocument(
                    a.id(),
                    a.name(),
                    a.description(),
                    a.imageKey(),
                    a.imageUrl(),
                    a.darkImageKey(),
                    a.darkImageUrl(),
                    a.width(),
                    a.height(),
                    a.createdBy(),
                    a.createdAt());
        }
    }

    @Override
    public MascotSelection currentSelection() {
        var latest = mongo.findOne(
                new Query().with(Sort.by(Sort.Direction.DESC, "_id")).limit(1), SelectionDocument.class);
        return latest == null ? MascotSelection.initial() : latest.model();
    }

    @Override
    public List<MascotSelection> history(long before) {
        return mongo
                .find(
                        Query.query(Criteria.where("_id").lt(before))
                                .with(Sort.by(Sort.Direction.DESC, "_id"))
                                .limit(HISTORY_PAGE),
                        SelectionDocument.class)
                .stream()
                .map(SelectionDocument::model)
                .toList();
    }

    @Override
    public void appendSelection(MascotSelection selection) {
        try {
            // unique _id 가 compare-and-set 펜스다. 첫 적용끼리의 경쟁도 여기서 한 건만 통과한다.
            mongo.insert(SelectionDocument.of(selection));
        } catch (DuplicateKeyException e) {
            throw new ConflictException("MASCOT_REVISION_CONFLICT", "다른 관리자가 먼저 바꿨습니다. 최신 값을 불러와 주세요");
        }
    }

    @Override
    public Optional<MascotAsset> findAsset(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(mongo.findById(id, AssetDocument.class)).map(AssetDocument::model);
    }

    @Override
    public List<MascotAsset> assets() {
        return mongo.find(new Query().with(Sort.by(Sort.Direction.DESC, "createdAt")), AssetDocument.class).stream()
                .map(AssetDocument::model)
                .toList();
    }

    @Override
    public long countAssets() {
        return mongo.count(new Query(), AssetDocument.class);
    }

    @Override
    public void saveAsset(MascotAsset asset) {
        mongo.insert(AssetDocument.of(asset));
    }

    @Override
    public boolean deleteAsset(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        return mongo.remove(Query.query(Criteria.where("_id").is(id)), AssetDocument.class)
                        .getDeletedCount()
                > 0;
    }
}
