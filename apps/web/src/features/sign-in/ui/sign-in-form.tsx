"use client";

import Link from "next/link";
import { type FormEvent, useState } from "react";
import { saveSession, signIn, type Session } from "@entities/user";
import { ApiError } from "@shared/api";
import { Button, Field, Input } from "@shared/ui";

export interface SignInFormProps {
  readonly onSignedIn: (session: Session) => void;
  readonly resetHref?: string;
}

export function SignInForm({ onSignedIn, resetHref = "/forgot-password" }: SignInFormProps) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | undefined>(undefined);
  const [needsVerification, setNeedsVerification] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(undefined);
    setNeedsVerification(false);
    setSubmitting(true);
    try {
      const session = await signIn(email, password);
      saveSession(session);
      onSignedIn(session);
    } catch (cause) {
      setNeedsVerification(cause instanceof ApiError && cause.code === "ACCOUNT_NOT_VERIFIED");
      setError(cause instanceof ApiError ? cause.message : "로그인 중 오류가 발생했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
      {error ? (
        <div className="p-3 rounded-md bg-danger-soft text-danger text-sm" role="alert">
          <p>{error}</p>
          {needsVerification ? (
            <Link
              className="mt-2 inline-flex font-semibold underline underline-offset-4"
              href={`/verify?email=${encodeURIComponent(email)}`}
            >
              이메일 인증하러 가기
            </Link>
          ) : null}
        </div>
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
      <div className="-mt-2 flex justify-end">
        <Link
          href={resetHref}
          className="text-sm font-semibold text-brand-700 underline-offset-4 hover:underline"
        >
          비밀번호를 잊으셨나요?
        </Link>
      </div>
      <Button type="submit" size="lg" fullWidth disabled={submitting}>
        {submitting ? "로그인 중..." : "로그인"}
      </Button>
    </form>
  );
}
