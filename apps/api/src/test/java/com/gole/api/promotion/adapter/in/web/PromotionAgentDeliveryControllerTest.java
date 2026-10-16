package com.gole.api.promotion.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.exception.UnauthorizedException;
import com.gole.api.promotion.adapter.in.web.AdminPromotionPostController.CaptureRequest;
import com.gole.api.promotion.adapter.in.web.AdminPromotionRunController.RecordRunRequest;
import com.gole.api.promotion.adapter.in.web.PromotionAgentDeliveryController.DeliveryRequest;
import com.gole.api.promotion.adapter.in.web.PromotionAgentDeliveryController.DraftRequest;
import com.gole.api.promotion.adapter.in.web.PromotionAgentDeliveryController.ImageRequest;
import com.gole.api.promotion.application.port.in.DeliverPromotionDraftUseCase;
import com.gole.api.promotion.application.port.in.DeliverPromotionDraftUseCase.DeliveryCommand;
import com.gole.api.promotion.domain.model.CaptureDataSource;
import com.gole.api.promotion.domain.model.PromotionCategory;
import com.gole.api.promotion.domain.model.PromotionChannel;
import com.gole.api.promotion.domain.model.RunOutcome;
import com.gole.api.promotion.domain.model.RunReasonCode;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

class PromotionAgentDeliveryControllerTest {

    private static final String PNG = Base64.getEncoder().encodeToString(new byte[] {(byte) 0x89, 'P', 'N', 'G'});

    private final DeliverPromotionDraftUseCase useCase = mock(DeliverPromotionDraftUseCase.class);
    private final PromotionAgentDeliveryController controller = new PromotionAgentDeliveryController(useCase);

    private static final RecordRunRequest RUN = new RecordRunRequest(
            "gh-9-1",
            PromotionCategory.SERVICE,
            null,
            RunOutcome.SUBMITTED,
            RunReasonCode.SUBMITTED,
            null,
            null,
            null,
            null,
            List.of());

    private static final CaptureRequest CAPTURE = new CaptureRequest(
            "매물 탐색",
            "/search",
            null,
            CaptureDataSource.DEMO,
            Instant.EPOCH,
            null,
            null,
            "royal-blue-brick-mobile",
            1,
            "브릭을\n찾다",
            false);

    private static MockHttpServletRequest asBot() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(PromotionAgentTokenInterceptor.ATTR_ACTOR_ID, "bot-1");
        return request;
    }

    private static DeliveryRequest delivery(List<ImageRequest> images) {
        return new DeliveryRequest(
                RUN, null, new DraftRequest(PromotionChannel.THREADS, "캡션", List.of(CAPTURE), null, images));
    }

    @Test
    @DisplayName("봇 계정·원장·설명표·이미지(원본·지시문 포함)를 유스케이스 명령으로 옮긴다")
    void deliver_mapsRequestToCommand() {
        controller.deliver(delivery(List.of(new ImageRequest(PNG, PNG, "다듬기"))), asBot());

        ArgumentCaptor<DeliveryCommand> command = ArgumentCaptor.forClass(DeliveryCommand.class);
        verify(useCase).deliver(command.capture());
        assertThat(command.getValue().actorId()).isEqualTo("bot-1");
        assertThat(command.getValue().run().runKey()).isEqualTo("gh-9-1");
        assertThat(command.getValue().draft().context().category()).isEqualTo(PromotionCategory.SERVICE);
        assertThat(command.getValue().draft().context().captures()).hasSize(1);
        var image = command.getValue().draft().images().getFirst();
        assertThat(image.image().content()).startsWith((byte) 0x89);
        assertThat(image.original()).isNotNull();
        assertThat(image.edit()).isEqualTo("다듬기");
    }

    @Test
    @DisplayName("설명표와 이미지 수가 다르거나 base64 가 깨졌으면 400")
    void deliver_badDraft_isBadRequest() {
        assertThatThrownBy(() -> controller.deliver(
                        delivery(List.of(new ImageRequest(PNG, null, null), new ImageRequest(PNG, null, null))),
                        asBot()))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> controller.deliver(delivery(List.of(new ImageRequest("%%%", null, null))), asBot()))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("가드를 거치지 않은 요청(봇 계정 없음)은 401")
    void deliver_withoutGuard_isUnauthorized() {
        assertThatThrownBy(() -> controller.deliver(
                        delivery(List.of(new ImageRequest(PNG, null, null))), new MockHttpServletRequest()))
                .isInstanceOf(UnauthorizedException.class);
        verify(useCase, org.mockito.Mockito.never()).deliver(any());
    }
}
