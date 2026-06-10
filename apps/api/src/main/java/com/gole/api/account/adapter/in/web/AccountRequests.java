package com.gole.api.account.adapter.in.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 계정 관련 요청 DTO 모음. Bean Validation으로 1차 형식 검증.
 */
public final class AccountRequests {

    private AccountRequests() {}

    public record RegisterRequest(@Email @NotBlank String email, @NotBlank String password) {}

    public record VerifyEmailRequest(@Email @NotBlank String email, @NotBlank String code) {}

    public record SignInRequest(@Email @NotBlank String email, @NotBlank String password) {}
}
