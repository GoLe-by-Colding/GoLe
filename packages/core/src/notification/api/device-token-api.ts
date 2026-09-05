import { apiRequest } from "../../runtime";

/** 백엔드 `DevicePlatform` 열거형과 같은 값이어야 한다. */
export type DevicePlatform = "IOS" | "ANDROID";

const DEVICES_PATH = "/api/v1/notifications/devices";

/**
 * 단말 푸시 토큰을 등록한다. 멱등하다 — 같은 토큰을 다시 보내면 소유 계정과 시각만 갱신된다.
 *
 * <b>대상 계정은 서버가 세션에서 정한다.</b> 본문에 계정을 싣지 않는 이유는, 실으면 남의 계정에
 * 자기 단말을 등록해 그 사람의 알림을 가로챌 수 있기 때문이다.
 */
export function registerDeviceToken(
  token: string,
  platform: DevicePlatform,
  signal?: AbortSignal,
): Promise<void> {
  return apiRequest<void>(DEVICES_PATH, {
    method: "POST",
    body: { token, platform },
    ...(signal === undefined ? {} : { signal }),
  });
}

/** 로그아웃 시 호출한다. 토큰만으로 지운다 — 계정이 바뀐 단말의 죽은 토큰을 남기지 않기 위해서다. */
export function unregisterDeviceToken(token: string, signal?: AbortSignal): Promise<void> {
  return apiRequest<void>(`${DEVICES_PATH}?token=${encodeURIComponent(token)}`, {
    method: "DELETE",
    ...(signal === undefined ? {} : { signal }),
  });
}
