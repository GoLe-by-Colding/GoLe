package com.gole.api.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.account.adapter.out.persistence.AccountMongoRepository;
import com.gole.api.account.adapter.out.persistence.LegacyEmailVerificationCodeMigration;
import com.gole.api.account.application.port.in.ResendVerificationUseCase;
import com.gole.api.account.application.port.in.ResendVerificationUseCase.ResendVerificationCommand;
import com.gole.api.account.application.port.in.SignInUseCase;
import com.gole.api.account.application.port.in.SignInUseCase.SignInCommand;
import com.gole.api.account.application.port.in.VerifyEmailUseCase;
import com.gole.api.account.application.port.in.VerifyEmailUseCase.VerifyEmailCommand;
import com.gole.api.account.application.port.out.AccountRepositoryPort;
import com.gole.api.account.application.port.out.PasswordHasherPort;
import com.gole.api.account.domain.exception.InvalidCredentialsException;
import com.gole.api.account.domain.exception.VerificationException;
import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.EmailVerificationChallenge;
import com.gole.api.account.domain.model.Role;
import java.time.Instant;
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
class AccountAuthenticationTransactionIntegrationTest {

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
    }

    @Autowired
    AccountRepositoryPort accounts;

    @Autowired
    AccountMongoRepository documents;

    @Autowired
    PasswordHasherPort passwordHasher;

    @Autowired
    VerifyEmailUseCase verification;

    @Autowired
    SignInUseCase signIn;

    @Autowired
    ResendVerificationUseCase resendVerification;

    @Autowired
    LegacyEmailVerificationCodeMigration legacyVerificationMigration;

    @Autowired
    MongoTemplate mongo;

    @BeforeEach
    void clean() {
        documents.deleteAll();
    }

    @Test
    void failedEmailVerificationCommitsSecurityCounterDespiteErrorResponse() {
        Instant issuedAt = Instant.now();
        accounts.save(Account.register(
                "account-1",
                new Email("pending@gole.test"),
                passwordHasher.hash("correct-password"),
                new EmailVerificationChallenge(passwordHasher.hash("123456").value(), issuedAt)));

        assertThatThrownBy(() -> verification.verify(new VerifyEmailCommand("pending@gole.test", "000000")))
                .isInstanceOf(VerificationException.class);

        assertThat(accounts.findById("account-1").orElseThrow().getVerificationFailedAttempts())
                .isEqualTo(1);
    }

    @Test
    void failedSignInCommitsSecurityCounterDespiteErrorResponse() {
        accounts.save(Account.provisioned(
                "account-1", new Email("member@gole.test"), passwordHasher.hash("correct-password"), Role.USER));

        assertThatThrownBy(() -> signIn.signIn(new SignInCommand("member@gole.test", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(accounts.findById("account-1").orElseThrow().getFailedAttempts())
                .isEqualTo(1);
    }

    @Test
    void legacyPlaintextVerificationCodeIsInvalidatedRemovedAndCanBeReissuedAsHash() {
        String email = "legacy-pending@gole.test";
        mongo.getDb()
                .getCollection("accounts")
                .insertOne(new Document("_id", "legacy-account")
                        .append("email", email)
                        .append(
                                "passwordHash",
                                passwordHasher.hash("correct-password").value())
                        .append("status", "UNVERIFIED")
                        .append("role", "USER")
                        .append("verificationCode", "123456")
                        .append("verificationCodeIssuedAt", Instant.now())
                        .append("verificationFailedAttempts", 3)
                        .append("failedAttempts", 0));

        legacyVerificationMigration.run(null);

        Document invalidated = mongo.getDb()
                .getCollection("accounts")
                .find(new Document("_id", "legacy-account"))
                .first();
        assertThat(invalidated).isNotNull();
        assertThat(invalidated.containsKey("verificationCode")).isFalse();
        assertThat(invalidated.containsKey("verificationCodeIssuedAt")).isFalse();
        assertThat(invalidated.getInteger("verificationFailedAttempts")).isZero();
        assertThatThrownBy(() -> verification.verify(new VerifyEmailCommand(email, "123456")))
                .isInstanceOf(VerificationException.class)
                .hasFieldOrPropertyWithValue("code", "VERIFICATION_CODE_MISSING");

        resendVerification.resend(new ResendVerificationCommand(email));

        Document reissued = mongo.getDb()
                .getCollection("accounts")
                .find(new Document("_id", "legacy-account"))
                .first();
        assertThat(reissued).isNotNull();
        assertThat(reissued.containsKey("verificationCode")).isFalse();
        assertThat(reissued.getString("verificationCodeHash")).startsWith("$2").isNotEqualTo("123456");
        assertThat(reissued.get("verificationCodeIssuedAt")).isNotNull();
    }
}
