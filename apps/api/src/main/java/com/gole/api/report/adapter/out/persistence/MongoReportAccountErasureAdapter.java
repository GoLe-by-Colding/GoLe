package com.gole.api.report.adapter.out.persistence;

import com.gole.api.report.application.port.in.ReportAccountErasureUseCase.ReportErasure;
import com.gole.api.report.application.port.out.ReportAccountErasurePort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * 회원 탈퇴 때 신고 기록의 차단 판정·파기. 규칙은 계정 탈퇴 어댑터에 있던 것을 그대로 옮겼다
 * (account-deletion-participants D1). 호출자의 Mongo 트랜잭션에 함께 묶인다.
 */
@Component
public class MongoReportAccountErasureAdapter implements ReportAccountErasurePort {

    private final MongoTemplate mongo;

    public MongoReportAccountErasureAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public boolean hasPendingReport(String accountId) {
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
        return exists("reports", pendingReport);
    }

    @Override
    public ReportErasure erase(String accountId, String anonymousSubject) {
        return new ReportErasure(update(
                        "reports",
                        Criteria.where("reporterId").is(accountId),
                        new Update().set("reporterId", anonymousSubject).unset("detail"))
                + update(
                        "reports",
                        Criteria.where("targetType")
                                .is("ACCOUNT")
                                .and("targetId")
                                .is(accountId),
                        new Update().set("targetId", anonymousSubject).unset("detail")));
    }

    private boolean exists(String collection, Criteria criteria) {
        return mongo.exists(Query.query(criteria), collection);
    }

    private long update(String collection, Criteria criteria, Update update) {
        return mongo.updateMulti(Query.query(criteria), update, collection).getModifiedCount();
    }
}
