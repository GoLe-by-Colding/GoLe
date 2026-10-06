import { apiRequest } from "../../runtime";
import type { NotificationPreferenceCategory, NotificationPreferenceChanges } from "../model/types";

interface NotificationPreferencesResponse {
  readonly categories: readonly NotificationPreferenceCategory[];
}

/** 경로의 userId는 서버가 무시하고 세션 계정을 쓴다 — 기존 알림 API와 같은 모양을 맞춘 것이다. */
function path(userId: string): string {
  return `/api/v1/users/${encodeURIComponent(userId)}/notification-preferences`;
}

/** 분류별 수신 설정. 순서는 서버 enum 순서(거래 진행 → 제안 → 관심 → 커뮤니티)다. */
export function fetchNotificationPreferences(
  userId: string,
  signal?: AbortSignal,
): Promise<readonly NotificationPreferenceCategory[]> {
  return apiRequest<NotificationPreferencesResponse>(path(userId), {
    cache: "no-store",
    ...(signal === undefined ? {} : { signal }),
  }).then((r) => r.categories);
}

/**
 * 분류 수신 여부를 바꾸고 바뀐 뒤의 전체 설정을 돌려받는다.
 * `trade: false`는 서버가 400 `NOTIFICATION_CATEGORY_MANDATORY`로 거부한다.
 */
export function updateNotificationPreferences(
  userId: string,
  enabled: NotificationPreferenceChanges,
): Promise<readonly NotificationPreferenceCategory[]> {
  return apiRequest<NotificationPreferencesResponse>(path(userId), {
    method: "PUT",
    body: { enabled },
  }).then((r) => r.categories);
}
