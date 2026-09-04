"use client";

import Link from "next/link";
import { type FormEvent, useState } from "react";
import {
  registerAccount,
  type CurrentSignupPolicy,
  type SignupPolicyAcceptance,
  ThirdPartyProvisionNotice,
} from "@entities/user";
import { ApiError } from "@shared/api";
import { Button, Field, Input } from "@shared/ui";

export interface SignUpFormProps {
  /** 가입 성공 시 인증 단계로 진행하기 위해 이메일을 전달한다. */
  readonly onRegistered: (email: string) => void;
  readonly policy: CurrentSignupPolicy | undefined;
  readonly policyError: string | undefined;
  readonly policyAcceptance: SignupPolicyAcceptance;
  readonly onPolicyAcceptanceChange: (next: SignupPolicyAcceptance) => void;
}

export function SignUpForm({
  onRegistered,
  policy,
  policyError,
  policyAcceptance,
  onPolicyAcceptanceChange,
}: SignUpFormProps) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | undefined>(undefined);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(undefined);
    setSubmitting(true);
    try {
      await registerAccount(email, password, policyAcceptance);
      onRegistered(email);
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "가입 중 오류가 발생했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  const policyReady =
    policy !== undefined &&
    policyAcceptance.termsAccepted &&
    policyAcceptance.privacyAcknowledged &&
    policyAcceptance.minimumAgeConfirmed;

  function updatePolicy(patch: Partial<SignupPolicyAcceptance>): void {
    onPolicyAcceptanceChange({ ...policyAcceptance, ...patch });
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
      <fieldset className="flex flex-col gap-3 rounded-xl border border-neutral-200 bg-neutral-50 p-4">
        <legend className="px-1 text-sm font-bold text-neutral-800">가입 전 확인</legend>
        {policyError ? (
          <p className="text-sm leading-relaxed text-danger" role="alert">
            {policyError}
          </p>
        ) : policy === undefined ? (
          <p className="text-sm text-neutral-500" role="status">
            최신 정책을 확인하는 중…
          </p>
        ) : (
          <>
            <PolicyCheckbox
              checked={policyAcceptance.termsAccepted}
              onChange={(checked) => updatePolicy({ termsAccepted: checked })}
            >
              <Link
                href="/terms"
                target="_blank"
                className="font-semibold text-brand-700 underline"
              >
                이용약관
              </Link>
              에 동의합니다. (필수)
            </PolicyCheckbox>
            <PolicyCheckbox
              checked={policyAcceptance.privacyAcknowledged}
              onChange={(checked) => updatePolicy({ privacyAcknowledged: checked })}
            >
              <Link
                href="/privacy"
                target="_blank"
                className="font-semibold text-brand-700 underline"
              >
                개인정보처리방침
              </Link>
              을 확인했습니다. (필수)
            </PolicyCheckbox>
            <PolicyCheckbox
              checked={policyAcceptance.minimumAgeConfirmed}
              onChange={(checked) => updatePolicy({ minimumAgeConfirmed: checked })}
            >
              만 {policy.minimumAge}세 이상입니다. (필수)
            </PolicyCheckbox>
          </>
        )}
      </fieldset>
      <fieldset className="flex flex-col gap-3 rounded-xl border border-brand-100 bg-brand-50/40 p-4">
        <legend className="px-1 text-sm font-bold text-neutral-800">제3자 제공 선택 동의</legend>
        {policy === undefined ? (
          <p className="text-sm text-neutral-500">최신 안내를 확인하는 중…</p>
        ) : (
          <>
            <ThirdPartyProvisionNotice compact />
            <PolicyCheckbox
              checked={policyAcceptance.thirdPartyProvisionAccepted}
              onChange={(checked) => updatePolicy({ thirdPartyProvisionAccepted: checked })}
            >
              위 개인정보 제3자 제공에 동의합니다. (선택)
            </PolicyCheckbox>
            <p className="text-xs text-neutral-400">
              제3자 제공 안내 버전 {policy.thirdPartyProvisionVersion}
            </p>
          </>
        )}
      </fieldset>
      <Button type="submit" size="lg" fullWidth disabled={submitting || !policyReady}>
        {submitting ? "처리 중..." : "가입하기"}
      </Button>
    </form>
  );
}

function PolicyCheckbox({
  checked,
  onChange,
  children,
}: {
  readonly checked: boolean;
  readonly onChange: (checked: boolean) => void;
  readonly children: React.ReactNode;
}) {
  return (
    <label className="flex cursor-pointer items-start gap-3 text-sm leading-relaxed text-neutral-700">
      <input
        type="checkbox"
        className="mt-0.5 h-4 w-4 shrink-0 accent-brand-600"
        checked={checked}
        onChange={(event) => onChange(event.target.checked)}
      />
      <span>{children}</span>
    </label>
  );
}
