package com.gole.api.account.domain.model;

/**
 * 계정 상태. (요구사항 1.1, 1.4)
 *
 * <p>{@code SUSPENDED}는 운영자가 비활성화한 상태로, 로그인과 기존 세션 해석이 모두 차단된다.
 * (admin-console 요구사항 6.2, 6.4, 6.5)
 */
public enum AccountStatus {
    UNVERIFIED,
    VERIFIED,
    SUSPENDED
}
