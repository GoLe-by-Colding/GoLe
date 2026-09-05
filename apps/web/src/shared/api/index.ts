/**
 * API 파사드. 요청 클라이언트는 `@gole/core`(웹·앱 공유)에 있고, 여기서는 그것을 다시 내보내며
 * 브라우저 전용 세션 저장소와 온보딩 이동만 덧붙인다.
 *
 * `server-session-headers`는 `next/headers`에 묶여 있어 이 배럴에 넣지 않는다 —
 * 클라이언트 번들로 새면 빌드가 깨진다. 서버 컴포넌트가 직접 경로로 가져간다.
 */
export { ApiError, apiRequest, uploadImage, uploadImages } from "@gole/core";
export type { ApiErrorBody, RequestOptions, UploadedImage } from "@gole/core";
export { isOnboardingRequiredError, ONBOARDING_REQUIRED_CODE } from "@gole/core";
export { redirectToOnboarding } from "./onboarding-guard";
export {
  browserSessionStore,
  clearStoredSession,
  readSessionAuthorization,
  SESSION_CHANGE_EVENT,
  SESSION_STORAGE_KEY,
} from "./session-auth";
