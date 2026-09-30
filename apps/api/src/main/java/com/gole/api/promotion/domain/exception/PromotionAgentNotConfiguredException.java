package com.gole.api.promotion.domain.exception;

import com.gole.api.common.exception.ServiceUnavailableException;

/** 발행 실행을 시작할 GitHub 자격증명이 운영 환경에 아직 없을 때. */
public class PromotionAgentNotConfiguredException extends ServiceUnavailableException {

    public PromotionAgentNotConfiguredException() {
        super("PROMOTION_AGENT_NOT_CONFIGURED", "Promotion agent dispatch is not configured");
    }
}
