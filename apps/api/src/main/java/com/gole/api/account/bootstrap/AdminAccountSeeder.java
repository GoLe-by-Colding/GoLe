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
 * 관리자 계정 부트스트랩. 각 (이메일, 비밀번호) 쌍이 환경변수로 주어지고 해당 계정이 없을
 * 때만 인증 완료(VERIFIED) + ADMIN 권한 계정을 1회 생성한다(멱등). 두 쌍은 서로 독립적으로
 * 게이트된다.
 *
 * <ul>
 *   <li>GOLE_ADMIN_EMAIL, GOLE_ADMIN_PASSWORD — 사람 관리자.
 *   <li>PROMOTION_AGENT_ADMIN_EMAIL, PROMOTION_AGENT_ADMIN_PASSWORD — 홍보 에이전트 봇
 *       전용 계정. 메이커-체커(promotion-review D4)로 이 계정은 자기가 만든 초안을 승인할
 *       수 없다.
 * </ul>
 */
@Component
public class AdminAccountSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountSeeder.class);

    private final AccountRepositoryPort accountRepository;
    private final PasswordHasherPort passwordHasher;
    private final IdentifierGeneratorPort identifierGenerator;
    private final String adminEmail;
    private final String adminPassword;
    private final String promotionBotEmail;
    private final String promotionBotPassword;

    public AdminAccountSeeder(
            AccountRepositoryPort accountRepository,
            PasswordHasherPort passwordHasher,
            IdentifierGeneratorPort identifierGenerator,
            @Value("${gole.admin.email:}") String adminEmail,
            @Value("${gole.admin.password:}") String adminPassword,
            @Value("${gole.admin.promotion-bot-email:}") String promotionBotEmail,
            @Value("${gole.admin.promotion-bot-password:}") String promotionBotPassword) {
        this.accountRepository = accountRepository;
        this.passwordHasher = passwordHasher;
        this.identifierGenerator = identifierGenerator;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.promotionBotEmail = promotionBotEmail;
        this.promotionBotPassword = promotionBotPassword;
    }

    @Override
    public void run(String... args) {
        seedIfAbsent(adminEmail, adminPassword, "admin");
        seedIfAbsent(promotionBotEmail, promotionBotPassword, "promotion-bot");
    }

    private void seedIfAbsent(String rawEmail, String rawPassword, String label) {
        if (rawEmail.isBlank() || rawPassword.isBlank()) {
            return; // 환경변수 미설정 시 비활성
        }
        Email email = new Email(rawEmail);
        if (accountRepository.existsByEmail(email)) {
            return; // 이미 존재 → 멱등
        }
        PasswordHash hash = passwordHasher.hash(rawPassword);
        Account account = Account.operationalBootstrap(identifierGenerator.newAccountId(), email, hash, Role.ADMIN);
        accountRepository.save(account);
        log.info("[seed] {} 계정 생성 완료", label);
    }
}
