"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, type FormEvent, useState } from "react";
import { confirmPasswordReset, requestPasswordReset } from "@entities/user";
import { resolveReturnTo } from "@views/auth/model/return-to";
import { AuthCard } from "@widgets/auth-layout";
import { ApiError } from "@shared/api";
import { Button, Field, Input } from "@shared/ui";

export function PasswordResetPage() {
  return (
    <AuthCard title="비밀번호 재설정" subtitle="이메일로 받은 일회용 코드로 안전하게 바꿔요.">
      <Suspense
        fallback={<p className="py-6 text-center text-sm text-neutral-500">불러오는 중…</p>}
      >
        <PasswordResetContent />
      </Suspense>
    </AuthCard>
  );
}

function PasswordResetContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const returnTo = resolveReturnTo(searchParams.get("returnTo"));
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [passwordConfirmation, setPasswordConfirmation] = useState("");
  const [requested, setRequested] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  async function requestCode(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await requestPasswordReset(email);
      setRequested(true);
      setNotice("가입된 이메일이라면 재설정 코드를 보냈어요. 코드는 10분 동안 유효합니다.");
    } catch {
      setError("코드 요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setSubmitting(false);
    }
  }

  async function confirmReset(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    if (newPassword !== passwordConfirmation) {
      setError("새 비밀번호가 서로 일치하지 않습니다.");
      return;
    }
    setSubmitting(true);
    try {
      await confirmPasswordReset(email, code, newPassword);
      const next = new URLSearchParams({ passwordReset: "1" });
      if (returnTo !== null) next.set("returnTo", returnTo);
      router.replace(`/login?${next.toString()}`);
    } catch (cause) {
      setError(
        cause instanceof ApiError
          ? cause.message
          : "비밀번호를 바꾸지 못했어요. 새 코드를 요청한 뒤 다시 시도해 주세요.",
      );
    } finally {
      setSubmitting(false);
    }
  }

  const loginHref =
    returnTo === null ? "/login" : `/login?returnTo=${encodeURIComponent(returnTo)}`;

  if (!requested) {
    return (
      <form className="flex flex-col gap-5" onSubmit={requestCode} noValidate>
        <p className="text-sm leading-relaxed text-neutral-600">
          가입할 때 사용한 이메일을 입력하세요. 계정 유무와 관계없이 같은 안내를 보여드려요.
        </p>
        {error ? (
          <p role="alert" className="rounded-md bg-danger-soft p-3 text-sm text-danger">
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
              onChange={(event) => setEmail(event.target.value)}
              required
            />
          )}
        </Field>
        <Button type="submit" size="lg" fullWidth disabled={submitting || email.length === 0}>
          {submitting ? "요청 중…" : "재설정 코드 받기"}
        </Button>
        <Link className="text-center text-sm font-semibold text-brand-700" href={loginHref}>
          로그인으로 돌아가기
        </Link>
      </form>
    );
  }

  return (
    <form className="flex flex-col gap-4" onSubmit={confirmReset} noValidate>
      {notice ? (
        <p
          role="status"
          className="rounded-md bg-brand-50 p-3 text-sm leading-relaxed text-brand-800"
        >
          {notice}
        </p>
      ) : null}
      {error ? (
        <p role="alert" className="rounded-md bg-danger-soft p-3 text-sm text-danger">
          {error}
        </p>
      ) : null}
      <Field label="이메일">
        {({ inputId, describedBy }) => (
          <Input id={inputId} value={email} aria-describedby={describedBy} readOnly />
        )}
      </Field>
      <Field label="재설정 코드" hint="이메일로 받은 숫자 6자리를 입력하세요.">
        {({ inputId, describedBy }) => (
          <Input
            id={inputId}
            inputMode="numeric"
            autoComplete="one-time-code"
            pattern="[0-9]{6}"
            maxLength={6}
            value={code}
            aria-describedby={describedBy}
            onChange={(event) => setCode(event.target.value.replace(/\D/g, "").slice(0, 6))}
            required
          />
        )}
      </Field>
      <Field label="새 비밀번호" hint="8자 이상, UTF-8 기준 72바이트 이하">
        {({ inputId, describedBy }) => (
          <Input
            id={inputId}
            type="password"
            autoComplete="new-password"
            value={newPassword}
            aria-describedby={describedBy}
            onChange={(event) => setNewPassword(event.target.value)}
            required
          />
        )}
      </Field>
      <Field label="새 비밀번호 확인">
        {({ inputId, describedBy }) => (
          <Input
            id={inputId}
            type="password"
            autoComplete="new-password"
            value={passwordConfirmation}
            aria-describedby={describedBy}
            onChange={(event) => setPasswordConfirmation(event.target.value)}
            required
          />
        )}
      </Field>
      <Button
        type="submit"
        size="lg"
        fullWidth
        disabled={submitting || code.length !== 6 || newPassword.length < 8}
      >
        {submitting ? "변경 중…" : "비밀번호 바꾸기"}
      </Button>
      <div className="flex items-center justify-between gap-3 text-sm">
        <button
          type="button"
          className="font-semibold text-brand-700 hover:underline"
          onClick={() => {
            setRequested(false);
            setCode("");
            setError(null);
            setNotice(null);
          }}
        >
          다른 이메일 입력
        </button>
        <button
          type="button"
          className="font-semibold text-neutral-600 hover:text-brand-700"
          disabled={submitting}
          onClick={() => {
            setSubmitting(true);
            setError(null);
            void requestPasswordReset(email)
              .then(() =>
                setNotice(
                  "코드를 다시 요청했어요. 재요청 간격에 따라 기존 코드가 유지될 수 있어요.",
                ),
              )
              .catch(() => setError("코드를 다시 요청하지 못했어요."))
              .finally(() => setSubmitting(false));
          }}
        >
          코드 다시 받기
        </button>
      </div>
    </form>
  );
}
