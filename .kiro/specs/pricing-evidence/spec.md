# 검증된 체결가와 시세 콜드스타트

## 문제

시세는 플랫폼 결제가 완료된 실제 거래만 근거로 삼아야 한다. 데모 시드, 출처를 확인할 수 없는
과거 문서, 양측이 임의로 금액을 합의할 수 있는 직거래 완료가는 같은 차트에 섞지 않는다. 또한
표본이 적을 때 통계와 감가 모델을 확정값처럼 보여 주지 않는다.

## 공개 계약

- `PLATFORM_PAYMENT`: 주문 ID가 있는 플랫폼 결제 완료가. 공개 시세의 기본 증빙이다.
- `PLATFORM_TEST`: PortOne TEST 채널 또는 로컬·E2E 스텁 주문. 주문 ID가 있어도 실제 금전
  결제를 증명하지 않으므로 공개 집계에서 제외한다.
- `DIRECT_TRADE`: 대화방 등 참조가 있는 직거래 신고가. 저장할 수 있지만 기본 집계에서 제외한다.
- `DEMO_SEED`: 로컬·E2E 전용 샘플. 공개 환경에서는 포함 설정 자체를 거부한다.
- `LEGACY_UNVERIFIED`: 출처가 없거나 알 수 없는 기존 문서. 자동으로 실거래로 승격하지 않는다.
- `PLATFORM_PAYMENT`, `PLATFORM_TEST`, `DIRECT_TRADE`는 비어 있지 않은 `sourceReference`가 필수다.

`GET /api/v1/pricing/sets/{setNumber}/snapshot`은 차트·밸류에이션과 동일한 **미개봉 체결**
모집단과 증빙 정책으로 다음 상태를 반환한다. 상태가 다른 중고 체결을 헤드라인 표본 수나
등락률에 섞지 않는다.

| 검증 표본 | state | 노출 |
|---:|---|---|
| 0 | `EMPTY` | 체결 전 안내, 거래가 쌓이는 방식 설명 |
| 1~2 | `OBSERVATIONS_ONLY` | 실제 관측 내역만 표시, 차트·등락률·고저가·밸류에이션 숨김 |
| 3 이상 | `ESTABLISHED` | 통계·차트 표시. 밸류에이션은 미개봉 기준 표본도 3건 이상일 때만 표시 |

응답은 `sampleCount`, `minimumSamples`, 최신순 `observations`, nullable `statistics`·`valuation`,
`provenance`를 함께 주어 프론트가 서로 다른 API의 표본 수를 추측하지 않게 한다.

## 운영 안전

- production/staging은 데모·레거시 증빙 포함 플래그가 켜지면 기동을 거부한다.
- 시더는 컬렉션이 완전히 비었을 때만 `DEMO_SEED`를 추가하며 기존 체결을 삭제·수정하지 않는다.
- 기존 `source=null` 문서는 읽을 때 `LEGACY_UNVERIFIED`로 보존한다.
- 트렌딩과 세트별 시세는 같은 출처 정책을 사용한다. 캐시 키는 정책 변경 전 버전과 분리한다.

## 작업

- [x] P1 체결 문서에 source/sourceReference 추가 및 주문 ID 연결
- [x] P2 기본 시장 증빙을 플랫폼 결제로 제한하고 데모·레거시 공개 가드 추가
- [x] P3 0건 / 1~2건 / 3건 이상 snapshot 계약과 백엔드 테스트 추가
- [x] P4 트렌딩 집계와 세트별 조회에 동일한 증빙 정책 적용
- [x] F1 가격 탐색 화면을 snapshot 상태로 전환
- [x] F2 세트 상세의 시세 요약을 snapshot 상태로 전환
- [x] F3 기간 0~1건을 전체 기간 데이터로 되돌리는 폴백 제거
- [ ] F4 EMPTY·OBSERVATIONS_ONLY·ESTABLISHED·오류 상태 브라우저 검증
