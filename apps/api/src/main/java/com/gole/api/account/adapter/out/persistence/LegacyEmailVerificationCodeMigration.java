package com.gole.api.account.adapter.out.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 과거 accounts.verificationCode에 저장된 6자리 원문을 무효화하고 제거한다. */
@Component
public class LegacyEmailVerificationCodeMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyEmailVerificationCodeMigration.class);

    private final MongoTemplate mongo;

    public LegacyEmailVerificationCodeMigration(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        Criteria noHashedReplacement = new Criteria()
                .orOperator(
                        Criteria.where("verificationCodeHash").exists(false),
                        Criteria.where("verificationCodeHash").is(null));
        Query legacyOnly = Query.query(
                new Criteria().andOperator(Criteria.where("verificationCode").exists(true), noHashedReplacement));
        mongo.updateMulti(
                legacyOnly,
                new Update().unset("verificationCodeIssuedAt").set("verificationFailedAttempts", 0),
                AccountDocument.class);

        long removed = mongo.updateMulti(
                        Query.query(Criteria.where("verificationCode").exists(true)),
                        new Update().unset("verificationCode"),
                        AccountDocument.class)
                .getModifiedCount();
        if (removed > 0) {
            // 이메일·계정 ID·인증번호는 로그에 남기지 않는다.
            log.info("Legacy plaintext email verification challenges invalidated: count={}", removed);
        }
    }
}
