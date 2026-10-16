package com.gole.api.promotion.application.port.in;

import com.gole.api.promotion.application.port.in.ManagePromotionMemoryUseCase.ReflectionCommand;
import com.gole.api.promotion.application.port.in.RecordPromotionRunUseCase.RecordRunCommand;
import com.gole.api.promotion.domain.model.PromotionChannel;
import com.gole.api.promotion.domain.model.PromotionPostContext;
import com.gole.api.promotion.domain.model.PromotionRun;
import java.util.List;

/**
 * 지인 서버의 홍보 초안 에이전트가 한 실행의 결과를 한 번에 납품한다(promotion-review D29).
 *
 * <p>성찰 제안 저장 → (초안이 있으면) 큐·중복 확인 → 이미지 업로드 → 초안 생성·검토 요청 → 실행 원장 한 줄.
 * 큐·중복 확인을 여기서 다시 하는 이유는 주문(러너)과 납품 사이에 시간이 지나기 때문이다.
 * 같은 {@code runKey} 로 다시 보내면 아무것도 하지 않고 처음 기록을 돌려준다 — 응답 유실 뒤 재시도로 초안이 둘 생기지 않는다.
 */
public interface DeliverPromotionDraftUseCase {

    /** 검토 대기 초안 한도(promotion-review D18). 러너의 {@code MAX_PENDING_REVIEW} 와 같다. */
    int MAX_PENDING_REVIEW = 5;

    /** 고른 화면은 1장(D25)이고, 다듬은 이미지와 원본까지 2장이다. 여유를 두되 남용은 막는다. */
    int MAX_IMAGES = 4;

    PromotionRun deliver(DeliveryCommand command);

    /**
     * @param actorId 봇 계정 id. 초안 작성자·성찰 제안자다(메이커-체커 D4)
     * @param run 에이전트가 정한 결과. 초안이 있으면 SUBMITTED 로 오고, 납품 중 큐·중복·오류에 따라 바뀐다
     * @param reflection 성찰할 반려가 없었으면 null
     * @param draft 초안을 만들지 않은 실행(건너뜀·실패)이면 null
     */
    record DeliveryCommand(String actorId, RecordRunCommand run, ReflectionCommand reflection, DraftDelivery draft) {}

    /** @param images 설명표({@code context.captures()})와 같은 순서 */
    record DraftDelivery(
            PromotionChannel channel, String caption, PromotionPostContext context, List<DeliveredImage> images) {}

    /** @param original 다듬기 전 원본. 원본을 그대로 올린 자리면 null 이고 edit 도 null 이다 */
    record DeliveredImage(Image image, Image original, String edit) {}

    record Image(byte[] content, String contentType, String filename) {}
}
