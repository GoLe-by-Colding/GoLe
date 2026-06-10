package com.gole.api.common.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

/**
 * MongoDB 클라이언트/데이터베이스 팩토리/트랜잭션 매니저를 명시적으로 구성한다.
 * - 연결 문자열(replicaSet 포함)과 데이터베이스 이름을 확실히 적용
 * - @Transactional 이 replica set 멀티 도큐먼트 트랜잭션으로 동작 (요구사항 13)
 */
@Configuration
public class MongoTransactionConfig {

    @Bean(destroyMethod = "close")
    public MongoClient mongoClient(@Value("${spring.data.mongodb.uri}") String uri) {
        return MongoClients.create(uri);
    }

    @Bean
    public MongoDatabaseFactory mongoDatabaseFactory(
            MongoClient mongoClient, @Value("${spring.data.mongodb.database:gole}") String database) {
        return new SimpleMongoClientDatabaseFactory(mongoClient, database);
    }

    @Bean
    public MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }
}
