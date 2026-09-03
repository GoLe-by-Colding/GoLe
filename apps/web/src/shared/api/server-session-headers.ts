import "server-only";

import { cookies } from "next/headers";

const SESSION_COOKIE_NAME = "gole_session";

/**
 * 브라우저 → Next 서버 → API로 이어지는 서버 컴포넌트 요청에 세션 쿠키를 전달한다.
 *
 * 서버의 fetch는 브라우저 쿠키를 다른 포트의 API로 자동 전달하지 않는다. 전체 Cookie 헤더를
 * 그대로 복사하지 않고 GoLe 인증 쿠키 하나만 골라 보내 분석·광고 쿠키가 백엔드로 새지 않게 한다.
 */
export async function serverSessionHeaders(): Promise<Readonly<Record<string, string>>> {
  const token = (await cookies()).get(SESSION_COOKIE_NAME)?.value;
  return token === undefined || token.length === 0
    ? {}
    : { Cookie: `${SESSION_COOKIE_NAME}=${token}` };
}
