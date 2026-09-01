"use client";

import { type FormEvent, useEffect, useState } from "react";
import {
  confirmPhoneVerification,
  normalizePhoneNumber,
  requestPhoneVerification,
  validatePhoneNumber,
} from "@entities/user";
import { ApiError } from "@shared/api";
import { Button, Field, Input } from "@shared/ui";

export interface VerifyPhoneFormProps {
  /** 인증에 성공하면 호출된다. 이 시점에 서버가 phoneVerifiedAt을 저장한 상태다. */
  readonly onCompleted: () => void;
}

/** 재발송 쿨다운. 서버 정책(D2)과 같은 값을 화면에서도 보여준다. */
const RESEND_COOLDOWN_SECONDS = 60;

/**
 * 온보딩 2단계 — 휴대폰 번호 인증(R4, R5).
 *
 * 번호 입력 → 코드 발송 → 코드 확인의 2단계를 한 화면에서 진행한다.
 * 코드는 카카오 알림톡으로만 발송된다(D3) — 카카오톡 미가입자는 이번 스코프에서
 * 완료할 수 없으므로 발송 경로를 안내 문구로 분명히 밝힌다.
 */
export function VerifyPhoneForm({ onCompleted }: VerifyPhoneFormProps) {
  const [phoneNumber, setPhoneNumber] = useState("");
  const [code, setCode] = useState("");
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);
  const [notice, setNotice] = useState<string | undefined>(undefined);
  const [sending, setSending] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [resendAfter, setResendAfter] = useState(0);

  useEffect(() => {
    if (resendAfter <= 0) return;
    const timer = window.setTimeout(
      () => setResendAfter((seconds) => Math.max(0, seconds - 1)),
      1000,
    );
    return () => window.clearTimeout(timer);
  }, [resendAfter]);

  async function handleSend() {
    const invalid = validatePhoneNumber(phoneNumber);
    if (invalid !== null) {
      setError(invalid);
      return;
    }
    setError(undefined);
    setNotice(undefined);
    setSending(true);
    try {
      await requestPhoneVerification(normalizePhoneNumber(phoneNumber));
      setSent(true);
      setNotice("카카오톡으로 인증 코드를 보냈습니다.");
      setResendAfter(RESEND_COOLDOWN_SECONDS);
    } catch (cause) {
      setError(
        cause instanceof ApiError ? cause.message : "인증 코드 발송 중 오류가 발생했습니다.",
      );
    } finally {
      setSending(false);
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(undefined);
    setSubmitting(true);
    try {
      await confirmPhoneVerification(normalizePhoneNumber(phoneNumber), code);
      onCompleted();
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "인증 중 오류가 발생했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
      {error ? (
        <p className="p-3 rounded-md bg-danger-soft text-danger text-sm" role="alert">
          {error}
        </p>
      ) : null}
      {notice ? (
        <p
          className="p-3 rounded-md bg-success-soft text-success text-sm"
          role="status"
          aria-live="polite"
        >
          {notice}
        </p>
      ) : null}

      <Field label="휴대폰 번호" hint="인증 코드는 카카오톡으로 발송됩니다.">
        {({ inputId, describedBy }) => (
          <Input
            id={inputId}
            type="tel"
            inputMode="numeric"
            autoComplete="tel"
            value={phoneNumber}
            placeholder="010-1234-5678"
            aria-describedby={describedBy}
            onChange={(e) => setPhoneNumber(e.target.value)}
            required
          />
        )}
      </Field>

      {sent ? (
        <Field label="인증 코드" hint="카카오톡으로 받은 6자리 코드를 입력하세요.">
          {({ inputId, describedBy }) => (
            <Input
              id={inputId}
              inputMode="numeric"
              autoComplete="one-time-code"
              value={code}
              placeholder="000000"
              maxLength={6}
              aria-describedby={describedBy}
              onChange={(e) => setCode(e.target.value)}
              required
            />
          )}
        </Field>
      ) : null}

      {sent ? (
        <Button type="submit" size="lg" fullWidth disabled={submitting}>
          {submitting ? "확인 중..." : "인증하고 다음"}
        </Button>
      ) : null}

      <Button
        type="button"
        variant={sent ? "ghost" : "primary"}
        size="lg"
        fullWidth
        disabled={sending || resendAfter > 0 || phoneNumber.trim() === ""}
        onClick={handleSend}
      >
        {sending
          ? "보내는 중..."
          : resendAfter > 0
            ? `${resendAfter}초 후 인증 코드 다시 받기`
            : sent
              ? "인증 코드 다시 받기"
              : "인증 코드 받기"}
      </Button>
    </form>
  );
}
