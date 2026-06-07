"use client";

import { type FormEvent, useState } from "react";
import { verifyEmail } from "@entities/user";
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

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(undefined);
    setSubmitting(true);
    try {
      await verifyEmail(email, code);
      onVerified();
    } catch (cause) {
      setError(
        cause instanceof ApiError ? cause.message : "인증 중 오류가 발생했습니다.",
      );
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
    </form>
  );
}
