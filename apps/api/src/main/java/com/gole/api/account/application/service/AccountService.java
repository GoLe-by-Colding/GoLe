package com.gole.api.account.application.service;

import com.gole.api.account.application.port.in.RegisterAccountUseCase;
import com.gole.api.account.application.port.in.SignInUseCase;
import com.gole.api.account.application.port.in.VerifyEmailUseCase;
import com.gole.api.account.application.port.out.AccountRepositoryPort;
import com.gole.api.account.application.port.out.IdentifierGeneratorPort;
import com.gole.api.account.application.port.out.PasswordHasherPort;
import com.gole.api.account.application.port.out.SessionTokenPort;
import com.gole.api.account.application.port.out.VerificationCodeGeneratorPort;
import com.gole.api.account.application.port.out.VerificationCodeSenderPort;
import com.gole.api.account.domain.exception.EmailAlreadyRegisteredException;
import com.gole.api.account.domain.exception.InvalidCredentialsException;
import com.gole.api.account.domain.exception.WeakPasswordException;
import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.PasswordHash;
import com.gole.api.account.domain.model.VerificationCode;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * 계정 유스케이스 구현(가입/인증/로그인). inbound port를 구현하고 outbound port에만 의존한다.
 * 시간은 Clock으로 주입받아 테스트 가능하게 한다. 횡단 로깅은 AOP가 처리.
 */
@Service
public class AccountService implements RegisterAccountUseCase, VerifyEmailUseCase, SignInUseCase {

    private static final int MIN_PASSWORD_LENGTH = 8; // 요구사항 1.3

    private final AccountRepositoryPort accountRepository;
    private final PasswordHasherPort passwordHasher;
    private final VerificationCodeSenderPort verificationCodeSender;
    private final VerificationCodeGeneratorPort verificationCodeGenerator;
    private final IdentifierGeneratorPort identifierGenerator;
    private final SessionTokenPort sessionToken;
    private final Clock clock;

    public AccountService(
            AccountRepositoryPort accountRepository,
            PasswordHasherPort passwordHasher,
            VerificationCodeSenderPort verificationCodeSender,
            VerificationCodeGeneratorPort verificationCodeGenerator,
            IdentifierGeneratorPort identifierGenerator,
            SessionTokenPort sessionToken,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.passwordHasher = passwordHasher;
        this.verificationCodeSender = verificationCodeSender;
        this.verificationCodeGenerator = verificationCodeGenerator;
        this.identifierGenerator = identifierGenerator;
        this.sessionToken = sessionToken;
        this.clock = clock;
    }

    @Override
    public String register(RegisterAccountCommand command) {
        Email email = new Email(command.email());

        // 요구사항 1.3: 비밀번호 길이 검증
        if (command.rawPassword() == null || command.rawPassword().length() < MIN_PASSWORD_LENGTH) {
            throw new WeakPasswordException();
        }
        // 요구사항 1.2: 이메일 중복 거부
        if (accountRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email.value());
        }

        PasswordHash hash = passwordHasher.hash(command.rawPassword());
        VerificationCode code =
                new VerificationCode(verificationCodeGenerator.generateCode(), Instant.now(clock));

        Account account = Account.register(identifierGenerator.newAccountId(), email, hash, code);
        Account saved = accountRepository.save(account);

        // 요구사항 1.1: 인증 코드 발송
        verificationCodeSender.send(email, code);
        return saved.getId();
    }

    @Override
    public void verify(VerifyEmailCommand command) {
        Email email = new Email(command.email());
        Account account = accountRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        account.verify(command.code(), Instant.now(clock)); // 1.4 성공 / 1.5 만료
        accountRepository.save(account);
    }

    @Override
    public SignInResult signIn(SignInCommand command) {
        Email email = new Email(command.email());
        Account account = accountRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        Instant now = Instant.now(clock);
        account.ensureNotLocked(now); // 요구사항 1.8

        if (!passwordHasher.matches(command.rawPassword(), account.getPasswordHash())) {
            account.recordFailedSignIn(now); // 1.7 + 1.8 누적
            accountRepository.save(account);
            throw new InvalidCredentialsException();
        }

        account.recordSuccessfulSignIn();
        accountRepository.save(account);

        // 요구사항 1.6: 세션 토큰 발급
        return new SignInResult(account.getId(), sessionToken.issue(account));
    }
}
