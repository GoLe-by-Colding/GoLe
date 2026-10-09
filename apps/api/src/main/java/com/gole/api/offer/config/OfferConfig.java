package com.gole.api.offer.config;

import com.gole.api.offer.application.service.OfferProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 가격 제안 설정 등록. */
@Configuration
@EnableConfigurationProperties(OfferProperties.class)
public class OfferConfig {}
