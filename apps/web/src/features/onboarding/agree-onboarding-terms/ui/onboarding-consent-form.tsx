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
        <div className="rounded-lg border border-neutral-200 bg-neutral-50 p-3 text-xs leading-relaxed text-neutral-600">
          <dl className="grid gap-1">
            <ConsentNoticeItem label="수집·이용 목적">
              계정 프로필 구성, 관심 콘텐츠 제공, 거래·문의 기능 운영
            </ConsentNoticeItem>
            <ConsentNoticeItem label="항목">
              닉네임, 관심 태그, 정책상 전화 인증을 이용한 경우 전화번호·인증일시
            </ConsentNoticeItem>
            <ConsentNoticeItem label="보유기간">
              회원 탈퇴 또는 삭제 요청 처리 시까지. 법령상 보존 대상은 해당 기간 동안 분리 보관
            </ConsentNoticeItem>
            <ConsentNoticeItem label="거부권·불이익">
              필수 수집·이용 동의를 거부할 수 있으나, 거부하면 온보딩을 완료할 수 없어 로그인 기반
              거래·커뮤니티 기능 이용이 제한됩니다.
            </ConsentNoticeItem>
          </dl>
        </div>
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
          label={
            <>
              (선택) 이메일을 이용한 혜택·소식 안내에 동의합니다. 동의 철회 시까지 이용하며,
              거부해도 서비스 이용에 불이익이 없습니다.
            </>
          }
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

function ConsentNoticeItem({
  label,
  children,
}: {
  readonly label: string;
  readonly children: ReactNode;
}) {
  return (
    <div>
      <dt className="inline font-semibold text-neutral-800">{label}: </dt>
      <dd className="inline">{children}</dd>
    </div>
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
