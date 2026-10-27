# 설계

- shared/config/makers.ts가 공개 팀원 정보의 단일 정본이다. 콘솔·메타데이터·JSON-LD·humans.txt가 재사용한다. 역할 출처는 2026-10-27 확인한 https://kscold.com/info/gole이며 GitHub 계정은 사용자 확정값이다.
- widgets/maker-credit는 서버 출력이 없는 클라이언트 컴포넌트다. window에 문서 단위 플래그를 두고 effect에서 한 번만 console.log를 호출한다. Strict Mode와 클라이언트 경로 전환에도 중복 출력하지 않는다. 테스트 환경에서는 건너뛴다.
- 운영에서는 SWC가 일반 콘솔 호출을 제거한다(error/warn 제외). 제작자 컴포넌트만 Function.prototype.bind.call로 콘솔 메서드를 간접 참조하며, 예외의 근거를 한국어 주석으로 한정한다. 기존 ESLint no-console 규칙이 없으므로 불필요한 비활성화 주석은 넣지 않는다.
- /humans.txt Route Handler는 공개 정적 응답으로 생성하고 명시적인 UTF-8 text/plain을 반환한다. DB·운영 API·로그인에 의존하지 않는다.
- 루트의 기존 Organization/WebSite 노드는 유지하며 Person 노드 3개와 참조만 추가한다. 같은 조직을 새로 만들지 않는다. creator는 CreativeWork 계열 WebSite, founder/member는 Organization에 사용한다.
- 루트 head의 humans 링크와 metadata가 제공하는 개인 author 링크는 목적이 다르므로 함께 유지한다.
- 푸터는 기존 반응형 flex 배치와 neutral 색상 토큰을 유지한다.
- 검증: HTTP/소스/구조화 데이터·브라우저 콘솔 1회·SPA 이동/새로고침·푸터 가시성·아트 원문과 색상 일치 및 전체 CI를 확인한다.

## 배포 기준선

- 시작 main: ad34eb6e14cd56d42c3fa2e2f4760acd727a27d3
- 시작 dev: 9b068624 (main 역병합 커밋만 앞서며 파일 차이 없음)
- #265/#266/#267은 이번 변경에 포함하지 않고 열린 PR로 보존한다.
