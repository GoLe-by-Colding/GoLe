import { expect, type Page } from "@playwright/test";

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

/**
 * role은 화면 렌더용이다 — 서버는 토큰으로 권한을 정하므로(계정 문서의 role) 여기서 바꿔도
 * 실제 권한은 변하지 않는다. API 응답을 전부 가로채는 화면 검증에서만 덮어쓴다.
 */
export interface E2ESession {
  readonly accountId: string;
  readonly sessionToken: string;
  readonly role: string;
}

/**
 * 이 탭에서 지금 쓸 계정. init 스크립트가 페이지를 열 때마다 이 값을 다시 심는다.
 *
 * `signInAs`의 init 스크립트는 **이동할 때마다** 돈다. 그 값을 고정으로 두면 `switchTo`로 바꾼 계정이 다음
 * `goto`에서 첫 계정으로 덮여, 판매자로 바꿨다고 믿은 화면이 실제로는 구매자 세션으로 요청을 보낸다
 * (구매자가 매물을 올리고 자기 입찰을 체결하려다 실패했다). 그래서 탭의 sessionStorage에 "지금 계정"을 두고
 * init 스크립트와 `switchTo`가 같은 자리를 본다. sessionStorage는 같은 탭·같은 원점 이동 동안 유지된다.
 */
const ACTIVE_SESSION_KEY = "gole.e2e.activeSession";

/** 첫 스크립트 실행 전에 세션을 심는다. 페이지 이동 전에 호출해야 한다. */
export async function signInAs(page: Page, session: E2ESession): Promise<void> {
  await page.addInitScript(
    ({ key, value }) => {
      const active = window.sessionStorage.getItem(key) ?? value;
      window.sessionStorage.setItem(key, active);
      window.localStorage.setItem("gole.session", active);
    },
    { key: ACTIVE_SESSION_KEY, value: JSON.stringify(session) },
  );
}

/** 이미 열린 페이지에서 계정을 바꾼다. 다음 이동(또는 reload)부터 그 계정으로 요청한다. */
export async function switchTo(page: Page, session: E2ESession): Promise<void> {
  await page.evaluate(
    ({ key, value }) => {
      window.sessionStorage.setItem(key, value);
      window.localStorage.setItem("gole.session", value);
    },
    { key: ACTIVE_SESSION_KEY, value: JSON.stringify(session) },
  );
}

/**
 * 시드 계정의 제3자 제공 동의를 API로 기록한다. 매물 채팅방은 구매자 본인 동의와 판매자 쪽 동의를 모두
 * 요구하는데(`ChatController` → `requireCurrent`·`requireCurrentSubject`), 시더는 동의를 심지 않는다.
 * 동의는 덧붙이기 이벤트라 여러 번 불러도 결과(현재 동의함)가 같다.
 */
export async function recordThirdPartyConsent(page: Page, session: E2ESession): Promise<void> {
  const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
  const headers = { Authorization: `Bearer ${session.sessionToken}` };
  const current = await page.request.get(
    `${apiBaseUrl}/api/v1/accounts/me/third-party-provision-consents/current`,
    { headers },
  );
  expect(current.ok(), "동의 상태를 읽으려면 시드 세션이 살아 있어야 합니다").toBeTruthy();
  const { noticeVersion } = (await current.json()) as { noticeVersion: string };
  const recorded = await page.request.post(
    `${apiBaseUrl}/api/v1/accounts/me/third-party-provision-consents`,
    {
      headers,
      data: {
        noticeVersion,
        accepted: true,
        path: "LISTING_CHAT",
        requestId: `e2e-${session.accountId}-${Date.now()}`,
      },
    },
  );
  expect(recorded.ok()).toBeTruthy();
}
