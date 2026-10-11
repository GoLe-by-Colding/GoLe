package com.gole.api.notification.adapter.out.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

/** 알림 수신 설정 Spring Data MongoDB 리포지토리. */
public interface NotificationPreferenceMongoRepository
        extends MongoRepository<NotificationPreferenceDocument, String> {}
