package com.gole.api.account.application.service;

import com.gole.api.account.application.port.out.ThirdPartyProvisionConsentRepositoryPort;
import com.gole.api.account.domain.model.PolicyAcceptance.Channel;
import com.gole.api.account.domain.model.SignupPolicyAcceptance;
import com.gole.api.account.domain.model.ThirdPartyProvisionConsentEvent;
import com.gole.api.account.domain.model.ThirdPartyProvisionConsentEvent.Decision;
import com.gole.api.account.domain.model.ThirdPartyProvisionConsentEvent.SourcePath;
import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.ForbiddenException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

/**
 * 현재 제3자 제공 동의 상태를 판정하고 동의·철회를 append-only 이벤트로 기록한다.
 *
 * <p>과거 버전의 동의나 철회 전 동의는 새 제공의 근거가 되지 않는다. 반면 이미 제공된 과거
 * 대화의 열람은 이 서비스로 막지 않아 이용자의 권리 행사와 분쟁 확인을 보존한다.
 */
@Service
public class ThirdPartyProvisionConsentService {

    public static final String REQUIRED_CODE = "THIRD_PARTY_PROVISION_CONSENT_REQUIRED";
    public static final String SUBJECT_REQUIRED_CODE = "THIRD_PARTY_PROVISION_SUBJECT_CONSENT_REQUIRED";

    private final ThirdPartyProvisionConsentRepositoryPort repository;
    private final SignupPolicyProperties properties;
    private final Clock clock;

    public ThirdPartyProvisionConsentService(
            ThirdPartyProvisionConsentRepositoryPort repository, SignupPolicyProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    public ConsentStatus currentStatus(String accountId) {
        String version = properties.getThirdPartyProvisionVersion();
        return repository
                .findLatest(requireAccountId(accountId), version)
                .map(event -> new ConsentStatus(version, event.decision() == Decision.CONSENTED, event.occurredAt()))
                .orElseGet(() -> new ConsentStatus(version, false, null));
    }

    /** 새 개인정보 제공을 수반하는 기능의 서버측 최종 gate. */
    public void requireCurrent(String accountId) {
        if (!currentStatus(accountId).consented()) {
            throw new ForbiddenException(REQUIRED_CODE, "거래 상대방 또는 대화 참여자에게 정보를 제공하기 전에 별도 동의가 필요합니다");
        }
    }

    /** 다른 이용자의 개인정보를 새 수신자에게 제공할 때 정보주체의 현재 동의를 확인한다. */
    public void requireCurrentSubject(String accountId) {
        if (!currentStatus(accountId).consented()) {
            throw new ForbiddenException(SUBJECT_REQUIRED_CODE, "상대방 또는 대화 참여자의 제3자 제공 동의가 없어 아직 이 기능을 사용할 수 없습니다");
        }
    }

    public ConsentStatus consent(String accountId, String noticeVersion, SourcePath path, String requestId) {
        requireCurrentVersion(noticeVersion);
        return append(accountId, noticeVersion, Decision.CONSENTED, path, requestId);
    }

    public ConsentStatus withdraw(String accountId, String noticeVersion, String requestId) {
        requireCurrentVersion(noticeVersion);
        return append(accountId, noticeVersion, Decision.WITHDRAWN, SourcePath.ACCOUNT_SETTINGS, requestId);
    }

    /** 가입 선택란이 체크된 경우에만 별도 동의 이벤트를 같은 가입 트랜잭션에 추가한다. */
    public void recordSignupIfAccepted(String accountId, SignupPolicyAcceptance input, Channel channel) {
        if (input == null || !input.thirdPartyProvisionAccepted()) {
            return;
        }
        consent(
                accountId,
                input.thirdPartyProvisionVersion(),
                SourcePath.signup(channel),
                "signup:" + channel.name() + ":" + accountId + ":" + input.thirdPartyProvisionVersion());
    }

    public void validateSignupChoice(SignupPolicyAcceptance input) {
        if (input == null || !input.thirdPartyProvisionAccepted()) {
            return;
        }
        String suppliedVersion = input.thirdPartyProvisionVersion();
        if (suppliedVersion == null || suppliedVersion.isBlank()) {
            throw new BadRequestException("THIRD_PARTY_PROVISION_VERSION_REQUIRED", "제3자 제공 동의 문서 버전이 필요합니다");
        }
        requireCurrentVersion(suppliedVersion);
    }

    private ConsentStatus append(
            String accountId, String version, Decision decision, SourcePath path, String requestId) {
        String validatedAccountId = requireAccountId(accountId);
        if (path == null) {
            throw new BadRequestException("CONSENT_PATH_REQUIRED", "동의가 이루어진 경로가 필요합니다");
        }
        if (requestId == null || requestId.isBlank() || requestId.length() > 160) {
            throw new BadRequestException("CONSENT_REQUEST_ID_INVALID", "동의 요청 식별자를 확인해 주세요");
        }
        ThirdPartyProvisionConsentEvent requested = new ThirdPartyProvisionConsentEvent(
                new ObjectId().toHexString(),
                validatedAccountId,
                version,
                decision,
                path,
                requestId,
                Instant.now(clock));
        ThirdPartyProvisionConsentEvent stored = repository.appendOnce(requested);
        if (!sameDecision(requested, stored)) {
            throw new ConflictException("CONSENT_REQUEST_ID_REUSED", "이미 다른 동의 결정에 사용된 요청 식별자입니다");
        }
        // 오래된 멱등 요청이 철회·재동의 뒤 다시 도착해도 그 과거 결정을 현재 상태로
        // 오인해 응답하지 않는다. 저장 결과가 아니라 컬렉션의 최신 결정을 다시 읽는다.
        return currentStatus(validatedAccountId);
    }

    private void requireCurrentVersion(String noticeVersion) {
        if (!properties.getThirdPartyProvisionVersion().equals(noticeVersion)) {
            throw new BadRequestException("THIRD_PARTY_PROVISION_VERSION_STALE", "동의 안내가 변경되었습니다. 최신 내용을 확인해 주세요");
        }
    }

    private static String requireAccountId(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("accountId must not be blank");
        }
        return accountId;
    }

    private static boolean sameDecision(
            ThirdPartyProvisionConsentEvent expected, ThirdPartyProvisionConsentEvent stored) {
        return Objects.equals(expected.accountId(), stored.accountId())
                && Objects.equals(expected.noticeVersion(), stored.noticeVersion())
                && expected.decision() == stored.decision()
                && expected.sourcePath() == stored.sourcePath()
                && Objects.equals(expected.requestId(), stored.requestId());
    }

    public record ConsentStatus(String noticeVersion, boolean consented, Instant lastDecisionAt) {}
}
