import type { Page } from "@playwright/test";

/**
 * 쓰기 플로우 E2E가 쓰는 실제 세션.
 *
 * 서버가 세션을 진짜로 검증하므로(UserAuthInterceptor → AccountService.resolve) 임의 토큰으로는
 * 매물 등록도 이미지 업로드도 401이다. 여기 토큰은 `scripts/seed-e2e-accounts.sh`가 Redis와
 * Mongo에 심어 둔 것과 짝이 맞아야 한다. 둘 중 하나만 바꾸면 조용히 401로 돌아간다.
 *
 * accountId도 같이 넣는다 — 서버는 토큰으로 신원을 정하지만, 화면은 "내 매물" 판정처럼
 * 이 값을 보고 자기 매물을 구분한다.
 */
export const E2E_SELLER = {
  accountId: "e2e-seller",
  sessionToken: "e2e-seller-session-token",
  role: "USER",
} as const;

export const E2E_BUYER = {
  accountId: "e2e-buyer",
  sessionToken: "e2e-buyer-session-token",
  role: "USER",
} as const;

type E2ESession = typeof E2E_SELLER | typeof E2E_BUYER;

/** 첫 스크립트 실행 전에 세션을 심는다. 페이지 이동 전에 호출해야 한다. */
export async function signInAs(page: Page, session: E2ESession): Promise<void> {
  await page.addInitScript((value) => {
    window.localStorage.setItem("gole.session", value);
  }, JSON.stringify(session));
}

/** 이미 열린 페이지에서 계정을 바꾼다. 호출 후 reload가 필요하다. */
export async function switchTo(page: Page, session: E2ESession): Promise<void> {
  await page.evaluate((value) => {
    window.localStorage.setItem("gole.session", value);
  }, JSON.stringify(session));
}
