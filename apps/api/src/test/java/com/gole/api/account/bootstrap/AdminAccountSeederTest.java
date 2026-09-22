package com.gole.api.account.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gole.api.account.application.port.out.AccountRepositoryPort;
import com.gole.api.account.application.port.out.IdentifierGeneratorPort;
import com.gole.api.account.application.port.out.PasswordHasherPort;
import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.PasswordHash;
import com.gole.api.account.domain.model.Role;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AdminAccountSeederTest {

    private final AccountRepositoryPort accounts = mock(AccountRepositoryPort.class);
    private final PasswordHasherPort passwordHasher = mock(PasswordHasherPort.class);
    private final IdentifierGeneratorPort identifierGenerator = mock(IdentifierGeneratorPort.class);

    @Test
    void doesNothingWhenBothPairsAreBlank() {
        seeder("", "", "", "").run();

        verifyNoInteractions(accounts, passwordHasher, identifierGenerator);
    }

    @Test
    void createsOnlyTheAdminAccountWhenPromotionBotPairIsUnset() {
        when(accounts.existsByEmail(any(Email.class))).thenReturn(false);
        when(identifierGenerator.newAccountId()).thenReturn("account-1");
        when(passwordHasher.hash(anyString())).thenReturn(new PasswordHash("unusable-random-hash"));

        seeder("admin@gole.test", "admin-pass", "", "").run();

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accounts).save(saved.capture());
        assertThat(saved.getValue().getEmail().value()).isEqualTo("admin@gole.test");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getValue().isLegacyExempt()).isTrue();
    }

    @Test
    void createsBothAccountsWhenBothPairsAreSet() {
        when(accounts.existsByEmail(any(Email.class))).thenReturn(false);
        AtomicInteger idSequence = new AtomicInteger();
        when(identifierGenerator.newAccountId()).thenAnswer(invocation -> "account-" + idSequence.incrementAndGet());
        when(passwordHasher.hash(anyString())).thenReturn(new PasswordHash("unusable-random-hash"));

        seeder("admin@gole.test", "admin-pass", "promo-bot@gole.test", "promo-pass")
                .run();

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accounts, times(2)).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(account -> account.getEmail().value())
                .containsExactlyInAnyOrder("admin@gole.test", "promo-bot@gole.test");
        assertThat(saved.getAllValues()).allSatisfy(account -> {
            assertThat(account.getRole()).isEqualTo(Role.ADMIN);
            assertThat(account.isLegacyExempt()).isTrue();
        });
    }

    @Test
    void rerunSkipsAlreadyExistingAccounts() {
        when(accounts.existsByEmail(eq(new Email("admin@gole.test")))).thenReturn(true);
        when(accounts.existsByEmail(eq(new Email("promo-bot@gole.test")))).thenReturn(true);

        seeder("admin@gole.test", "admin-pass", "promo-bot@gole.test", "promo-pass")
                .run();

        verify(accounts, never()).save(any(Account.class));
        verifyNoInteractions(passwordHasher, identifierGenerator);
    }

    private AdminAccountSeeder seeder(
            String adminEmail, String adminPassword, String promotionBotEmail, String promotionBotPassword) {
        return new AdminAccountSeeder(
                accounts,
                passwordHasher,
                identifierGenerator,
                adminEmail,
                adminPassword,
                promotionBotEmail,
                promotionBotPassword);
    }
}
