package com.gole.api.account.application.service;

import com.gole.api.account.application.port.out.AccountRepositoryPort;
import com.gole.api.account.application.port.out.IdentifierGeneratorPort;
import com.gole.api.account.application.port.out.PasswordHasherPort;
import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.AuthProvider;
import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.PolicyAcceptance.Channel;
import com.gole.api.account.domain.model.Role;
import com.gole.api.account.domain.model.SignupPolicyAcceptance;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 소셜 신규 계정과 정책 증빙을 하나의 Mongo 트랜잭션으로 생성한다. */
@Service
public class SocialAccountProvisioner {

    private final AccountRepositoryPort accounts;
    private final IdentifierGeneratorPort identifiers;
    private final PasswordHasherPort passwordHasher;
    private final PolicyAcceptanceService policyAcceptances;

    public SocialAccountProvisioner(
            AccountRepositoryPort accounts,
            IdentifierGeneratorPort identifiers,
            PasswordHasherPort passwordHasher,
            PolicyAcceptanceService policyAcceptances) {
        this.accounts = accounts;
        this.identifiers = identifiers;
        this.passwordHasher = passwordHasher;
        this.policyAcceptances = policyAcceptances;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Account provision(Email email, AuthProvider provider, SignupPolicyAcceptance signupPolicyAcceptance) {
        policyAcceptances.validate(signupPolicyAcceptance);
        String id = identifiers.newAccountId();
        var hash = passwordHasher.hash(UUID.randomUUID().toString());
        Account saved = accounts.save(Account.provisioned(id, email, hash, Role.USER));
        policyAcceptances.record(saved.getId(), signupPolicyAcceptance, Channel.social(provider));
        return saved;
    }
}
