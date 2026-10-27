/** SDK와 독립적인 전처리 결과. 전송 설정·DSN·토큰·요청 본문·사용자 정보를 포함하지 않는다. */
export interface SafeSentryEvent {
  level: "error" | "fatal";
  environment: "production";
  message: string;
  fingerprint: string[];
  tags: { component: string; diagnostic: string };
}
export interface SentryPolicyInput {
  level?: string;
  environment?: string;
  tags?: Record<string, unknown>;
  [key: string]: unknown;
}
const components = new Set(["web", "api", "operations"]);
const diagnostics = new Set(["UNEXPECTED_ERROR", "DEPENDENCY_UNAVAILABLE", "JOB_FAILED"]);

/** 명시적으로 활성화하는 beforeSend 어댑터. 허용 필드만 추리고 중복·전송 횟수를 제한한다.
 * 전체 인스턴스에 걸친 알림 제한은 Sentry에서도 별도로 설정해야 한다.
 * 제외한 이벤트를 전달 건수에 포함하거나 전송 성공으로 저장하지 않는다.
 */
export function createSentryPolicy({
  enabled = false,
  now = Date.now,
}: { enabled?: boolean; now?: () => number } = {}) {
  const accepted = new Map<string, number>();
  let windowStart = 0;
  let windowCount = 0;
  return (event: SentryPolicyInput): SafeSentryEvent | null => {
    if (
      !enabled ||
      event.environment !== "production" ||
      (event.level !== "error" && event.level !== "fatal")
    )
      return null;
    const component =
      typeof event.tags?.component === "string" && components.has(event.tags.component)
        ? event.tags.component
        : "web";
    const diagnostic =
      typeof event.tags?.diagnostic === "string" && diagnostics.has(event.tags.diagnostic)
        ? event.tags.diagnostic
        : "UNEXPECTED_ERROR";
    const timestamp = now();
    const key = `${component}:${diagnostic}`;
    for (const [fingerprint, time] of accepted)
      if (timestamp - time >= 300_000 || timestamp < time) accepted.delete(fingerprint);
    if (timestamp - windowStart >= 60_000 || timestamp < windowStart) {
      windowStart = timestamp;
      windowCount = 0;
    }
    if (accepted.has(key) || windowCount >= 3) return null;
    accepted.set(key, timestamp);
    windowCount += 1;
    // 원본 이벤트를 펼쳐 복사하지 않는다. 예외·스택 변수·탐색 기록·추가 정보·요청 주소와 헤더,
    // 사용자·세션 재생·임의 태그에는 인증 정보가 들어 있을 수 있다.
    return {
      level: event.level,
      environment: "production",
      message: diagnostic,
      fingerprint: ["gole", component, diagnostic],
      tags: { component, diagnostic },
    };
  };
}
