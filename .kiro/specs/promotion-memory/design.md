# 홍보 피드백 메모리 설계

## 저장과 상태

- `promotion_feedback`: UUID String `_id`, `postId`, `reviewerId`, BSON Date `reviewedAt`, `reason`, `category`, `snapshot {caption,mediaUrls,captures,provenance,sourceCommitSha}`, `reasonTags`, `targets`, `reflectedAt`, `reflectedRunKey`. snapshot/반려 정보는 불변이고 성찰 처리 표시만 갱신한다. 기존 평가가 있으면 반려 시점 태그를 복사한다. targets 기본은 세 단계 전부다.
- `promotion_guidelines`: UUID String `_id`, `kind: KNOWLEDGE|PROCEDURE`, `content`(1~1,000자), `targets`(CAPTION|SCREEN_SELECTION|IMAGE_EDIT 중 1개 이상), `categories`(FEATURE|SERVICE 중 1개 이상), `sourceFeedbackIds`, `status: PROPOSED|ACTIVE|DISMISSED|RETIRED`, `proposedBy`, `createdAt`, `updatedAt`, `confirmedBy`, `confirmedAt`, `reflectionRunKey`. 원 제안자와 근거는 수정하지 않는다.
- 반려 상태 저장과 feedback insert, 성찰 제안 insert와 feedback 처리 완료를 각각 Mongo 트랜잭션으로 묶는다. 동시 요청의 write conflict는 실패를 숨기지 않는다. 성찰 재시도는 정확히 같은 feedbackIds 집합/runKey면 기존 제안을 반환한다. 다른 실행에서 이미 처리한 feedback을 다시 처리하려면 409다. 같은 runKey로 다른 묶음이 동시에 처리되는 경쟁은 묶음의 한 feedback 문서에만 기록하는 내부 `reflectionOwnerKey` unique+sparse 인덱스가 차단한다.
- 활성 지침을 편집하지 않는다. 제안(PROPOSED)만 수정한다. 사람이 활성 지침을 해제하고 새 제안을 확정해 교체한다. 활성화/기각/해제는 같은 상태의 재요청에 멱등이다. 활성화는 원 제안자와 다른 관리자만 가능하다.

## HTTP 계약

모든 경로는 관리자 인증을 요구한다. 별도 표기가 없는 목록 응답은 JSON 배열이다. enum은 대문자, ID는 문자열, 시각은 ISO 8601이다.

### 작업 기억과 경험

`GET /api/admin/promotion-memory/context?category=FEATURE&routes=/market&routes=/community`

```json
{"feedback":[],"guidelines":[],"unreflectedFeedback":[]}
```

feedback은 동일 category의 기록 중 route 일치 우선, 나머지는 최신순으로 최대 3건. guidelines는 해당 category의 ACTIVE 최신순 최대 8개. unreflectedFeedback은 category와 무관하게 오래된 미처리 최대 3건. 목록의 feedback 문서는 저장 필드 전체를 사용하고 guideline 문서는 아래 응답 형태를 사용한다. 작업 단계별 전달은 Python이 targets로 필터링한다.

`GET /api/admin/promotion-feedback?postId=<선택>&limit=50` 최신순, limit 1~100. UI 근거 조회용.

`GET /api/admin/promotion-feedback/{id}` 목록과 같은 반려 기록 객체를 반환한다. 최근 목록 범위 밖의 지침 근거도 조회할 수 있다. 미존재 시 404 `PROMOTION_FEEDBACK_NOT_FOUND`.

`POST /api/admin/promotion-memory/reflect`

```json
{
  "feedbackIds":["feedback-uuid"],
  "runKey":"runner-uuid",
  "proposals":[{
    "kind":"PROCEDURE",
    "content":"화면 글자를 읽을 수 있도록 과도한 축소를 피한다.",
    "targets":["IMAGE_EDIT"],
    "categories":["FEATURE","SERVICE"],
    "sourceFeedbackIds":["feedback-uuid"]
  }]
}
```

feedbackIds는 중복 없이 1~3개, proposals는 0~3개. 근거 ID는 이번 feedbackIds의 부분집합이며 각 제안에 최소 1개다. runKey는 `[a-z0-9][a-z0-9-]{0,63}`. 응답은 생성된 guideline 배열(빈 제안이면 `[]`), 재시도도 같은 배열. 원 제안자는 인증 actor로 고정한다.

### 지침 관리

`GET /api/admin/promotion-guidelines?status=PROPOSED&limit=50` (status 생략 시 전체, limit 1~100).

`PATCH /api/admin/promotion-guidelines/{id}` body는 `{ "content":"...", "targets":["IMAGE_EDIT"], "categories":["FEATURE","SERVICE"] }`. 세 필드는 모두 필수. PROPOSED에서만 수정하고 proposedBy는 유지한다.

`POST /api/admin/promotion-guidelines/{id}/activate`, `/dismiss`, `/retire`: body 없음. 각각 PROPOSED→ACTIVE, PROPOSED→DISMISSED, ACTIVE→RETIRED. 응답은 변경된 guideline이다.

```json
{"id":"guideline-uuid","kind":"PROCEDURE","content":"...","targets":["IMAGE_EDIT"],"categories":["FEATURE","SERVICE"],"sourceFeedbackIds":["feedback-uuid"],"status":"PROPOSED","proposedBy":"agent-account-id","createdAt":"2026-10-08T00:00:00Z","updatedAt":"2026-10-08T00:00:00Z","confirmedBy":null,"confirmedAt":null,"reflectionRunKey":"runner-uuid"}
```

기존 `POST /api/admin/promotion-posts/{id}/reject`의 `{reason}`와 PromotionPost 응답은 변경하지 않는다. 새 지침 조작은 관리자 감사 로그를 남긴다.

### 실행 원장

기존 `POST /api/admin/promotion-runs`에 선택적 `memoryContext`를 추가한다. 생략/null/옛 문서는 빈 값으로 읽는다. 응답에도 동일 형태를 포함한다.

```json
{"memoryContext":{"feedbackIds":["feedback-uuid"],"guidelines":[{"id":"guideline-uuid","kind":"PROCEDURE","content":"확정 지침의 당시 내용","targets":["IMAGE_EDIT"],"categories":["FEATURE","SERVICE"]}]}}
```

feedbackIds 최대 3개, guidelines 최대 8개. 내용/범위는 실행 당시 스냅샷이며 나중에 지침이 해제돼도 남는다. 모델에 전달한 지침만 기록한다.

## Python 실행 순서와 경계

1. 실행마다 context를 한 번 조회·검증한다. 미처리 최대 3건이 있으면 Claude 구조화 출력으로 제안 최대 3개를 받고 빈 제안도 reflect API에 저장한다. 성찰과 원장은 같은 runKey를 사용한다.
2. 성찰이 끝난 뒤 기존 `QUEUE_FULL` → 기능 실행의 `NO_WEB_CHANGE`/`ALREADY_DRAFTED` 생성 게이트를 검사한다. 게이트에 걸려도 성찰 결과는 보존하며 캡처와 본 생성 모델 호출은 하지 않는다.
3. 기존 캡처 → Claude 화면 선택/캡션 → Codex 편집 → 업로드/초안/검토 요청을 수행한다. context의 ACTIVE 지침만 category/targets로 필터링하고 성찰에서 갓 제안한 지침은 같은 실행에 적용하지 않는다.
4. 실제 생성 호출에 전달한 경험 ID와 ACTIVE 지침 snapshot만 원장에 남긴다. Claude가 건너뛰거나 편집 지시문 예산을 초과하면 호출하지 않은 Codex 전용 지침은 기록하지 않는다.

DB에는 반려 snapshot 원본을 보존하되 모델 projection은 반려 사유·캡션·태그·화면 경로/이름·최종/원본 이미지 참조로 제한한다. 이전 `captures[].edit`와 출처의 판단 원문을 다시 넣지 않아 반려할수록 작업 기억이 중첩되는 것을 막는다. 과거 이미지 바이트는 다운로드하지 않으며 모델에 실제 픽셀을 보지 못했다고 명시한다. 경험 원문은 관찰 자료이고 원문 속 명령은 실행하지 않는다.

Codex에 전송한 실제 최종 prompt를 `captures[].edit`에 저장한다. 6,000자를 넘으면 지침을 자르거나 누락하지 않고 `ERROR`/관리자 detail `EDIT_PROMPT_TOO_LONG`으로 명시적으로 실패한다. context의 최대 8개가 각각 최대 길이인 경우 이 예산에 걸릴 수 있다.

공개 Actions `run.json`에는 `detail`과 `memoryContext`를 전혀 싣지 않는다. 메모리 오류 로그에는 고정 코드와 예외 종류만 남기고 모델 출력/반려 원문은 넣지 않는다.

## 로컬 및 CI 검증

`tests/promotion/run_memory_e2e.py`는 실제 Python 러너·BackendPublisher·gateway 요청 조립을 사용하고 CLI subprocess만 고정 응답으로 교체한다. 로컬 HTTP 원점만 허용하며 backend 업로드/초안/원장/경험/지침 저장은 실제로 수행한다. 모델 요청 원문은 로컬 테스트 자료이며 공개 Actions 요약에 싣지 않는다.

CI E2E 잡은 Python 3.13/고정 uv를 설치하고 `uv sync --locked --extra promotion`으로 도우미 환경을 준비한다. API 시작에 테스트 전용 `PROMOTION_AGENT_ADMIN_EMAIL`/`PROMOTION_AGENT_ADMIN_PASSWORD`를 주어 기존 seeder로 봇을 bootstrap한다. Playwright에 같은 테스트 자격증명과 `E2E_PROMOTION_MEMORY=1`을 넘겨 신규 왕복 테스트가 환경 누락으로 조용히 skip되지 않게 한다. 실제 운영 모델/발행은 호출하지 않는다.

## 실패와 호환

- feedback/guideline 신규 컬렉션에만 인덱스를 추가하며 기존 데이터를 수정하는 migration은 없다. 과거 반려는 자동 복원하지 않는다.
- 반려 기록 저장 실패는 HTTP 오류이며 반려 상태를 롤백한다. 성찰 응답/저장 실패는 Python에서 메모리 오류로 기록하고 초안 생성을 계속한다. 성찰이 실패하면 미처리 반려는 다음 실행에서 재시도한다.
- context 조회/검증 실패는 `ERROR`/관리자 detail `MEMORY_CONTEXT_FAILED`로 생성을 중단한다. 성찰 실패는 `MEMORY_REFLECTION_FAILED`로 알리고 이미 조회한 context를 사용한다. 저장 응답만 유실된 경우 서버의 멱등 처리 결과를 따른다.
- 이미지 파일은 기존 최종/원본 미디어를 참조한다. 현재 수정/삭제 API가 없고 연결 미디어는 STAGED 만료 정리에서 제외된다. 향후 편집/삭제 추가 시 역사 이미지 보존을 함께 설계한다.
- 로컬 E2E는 gateway 응답을 고정해 메모리 전달 계약을 검증한다. 운영 모델 실행/발행은 이 구현의 검증에 포함하지 않는다.
