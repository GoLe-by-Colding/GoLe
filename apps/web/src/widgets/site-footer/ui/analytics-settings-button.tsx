"use client";

import { OPEN_ANALYTICS_SETTINGS_EVENT } from "@shared/config";

/** 전역 동의 패널을 여는 푸터 진입점. 링크형 디자인을 유지한다. */
export function AnalyticsSettingsButton() {
  return (
    <a
      href="#analytics-settings"
      className="text-xs text-brand-300/60 transition-colors hover:text-white"
      onClick={() => {
        // 이벤트는 즉시 열고, hash는 manager hydration 전 클릭도 잃지 않는 영속 fallback이다.
        window.dispatchEvent(new Event(OPEN_ANALYTICS_SETTINGS_EVENT));
      }}
    >
      분석 설정
    </a>
  );
}
