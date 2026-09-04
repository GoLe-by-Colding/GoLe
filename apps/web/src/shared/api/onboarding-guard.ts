/**
 * 서버측 온보딩 게이트(onboarding D5, R13)에 걸렸을 때의 클라이언트 대응.
 *
 * 매물등록·주문생성·채팅시작은 **서버가** 막는다 — 클라이언트만 믿고 게이트를 걸었다가
 * 실제 우회 사고가 났던 전례가 있어서다. 그래서 프론트는 차단을 판정하지 않고,
 * 서버가 내려준 403을 받아 사용자를 온보딩으로 안내하기만 한다.
 *
 * 공용 fetch 래퍼 한 곳에서 처리하므로 거래성 액션이 늘어나도 호출부는 손댈 필요가 없다.
 */

/** 서버 가드가 거부할 때 쓰는 에러 코드(R9). */
export const ONBOARDING_REQUIRED_CODE = "ONBOARDING_REQUIRED";

const ONBOARDING_PATH = "/onboarding";

/** 응답이 여러 개 동시에 403으로 떨어져도 이동은 한 번만 시도한다. */
let redirecting = false;

/**
 * 현재 화면을 `returnTo`로 남기고 온보딩으로 보낸다.
 *
 * `shared`는 라우팅 계층을 알 수 없으므로 라우터 대신 location으로 이동한다.
 * 서버 렌더 중에는 이동할 대상이 없으니 아무것도 하지 않는다.
 */
export function redirectToOnboarding(): void {
  if (typeof window === "undefined" || redirecting) {
    return;
  }
  // 온보딩 화면 자체의 요청이 실패한 경우까지 이동시키면 새로고침 루프가 된다.
  if (window.location.pathname === ONBOARDING_PATH) {
    return;
  }
  redirecting = true;
  const returnTo = `${window.location.pathname}${window.location.search}`;
  const query = new URLSearchParams({ returnTo });
  // 여기는 React 트리 밖(공용 fetch 래퍼)이라 useRouter를 쓸 수 없고 redirect()는 서버 전용이다.
  // 게이트에 걸리는 일 자체가 드물고, 이동 후 상태를 새로 받는 편이 안전해 전체 로드를 택한다.
  // eslint-disable-next-line @next/next/no-location-assign-relative-destination
  window.location.assign(`${ONBOARDING_PATH}?${query.toString()}`);
}
