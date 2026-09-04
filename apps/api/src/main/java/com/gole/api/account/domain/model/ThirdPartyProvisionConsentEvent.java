package com.gole.api.account.domain.model;

import java.time.Instant;

/**
 * 거래 상대방·대화 참여자에게 개인정보를 제공하는 데 대한 동의 감사 이벤트.
 *
 * <p>동의와 철회는 기존 행을 갱신하지 않고 항상 새 이벤트로 추가한다. {@code requestId}는
 * 네트워크 재시도가 같은 이벤트를 중복 기록하지 않게 하는 계정별 멱등 키다.
 */
public record ThirdPartyProvisionConsentEvent(
        String id,
        String accountId,
        String noticeVersion,
        Decision decision,
        SourcePath sourcePath,
        String requestId,
        Instant occurredAt) {

    public enum Decision {
        CONSENTED,
        WITHDRAWN
    }

    /** 사용자가 동의 또는 철회를 결정한 실제 화면·기능 경로. */
    public enum SourcePath {
        EMAIL_SIGNUP,
        SOCIAL_GOOGLE_SIGNUP,
        SOCIAL_KAKAO_SIGNUP,
        SOCIAL_NAVER_SIGNUP,
        LISTING_CHAT,
        SOCIAL_DIRECT_CHAT,
        SOCIAL_GROUP_CHAT,
        SOCIAL_GROUP_INVITE,
        CHAT_MESSAGE,
        ORDER_CONTACTS,
        ACCOUNT_SETTINGS;

        public static SourcePath signup(PolicyAcceptance.Channel channel) {
            return switch (channel) {
                case EMAIL -> EMAIL_SIGNUP;
                case SOCIAL_GOOGLE -> SOCIAL_GOOGLE_SIGNUP;
                case SOCIAL_KAKAO -> SOCIAL_KAKAO_SIGNUP;
                case SOCIAL_NAVER -> SOCIAL_NAVER_SIGNUP;
            };
        }
    }
}
