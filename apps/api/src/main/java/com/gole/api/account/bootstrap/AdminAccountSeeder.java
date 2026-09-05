package com.gole.api.account.bootstrap;

import com.gole.api.account.application.port.out.AccountRepositoryPort;
import com.gole.api.account.application.port.out.IdentifierGeneratorPort;
import com.gole.api.account.application.port.out.PasswordHasherPort;
import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.PasswordHash;
import com.gole.api.account.domain.model.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 관리자 계정 부트스트랩. 환경변수로 이메일/비밀번호가 주어지고 해당 계정이 없을 때만
 * 인증 완료(VERIFIED) + ADMIN 권한 계정을 1회 생성한다(멱등).
 *
 *   GOLE_ADMIN_EMAIL, GOLE_ADMIN_PASSWORD (둘 다 있을 때만 동작)
 */
@Component
public class AdminAccountSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountSeeder.class);

    private final AccountRepositoryPort accountRepository;
    private final PasswordHasherPort passwordHasher;
    private final IdentifierGeneratorPort identifierGenerator;
    private final String adminEmail;
    private final String adminPassword;

    public AdminAccountSeeder(
            AccountRepositoryPort accountRepository,
            PasswordHasherPort passwordHasher,
            IdentifierGeneratorPort identifierGenerator,
            @Value("${gole.admin.email:}") String adminEmail,
            @Value("${gole.admin.password:}") String adminPassword) {
        this.accountRepository = accountRepository;
        this.passwordHasher = passwordHasher;
        this.identifierGenerator = identifierGenerator;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            return; // 환경변수 미설정 시 비활성
        }
        Email email = new Email(adminEmail);
        if (accountRepository.existsByEmail(email)) {
            return; // 이미 존재 → 멱등
        }
        PasswordHash hash = passwordHasher.hash(adminPassword);
        Account admin = Account.operationalBootstrap(identifierGenerator.newAccountId(), email, hash, Role.ADMIN);
        accountRepository.save(admin);
        log.info("[seed] admin 계정 생성: {}", adminEmail);
    }
}
