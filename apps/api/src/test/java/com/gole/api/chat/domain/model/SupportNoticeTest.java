package com.gole.api.chat.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.common.exception.BadRequestException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SupportNoticeTest {
    private static final Instant OPENED = Instant.parse("2026-10-01T10:00:00Z");

    @Test
    @DisplayName("일반 답변과 해결 처리로 안내 이행을 추정하지 않는다")
    void ordinaryReplyAndResolve_doNotInventNotice() {
        SupportTicket ticket = assigned().agentReplied(OPENED.plusSeconds(1)).resolve(OPENED.plusSeconds(2));
        assertThat(ticket.notices()).isEmpty();
        assertThat(ticket.firstNoticeAt(SupportNoticeKind.PROGRESS)).isNull();
        assertThat(ticket.firstNoticeAt(SupportNoticeKind.RESULT)).isNull();
        assertThat(ticket.reopen(OPENED.plusSeconds(3)).progressDueAt()).isEqualTo(OPENED.plusSeconds(72 * 3600));
    }

    @Test
    @DisplayName("결과 안내는 진행 안내도 충족하고 최초 실제 메시지 시각을 보존한다")
    void resultNoticeUsesFirstMessageAcrossRepeatedNoticesAndTransitions() {
        Instant firstAt = OPENED.plusSeconds(60);
        SupportTicket first = assigned().recordNotice(SupportNoticeKind.RESULT, message("first", firstAt));
        SupportTicket next = first.recordNotice(SupportNoticeKind.PROGRESS, message("second", firstAt.plusSeconds(1)))
                .agentReplied(firstAt.plusSeconds(2))
                .userReplied(firstAt.plusSeconds(3))
                .transferTo("another-admin", firstAt.plusSeconds(4))
                .resolve(firstAt.plusSeconds(5))
                .reopen(firstAt.plusSeconds(6));
        assertThat(next.notices()).hasSize(2);
        assertThat(next.firstNoticeAt(SupportNoticeKind.PROGRESS)).isEqualTo(firstAt);
        assertThat(next.firstNoticeAt(SupportNoticeKind.RESULT)).isEqualTo(firstAt);
        assertThat(next.progressDueAt()).isEqualTo(first.progressDueAt());
        assertThat(next.responseDueAt()).isEqualTo(first.responseDueAt());
        assertThat(next.notices().getFirst())
                .isEqualTo(new SupportNotice(SupportNoticeKind.RESULT, "first", "admin", firstAt));
    }

    @Test
    @DisplayName("진행 안내만으로 결과 안내를 완료시키지 않는다")
    void progressNoticeDoesNotCompleteResultTarget() {
        SupportTicket ticket =
                assigned().recordNotice(SupportNoticeKind.PROGRESS, message("progress", OPENED.plusSeconds(1)));
        assertThat(ticket.firstNoticeAt(SupportNoticeKind.PROGRESS)).isNotNull();
        assertThat(ticket.firstNoticeAt(SupportNoticeKind.RESULT)).isNull();
    }

    @Test
    @DisplayName("같은 메시지 기록은 중복되지 않고 다른 방·담당자·과거 메시지는 거부한다")
    void noticeReferencesAreBoundToCurrentConversationAndSender() {
        ChatMessage message = message("first", OPENED.plusSeconds(1));
        SupportTicket first = assigned().recordNotice(SupportNoticeKind.PROGRESS, message);
        assertThat(first.recordNotice(SupportNoticeKind.PROGRESS, message)).isSameAs(first);
        for (ChatMessage invalid : java.util.List.of(
                new ChatMessage("x", "other", "admin", "안내", OPENED),
                new ChatMessage("x", "room", "user", "안내", OPENED),
                message("x", OPENED.minusSeconds(1)))) {
            assertThatThrownBy(() -> assigned().recordNotice(SupportNoticeKind.PROGRESS, invalid))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    private SupportTicket assigned() {
        return SupportTicket.opened("room", "user", OPENED).assignTo("admin", OPENED);
    }

    private ChatMessage message(String id, Instant at) {
        return new ChatMessage(id, "room", "admin", "고객에게 보낼 안내", at);
    }
}
