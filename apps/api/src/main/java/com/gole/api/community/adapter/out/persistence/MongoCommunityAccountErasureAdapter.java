package com.gole.api.community.adapter.out.persistence;

import com.gole.api.community.application.port.in.CommunityAccountErasureUseCase.CommunityErasure;
import com.gole.api.community.application.port.out.CommunityAccountErasurePort;
import java.util.List;
import java.util.Set;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * 회원 탈퇴 때 커뮤니티 기록의 차단 판정·파기. 규칙은 계정 탈퇴 어댑터에 있던 것을 그대로 옮겼다
 * (account-deletion-participants D1). 호출자의 Mongo 트랜잭션에 함께 묶인다.
 */
@Component
public class MongoCommunityAccountErasureAdapter implements CommunityAccountErasurePort {

    private final MongoTemplate mongo;

    public MongoCommunityAccountErasureAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public boolean hasPublicContent(String accountId) {
        return exists(
                        "posts",
                        Criteria.where("authorId").is(accountId).and("status").ne("DELETED"))
                || exists(
                        "comments",
                        Criteria.where("authorId").is(accountId).and("hiddenAt").is(null));
    }

    @Override
    public CommunityErasure erase(String accountId, String anonymousSubject) {
        long posts = update(
                "posts",
                Criteria.where("authorId").is(accountId).and("status").is("DELETED"),
                new Update()
                        .set("authorId", anonymousSubject)
                        .set("content", "")
                        .set("imageUrls", List.of())
                        .set("likedBy", Set.of()));
        long comments = update(
                "comments",
                Criteria.where("authorId").is(accountId).and("hiddenAt").ne(null),
                new Update().set("authorId", anonymousSubject).set("content", ""));
        return new CommunityErasure(posts, comments);
    }

    private boolean exists(String collection, Criteria criteria) {
        return mongo.exists(Query.query(criteria), collection);
    }

    private long update(String collection, Criteria criteria, Update update) {
        return mongo.updateMulti(Query.query(criteria), update, collection).getModifiedCount();
    }
}
