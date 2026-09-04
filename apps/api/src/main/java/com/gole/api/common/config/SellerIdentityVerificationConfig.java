package com.gole.api.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 개인 판매자 신원확인 준비 플래그를 명시적으로 등록한다. */
@Configuration
@EnableConfigurationProperties(SellerIdentityVerificationProperties.class)
public class SellerIdentityVerificationConfig {}
