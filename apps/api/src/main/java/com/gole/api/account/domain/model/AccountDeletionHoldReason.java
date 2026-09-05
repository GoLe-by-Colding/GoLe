package com.gole.api.account.domain.model;

/** 보존 중지 사유. 자유서술 개인정보를 탈퇴 원장에 남기지 않도록 코드만 허용한다. */
public enum AccountDeletionHoldReason {
    LEGAL_OBLIGATION,
    DISPUTE_OR_CLAIM,
    FRAUD_OR_SECURITY_INVESTIGATION
}
