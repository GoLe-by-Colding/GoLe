import { Platform } from "react-native";

/**
 * 앱 환경설정. `EXPO_PUBLIC_*`은 웹의 `NEXT_PUBLIC_*`과 같이 <b>빌드 시점에 번들로 인라인</b>된다 —
 * 값을 바꾸면 재빌드해야 한다.
 */
export interface AppEnv {
  readonly apiBaseUrl: string;
}

/**
 * 개발 기본값은 플랫폼마다 다르다. Android 에뮬레이터에서 `localhost`는 <b>에뮬레이터 자신</b>을
 * 가리키므로 호스트 머신을 뜻하는 10.0.2.2를 써야 한다. iOS 시뮬레이터는 호스트와 같은
 * 네트워크 스택을 쓴다.
 */
function developmentApiBaseUrl(): string {
  return Platform.OS === "android" ? "http://10.0.2.2:8080" : "http://localhost:8080";
}

function readApiBaseUrl(): string {
  // 점 표기여야 번들러가 정적 인라인한다(대괄호 표기는 런타임에 undefined로 남는다).
  const raw = process.env.EXPO_PUBLIC_API_BASE_URL;
  if (raw !== undefined && raw.length > 0) {
    return raw.replace(/\/+$/, "");
  }
  return developmentApiBaseUrl();
}

export const env: AppEnv = Object.freeze({
  apiBaseUrl: readApiBaseUrl(),
});
