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
