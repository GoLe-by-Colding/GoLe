import { ApiError } from "@shared/api";

export function errorText(error: unknown, fallback: string): string {
  return error instanceof ApiError && error.message.length > 0 ? error.message : fallback;
}

export function formatTime(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : date.toLocaleString("ko-KR", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
      });
}
