"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  ANALYTICS_CONSENT_STORAGE_KEY,
  OPEN_ANALYTICS_SETTINGS_EVENT,
  type AnalyticsRuntimeConfig,
} from "@shared/config";
import { Button } from "@shared/ui";
import {
  activateAnalytics,
  type AnalyticsConsentDecision,
  clearKnownGoogleAnalyticsCookies,
  deactivateAnalytics,
  readAnalyticsConsent,
  resetAnalyticsConsent,
  trackAnalyticsPageView,
  writeAnalyticsConsent,
} from "../model/analytics-consent";

export interface AnalyticsConsentManagerProps {
  readonly configuration: AnalyticsRuntimeConfig;
}

function showConsentDialog(dialog: HTMLDialogElement | null): void {
  if (dialog === null || dialog.open) return;
  dialog.show();
  window.requestAnimationFrame(() => {
    dialog.querySelector<HTMLElement>("[data-analytics-initial-focus]")?.focus();
  });
}

function closeConsentDialog(dialog: HTMLDialogElement | null): void {
  dialog?.close();
  if (window.location.hash === "#analytics-settings") {
    window.history.replaceState(null, "", `${window.location.pathname}${window.location.search}`);
  }
}

/** ID 미설정 빌드에서는 스토리지 접근·UI·외부 스크립트를 모두 생략한다. */
export function AnalyticsConsentManager({ configuration }: AnalyticsConsentManagerProps) {
  if (configuration.provider === "disabled") return null;
  return <EnabledAnalyticsConsentManager configuration={configuration} />;
}

function EnabledAnalyticsConsentManager({ configuration }: AnalyticsConsentManagerProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const lastTrackedPathRef = useRef<string | null>(null);
  const [decision, setDecision] = useState<AnalyticsConsentDecision | null>(null);
  const [hydrated, setHydrated] = useState(false);
  const [analyticsReady, setAnalyticsReady] = useState(false);
  const pathname = usePathname();

  useEffect(() => {
    const frame = window.requestAnimationFrame(() => {
      let storedDecision: AnalyticsConsentDecision | null = null;
      try {
        storedDecision = readAnalyticsConsent(window.localStorage);
      } catch {
        // 저장소 접근이 막힌 경우에도 동의가 있었다고 추정하지 않는다.
      }
      if (storedDecision !== "granted") clearKnownGoogleAnalyticsCookies(document);
      setDecision(storedDecision);
      setHydrated(true);
      if (storedDecision === null) showConsentDialog(dialogRef.current);
    });
    return () => window.cancelAnimationFrame(frame);
  }, []);

  useEffect(() => {
    const openSettings = (): void => {
      showConsentDialog(dialogRef.current);
    };
    const openSettingsFromHash = (): void => {
      if (window.location.hash === "#analytics-settings") openSettings();
    };
    window.addEventListener(OPEN_ANALYTICS_SETTINGS_EVENT, openSettings);
    window.addEventListener("hashchange", openSettingsFromHash);
    openSettingsFromHash();
    return () => {
      window.removeEventListener(OPEN_ANALYTICS_SETTINGS_EVENT, openSettings);
      window.removeEventListener("hashchange", openSettingsFromHash);
    };
  }, []);

  useEffect(() => {
    if (!hydrated || decision !== "granted") {
      lastTrackedPathRef.current = null;
      return;
    }

    let cancelled = false;
    void activateAnalytics(configuration)
      .then(() => {
        if (!cancelled) setAnalyticsReady(true);
      })
      .catch(() => {
        // 분석 실패는 서비스 이용을 방해하지 않으며 재방문 시 다시 시도한다.
        if (!cancelled) setAnalyticsReady(false);
      });
    return () => {
      cancelled = true;
    };
  }, [configuration, decision, hydrated]);

  useEffect(() => {
    if (!analyticsReady || decision !== "granted") return;
    if (lastTrackedPathRef.current === pathname) return;
    lastTrackedPathRef.current = pathname;
    trackAnalyticsPageView(configuration, pathname);
  }, [analyticsReady, configuration, decision, pathname]);

  const saveDecision = (nextDecision: AnalyticsConsentDecision): void => {
    try {
      writeAnalyticsConsent(window.localStorage, nextDecision);
    } catch {
      // 브라우저가 저장을 막아도 현재 탭의 선택은 적용한다.
    }
    if (nextDecision === "denied") {
      clearKnownGoogleAnalyticsCookies(document);
    }
    setDecision(nextDecision);
    closeConsentDialog(dialogRef.current);
  };

  const withdraw = (): void => {
    try {
      writeAnalyticsConsent(window.localStorage, "denied");
    } catch {
      // 저장 실패 시에도 현재 문서의 분석 실행을 종료한다.
    }
    deactivateAnalytics(configuration);
    closeConsentDialog(dialogRef.current);
    window.location.reload();
  };

  const reset = (): void => {
    try {
      resetAnalyticsConsent(window.localStorage);
    } catch {
      // 저장소 접근이 막힌 환경은 현재 탭에서 미선택 상태로 되돌린다.
    }
    const hadGrantedConsent = decision === "granted";
    deactivateAnalytics(configuration);
    setDecision(null);
    if (hadGrantedConsent) {
      window.location.reload();
      return;
    }
    showConsentDialog(dialogRef.current);
  };

  return (
    <dialog
      id="analytics-settings"
      ref={dialogRef}
      aria-labelledby="analytics-consent-title"
      aria-describedby="analytics-consent-description"
      aria-modal="false"
      data-testid="analytics-consent-dialog"
      className="fixed inset-x-4 top-auto bottom-4 z-50 m-auto w-auto max-w-xl rounded-2xl border border-neutral-200 bg-white p-0 text-neutral-900 shadow-lift sm:inset-x-auto sm:right-6 sm:bottom-6 sm:left-auto sm:w-[min(32rem,calc(100vw-3rem))]"
      onKeyDown={(event) => {
        if (event.key === "Escape") {
          event.preventDefault();
          closeConsentDialog(dialogRef.current);
        }
      }}
    >
      <div className="p-5 sm:p-6">
        <p className="font-mono text-xs font-semibold uppercase tracking-wider text-brand-600">
          Optional analytics
        </p>
        <h2 id="analytics-consent-title" className="mt-2 text-lg font-bold text-neutral-900">
          서비스 이용 분석 설정
        </h2>
        <p id="analytics-consent-description" className="mt-3 text-sm leading-6 text-neutral-600">
          허용한 경우에만 Google Analytics 또는 Tag Manager를 불러와 방문 페이지·브라우저·기기
          정보와 대략적 지역을 통계로 확인합니다. 거부해도 모든 서비스 기능을 그대로 이용할 수
          있습니다.
        </p>
        <p className="mt-2 text-xs leading-5 text-neutral-500">
          현재 선택: {decision === "granted" ? "허용" : decision === "denied" ? "거부" : "미선택"}
          {" · "}
          <Link href="/privacy#analytics" className="font-semibold text-brand-700 underline">
            처리 항목과 국외 이전 안내
          </Link>
        </p>

        <div className="mt-5 flex flex-wrap justify-end gap-2">
          {decision === "granted" ? (
            <Button
              data-analytics-initial-focus
              size="sm"
              variant="secondary"
              className="min-w-24"
              onClick={withdraw}
            >
              동의 철회
            </Button>
          ) : (
            <Button
              data-analytics-initial-focus
              size="sm"
              variant="secondary"
              className="min-w-24"
              onClick={() => saveDecision("denied")}
            >
              {decision === "denied" ? "거부 유지" : "거부"}
            </Button>
          )}
          {decision !== null ? (
            <Button size="sm" variant="ghost" onClick={reset}>
              선택 초기화
            </Button>
          ) : null}
          {decision !== "granted" ? (
            <Button
              size="sm"
              variant="secondary"
              className="min-w-24"
              onClick={() => saveDecision("granted")}
            >
              분석 허용
            </Button>
          ) : null}
          {decision === null ? (
            <Button size="sm" variant="ghost" onClick={() => closeConsentDialog(dialogRef.current)}>
              나중에
            </Button>
          ) : (
            <Button size="sm" variant="ghost" onClick={() => closeConsentDialog(dialogRef.current)}>
              닫기
            </Button>
          )}
        </div>
      </div>
      <span className="sr-only" data-storage-key={ANALYTICS_CONSENT_STORAGE_KEY}>
        분석 동의 선택은 이 브라우저에만 저장됩니다.
      </span>
    </dialog>
  );
}
