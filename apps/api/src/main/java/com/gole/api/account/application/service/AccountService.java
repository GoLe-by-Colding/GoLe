package com.gole.api.account.application.service;

import com.gole.api.account.application.port.in.GetCurrentSessionUseCase;
import com.gole.api.account.application.port.in.LogoutUseCase;
import com.gole.api.account.application.port.in.RegisterAccountUseCase;
import com.gole.api.account.application.port.in.ResendVerificationUseCase;
import com.gole.api.account.application.port.in.SignInUseCase;
import com.gole.api.account.application.port.in.VerifyEmailUseCase;
import com.gole.api.account.application.port.out.AccountRepositoryPort;
import com.gole.api.account.application.port.out.IdentifierGeneratorPort;
import com.gole.api.account.application.port.out.PasswordHasherPort;
import com.gole.api.account.application.port.out.SessionStorePort;
import com.gole.api.account.application.port.out.SessionTokenPort;
import com.gole.api.account.application.port.out.VerificationCodeGeneratorPort;
import com.gole.api.account.application.port.out.VerificationCodeSenderPort;
import com.gole.api.account.domain.exception.AccountNotVerifiedException;
import com.gole.api.account.domain.exception.EmailAlreadyRegisteredException;
import com.gole.api.account.domain.exception.InvalidCredentialsException;
import com.gole.api.account.domain.exception.PasswordTooLongException;
import com.gole.api.account.domain.exception.VerificationException;
import com.gole.api.account.domain.exception.WeakPasswordException;
import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.AccountStatus;
import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.PasswordHash;
import com.gole.api.account.domain.model.VerificationCode;
import com.gole.api.common.operations.OperationalEvent.Category;
import com.gole.api.common.operations.OperationalSignal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 계정 유스케이스 구현(가입/인증/로그인/세션해석). inbound port를 구현하고 outbound port에만 의존한다.
 * 시간은 Clock으로 주입받아 테스트 가능하게 한다. 횡단 로깅은 AOP가 처리.
 */
@Service
public class AccountService
        implements RegisterAccountUseCase,
                ResendVerificationUseCase,
                VerifyEmailUseCase,
                SignInUseCase,
                LogoutUseCase,
                GetCurrentSessionUseCase {

    private static final int MIN_PASSWORD_LENGTH = 8; // 요구사항 1.3
    private static final int MAX_PASSWORD_BYTES = 72; // BCrypt 입력 한계
    private static final Duration SESSION_TTL = Duration.ofDays(7);

    private final AccountRepositoryPort accountRepository;
    private final PasswordHasherPort passwordHasher;
    private final VerificationCodeSenderPort verificationCodeSender;
    private final VerificationCodeGeneratorPort verificationCodeGenerator;
    private final IdentifierGeneratorPort identifierGenerator;
    private final SessionTokenPort sessionToken;
    private final SessionStorePort sessionStore;
    private final Clock clock;

    public AccountService(
            AccountRepositoryPort accountRepository,
            PasswordHasherPort passwordHasher,
            VerificationCodeSenderPort verificationCodeSender,
            VerificationCodeGeneratorPort verificationCodeGenerator,
            IdentifierGeneratorPort identifierGenerator,
            SessionTokenPort sessionToken,
            SessionStorePort sessionStore,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.passwordHasher = passwordHasher;
        this.verificationCodeSender = verificationCodeSender;
        this.verificationCodeGenerator = verificationCodeGenerator;
        this.identifierGenerator = identifierGenerator;
        this.sessionToken = sessionToken;
        this.sessionStore = sessionStore;
        this.clock = clock;
    }

    @Override
    @OperationalSignal(
            category = Category.ACCOUNT,
            title = "새 이메일 회원가입",
            description = "이메일 인증 대기 계정이 생성되었습니다.",
            includeResult = true)
    public String register(RegisterAccountCommand command) {
        Email email = new Email(command.email());

        // 요구사항 1.3: 비밀번호 길이 검증
        if (command.rawPassword() == null || command.rawPassword().length() < MIN_PASSWORD_LENGTH) {
            throw new WeakPasswordException();
        }
        if (utf8Length(command.rawPassword()) > MAX_PASSWORD_BYTES) {
            throw new PasswordTooLongException();
        }
        // 요구사항 1.2: 이메일 중복 거부
        if (accountRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email.value());
        }

        PasswordHash hash = passwordHasher.hash(command.rawPassword());
        VerificationCode code = new VerificationCode(verificationCodeGenerator.generateCode(), Instant.now(clock));

        Account account = Account.register(identifierGenerator.newAccountId(), email, hash, code);
        Account saved = accountRepository.save(account);

        // 요구사항 1.1: 인증 코드 발송
        verificationCodeSender.send(email, code);
        return saved.getId();
    }

    @Override
    public void verify(VerifyEmailCommand command) {
        Email email = new Email(command.email());
        Account account = accountRepository.findByEmail(email).orElseThrow(InvalidCredentialsException::new);

        try {
            account.verify(command.code(), Instant.now(clock)); // 1.4 성공 / 1.5 만료
            accountRepository.save(account);
        } catch (VerificationException ex) {
            // 코드 불일치 횟수도 보안 상태이므로 실패 응답 전에 영속화한다.
            accountRepository.save(account);
            throw ex;
        }
    }

    @Override
    public void resend(ResendVerificationCommand command) {
        Email email = new Email(command.email());
        Optional<Account> found = accountRepository.findByEmail(email);
        // 계정 존재 여부를 노출하지 않는다. 미가입·인증완료·정지 계정 모두 동일하게 204로 응답한다.
        if (found.isEmpty() || found.get().getStatus() != AccountStatus.UNVERIFIED) {
            return;
        }
        Account account = found.get();
        Instant now = Instant.now(clock);
        VerificationCode code = new VerificationCode(verificationCodeGenerator.generateCode(), now);
        account.reissueVerificationCode(code, now);
        accountRepository.save(account);
        verificationCodeSender.send(email, code);
    }

    @Override
    public SignInResult signIn(SignInCommand command) {
        Email email = new Email(command.email());
        if (command.rawPassword() == null || utf8Length(command.rawPassword()) > MAX_PASSWORD_BYTES) {
            throw new InvalidCredentialsException();
        }
        Account account = accountRepository.findByEmail(email).orElseThrow(InvalidCredentialsException::new);

        Instant now = Instant.now(clock);
        account.ensureNotSuspended(); // admin-console 요구사항 6.4
        account.ensureNotLocked(now); // 요구사항 1.8

        if (!passwordHasher.matches(command.rawPassword(), account.getPasswordHash())) {
            account.recordFailedSignIn(now); // 1.7 + 1.8 누적
            accountRepository.save(account);
            throw new InvalidCredentialsException();
        }
        if (!account.isVerified()) {
            throw new AccountNotVerifiedException();
        }

        account.recordSuccessfulSignIn();

        // 요구사항 1.12: 레거시 해시(SHA-256 등)는 로그인 성공 시 BCrypt로 점진 승격.
        if (passwordHasher.needsRehash(account.getPasswordHash())) {
            account.upgradePasswordHash(passwordHasher.hash(command.rawPassword()));
        }
        accountRepository.save(account);

        // 요구사항 1.6: 세션 토큰 발급 + Redis 세션 저장(실제 검증 가능 세션)
        String token = sessionToken.issue(account);
        sessionStore.store(token, account.getId(), account.getRole(), SESSION_TTL);
        return new SignInResult(account.getId(), token, account.getRole(), account.isOnboardingRequired());
    }

    @Override
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            sessionStore.revoke(token);
        }
    }

    /**
     * 세션 해석. 권한(role)은 저장된 토큰 값이 아니라 <b>계정의 현재 상태</b>에서 읽는다.
     *
     * <p>정지된 계정은 이미 발급된 토큰이 남아 있어도 해석에 실패시켜 401이 되게 한다.
     * 세션 일괄 폐기(요구사항 6.3)가 어떤 이유로 누락돼도 정지가 실효되는 최종 방어선이다.
     * (admin-console 요구사항 6.5, 6.7)
     */
    @Override
    public Optional<CurrentSession> resolve(String token) {
        return sessionStore
                .resolve(token)
                .flatMap(p -> accountRepository.findById(p.accountId()))
                .filter(account -> !account.isSuspended())
                .map(account -> new CurrentSession(
                        account.getId(),
                        account.getEmail().value(),
                        account.getRole(),
                        account.isOnboardingRequired()));
    }

    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}
