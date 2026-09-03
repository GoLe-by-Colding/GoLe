package com.gole.api.account.adapter.in.web;

import com.gole.api.account.application.port.in.GetCurrentSignupPolicyUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 가입 폼이 빌드 시점 상수가 아닌 서버의 현재 정책 버전을 읽는다. */
@RestController
@RequestMapping("/api/v1/policies")
public class PolicyController {

    private final GetCurrentSignupPolicyUseCase policies;

    public PolicyController(GetCurrentSignupPolicyUseCase policies) {
        this.policies = policies;
    }

    @GetMapping("/current")
    public CurrentPolicyResponse current() {
        var policy = policies.currentSignupPolicy();
        return new CurrentPolicyResponse(policy.termsVersion(), policy.privacyVersion(), policy.minimumAge());
    }

    public record CurrentPolicyResponse(String termsVersion, String privacyVersion, int minimumAge) {}
}
