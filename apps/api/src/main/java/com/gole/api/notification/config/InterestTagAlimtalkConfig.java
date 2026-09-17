package com.gole.api.notification.config;

import com.gole.api.notification.application.service.InterestTagAlimtalkProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 관심태그 알림톡 설정 바인딩. */
@Configuration
@EnableConfigurationProperties(InterestTagAlimtalkProperties.class)
public class InterestTagAlimtalkConfig {}
