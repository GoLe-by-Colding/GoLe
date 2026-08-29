package com.gole.api.chat.adapter.out.persistence;

import com.gole.api.chat.application.port.out.ChatBlockRepositoryPort;
import com.gole.api.chat.domain.model.ChatBlock;
import java.util.Collection;
import java.util.List;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

@Component
public class MongoChatBlockAdapter implements ChatBlockRepositoryPort {

    private final ChatBlockMongoRepository blocks;
    private final MongoTemplate mongoTemplate;

    public MongoChatBlockAdapter(ChatBlockMongoRepository blocks, MongoTemplate mongoTemplate) {
        this.blocks = blocks;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void save(ChatBlock block) {
        blocks.save(new ChatBlockDocument(
                id(block.blockerId(), block.blockedId()),
                block.blockerId(),
                block.blockedId(),
                block.reason(),
                block.blockedAt()));
    }

    @Override
    public void delete(String blockerId, String blockedId) {
        blocks.deleteById(id(blockerId, blockedId));
    }

    @Override
    public boolean blockedBetween(String a, String b) {
        return mongoTemplate.exists(pairQuery(a, b), ChatBlockDocument.class);
    }

    @Override
    public boolean blockedBetweenAny(Collection<String> leftAccountIds, Collection<String> rightAccountIds) {
        if (leftAccountIds.isEmpty() || rightAccountIds.isEmpty()) {
            return false;
        }
        Query query = Query.query(new Criteria()
                .orOperator(
                        Criteria.where("blockerId")
                                .in(leftAccountIds)
                                .and("blockedId")
                                .in(rightAccountIds),
                        Criteria.where("blockerId")
                                .in(rightAccountIds)
                                .and("blockedId")
                                .in(leftAccountIds)));
        return mongoTemplate.exists(query, ChatBlockDocument.class);
    }

    @Override
    public List<String> blockedCounterparts(String accountId) {
        Query query = Query.query(new Criteria()
                .orOperator(
                        Criteria.where("blockerId").is(accountId),
                        Criteria.where("blockedId").is(accountId)));
        return mongoTemplate.find(query, ChatBlockDocument.class).stream()
                .map(block -> accountId.equals(block.getBlockerId()) ? block.getBlockedId() : block.getBlockerId())
                .distinct()
                .toList();
    }

    @Override
    public List<String> blockedTargets(String blockerId) {
        return blocks.findAllByBlockerId(blockerId).stream()
                .map(ChatBlockDocument::getBlockedId)
                .distinct()
                .toList();
    }

    private static Query pairQuery(String a, String b) {
        return Query.query(new Criteria()
                .orOperator(
                        Criteria.where("blockerId").is(a).and("blockedId").is(b),
                        Criteria.where("blockerId").is(b).and("blockedId").is(a)));
    }

    private static String id(String blockerId, String blockedId) {
        return blockerId + ":" + blockedId;
    }
}
