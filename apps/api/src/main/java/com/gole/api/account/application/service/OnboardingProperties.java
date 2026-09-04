package com.gole.api.account.application.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 온보딩 전화번호 인증 발송 설정. (onboarding D3)
 *
 * <p>알림톡 템플릿 ID를 코드에 박지 않는 이유 — 카카오 템플릿 승인은 코드 밖 운영 과제라
 * 리드타임이 있고, 승인 전/후에 배포 없이 값만 채워 켜야 한다. 값이 비어 있으면 발송 단계가
 * 조용히 건너뛰어지는 게 아니라 명시적으로 거부된다({@code OnboardingService} 참고).
 *
 * <p>record가 아닌 세터 바인딩 클래스인 이유는 {@code PipelineProperties}와 같다 — AOP 프록시
 * 대상이 될 수 있는데 record(final)는 CGLIB 서브클래싱이 불가능하다.
 */
@ConfigurationProperties(prefix = "gole.onboarding")
public class OnboardingProperties {

    /** 전화번호 인증을 온보딩 완료 조건으로 요구하는가. 로컬 기본값은 기존 4단계 흐름을 유지한다. */
    private boolean phoneVerificationRequired = true;

    /** 인증코드 알림톡 템플릿 연동 ID. 비어 있으면 전화번호 인증 요청이 503으로 거부된다. */
    private String phoneVerificationTemplateId = "";

    /** 템플릿에 넘길 인증코드 변수명. 승인된 템플릿의 표기를 그대로 쓴다. */
    private String phoneVerificationCodeVariable = "#{인증번호}";

    public boolean phoneVerificationRequired() {
        return phoneVerificationRequired;
    }

    public String phoneVerificationTemplateId() {
        return phoneVerificationTemplateId;
    }

    public String phoneVerificationCodeVariable() {
        return phoneVerificationCodeVariable;
    }

    public void setPhoneVerificationRequired(boolean phoneVerificationRequired) {
        this.phoneVerificationRequired = phoneVerificationRequired;
    }

    public void setPhoneVerificationTemplateId(String phoneVerificationTemplateId) {
        this.phoneVerificationTemplateId = phoneVerificationTemplateId;
    }

    public void setPhoneVerificationCodeVariable(String phoneVerificationCodeVariable) {
        this.phoneVerificationCodeVariable = phoneVerificationCodeVariable;
    }
}
