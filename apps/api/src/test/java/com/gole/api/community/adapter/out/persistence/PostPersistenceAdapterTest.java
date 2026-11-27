package com.gole.api.community.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.community.domain.model.PostType;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

class PostPersistenceAdapterTest {

    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final PostPersistenceAdapter adapter =
            new PostPersistenceAdapter(mock(PostMongoRepository.class), mongoTemplate);

    private Document capturedQuery() {
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(PostDocument.class));
        return query.getValue().getQueryObject();
    }

    private static boolean mentionsAuthorId(Object node) {
        if (node instanceof Document document) {
            return document.containsKey("authorId")
                    || document.values().stream().anyMatch(PostPersistenceAdapterTest::mentionsAuthorId);
        }
        if (node instanceof List<?> list) {
            return list.stream().anyMatch(PostPersistenceAdapterTest::mentionsAuthorId);
        }
        return false;
    }

    private static Document authorIdCondition(Document root) {
        for (Object clause : root.getList("$and", Object.class)) {
            if (clause instanceof Document document && document.containsKey("authorId")) {
                return document.get("authorId", Document.class);
            }
        }
        throw new AssertionError("authorId 조건이 없음: " + root);
    }

    @Test
    @DisplayName("findPublishedPage 는 제외 작성자를 authorId $nin 으로 건다")
    void findPublishedPage_appliesNinForExcludedAuthors() {
        when(mongoTemplate.find(any(Query.class), eq(PostDocument.class))).thenReturn(List.of());

        adapter.findPublishedPage(10, Optional.empty(), Optional.of(PostType.TIP), Optional.empty(), List.of("B"));

        Document nin = authorIdCondition(capturedQuery());
        assertThat(nin.getList("$nin", String.class)).containsExactly("B");
    }

    @Test
    @DisplayName("findPublishedPage 는 제외 목록이 비면 authorId 조건을 걸지 않는다")
    void findPublishedPage_omitsAuthorConditionWhenNoExclusions() {
        when(mongoTemplate.find(any(Query.class), eq(PostDocument.class))).thenReturn(List.of());

        adapter.findPublishedPage(10, Optional.empty(), Optional.empty(), Optional.empty(), List.of());

        assertThat(mentionsAuthorId(capturedQuery())).isFalse();
    }

    @Test
    @DisplayName("findPublishedRecentFirst 는 PUBLISHED 와 $nin 을 걸고 최신순으로 자른다")
    void findPublishedRecentFirst_appliesStatusNinSortAndLimit() {
        when(mongoTemplate.find(any(Query.class), eq(PostDocument.class))).thenReturn(List.of());

        adapter.findPublishedRecentFirst(7, List.of("B"));

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(PostDocument.class));
        Document criteria = query.getValue().getQueryObject();
        assertThat(criteria.getList("$and", Document.class))
                .anySatisfy(clause -> assertThat(clause.getString("status")).isEqualTo("PUBLISHED"));
        assertThat(authorIdCondition(criteria).getList("$nin", String.class)).containsExactly("B");
        assertThat(query.getValue().getLimit()).isEqualTo(7);
        assertThat(query.getValue().getSortObject().keySet()).containsExactly("createdAt", "_id");
    }

    @Test
    @DisplayName("findPublishedRecentFirst 는 제외 목록이 비면 authorId 조건 없이 조회한다")
    void findPublishedRecentFirst_omitsAuthorConditionWhenNoExclusions() {
        when(mongoTemplate.find(any(Query.class), eq(PostDocument.class))).thenReturn(List.of());

        adapter.findPublishedRecentFirst(7, List.of());

        assertThat(mentionsAuthorId(capturedQuery())).isFalse();
    }
}
