"use client";

import { type FormEvent, useState } from "react";
import { registerAccount } from "@entities/user";
import { ApiError } from "@shared/api";
import { Button, Field, Input } from "@shared/ui";

export interface SignUpFormProps {
  /** 가입 성공 시 인증 단계로 진행하기 위해 이메일을 전달한다. */
  readonly onRegistered: (email: string) => void;
}

export function SignUpForm({ onRegistered }: SignUpFormProps) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | undefined>(undefined);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(undefined);
    setSubmitting(true);
    try {
      await registerAccount(email, password);
      onRegistered(email);
    } catch (cause) {
      setError(
        cause instanceof ApiError ? cause.message : "가입 중 오류가 발생했습니다.",
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
            placeholder="you@example.com"
            aria-describedby={describedBy}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        )}
      </Field>
      <Field label="비밀번호" hint="8자 이상 입력하세요.">
        {({ inputId, describedBy }) => (
          <Input
            id={inputId}
            type="password"
            autoComplete="new-password"
            value={password}
            placeholder="••••••••"
            aria-describedby={describedBy}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        )}
      </Field>
      <Button type="submit" size="lg" fullWidth disabled={submitting}>
        {submitting ? "처리 중..." : "가입하기"}
      </Button>
    </form>
  );
}
