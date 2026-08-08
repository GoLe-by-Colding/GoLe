package com.gole.api.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 운영 재조정 작업을 활성화한다. 개별 작업은 기능 플래그로 실행 여부를 제어한다. */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
