package com.gole.api.account.domain.model;

/** 회원 탈퇴 요청의 운영 상태. 물리 파기는 READY 상태를 다시 검증한 뒤에만 가능하다. */
public enum AccountDeletionStatus {
    BLOCKED,
    READY,
    COMPLETED
}
