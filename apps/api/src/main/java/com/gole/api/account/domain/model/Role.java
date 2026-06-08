package com.gole.api.account.domain.model;

/**
 * 계정 권한. 관리자 기능(RBAC) 기준. USER는 일반 사용자, ADMIN은 운영 관리자.
 */
public enum Role {
    USER,
    ADMIN
}
