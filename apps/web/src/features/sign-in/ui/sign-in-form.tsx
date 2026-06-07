"use client";

import { type FormEvent, useState } from "react";
import { saveSession, signIn, type Session } from "@entities/user";
import { ApiError } from "@shared/api";
import { Button, Field, Input } from "@shared/ui";

export interface SignInFormProps {
  readonly onSignedIn: (session: Session) => void;
}

export function SignInForm({ onSignedIn }: SignInFormProps) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | undefined>(undefined);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(undefined);
    setSubmitting(true);
    try {
      const session = await signIn(email, password);
      saveSession(session);
      onSignedIn(session);
    } catch (cause) {
      setError(
        cause instanceof ApiError ? cause.message : "로그인 중 오류가 발생했습니다.",
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
      <Field label="비밀번호">
        {({ inputId, describedBy }) => (
          <Input
            id={inputId}
            type="password"
            autoComplete="current-password"
            value={password}
            placeholder="••••••••"
            aria-describedby={describedBy}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        )}
      </Field>
      <Button type="submit" size="lg" fullWidth disabled={submitting}>
        {submitting ? "로그인 중..." : "로그인"}
      </Button>
    </form>
  );
}
