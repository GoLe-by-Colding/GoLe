import { apiRequest } from "@shared/api";
import type { Notification } from "../model/types";

function base(userId: string): string {
  return `/api/v1/users/${userId}/notifications`;
}

export function fetchNotifications(
  userId: string,
  signal?: AbortSignal,
): Promise<readonly Notification[]> {
  return apiRequest<readonly Notification[]>(base(userId), {
    cache: "no-store",
    ...(signal === undefined ? {} : { signal }),
  });
}

export function fetchUnreadCount(userId: string, signal?: AbortSignal): Promise<number> {
  return apiRequest<{ readonly unreadCount: number }>(`${base(userId)}/unread-count`, {
    cache: "no-store",
    ...(signal === undefined ? {} : { signal }),
  }).then((r) => r.unreadCount);
}

export function markNotificationRead(userId: string, notificationId: string): Promise<void> {
  return apiRequest<void>(`${base(userId)}/${notificationId}/read`, { method: "POST" });
}

export function markAllNotificationsRead(userId: string): Promise<void> {
  return apiRequest<void>(`${base(userId)}/read-all`, { method: "POST" });
}
