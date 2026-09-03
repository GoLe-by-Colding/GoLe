package com.gole.api.account.domain.model;

import java.time.Instant;

/** 가입 당시 정책 확인 증빙. 기존 행을 고치지 않고 새 버전 수락마다 추가한다. */
public record PolicyAcceptance(
        String id,
        String accountId,
        String termsVersion,
        String privacyVersion,
        boolean termsAccepted,
        boolean privacyAcknowledged,
        boolean minimumAgeConfirmed,
        Channel channel,
        Instant acceptedAt) {

    public enum Channel {
        EMAIL,
        SOCIAL_GOOGLE,
        SOCIAL_KAKAO,
        SOCIAL_NAVER;

        public static Channel social(AuthProvider provider) {
            return switch (provider) {
                case GOOGLE -> SOCIAL_GOOGLE;
                case KAKAO -> SOCIAL_KAKAO;
                case NAVER -> SOCIAL_NAVER;
            };
        }
    }
}
