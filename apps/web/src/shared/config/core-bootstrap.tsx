"use client";

import "./bootstrap";

/**
 * 클라이언트 번들에서 코어 부트스트랩을 실행시키는 자리표. 렌더 결과는 없다.
 *
 * 서버 번들은 `app/layout.tsx`가 같은 모듈을 직접 import해 처리한다. 두 그래프가 분리돼 있어
 * 한쪽만으로는 부족하다.
 */
export function CoreBootstrap(): null {
  return null;
}
