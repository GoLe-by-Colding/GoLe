export type { Notification } from "./model/types";
export {
  fetchNotifications,
  fetchUnreadCount,
  markNotificationRead,
  markAllNotificationsRead,
} from "./api/notification-api";

// 단말 푸시 토큰. 지금은 앱만 쓰지만 백엔드 계약이므로 코어에 둔다 —
// 웹 푸시가 생기면 같은 엔드포인트를 그대로 쓴다.
export { registerDeviceToken, unregisterDeviceToken } from "./api/device-token-api";
export type { DevicePlatform } from "./api/device-token-api";
