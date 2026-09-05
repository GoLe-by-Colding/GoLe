package com.gole.api.account.application.service;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/** 가입 시 제시하는 문서 버전. 문서를 바꾸면 버전도 함께 올려 과거 증빙을 보존한다. */
@Configuration
@ConfigurationProperties(prefix = "gole.policy")
@Validated
public class SignupPolicyProperties {

    @NotBlank
    private String termsVersion = "2026-09-04";

    @NotBlank
    private String privacyVersion = "2026-09-04";

    @NotBlank
    private String thirdPartyProvisionVersion = "2026-09-04";

    @Min(14)
    private int minimumAge = 14;

    public String getTermsVersion() {
        return termsVersion;
    }

    public void setTermsVersion(String termsVersion) {
        this.termsVersion = termsVersion;
    }

    public String getPrivacyVersion() {
        return privacyVersion;
    }

    public void setPrivacyVersion(String privacyVersion) {
        this.privacyVersion = privacyVersion;
    }

    public String getThirdPartyProvisionVersion() {
        return thirdPartyProvisionVersion;
    }

    public void setThirdPartyProvisionVersion(String thirdPartyProvisionVersion) {
        this.thirdPartyProvisionVersion = thirdPartyProvisionVersion;
    }

    public int getMinimumAge() {
        return minimumAge;
    }

    public void setMinimumAge(int minimumAge) {
        this.minimumAge = minimumAge;
    }
}
