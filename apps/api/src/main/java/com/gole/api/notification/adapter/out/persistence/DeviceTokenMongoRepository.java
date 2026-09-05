package com.gole.api.notification.adapter.out.persistence;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

/** 단말 푸시 토큰 Spring Data MongoDB 리포지토리. */
public interface DeviceTokenMongoRepository extends MongoRepository<DeviceTokenDocument, String> {

    List<DeviceTokenDocument> findByAccountId(String accountId);
}
