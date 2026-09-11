package com.gole.api.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.account.application.port.in.ListInterestTagRecipientsUseCase;
import com.gole.api.account.domain.model.AccountStatus;
import java.time.Instant;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class InterestTagRecipientQueryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-11T00:00:00Z");

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("gole.catalog.seed-on-empty", () -> "false");
        registry.add("gole.listing.seed-on-empty", () -> "false");
        registry.add("gole.pricing.seed-on-empty", () -> "false");
        registry.add("gole.community.seed-on-empty", () -> "false");
        registry.add("gole.report.seed-on-empty", () -> "false");
        registry.add("gole.review.seed-on-empty", () -> "false");
        registry.add("gole.media.seed-on-startup", () -> "false");
        registry.add("gole.support-notification-outbox.processing-enabled", () -> "false");
    }

    @Autowired
    ListInterestTagRecipientsUseCase recipients;

    @Autowired
    MongoTemplate mongo;

    @BeforeEach
    void setUp() {
        mongo.getDb().getCollection("accounts").deleteMany(new Document());
        insert("account-001", "technic", true, true, AccountStatus.VERIFIED);
        insert("account-002", "technic", true, true, AccountStatus.VERIFIED);
        insert("account-003", "technic", true, true, AccountStatus.VERIFIED);
        insert("account-no-phone", "technic", false, true, AccountStatus.VERIFIED);
        insert("account-no-consent", "technic", true, false, AccountStatus.VERIFIED);
        insert("account-suspended", "technic", true, true, AccountStatus.SUSPENDED);
        insert("account-other-tag", "city", true, true, AccountStatus.VERIFIED);
    }

    @Test
    void filtersEligibilityAndPagesWithoutMissingOrDuplicatingIds() {
        List<String> first = recipients.listEligibleAccountIds("technic", null, 2);
        List<String> second = recipients.listEligibleAccountIds("technic", first.getLast(), 2);
        List<String> third = recipients.listEligibleAccountIds("technic", second.getLast(), 2);

        assertThat(first).containsExactly("account-001", "account-002");
        assertThat(second).containsExactly("account-003");
        assertThat(third).isEmpty();
        assertThat(List.of(first, second, third).stream().flatMap(List::stream))
                .containsExactly("account-001", "account-002", "account-003")
                .doesNotHaveDuplicates();
    }

    @Test
    void resolveEligibleReadsPhoneOnlyWhileEligibilityStillHolds() {
        assertThat(recipients.resolveEligible("account-001", "technic")).hasValueSatisfying(recipient -> {
            assertThat(recipient.accountId()).isEqualTo("account-001");
            assertThat(recipient.phoneNumber().masked()).isEqualTo("010-****-5678");
        });
        assertThat(recipients.resolveEligible("account-no-consent", "technic")).isEmpty();
        assertThat(recipients.resolveEligible("account-001", "city")).isEmpty();
    }

    @Test
    void eligibleQueryUsesInterestTagCompoundIndex() {
        Document filter = new Document("interestTags", "technic")
                .append("phoneVerifiedAt", new Document("$ne", null))
                .append("marketingConsentedAt", new Document("$ne", null))
                .append("status", AccountStatus.VERIFIED.name())
                .append("_id", new Document("$gt", "account-000"));
        Document explain = mongo.getDb()
                .runCommand(new Document(
                                "explain",
                                new Document("find", "accounts")
                                        .append("filter", filter)
                                        .append("sort", new Document("_id", 1))
                                        .append("projection", new Document("_id", 1)))
                        .append("verbosity", "queryPlanner"));

        assertThat(explain.toJson()).contains("account_interest_tag_idx");
    }

    private void insert(
            String id, String tag, boolean phoneVerified, boolean marketingConsented, AccountStatus status) {
        Document account = new Document("_id", id)
                .append("email", id + "@example.invalid")
                .append("status", status.name())
                .append("interestTags", List.of(tag))
                .append("phoneNumber", "01012345678");
        if (phoneVerified) {
            account.append("phoneVerifiedAt", java.util.Date.from(NOW));
        }
        if (marketingConsented) {
            account.append("marketingConsentedAt", java.util.Date.from(NOW));
        }
        mongo.getDb().getCollection("accounts").insertOne(account);
    }
}
