const STORAGE_KEY = "gole.pending-verification-email";

/** 인증 대기 이메일을 URL query 대신 현재 탭의 저장소에만 보관한다. */
export function storePendingVerificationEmail(email: string): void {
  if (typeof window === "undefined") return;
  try {
    window.sessionStorage.setItem(STORAGE_KEY, email.trim().slice(0, 254));
  } catch {
    // 저장소가 차단돼도 인증 화면 자체는 열 수 있다.
  }
}

export function readPendingVerificationEmail(): string {
  if (typeof window === "undefined") return "";
  try {
    return window.sessionStorage.getItem(STORAGE_KEY)?.slice(0, 254) ?? "";
  } catch {
    return "";
  }
}

export function clearPendingVerificationEmail(): void {
  if (typeof window === "undefined") return;
  try {
    window.sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // 브라우저 저장소 정리는 best-effort다.
  }
}
