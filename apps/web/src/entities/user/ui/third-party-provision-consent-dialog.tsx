"use client";

import { useEffect, useId, useRef, useState } from "react";
import { Button, Heading } from "@shared/ui";
import type { ThirdPartyProvisionConsentDialogController } from "../model/use-third-party-provision-consent";
import { ThirdPartyProvisionNotice } from "./third-party-provision-notice";

export type ThirdPartyProvisionConsentDialogProps = ThirdPartyProvisionConsentDialogController;

export function ThirdPartyProvisionConsentDialog(props: ThirdPartyProvisionConsentDialogProps) {
  if (!props.open) return null;
  return (
    <OpenThirdPartyProvisionConsentDialog
      key={props.policy?.thirdPartyProvisionVersion ?? "loading"}
      {...props}
    />
  );
}

function OpenThirdPartyProvisionConsentDialog({
  policy,
  loading,
  submitting,
  error,
  accept,
  cancel,
  retryPolicyLoad,
}: ThirdPartyProvisionConsentDialogProps) {
  const [checked, setChecked] = useState(false);
  const titleId = useId();
  const descriptionId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  const restoreTargetRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    restoreTargetRef.current =
      document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    window.requestAnimationFrame(() => dialogRef.current?.focus());

    return () => {
      document.body.style.overflow = previousOverflow;
      window.requestAnimationFrame(() => restoreTargetRef.current?.focus());
    };
  }, []);

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !submitting) {
        cancel();
        return;
      }
      if (event.key !== "Tab") return;
      const dialog = dialogRef.current;
      if (dialog === null) return;
      const focusable = Array.from(
        dialog.querySelectorAll<HTMLElement>(
          'button:not([disabled]), [href], input:not([disabled]), [tabindex]:not([tabindex="-1"])',
        ),
      );
      const first = focusable.at(0);
      const last = focusable.at(-1);
      if (first === undefined || last === undefined) {
        event.preventDefault();
        dialog.focus();
      } else if (
        event.shiftKey &&
        (document.activeElement === first || !dialog.contains(document.activeElement))
      ) {
        event.preventDefault();
        last.focus();
      } else if (
        !event.shiftKey &&
        (document.activeElement === last || !dialog.contains(document.activeElement))
      ) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [cancel, submitting]);

  return (
    <div
      className="fixed inset-0 z-[70] grid place-items-center overflow-y-auto bg-neutral-950/55 px-4 py-8"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !submitting) cancel();
      }}
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={descriptionId}
        tabIndex={-1}
        className="flex max-h-full w-full max-w-xl flex-col gap-5 overflow-y-auto rounded-2xl bg-white p-5 shadow-2xl sm:p-6"
      >
        <div className="flex flex-col gap-2">
          <Heading level={2} id={titleId}>
            제3자 제공 동의가 필요합니다
          </Heading>
          <p id={descriptionId} className="text-sm leading-relaxed text-neutral-600">
            대화 참여자나 거래 상대방에게 아래 정보를 제공하기 전에 별도 동의를 받습니다.
          </p>
        </div>

        {loading ? (
          <p role="status" className="rounded-lg bg-neutral-50 p-4 text-sm text-neutral-500">
            최신 안내를 불러오는 중…
          </p>
        ) : policy === undefined ? (
          <div className="flex flex-col gap-3 rounded-lg bg-danger-soft p-4">
            <p role="alert" className="text-sm text-danger">
              {error ?? "최신 안내를 확인할 수 없습니다."}
            </p>
            <Button type="button" size="sm" variant="secondary" onClick={retryPolicyLoad}>
              다시 불러오기
            </Button>
          </div>
        ) : (
          <>
            <div className="rounded-xl border border-neutral-200 bg-neutral-50 p-4">
              <ThirdPartyProvisionNotice />
              <p className="mt-3 text-xs text-neutral-400">
                제3자 제공 안내 버전 {policy.thirdPartyProvisionVersion}
              </p>
            </div>
            <label className="flex cursor-pointer items-start gap-3 rounded-xl border border-brand-200 bg-brand-50 p-4 text-sm font-semibold leading-relaxed text-neutral-800">
              <input
                type="checkbox"
                checked={checked}
                disabled={submitting}
                onChange={(event) => setChecked(event.target.checked)}
                className="mt-0.5 h-4 w-4 shrink-0 accent-brand-600"
              />
              위 개인정보 제3자 제공에 동의합니다. (선택)
            </label>
            {error ? (
              <p role="alert" className="rounded-lg bg-danger-soft p-3 text-sm text-danger">
                {error}
              </p>
            ) : null}
          </>
        )}

        <div className="flex gap-2">
          <Button fullWidth variant="secondary" disabled={submitting} onClick={cancel}>
            동의하지 않고 돌아가기
          </Button>
          <Button
            fullWidth
            disabled={policy === undefined || !checked || submitting}
            onClick={() => void accept()}
          >
            {submitting ? "기록 중…" : "동의하고 계속"}
          </Button>
        </div>
      </div>
    </div>
  );
}
