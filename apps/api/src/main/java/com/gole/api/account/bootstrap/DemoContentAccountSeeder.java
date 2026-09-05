package com.gole.api.account.bootstrap;

import com.gole.api.account.application.port.out.AccountRepositoryPort;
import com.gole.api.account.application.port.out.PasswordHasherPort;
import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.PasswordHash;
import com.gole.api.account.domain.model.Role;
import com.gole.api.common.bootstrap.DemoContentActors;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 로컬·E2E 데모 콘텐츠가 참조하는 계정을 먼저 만든다.
 *
 * <p>리스팅과 커뮤니티 시드는 고정 actor ID를 사용한다. 계정 없이 콘텐츠만 만들면 매물 문의
 * 채팅이 {@code CHAT_ACCOUNT_NOT_FOUND}로 끊기므로, 콘텐츠 시더보다 앞서 인증 완료 USER 계정을
 * 멱등 생성한다. 로그인 가능한 알려진 비밀번호는 두지 않고 실행 때마다 난수를 해시한다.
 *
 * <p>어떤 샘플 시드도 켜지지 않은 운영 기본값에서는 아무 작업도 하지 않는다. 운영 프로필은
 * {@code ProductionConfigurationGuard}가 샘플 시드 자체를 별도로 금지한다.
 */
@Component
@Order(1)
public class DemoContentAccountSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoContentAccountSeeder.class);

    private final AccountRepositoryPort accounts;
    private final PasswordHasherPort passwordHasher;
    private final boolean enabled;

    public DemoContentAccountSeeder(
            AccountRepositoryPort accounts,
            PasswordHasherPort passwordHasher,
            @Value("${gole.listing.seed-on-empty:false}") boolean listingSeed,
            @Value("${gole.community.seed-on-empty:false}") boolean communitySeed,
            @Value("${gole.report.seed-on-empty:false}") boolean reportSeed,
            @Value("${gole.review.seed-on-empty:false}") boolean reviewSeed) {
        this.accounts = accounts;
        this.passwordHasher = passwordHasher;
        this.enabled = listingSeed || communitySeed || reportSeed || reviewSeed;
    }

    @Override
    public void run(String... args) {
        if (!enabled) {
            return;
        }

        int created = 0;
        for (String accountId : DemoContentActors.ALL_ACCOUNT_IDS) {
            var existing = accounts.findById(accountId);
            if (existing.isPresent()) {
                assertUsableDemoAccount(existing.orElseThrow());
                continue;
            }

            Email email = demoEmail(accountId);
            accounts.findByEmail(email).ifPresent(conflict -> {
                throw new IllegalStateException("데모 계정 이메일이 다른 ID에 이미 사용 중임");
            });

            PasswordHash passwordHash = passwordHasher.hash(UUID.randomUUID().toString());
            Account demo = Account.operationalBootstrap(accountId, email, passwordHash, Role.USER);
            accounts.save(demo);
            created++;
        }

        log.info("[seed] 데모 콘텐츠 참조 계정 준비: 생성 {}개, 전체 {}개", created, DemoContentActors.ALL_ACCOUNT_IDS.size());
    }

    private static Email demoEmail(String accountId) {
        return new Email(accountId + "@demo.gole.invalid");
    }

    private static void assertUsableDemoAccount(Account account) {
        if (!account.isVerified() || account.isSuspended() || account.isAdmin()) {
            throw new IllegalStateException("데모 콘텐츠가 사용할 수 없는 계정을 참조함: " + account.getId());
        }
    }
}
