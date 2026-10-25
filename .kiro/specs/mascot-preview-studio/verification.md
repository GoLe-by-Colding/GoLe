# 검증 — 2026-10-26

## 구현 기준선

- `e03c8343`: API·core에 `side-brick-mellow`를 등록하고 적용·감사 기록을 검사함.
- `e1cded39`: 작은 눈·짧은 미소·이어진 꼬리의 옆모습 SVG와 제작 근거를 추가함.
- `3b4ff82d`: 상단 비교 화면, 일반/히어로 전환, 모바일 배치, 상태·확인 경계를 정리함.
- 이전 13종과 기본 `side-brick-soft`를 유지함. 이번 작업은 새 후보 추가이며 운영 설정 게시와 정적 앱 아이콘 교체를 포함하지 않음.

## 자동 검사

| 검사 | 결과와 실행 범위 |
| --- | --- |
| web format → lint → typecheck → fsd → build | 모두 통과. 빌드는 `NEXT_DIST_DIR=.next/mascot-verification`으로 사용자 개발 서버와 분리함 |
| core format → typecheck → check:platform | 모두 통과 |
| `pnpm brand:icons --check` | 정적 아이콘 2개 경로 일치 |
| API `spotlessCheck`와 디자인 도메인 테스트 | 25건 실제 실행, 실패·스킵 0 |
| API `cleanTest test` | 총 1,735건, 실패 0, 스킵 2, 실행 1,733건 |
| `cleanIntegrationTest integrationTest --tests '*.MascotPersistenceIntegrationTest'` | Testcontainers 기반 영속성·동시 적용 2건 통과, 스킵 0 |
| `admin-mascot.spec.ts` | 최종 문구 수정 후 16건 통과, 실패·스킵·재시도 0 |
| `admin-console.spec.ts` 마스코트 연동 2건 | 새 후보 적용 후 헤더 변경과 탭 갱신 통과, 실패·스킵·재시도 0 |

API 단위 테스트 스킵 2건은 기존 `HeifImageProcessorTest.realDecoderRejectsCorruptAndPixelBomb`와 `realHeicRotatesAndRemovesExifGpsThroughCommonUploadPipeline`이다. Gradle은 `cleanTest` 및 `cleanIntegrationTest`로 실제 재실행했다.

E2E 쓰기 검증은 별도 브라우저 컨텍스트에서 인증·관리 API를 모사한 계약 검사다. 기존 로컬 API에 새 후보를 실제로 게시한 결과와 혼동하지 않는다. 실제 API를 쓰는 콘솔 테스트의 프리셋 기대값도 공통 `MASCOT_PRESET_KEYS`와 일치하도록 고쳤으며 전체 백엔드 E2E는 PR CI에서 확인한다.

## 실제 Orca 화면

- 기존 WEB3000·API8090의 실제 관리자 세션으로 읽기·미리보기만 확인함. 세션 정보와 토큰을 기록하지 않음.
- 기본 후보 14종을 차례로 선택해 그림과 이름이 바뀌는 것을 확인함. 현재 적용본 `side-brick-soft`, 변경 버전 5는 유지됨.
- 320·375·414·768·1440px에서 문서 가로 넘침 0, 제목 한 줄, 큰 그림과 14종 카드 그림이 프레임 안에 들어옴.
- focus/Enter로 이전·다음 후보와 히어로 전환을 확인함. 후보 변경 시 검토 체크가 풀리고 조작 버튼 초점은 유지됨.
- 히어로 모드는 큰 그림의 분수만 표시하며 헤더·푸터의 작은 마크에는 영향을 주지 않음.
- worker 전용 탭에서 조회 GET 하나를 503으로 모사한 뒤 원래 fetch를 복구하고 실제 조회 재시도를 확인함. 실제 서버 장애를 발생시킨 검증은 아님.
- 중복 안내를 줄이고 SUITE/SUIT·간격·정렬을 유지함. 장식용 통계·영문 헤드라인·불필요한 애니메이션을 추가하지 않음.

큰 화면과 모바일 스크린샷을 직접 열어 확인했다. root 최종 화면은 `/tmp/gole-mascot-studio-1440-final.png`, worker 다섯 폭은 `/tmp/gole-mascot-ui-final-{폭}.png`에 남겼다. 이 임시 파일은 개발자 로컬 근거이며 배포 자산이 아니다.

## 코드 리뷰

`codex review --base origin/dev`로 최종 UI 커밋 `3b4ff82d`까지 검토했다. 명확한 회귀 결함을 발견하지 못했다는 결과를 받았다. 리뷰어도 웹·코어 타입 검사와 변경 파일 ESLint/Prettier·FSD를 실행했다. 리뷰어 자체의 Java 탐지·Orca 접근은 샌드박스에서 제한됐으며, 위 백엔드·실제 화면 근거는 감독자가 별도로 실행한 결과다.

## 확인 범위 밖

- 이미 실행 중인 API8090은 이번 새 키가 추가되기 전 프로세스다. 사용자 서버를 재시작하거나 실제 로컬/운영 마스코트를 게시하지 않았다.
- 전체 API 통합·전체 백엔드 E2E의 결과는 PR CI가 정본이다. iOS/Android 실기기, 스토어 아이콘, 운영 업로드·삭제·게시를 검증했다고 보고하지 않는다.
- 새 서버·워크트리를 만들지 않았다. 사용자 WEB3000·API8090·Metro8082와 공유 체크아웃을 보존한다.
- 소스 dev 병합·원격 확인과 볼트 커밋은 PR 및 `GoLe-obsidian/10_개발일지/승찬/2026-10-26/2026-10-26.md`에 확정 기록한다.
