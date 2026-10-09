package com.gole.api.admin.adapter.out.persistence;

import com.gole.api.admin.application.port.out.AdminAuditPseudonymizationPort;
import com.gole.api.admin.domain.model.AdminTargetType;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/** 감사 기록({@code admin_actions})의 대상 ID만 바꾼다. 기록을 지우거나 다른 필드를 고치지 않는다. */
@Component
public class AdminAuditPseudonymizationAdapter implements AdminAuditPseudonymizationPort {

    private final MongoTemplate mongoTemplate;

    public AdminAuditPseudonymizationAdapter(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public long replaceTargetId(AdminTargetType targetType, String fromTargetId, String toTargetId) {
        return mongoTemplate
                .updateMulti(
                        Query.query(new Criteria()
                                .andOperator(
                                        Criteria.where("targetType").is(targetType.name()),
                                        Criteria.where("targetId").is(fromTargetId))),
                        new Update().set("targetId", toTargetId),
                        AdminActionDocument.class)
                .getModifiedCount();
    }
}
