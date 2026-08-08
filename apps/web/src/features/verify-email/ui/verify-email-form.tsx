"use client";

import { type FormEvent, useEffect, useState } from "react";
import { resendVerificationEmail, verifyEmail } from "@entities/user";
import { ApiError } from "@shared/api";
import { Button, Field, Input } from "@shared/ui";

export interface VerifyEmailFormProps {
  readonly initialEmail?: string;
  readonly onVerified: () => void;
}

export function VerifyEmailForm({ initialEmail = "", onVerified }: VerifyEmailFormProps) {
  const [email, setEmail] = useState(initialEmail);
  const [code, setCode] = useState("");
  const [error, setError] = useState<string | undefined>(undefined);
  const [submitting, setSubmitting] = useState(false);
  const [resending, setResending] = useState(false);
  const [resendAfter, setResendAfter] = useState(60);
  const [notice, setNotice] = useState<string | undefined>(undefined);

  useEffect(() => {
    if (resendAfter <= 0) return;
    const timer = window.setTimeout(
      () => setResendAfter((seconds) => Math.max(0, seconds - 1)),
      1000,
    );
    return () => window.clearTimeout(timer);
  }, [resendAfter]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(undefined);
    setSubmitting(true);
    try {
      await verifyEmail(email, code);
      onVerified();
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "인증 중 오류가 발생했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleResend() {
    setError(undefined);
    setNotice(undefined);
    setResending(true);
    try {
      await resendVerificationEmail(email);
      setNotice("새 인증 코드를 보냈습니다. 이메일을 확인해 주세요.");
      setResendAfter(60);
    } catch (cause) {
      setError(
        cause instanceof ApiError ? cause.message : "인증 코드 재발급 중 오류가 발생했습니다.",
      );
    } finally {
      setResending(false);
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
      <Field label="이메일">
        {({ inputId, describedBy }) => (
          <Input
            id={inputId}
            type="email"
            autoComplete="email"
            value={email}
            aria-describedby={describedBy}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        )}
      </Field>
      <Field label="인증 코드" hint="이메일로 받은 6자리 코드를 입력하세요.">
        {({ inputId, describedBy }) => (
          <Input
            id={inputId}
            inputMode="numeric"
            autoComplete="one-time-code"
            value={code}
            placeholder="000000"
            aria-describedby={describedBy}
            onChange={(e) => setCode(e.target.value)}
            required
          />
        )}
      </Field>
      <Button type="submit" size="lg" fullWidth disabled={submitting}>
        {submitting ? "확인 중..." : "인증하기"}
      </Button>
      <Button
        type="button"
        variant="ghost"
        fullWidth
        disabled={resending || resendAfter > 0 || email.trim() === ""}
        onClick={handleResend}
      >
        {resending
          ? "다시 보내는 중..."
          : resendAfter > 0
            ? `${resendAfter}초 후 인증 코드 다시 받기`
            : "인증 코드 다시 받기"}
      </Button>
    </form>
  );
}
