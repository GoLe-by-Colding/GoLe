package com.gole.api.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.account.adapter.out.persistence.AccountMongoRepository;
import com.gole.api.account.adapter.out.persistence.AccountPersistenceAdapter;
import com.gole.api.account.application.port.in.GetPublicProfilesUseCase.PublicProfile;
import com.gole.api.account.application.service.PublicProfileService;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 실제 Mongo 에서 닉네임 투영 조회가 다른 계정 필드 없이 닉네임만 읽는지 확인한다. (public-display-name R2) */
@Testcontainers
class PublicProfileQueryIntegrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    static MongoClient client;
    static MongoTemplate mongo;
    static PublicProfileService service;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MONGO.getReplicaSetUrl());
        mongo = new MongoTemplate(client, "gole_public_profile_test");
        AccountMongoRepository repository =
                new MongoRepositoryFactory(mongo).getRepository(AccountMongoRepository.class);
        service = new PublicProfileService(new AccountPersistenceAdapter(repository, mongo));
    }

    @AfterAll
    static void close() {
        client.close();
    }

    @BeforeEach
    void setUp() {
        mongo.getCollection("accounts").deleteMany(new Document());
        mongo.getCollection("accounts")
                .insertMany(List.of(
                        account("named", "브릭고래"),
                        account("unnamed", null),
                        account("blank", " "),
                        account("other", "다른사람")));
    }

    @Test
    @DisplayName("요청한 계정의 닉네임만 읽고, 닉네임이 없거나 없는 계정은 null 로 돌려준다")
    void publicProfiles_projectsNicknamesOnly() {
        List<PublicProfile> profiles = service.publicProfiles(List.of("named", "unnamed", "blank", "withdrawn"));

        assertThat(profiles)
                .containsExactly(
                        new PublicProfile("named", "브릭고래"),
                        new PublicProfile("unnamed", null),
                        new PublicProfile("blank", null),
                        new PublicProfile("withdrawn", null));
    }

    private static Document account(String id, String nickname) {
        Document document = new Document("_id", id)
                .append("email", id + "@gole.test")
                .append("passwordHash", "hashed-password")
                .append("status", "VERIFIED")
                .append("role", "USER");
        if (nickname != null) {
            document.append("nickname", nickname).append("nicknameNormalized", nickname.toLowerCase());
        }
        return document;
    }
}
