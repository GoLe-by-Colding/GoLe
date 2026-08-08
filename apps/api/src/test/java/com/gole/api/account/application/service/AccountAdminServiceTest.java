package com.gole.api.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.account.application.port.in.ManageAccountsUseCase.AccountSummary;
import com.gole.api.account.application.port.out.AccountRepositoryPort;
import com.gole.api.account.application.port.out.SessionStorePort;
import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.AccountStatus;
import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.PasswordHash;
import com.gole.api.account.domain.model.Role;
import com.gole.api.common.exception.BadRequestException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * admin-console 요구사항 6 — 회원 정지/복구/권한 및 운영 잠금(lockout) 방지 가드.
 */
class AccountAdminServiceTest {

    private InMemoryAccounts accounts;
    private RecordingSessionStore sessions;
    private AccountAdminService service;

    @BeforeEach
    void setUp() {
        accounts = new InMemoryAccounts();
        sessions = new RecordingSessionStore();
        service = new AccountAdminService(accounts, sessions);
    }

    private Account seed(String id, String email, Role role) {
        Account account = Account.provisioned(id, new Email(email), new PasswordHash("plain:pw"), role);
        accounts.save(account);
        return account;
    }

    @Test
    @DisplayName("6.2/6.3 정지하면 상태가 SUSPENDED가 되고 활성 세션이 전부 폐기된다")
    void suspendRevokesSessions() {
        seed("u1", "user@gole.io", Role.USER);
        seed("admin", "admin@gole.io", Role.ADMIN);

        AccountSummary result = service.suspend("u1", "admin", "사기 신고 다발");

        assertThat(result.status()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(result.suspendedReason()).isEqualTo("사기 신고 다발");
        assertThat(sessions.revokedAccounts).containsExactly("u1");
    }

    @Test
    @DisplayName("정지 사유가 비어 있으면 MODERATION_REASON_REQUIRED로 거부한다")
    void suspendRequiresReason() {
        seed("u1", "user@gole.io", Role.USER);
        seed("admin", "admin@gole.io", Role.ADMIN);

        assertThatThrownBy(() -> service.suspend("u1", "admin", "  "))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("사유");
        assertThat(sessions.revokedAccounts).isEmpty();
    }

    @Test
    @DisplayName("6.8 자기 자신은 정지할 수 없다")
    void cannotSuspendSelf() {
        seed("admin", "admin@gole.io", Role.ADMIN);
        seed("admin2", "admin2@gole.io", Role.ADMIN);

        assertThatThrownBy(() -> service.suspend("admin", "admin", "실수"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("자기 자신");
    }

    @Test
    @DisplayName("6.9 마지막 관리자는 정지할 수 없다")
    void cannotSuspendLastAdmin() {
        seed("admin", "admin@gole.io", Role.ADMIN);
        seed("other", "other@gole.io", Role.USER);

        assertThatThrownBy(() -> service.suspend("admin", "other", "테스트"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("마지막 관리자");
    }

    @Test
    @DisplayName("6.9 마지막 관리자는 강등할 수 없다")
    void cannotDemoteLastAdmin() {
        seed("admin", "admin@gole.io", Role.ADMIN);
        seed("other", "other@gole.io", Role.USER);

        assertThatThrownBy(() -> service.changeRole("admin", "other", Role.USER))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("마지막 관리자");
    }

    @Test
    @DisplayName("6.6 정지 해제하면 VERIFIED로 복구되고 사유가 지워진다")
    void reinstateClearsSuspension() {
        seed("u1", "user@gole.io", Role.USER);
        seed("admin", "admin@gole.io", Role.ADMIN);
        service.suspend("u1", "admin", "일시 정지");

        AccountSummary result = service.reinstate("u1", "admin");

        assertThat(result.status()).isEqualTo(AccountStatus.VERIFIED);
        assertThat(result.suspendedReason()).isNull();
        assertThat(result.lockedUntil()).isNull();
    }

    @Test
    @DisplayName("6.7 권한을 변경하면 세션이 폐기되어 새 권한으로 재로그인하게 된다")
    void changeRoleRevokesSessions() {
        seed("u1", "user@gole.io", Role.USER);
        seed("admin", "admin@gole.io", Role.ADMIN);

        AccountSummary result = service.changeRole("u1", "admin", Role.ADMIN);

        assertThat(result.role()).isEqualTo(Role.ADMIN);
        assertThat(sessions.revokedAccounts).containsExactly("u1");
    }

    @Test
    @DisplayName("이미 같은 권한이면 멱등하게 통과하고 세션도 건드리지 않는다")
    void changeRoleIsIdempotent() {
        seed("u1", "user@gole.io", Role.USER);

        AccountSummary result = service.changeRole("u1", "admin", Role.USER);

        assertThat(result.role()).isEqualTo(Role.USER);
        assertThat(sessions.revokedAccounts).isEmpty();
    }

    @Test
    @DisplayName("6.1 목록은 이메일 부분 일치로 좁혀지고 limit은 100으로 클램프된다")
    void listFiltersAndClamps() {
        seed("u1", "alice@gole.io", Role.USER);
        seed("u2", "bob@gole.io", Role.USER);

        assertThat(service.list("ali", 30)).extracting(AccountSummary::email).containsExactly("alice@gole.io");
        assertThat(accounts.lastLimit).isEqualTo(30);

        service.list(null, 9999);
        assertThat(accounts.lastLimit).isEqualTo(100);
    }

    // --- 가짜 구현들 ---

    private static final class InMemoryAccounts implements AccountRepositoryPort {
        private final Map<String, Account> byId = new LinkedHashMap<>();
        private int lastLimit;

        @Override
        public boolean existsByEmail(Email email) {
            return byId.values().stream().anyMatch(a -> a.getEmail().value().equals(email.value()));
        }

        @Override
        public Account save(Account account) {
            byId.put(account.getId(), account);
            return account;
        }

        @Override
        public Optional<Account> findByEmail(Email email) {
            return byId.values().stream()
                    .filter(a -> a.getEmail().value().equals(email.value()))
                    .findFirst();
        }

        @Override
        public Optional<Account> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public List<Account> findRecent(String emailQuery, int limit) {
            lastLimit = limit;
            List<Account> rows = new ArrayList<>();
            for (Account a : byId.values()) {
                if (emailQuery == null
                        || emailQuery.isBlank()
                        || a.getEmail().value().contains(emailQuery)) {
                    rows.add(a);
                }
            }
            return rows.size() > limit ? rows.subList(0, limit) : rows;
        }

        @Override
        public long countByRole(Role role) {
            return byId.values().stream().filter(a -> a.getRole() == role).count();
        }
    }

    private static final class RecordingSessionStore implements SessionStorePort {
        private final Set<String> revokedAccounts = new HashSet<>();

        @Override
        public void store(String token, String accountId, Role role, Duration ttl) {
            // 테스트에서 사용하지 않음
        }

        @Override
        public Optional<SessionPrincipal> resolve(String token) {
            return Optional.empty();
        }

        @Override
        public void revoke(String token) {
            // 테스트에서 사용하지 않음
        }

        @Override
        public void revokeAllForAccount(String accountId) {
            revokedAccounts.add(accountId);
        }
    }
}
