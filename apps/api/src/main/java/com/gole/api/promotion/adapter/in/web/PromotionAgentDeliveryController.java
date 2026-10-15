package com.gole.api.promotion.adapter.in.web;

import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.exception.UnauthorizedException;
import com.gole.api.promotion.adapter.in.web.AdminPromotionMemoryController.ProposalRequest;
import com.gole.api.promotion.adapter.in.web.AdminPromotionMemoryController.ReflectRequest;
import com.gole.api.promotion.adapter.in.web.AdminPromotionPostController.CaptureRequest;
import com.gole.api.promotion.adapter.in.web.AdminPromotionPostController.CreatePromotionPostRequest;
import com.gole.api.promotion.adapter.in.web.AdminPromotionPostController.ProvenanceRequest;
import com.gole.api.promotion.adapter.in.web.AdminPromotionRunController.RecordRunRequest;
import com.gole.api.promotion.application.port.in.DeliverPromotionDraftUseCase;
import com.gole.api.promotion.application.port.in.DeliverPromotionDraftUseCase.DeliveredImage;
import com.gole.api.promotion.application.port.in.DeliverPromotionDraftUseCase.DeliveryCommand;
import com.gole.api.promotion.application.port.in.DeliverPromotionDraftUseCase.DraftDelivery;
import com.gole.api.promotion.application.port.in.DeliverPromotionDraftUseCase.Image;
import com.gole.api.promotion.application.port.in.ManagePromotionMemoryUseCase.ReflectionCommand;
import com.gole.api.promotion.domain.model.PromotionChannel;
import com.gole.api.promotion.domain.model.PromotionRun;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Base64;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 홍보 납품 창구(promotion-review D29). 지인 서버의 에이전트가 홍보 토큰으로 한 실행의 결과를 한 번에 보낸다.
 * 가드는 {@link PromotionAgentTokenInterceptor} 다. 요청 모양은 관리자 API 의 원장·성찰·초안 요청을 그대로 쓴다.
 */
@Tag(name = "Internal · 홍보 납품", description = "홍보 초안 에이전트(지인 서버)가 홍보 토큰으로 결과를 납품")
@RestController
@RequestMapping("/api/internal/promotion-agent/deliveries")
public class PromotionAgentDeliveryController {

    /** 게이트웨이의 이미지 상한(8MB)을 base64 로 늘린 길이. 실제 크기 검증은 media 가 한다. */
    static final int MAX_IMAGE_BASE64 = 11_200_000;

    private final DeliverPromotionDraftUseCase deliveries;

    public PromotionAgentDeliveryController(DeliverPromotionDraftUseCase deliveries) {
        this.deliveries = deliveries;
    }

    @Operation(summary = "실행 결과 납품", description = "같은 runKey 로 다시 보내면 아무것도 하지 않고 처음 원장을 돌려준다.")
    @PostMapping
    public PromotionRun deliver(@Valid @RequestBody DeliveryRequest request, HttpServletRequest http) {
        Object actor = http.getAttribute(PromotionAgentTokenInterceptor.ATTR_ACTOR_ID);
        if (!(actor instanceof String actorId)) {
            // 가드가 등록되지 않은 채 이 경로가 열리면 여기서 막는다.
            throw new UnauthorizedException("INVALID_PROMOTION_AGENT_TOKEN", "홍보 토큰이 필요합니다");
        }
        try {
            return deliveries.deliver(new DeliveryCommand(
                    actorId, request.run().toCommand(), reflection(request.reflection()), draft(request)));
        } catch (IllegalArgumentException invalid) {
            throw new BadRequestException("INVALID_PROMOTION_DELIVERY", invalid.getMessage());
        }
    }

    private static ReflectionCommand reflection(ReflectRequest request) {
        return request == null
                ? null
                : new ReflectionCommand(
                        request.feedbackIds(),
                        request.runKey(),
                        request.proposals().stream()
                                .map(ProposalRequest::toProposal)
                                .toList());
    }

    private static DraftDelivery draft(DeliveryRequest request) {
        DraftRequest draft = request.draft();
        if (draft == null) {
            return null;
        }
        if (draft.images().size() != draft.captures().size()) {
            throw new IllegalArgumentException("images must match captures one to one");
        }
        var context = new CreatePromotionPostRequest(
                        draft.channel(),
                        draft.caption(),
                        List.of(),
                        request.run().sourceCommitSha(),
                        request.run().category(),
                        draft.captures(),
                        draft.provenance())
                .toContext();
        List<DeliveredImage> images = draft.images().stream()
                .map(image -> new DeliveredImage(
                        png(image.data()), image.original() == null ? null : png(image.original()), image.edit()))
                .toList();
        return new DraftDelivery(draft.channel(), draft.caption(), context, images);
    }

    private static Image png(String base64) {
        return new Image(Base64.getDecoder().decode(base64), "image/png", "promotion.png");
    }

    /**
     * @param run 에이전트가 정한 결과. 초안이 있으면 SUBMITTED 로 보내고, 납품 중 큐·중복·오류에 따라 바뀐다
     * @param reflection 성찰할 반려가 없었으면 생략
     * @param draft 초안을 만들지 않은 실행이면 생략 — 원장만 남는다
     */
    public record DeliveryRequest(
            @NotNull @Valid RecordRunRequest run,
            @Valid ReflectRequest reflection,
            @Valid DraftRequest draft) {}

    /** @param images 설명표({@code captures})와 같은 순서 */
    public record DraftRequest(
            @NotNull PromotionChannel channel,
            @NotBlank @Size(max = 500) String caption,

            @NotNull @Size(min = 1, max = DeliverPromotionDraftUseCase.MAX_IMAGES)
            List<@NotNull @Valid CaptureRequest> captures,

            @Valid ProvenanceRequest provenance,

            @NotNull @Size(min = 1, max = DeliverPromotionDraftUseCase.MAX_IMAGES)
            List<@NotNull @Valid ImageRequest> images) {}

    /**
     * @param data 게시할 PNG(base64)
     * @param original 다듬기 전 원본 PNG(base64). 원본을 그대로 올렸으면 생략
     * @param edit 다듬기 지시문. 원본이 있을 때만
     */
    public record ImageRequest(
            @NotBlank @Size(max = MAX_IMAGE_BASE64) String data,
            @Size(max = MAX_IMAGE_BASE64) String original,
            @Size(max = 6000) String edit) {}
}
