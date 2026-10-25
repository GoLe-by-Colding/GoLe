package com.gole.api.chat.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gole.api.chat.domain.model.SupportTicket;
import com.gole.api.common.exception.ConflictException;
import com.mongodb.MongoException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mongodb.core.MongoTemplate;

class MongoSupportTicketAdapterTest {
    private final SupportTicketMongoRepository repository = mock(SupportTicketMongoRepository.class);
    private final MongoSupportTicketAdapter adapter =
            new MongoSupportTicketAdapter(repository, mock(MongoTemplate.class));
    private final SupportTicket ticket =
            SupportTicket.opened("room", "requester", Instant.parse("2026-10-25T00:00:00Z"));

    @Test
    @DisplayName("Mongo 쓰기 충돌112만 문의 갱신 충돌로 변환함")
    void save_mapsTransactionWriteConflict() {
        when(repository.save(any()))
                .thenThrow(new DataIntegrityViolationException(
                        "write conflict", new MongoException(112, "WriteConflict")));
        assertThatThrownBy(() -> adapter.save(ticket)).isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("다른 DB 오류를 동시 갱신 문제로 숨기지 않음")
    void save_preservesOtherDatabaseFailures() {
        var failure = new DataIntegrityViolationException("other failure", new MongoException(11000, "DuplicateKey"));
        when(repository.save(any())).thenThrow(failure);
        assertThatThrownBy(() -> adapter.save(ticket)).isSameAs(failure);
    }
}
