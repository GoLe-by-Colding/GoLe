package com.gole.api.account.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gole.api.account.application.port.out.AccountRepositoryPort;
import com.gole.api.account.application.port.out.PasswordHasherPort;
import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.PasswordHash;
import com.gole.api.account.domain.model.Role;
import com.gole.api.common.bootstrap.DemoContentActors;
import com.gole.api.community.bootstrap.CommunitySeeder;
import com.gole.api.listing.bootstrap.ListingSeeder;
import com.gole.api.report.bootstrap.ReportSeeder;
import com.gole.api.review.bootstrap.ReviewSeeder;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.annotation.Order;

class DemoContentAccountSeederTest {

    private final AccountRepositoryPort accounts = mock(AccountRepositoryPort.class);
    private final PasswordHasherPort passwordHasher = mock(PasswordHasherPort.class);

    @Test
    void doesNothingWhenEveryDemoSeedIsDisabled() {
        seeder(false, false, false, false).run();

        verifyNoInteractions(accounts, passwordHasher);
    }

    @Test
    void runsBeforeEveryContentSeederThatReferencesDemoAccounts() {
        int accountOrder = orderOf(DemoContentAccountSeeder.class);

        assertThat(accountOrder)
                .isLessThan(orderOf(ListingSeeder.class))
                .isLessThan(orderOf(CommunitySeeder.class))
                .isLessThan(orderOf(ReviewSeeder.class))
                .isLessThan(orderOf(ReportSeeder.class));
    }

    @Test
    void createsEveryReferencedActorBeforeDemoContentSeeds() {
        when(accounts.findById(anyString())).thenReturn(Optional.empty());
        when(accounts.findByEmail(any(Email.class))).thenReturn(Optional.empty());
        when(passwordHasher.hash(anyString())).thenReturn(new PasswordHash("unusable-random-hash"));
        when(accounts.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        seeder(true, false, false, false).run();

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accounts, times(DemoContentActors.ALL_ACCOUNT_IDS.size())).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(Account::getId)
                .containsExactlyElementsOf(DemoContentActors.ALL_ACCOUNT_IDS);
        assertThat(saved.getAllValues()).allSatisfy(account -> {
            assertThat(account.isVerified()).isTrue();
            assertThat(account.isSuspended()).isFalse();
            assertThat(account.getRole()).isEqualTo(Role.USER);
            assertThat(account.isLegacyExempt()).isTrue();
            assertThat(account.getEmail().value()).endsWith("@demo.gole.invalid");
        });
        verify(passwordHasher, times(DemoContentActors.ALL_ACCOUNT_IDS.size())).hash(anyString());
    }

    @Test
    void rerunKeepsUsableExistingAccountsUntouched() {
        when(accounts.findById(anyString())).thenAnswer(invocation -> Optional.of(demo(invocation.getArgument(0))));

        seeder(false, true, false, false).run();

        verify(accounts, never()).findByEmail(any(Email.class));
        verify(accounts, never()).save(any(Account.class));
        verifyNoInteractions(passwordHasher);
    }

    @Test
    void failsFastWhenAReferencedAccountCannotReceivePrivateChats() {
        Account suspended = demo(DemoContentActors.ALL_ACCOUNT_IDS.getFirst());
        suspended.suspend("테스트 정지");
        when(accounts.findById(anyString())).thenReturn(Optional.of(suspended));

        assertThatThrownBy(() -> seeder(false, false, false, true).run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("사용할 수 없는 계정");
        verify(accounts, never()).save(any(Account.class));
        verifyNoInteractions(passwordHasher);
    }

    private DemoContentAccountSeeder seeder(
            boolean listingSeed, boolean communitySeed, boolean reportSeed, boolean reviewSeed) {
        return new DemoContentAccountSeeder(
                accounts, passwordHasher, listingSeed, communitySeed, reportSeed, reviewSeed);
    }

    private static Account demo(String id) {
        return Account.operationalBootstrap(
                id, new Email(id + "@demo.gole.invalid"), new PasswordHash("unusable-random-hash"), Role.USER);
    }

    private static int orderOf(Class<?> type) {
        return type.getAnnotation(Order.class).value();
    }
}
