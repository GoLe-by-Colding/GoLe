package com.gole.api.chat.domain.model;

/** 채팅이 판단에 쓰는 계정 자격. 계정 컨텍스트의 계정 모델을 chat 에 필요한 플래그로 환원한 값이다. */
public record ChatAccount(String id, boolean admin, boolean verified, boolean suspended) {

    public boolean isAdmin() {
        return admin;
    }

    public boolean isVerified() {
        return verified;
    }

    public boolean isSuspended() {
        return suspended;
    }
}
