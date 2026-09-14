# 회원 전용 브릭 필터 — 구현 및 인수인계

## 구현

- 웹 `/brick-filter` 및 desktop/mobile site-header 메뉴. 인물 미니피겨/사물 브릭 모드, 업로드, 원본 로컬 미리보기(브라우저가 HEIF를 지원하지 않으면 생략), 남은 횟수, 생성 진행, 실패 재시도, 불확정 응답 상태 재조회, 결과 표시/다운로드와 오늘 작업 목록.
- Java `brickfilter`의 application 서비스와 quota/blob/image/provider/gate ports, Mongo/web/HTTP adapters. order/payment/media 구현 파일은 수정하지 않았다.
- `@gole/core/brick-filter` subpath API. 공통 core barrel/manifest 변경 없음.
- media 공개 `NormalizeImageUseCase`를 호출한다. MIME sniff와 `ImageProcessorPort` 위임은 media 내부에 숨기며 파일명/클라이언트 MIME을 신뢰하지 않는다. 브릭 컨텍스트가 media 아웃바운드 포트나 HEIF 판별 구현을 직접 참조하지 않는다.
- 입력 4MiB, 6,000px/변, 12,000,000 pixels 상한과 공통 decoder 자체 상한 적용. 정규화 후 provider 입력은 긴 변 1024px 이하 PNG로 축소하며 메타데이터를 전달하지 않는다. Provider 응답은 전체 body timeout과 스트리밍 수신 8MiB 상한 후 이미지로 재검증/정규화한다.
- Python `gole_brick_filter`는 기존 support-agent 인접 독립 모듈이다. StateGraph `validate → generate → result`이고 기존 support 분류 그래프/gRPC server/entrypoint는 바꾸지 않았다. 원본 상태 checkpoint와 tracing을 사용하지 않는다.
- OpenAI Python SDK 2.54.0과 Pillow 12.3.0을 지원 에이전트 pyproject/uv.lock에 추가했다. pnpm 재설치 없음.

## API 계약

모든 경로는 `/api/v1/brick-filter` 아래에 있다. 기존 UserAuthInterceptor가 해석한 account attribute만 사용하고, GET도 controller에서 인증 attribute를 필수 검사한다. client userId/header로 소유자를 선택할 수 없다.

| 메서드/경로 | 계약 |
|---|---|
| GET `/quota` | 서울 날짜, 남은 예약 가능 횟수, limit=3, enabled, retryAvailable, 다음 자정 |
| GET `/jobs` | 현재 서버 세션 소유자의 오늘 작업 목록 |
| POST `/jobs` | multipart image/mode, `Idempotency-Key: YYYY-MM-DD_UUID` |
| GET `/jobs/{id}` | 소유자의 작업 상태 RESERVED/SUCCEEDED/FAILED |
| GET `/jobs/{id}/result` | 소유자의 성공 결과 PNG bytes; no-store, nosniff |

원본/결과는 공개 media endpoint나 외부 URL로 노출하지 않는다. Mongo `brick_filter_blobs` 문서는 최대 8MiB bytes여서 16MiB BSON 한도보다 작고, source/result 각각 owner+job+kind로 분리한다. 원본은 정규화된 이미지이며 처리 직후 삭제; crash 시 5분 lease 만료 cleanup 및 TTL index로 삭제한다. 결과는 요청 시작부터 24시간 보관하며 조회 시 만료를 즉시 차단한다. TTL 실제 삭제는 Mongo TTL monitor 주기에 따라 지연될 수 있다.

## quota / idempotency / recovery

- `brick_filter_days`의 사용자+서울 날짜 단일 문서에서 `occupied<3`, 중복 job 부재, attempts<30을 한 atomic update로 검사하고 occupied/attempts 증가+job 추가를 함께 수행한다. 두 모드의 성공+예약이 합산된다.
- 같은 키와 같은 image digest/mode는 기존 상태만 반환한다. 같은 키의 다른 이미지/모드는 409. FAILED tombstone도 재생성하지 않는다.
- 성공은 `RESERVED && leaseUntil > now` CAS로만 확정한다. 실패는 RESERVED→FAILED와 occupied 감소를 단일 update로 수행하므로 이중 환급되지 않는다.
- 프로세스 중단/예외는 5분 lease와 정기 cleanup/read recovery로 복구한다. 만료 후 늦은 provider 성공은 확정 불가하고 결과를 삭제한다. DB 성공 후 응답 유실 때 SUCCEEDED 결과를 삭제하지 않는다.
- tombstone은 8일 TTL. 과거 날짜의 새 키는 거부하므로 TTL 이후 동일 과거 키도 provider 재호출 불가다. 서울 자정 통과 후 정규화가 끝나면 예약 전에 날짜를 재검사한다.
- 실패 재시도는 사용자가 새 키로 명시 실행한다. 응답 유실은 기존 키의 상태만 조회하고, 404일 때에만 같은 키 재전송 UI를 제공한다. Provider SDK retry=0, Java redirect 없음, 자동 재생성 없음.
- 사용자당 일일 예약 시도 30회 한도는 실패 반복으로 ledger가 무한 증가하는 것을 방지한다. `retryAvailable=false`일 때 UI에서 별도 안내하고 생성 차단한다.
- Mongo global provider gate: 모든 Java 인스턴스 합산 동시 예약 2개, 분당 6개, 서울 일별 100개. 실패/불확정 요청도 provider 호출 예산은 환급하지 않는다. 만료 lease는 다음 acquire에서 정리한다. Java 로컬 처리 semaphore 4, Python provider 동시성 2를 추가 적용한다.
- 원격 provider가 timeout 후에도 내부 처리를 계속하는지는 확인할 수 없다. 시스템은 그 호출을 자동 재전송하지 않고 전역 호출 예산을 유지하며, 종료되지 않은 원격 처리의 실제 비용을 환급했다고 주장하지 않는다.

## SDK 공식 계약 확인

공식 [이미지 편집 API](https://developers.openai.com/api/reference/python/resources/images/methods/edit)와 설치된 SDK `openai/resources/images.py`를 확인했다. 고정 모드 프롬프트, `model=gpt-image-2`, 이미지 multipart, `n=1`, `size=1024x1024`, `quality=low`, `output_format=png`를 사용하며 base64 결과를 읽는다. URL-only/빈 응답은 실패다. 실제 SDK의 HTTP MockTransport 테스트로 `/v1/images/edits` 요청 형식과 단일 호출을 검증했다.

## 운영 활성 상태

**소스 구현 및 로컬 검증 완료, 유료 provider는 활성화/호출하지 않음.** 비밀 키를 읽거나 출력/저장하지 않았다. 운영 DB, 외부 메시지, commit/push, 서버 재시작/배포 없음.

Java 기본 `gole.brick-filter.enabled=false`. 활성화에는 endpoint와 32자 이상의 internal-token을 명시해야 한다. endpoint는 HTTPS 또는 loopback HTTP만 허용한다. Python 별도 프로세스도 `BRICK_FILTER_PROVIDER_ENABLED=true`를 명시해야 실행된다. 토큰은 `BRICK_FILTER_INTERNAL_TOKEN`으로 공급하고 Java와 일치해야 하며 provider credential은 안전한 프로세스 환경으로만 공급한다. 사진이 포함된 LangSmith/LangChain tracing 활성화 상태에서는 server 시작을 거부한다.

개발/배포 담당자가 이후 활성화를 승인한 경우의 별도 Python entrypoint는 `PYTHONPATH=apps/support-agent/src ... python -m gole_brick_filter.server`이며 기본 bind는 127.0.0.1:50052다. 기존 support Docker entrypoint를 바꾸지 않았으므로 별도 프로세스/서비스 설정이 필요하다. 이 보고서는 유료 호출·프로세스 실행·배포 승인이 아니다.

브라우저 검증은 기존 web3010의 별도 Orca 탭에서 브릭 API에만 로컬 fetch mock을 설치했다. 실제 실행 API의 신규 endpoint 활성화나 실제 생성 품질/계정 모델 접근 권한은 검증하지 않았다.
