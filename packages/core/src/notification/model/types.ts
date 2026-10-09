/**
 * 알림 도메인 타입. 백엔드 NotificationController 응답과 대응.
 */
export interface Notification {
  readonly id: string;
  readonly type: string;
  readonly message: string;
  readonly link: string | null;
  readonly read: boolean;
  readonly createdAt: string;
}

/**
 * 알림 수신 설정 분류 키. 백엔드 `NotificationCategory.key()`(소문자)와 같다.
 * `trade`는 거래 진행 알림이라 끌 수 없다(`mandatory: true`).
 */
export type NotificationPreferenceKey = "trade" | "offer" | "watch" | "community";

/** 수신 설정 한 줄. 백엔드 `NotificationPreferenceController` 응답의 `categories[]`와 대응. */
export interface NotificationPreferenceCategory {
  readonly key: NotificationPreferenceKey;
  readonly label: string;
  readonly enabled: boolean;
  readonly mandatory: boolean;
}

/** 바꿀 분류만 담는다. 빠진 키는 서버가 그대로 둔다. */
export type NotificationPreferenceChanges = Partial<Record<NotificationPreferenceKey, boolean>>;
