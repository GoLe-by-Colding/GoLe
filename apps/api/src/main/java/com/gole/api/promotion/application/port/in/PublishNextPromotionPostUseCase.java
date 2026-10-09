package com.gole.api.promotion.application.port.in;

import com.gole.api.promotion.domain.model.PromotionPost;

/**
 * 승인된 글 중 가장 먼저 승인된 것 하나를 발행한다. 무엇을 올릴지는 규칙이 정한다 — 모델 판단 없음.
 *
 * <p>승인된 글이 없으면 {@code NoApprovedPromotionPostsException}, 직전 발행과의 간격이 모자라면
 * {@code PromotionPublishTooSoonException}.
 */
public interface PublishNextPromotionPostUseCase {

    PromotionPost publishNext();
}
