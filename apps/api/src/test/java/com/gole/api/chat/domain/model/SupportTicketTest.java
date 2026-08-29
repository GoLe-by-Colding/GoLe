package com.gole.api.chat.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.common.exception.BadRequestException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SupportTicketTest {

    private static final Instant NOW = Instant.parse("2026-08-29T10:00:00Z");

    @Test
    void onlyUnassignedTicketCanBeAssigned() {
        SupportTicket assigned = SupportTicket.opened("room-1", "user-1", NOW).assignTo("admin-1", NOW);

        assertThat(assigned.status()).isEqualTo(SupportStatus.IN_PROGRESS);
        assertThatThrownBy(() -> assigned.assignTo("admin-2", NOW)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void adminCannotReplyBeforeAssignmentOrAfterResolution() {
        SupportTicket unassigned = SupportTicket.opened("room-1", "user-1", NOW);
        SupportTicket resolved = unassigned.assignTo("admin-1", NOW).resolve(NOW);

        assertThatThrownBy(() -> unassigned.agentReplied(NOW)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> resolved.agentReplied(NOW)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void userReplyReopensResolvedTicketForSameAssignee() {
        SupportTicket resolved = SupportTicket.opened("room-1", "user-1", NOW)
                .assignTo("admin-1", NOW)
                .resolve(NOW);

        SupportTicket reopened = resolved.userReplied(NOW.plusSeconds(10));

        assertThat(reopened.status()).isEqualTo(SupportStatus.IN_PROGRESS);
        assertThat(reopened.assigneeId()).isEqualTo("admin-1");
        assertThat(reopened.resolvedAt()).isNull();
    }

    @Test
    void userReplyRefreshesUnassignedTicketActivity() {
        SupportTicket unassigned = SupportTicket.opened("room-1", "user-1", NOW);

        SupportTicket refreshed = unassigned.userReplied(NOW.plusSeconds(30));

        assertThat(refreshed.status()).isEqualTo(SupportStatus.UNASSIGNED);
        assertThat(refreshed.assigneeId()).isNull();
        assertThat(refreshed.updatedAt()).isEqualTo(NOW.plusSeconds(30));
    }

    @Test
    void resolveAndReopenAreIdempotentAtTheirTargetState() {
        SupportTicket assigned = SupportTicket.opened("room-1", "user-1", NOW).assignTo("admin-1", NOW);
        SupportTicket resolved = assigned.resolve(NOW);

        assertThat(resolved.resolve(NOW.plusSeconds(1))).isSameAs(resolved);
        SupportTicket reopened = resolved.reopen(NOW.plusSeconds(2));
        assertThat(reopened.reopen(NOW.plusSeconds(3))).isSameAs(reopened);
    }
}
