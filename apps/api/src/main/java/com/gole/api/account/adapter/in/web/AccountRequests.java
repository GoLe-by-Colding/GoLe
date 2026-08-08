package com.gole.api.account.adapter.in.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 계정 관련 요청 DTO 모음. Bean Validation으로 1차 형식 검증.
 */
public final class AccountRequests {

    private AccountRequests() {}

    public record RegisterRequest(
            @Email @NotBlank @Size(max = 254) String email, @NotBlank @Size(min = 8, max = 128) String password) {}

    public record VerifyEmailRequest(
            @Email @NotBlank @Size(max = 254) String email, @NotBlank @Pattern(regexp = "\\d{6}") String code) {}

    public record ResendVerificationRequest(@Email @NotBlank @Size(max = 254) String email) {}

    public record SignInRequest(
            @Email @NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 128) String password) {}
}
