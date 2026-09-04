"use client";

import Link from "next/link";
import { type FormEvent, type ReactNode, useState } from "react";
import { submitOnboardingConsent } from "@entities/user";
import { ApiError } from "@shared/api";
import { Button } from "@shared/ui";

export interface OnboardingConsentFormProps {
  /** 동의 저장이 끝나면 호출된다. 온보딩의 마지막 단계다. */
  readonly onCompleted: () => void;
}

/**
 * 온보딩 4단계 — 약관 동의(R7).
 *
 * 개인정보 수집·이용 동의는 필수라 체크 없이는 제출 자체를 막는다(서버도 false면 거부한다).
 * 마케팅 수신은 선택이며 기본값은 미동의다 — 동의를 기본 체크로 두지 않는다.
 */
export function OnboardingConsentForm({ onCompleted }: OnboardingConsentFormProps) {
  const [privacyConsented, setPrivacyConsented] = useState(false);
  const [marketingConsented, setMarketingConsented] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!privacyConsented) {
      setError("개인정보 수집·이용에 동의해야 가입을 완료할 수 있습니다.");
      return;
    }
    setError(undefined);
    setSubmitting(true);
    try {
      await submitOnboardingConsent(privacyConsented, marketingConsented);
      onCompleted();
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "동의 처리 중 오류가 발생했습니다.");
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

      <div className="flex flex-col gap-3">
        <ConsentCheckbox
          checked={privacyConsented}
          onChange={setPrivacyConsented}
          label={
            <>
              <span className="font-semibold text-danger">(필수)</span>{" "}
              <Link
                href="/privacy"
                target="_blank"
                className="font-semibold text-brand-700 underline underline-offset-4"
              >
                개인정보 수집·이용
              </Link>
              에 동의합니다.
            </>
          }
        />
        <ConsentCheckbox
          checked={marketingConsented}
          onChange={setMarketingConsented}
          label={<>(선택) 혜택·소식 마케팅 정보 수신에 동의합니다.</>}
        />
      </div>

      <p className="text-sm text-neutral-500">
        가입을 완료하면{" "}
        <Link
          href="/terms"
          target="_blank"
          className="font-semibold text-brand-700 underline underline-offset-4"
        >
          이용약관
        </Link>
        에 동의한 것으로 봅니다.
      </p>

      <Button type="submit" size="lg" fullWidth disabled={submitting}>
        {submitting ? "처리 중..." : "가입 완료"}
      </Button>
    </form>
  );
}

function ConsentCheckbox({
  checked,
  onChange,
  label,
}: {
  readonly checked: boolean;
  readonly onChange: (next: boolean) => void;
  readonly label: ReactNode;
}) {
  return (
    <label className="flex cursor-pointer items-start gap-3 text-sm leading-relaxed text-neutral-700">
      <input
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
        className="mt-0.5 size-4 shrink-0 accent-brand-600"
      />
      <span>{label}</span>
    </label>
  );
}
