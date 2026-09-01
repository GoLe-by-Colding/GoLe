package com.gole.api.account.config;

import com.gole.api.account.application.service.OnboardingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 온보딩 전화번호 인증 발송 설정 등록. (onboarding D3) */
@Configuration
@EnableConfigurationProperties(OnboardingProperties.class)
public class OnboardingConfig {}
