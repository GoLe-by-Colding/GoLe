package com.gole.api.promotion.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PromotionRunTest {

    private static PromotionRun run(String runKey, String detail, List<ModelCall> calls, String runUrl) {
        return new PromotionRun(
                "id-1",
                runKey,
                PromotionCategory.FEATURE,
                "a".repeat(40),
                RunOutcome.SKIPPED,
                RunReasonCode.MODEL_SKIPPED,
                detail,
                null,
                "abc1234",
                runUrl,
                calls,
                Instant.EPOCH);
    }

    @Test
    @DisplayName("모델이 쓴 사유는 제어문자를 지우고 300자(글자 단위)로 잘라 저장한다")
    void detail_isCleanedAndCapped() {
        String injected = "무시해\u0000\u001b[31m 빨강\n\n" + "😀".repeat(400);

        String detail = run("gh-1-1", injected, List.of(), null).detail();

        assertThat(detail).doesNotContainPattern("\\p{Cntrl}").startsWith("무시해 [31m 빨강 ");
        assertThat(detail.codePointCount(0, detail.length())).isEqualTo(PromotionRun.MAX_DETAIL);
        assertThat(Character.isHighSurrogate(detail.charAt(detail.length() - 1)))
                .isFalse();
    }

    @Test
    @DisplayName("공백뿐인 사유는 null 로 남긴다")
    void blankDetail_isNull() {
        assertThat(run("gh-1-1", " \n\t ", List.of(), null).detail()).isNull();
    }

    @Test
    @DisplayName("runKey·실행 링크·호출 수가 규칙을 벗어나면 거부한다")
    void invalidFields_areRejected() {
        assertThatThrownBy(() -> run("GH 1", null, List.of(), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> run("gh-1-1", null, List.of(), "https://evil.example/run"))
                .isInstanceOf(IllegalArgumentException.class);
        ModelCall call = new ModelCall("codex", null, true, 1, 0, 1, null, null);
        assertThatThrownBy(() -> run("gh-1-1", null, Collections.nCopies(PromotionRun.MAX_CALLS + 1, call), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("제출 결과는 초안 ID 와 SUBMITTED 사유가 함께 있어야 한다")
    void submitted_requiresDraftAndReason() {
        assertThatThrownBy(() -> new PromotionRun(
                        "id-1",
                        "gh-1-1",
                        PromotionCategory.SERVICE,
                        null,
                        RunOutcome.SUBMITTED,
                        RunReasonCode.SUBMITTED,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PromotionRun(
                        "id-1",
                        "gh-1-1",
                        PromotionCategory.SERVICE,
                        null,
                        RunOutcome.SKIPPED,
                        RunReasonCode.SUBMITTED,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("호출 사용량은 음수·캐시가 입력보다 큰 값·모델 이름의 이상한 문자를 거부한다")
    void modelCall_rejectsImpossibleNumbers() {
        assertThatThrownBy(() -> new ModelCall("codex", null, true, -1, 0, 0, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModelCall("codex", null, true, 1, 2, 0, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModelCall("codex", "<script>", true, 1, 0, 0, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
