export interface JsonLdProps {
  /** schema.org 구조화 데이터 객체. 직렬화 가능한 값만 담는다. */
  readonly data: Record<string, unknown>;
}

/**
 * schema.org JSON-LD를 `<script type="application/ld+json">`으로 렌더한다.
 *
 * 구조화 데이터는 `dangerouslySetInnerHTML`로 넣을 수밖에 없다(React가 script 본문을
 * 텍스트로 이스케이프해버리면 검색엔진이 파싱하지 못한다). 그래서 삽입 지점을 이 컴포넌트
 * 하나로 모으고, 여기서만 이스케이프 규칙을 관리한다.
 *
 * `<`를 유니코드 이스케이프하는 이유: 데이터에 `</script>` 문자열이 섞여 들어오면
 * 브라우저 HTML 파서가 스크립트를 조기 종료해 뒤 내용이 마크업으로 실행된다.
 * 매물 제목·설명처럼 사용자 입력에서 파생된 값이 들어오므로 실제 위험이다.
 * JSON 문법상 `<`는 `<`와 동등하게 파싱되므로 구조화 데이터 의미는 그대로다.
 */
export function JsonLd({ data }: JsonLdProps) {
  const json = JSON.stringify(data).replace(/</g, "\\u003c");
  return <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: json }} />;
}
