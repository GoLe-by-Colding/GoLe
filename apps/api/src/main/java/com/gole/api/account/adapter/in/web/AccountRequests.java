package com.gole.api.account.adapter.in.web;

import jakarta.validation.constraints.AssertTrue;
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
            @Email @NotBlank @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 128) String password,
            @NotBlank @Size(max = 64) String termsVersion,
            @NotBlank @Size(max = 64) String privacyVersion,
            @AssertTrue(message = "이용약관에 동의해야 합니다") boolean termsAccepted,
            @AssertTrue(message = "개인정보처리방침을 확인해야 합니다") boolean privacyAcknowledged,
            @AssertTrue(message = "만 14세 이상임을 확인해야 합니다") boolean minimumAgeConfirmed) {}

    public record VerifyEmailRequest(
            @Email @NotBlank @Size(max = 254) String email, @NotBlank @Pattern(regexp = "\\d{6}") String code) {}

    public record ResendVerificationRequest(@Email @NotBlank @Size(max = 254) String email) {}

    public record SignInRequest(
            @Email @NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 128) String password) {}

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(min = 8, max = 128) String newPassword) {}

    public record RequestPasswordResetRequest(@Email @NotBlank @Size(max = 254) String email) {}

    public record ConfirmPasswordResetRequest(
            @Email @NotBlank @Size(max = 254) String email,
            @NotBlank @Pattern(regexp = "\\d{6}") String code,
            @NotBlank @Size(min = 8, max = 128) String newPassword) {}
}
