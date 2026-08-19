package com.gole.api.order.application.service.pipeline;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 파이프라인 타임아웃 설정 등록. */
@Configuration
@EnableConfigurationProperties(PipelineProperties.class)
public class PipelineConfig {}
