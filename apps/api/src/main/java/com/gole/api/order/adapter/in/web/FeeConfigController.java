package com.gole.api.order.adapter.in.web;

import com.gole.api.order.domain.model.FeePolicy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 판매자가 등록 전에 확인할 수 있는 공개 수수료 정책 조회 API. */
@Tag(name = "Config", description = "공개 설정")
@RestController
@RequestMapping("/api/v1/config")
public class FeeConfigController {

    private final FeePolicy feePolicy;

    public FeeConfigController(FeePolicy feePolicy) {
        this.feePolicy = feePolicy;
    }

    @Operation(summary = "판매 수수료 정책 조회", description = "현재 적용되는 수수료율과 원화 기준 최소·최대 수수료를 공개한다. 인증이 필요 없다.")
    @GetMapping("/fees")
    public FeeConfigResponse fees() {
        return FeeConfigResponse.from(feePolicy);
    }

    /** 운영 설정이나 내부 구현을 노출하지 않는 공개 응답 계약. 최대 수수료 0은 상한 없음을 뜻한다. */
    public record FeeConfigResponse(double rate, long minFee, long maxFee) {

        static FeeConfigResponse from(FeePolicy policy) {
            return new FeeConfigResponse(policy.rate(), policy.minFee(), policy.maxFee());
        }
    }
}
