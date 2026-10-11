package com.gole.api.collection.config;

import com.gole.api.collection.application.service.CollectionValueSnapshotProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 컬렉션 자산 추이 스냅샷 설정 바인딩. */
@Configuration
@EnableConfigurationProperties(CollectionValueSnapshotProperties.class)
public class CollectionValueSnapshotConfig {}
