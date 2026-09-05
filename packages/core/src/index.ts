/**
 * GoLe 공유 코어. 웹(Next.js)과 앱(React Native)이 함께 쓰는 플랫폼 중립 계층이다.
 *
 * 엔티티는 슬라이스별 subpath로 가져온다 — `@gole/core/listing` 처럼. 단일 배럴로 모으지 않는
 * 이유는 15개 슬라이스의 타입 이름이 충돌하고(Status·Summary 류) 슬라이스 격리도 잃기 때문이다.
 */
export * from "./runtime";
export * from "./lib";
