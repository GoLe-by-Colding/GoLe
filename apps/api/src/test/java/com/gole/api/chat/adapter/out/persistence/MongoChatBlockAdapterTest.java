package com.gole.api.chat.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

class MongoChatBlockAdapterTest {

    private final ChatBlockMongoRepository blocks = mock(ChatBlockMongoRepository.class);
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final MongoChatBlockAdapter adapter = new MongoChatBlockAdapter(blocks, mongoTemplate);

    @Test
    void blockedBetweenAnyUsesOneBidirectionalMongoQuery() {
        when(mongoTemplate.exists(any(Query.class), eq(ChatBlockDocument.class)))
                .thenReturn(true);

        assertThat(adapter.blockedBetweenAny(List.of("user-4"), List.of("user-1", "user-2", "user-3")))
                .isTrue();

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).exists(query.capture(), eq(ChatBlockDocument.class));
        Document criteria = query.getValue().getQueryObject();
        List<?> alternatives = criteria.getList("$or", Object.class);
        assertThat(alternatives).hasSize(2);
        assertDirection((Document) alternatives.get(0), List.of("user-4"), List.of("user-1", "user-2", "user-3"));
        assertDirection((Document) alternatives.get(1), List.of("user-1", "user-2", "user-3"), List.of("user-4"));
    }

    @Test
    void blockedBetweenAnySkipsMongoWhenEitherSideIsEmpty() {
        assertThat(adapter.blockedBetweenAny(List.of(), List.of("user-1"))).isFalse();
        assertThat(adapter.blockedBetweenAny(List.of("user-1"), List.of())).isFalse();

        verify(mongoTemplate, never()).exists(any(Query.class), eq(ChatBlockDocument.class));
    }

    @Test
    void blockedTargetsDoesNotExposeBlocksCreatedByTheOtherUser() {
        when(blocks.findAllByBlockerId("user-1"))
                .thenReturn(List.of(new ChatBlockDocument(
                        "user-1:user-2", "user-1", "user-2", null, Instant.parse("2026-08-30T00:00:00Z"))));

        assertThat(adapter.blockedTargets("user-1")).containsExactly("user-2");

        verify(blocks).findAllByBlockerId("user-1");
    }

    private static void assertDirection(Document criteria, List<String> blockers, List<String> blocked) {
        assertThat(criteria.get("blockerId", Document.class).getList("$in", String.class))
                .containsExactlyElementsOf(blockers);
        assertThat(criteria.get("blockedId", Document.class).getList("$in", String.class))
                .containsExactlyElementsOf(blocked);
    }
}
