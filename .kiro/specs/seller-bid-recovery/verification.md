# 검증 기록 — 2026-10-25

## 재현과 수정

기준선은 `origin/dev d6a6eb4f`, 작업 브랜치는 `fix/seller-bid-recovery`다. 감독자에게 웹 두 파일·스펙·Git 전역 작업권을 인수한 뒤 SDD와 회귀 테스트를 먼저 추가했다.

- 최초 신규 8건: **6 실패·2 통과·0 스킵·0 재시도**. 최초 조회 오류 숨김, 판매 409 뒤 재조회 503 시 오류 소실, 재조회 중 지난 가격 버튼 잔존, 빈 입찰 재시도 없음, 320px 구획 31px 넘침, 768px 행동 높이 36px을 확인했다. `/tmp/gole-seller-bid-red`.
- 상태 분리 후 8건이 통과했으나 Orca 실제 320px 화면에서 버튼 안의 긴 문구 잘림을 추가 발견했다. Range 텍스트 경계를 검사하자 왼쪽 여백 **-11.609375px**로 실패했다. `/tmp/gole-seller-bid-text-red`.
- 텍스트 span의 줄바꿈과 버튼 자동 높이를 적용한 뒤 강화한 8건이 **8 통과·0 실패·0 스킵·0 재시도**, 5.1초에 끝났다. `/tmp/gole-seller-bid-final`.

## 검증 환경

최종 코드의 `buy-bids.spec.ts` 전체를 `E2E_WEB_PORT=3003`으로 실행해 **19 통과·0 실패·0 스킵·0 재시도**, 14.9초를 확인했다. 신규 8건과 기존 11건을 함께 실행한 결과이며 실제 입찰 생성→매물 등록→원자적 가격 인상·수락 제안 생성까지 포함한다. `/tmp/gole-seller-bid-complete`.

웹 `format:check → lint → typecheck → fsd:lint → build`도 최종 코드에서 통과했다. 빌드는 결제 disabled와 별도 캐시를 사용했고, Next가 tsconfig에 자동 추가한 이번 캐시 경로는 커밋에서 제외했다. 빌드 로그는 `/tmp/gole-seller-bid-build-final.log`다. Java/core는 변경하지 않아 로컬에서 재실행하지 않았다.

`codex review --uncommitted`는 최신 웹 변경을 확인했고 지적은 기존 미추적 `.codex/`의 개인 훅 경로 2건뿐이었다. 이 파일은 다른 작업의 보존 대상이어서 이번 변경에 넣거나 수정하지 않았다. 정확한 커밋의 추가 리뷰·PR CI·dev 반영 결과는 PR과 볼트 일지에 남긴다.

- Orca 별도 WEB3003/API8093, Mongo `gole_e2e`, Redis DB15, MinIO `gole-e2e`, Next `.next/seller-bid-recovery` 및 `.next/seller-bid-build`를 사용했다.
- e2e 프로필·고정 시드 계정만 사용했다. PortOne·Discord·문의 알림 outbox·관심 태그 알림톡·인증 메일을 비활성화했다.
- 사용자 WEB3000/API8090과 `.codex/`는 보존했다. 3000/8090에서는 공개 매물 GET과 화면 읽기만 수행했다.
- API 응답 가로채기 테스트와 실제 입찰 API 쓰기 테스트를 구분했다. 첫 전체 파일 실행의 18 통과·1 스킵은 `E2E_BASE_URL`이 실제 쓰기를 막은 결과이며, `E2E_WEB_PORT=3003`으로 실제 쓰기 1건을 별도 실행해 통과했다.

## Orca 화면

별도 테스트 탭에서 실제 시드 판매자의 세트 매물과 실제 E2E 입찰을 조회했다. 화면 검수용 1억 원 입찰은 확인 뒤 DELETE 204로 취소했다. 이 입찰로 판매를 수락하거나 결제를 실행하지 않았다.

| 너비 | 페이지·구획 가로 넘침 | 주 행동 높이 |
| --- | --- | --- |
| 320px | 0px / 0px | 62.5px, 문구 두 줄 |
| 375px | 0px / 0px | 44px |
| 414px | 0px / 0px | 44px |
| 768px | 0px / 0px | 44px |

- 화면 근거: `/tmp/gole-seller-orca-320-corrected.png`, `/tmp/gole-seller-orca-768.png`.
- Orca 탭을 전면에 표시하기 전 캡처는 빈 이미지가 나와 근거로 쓰지 않았다. `tab switch --focus` 이후 화면을 다시 캡처해 확인했다.
- 키보드 Enter로 확인 모달을 열고 취소하면 fill 요청이 없는 것은 4개 너비의 Playwright 회귀로 확인했다.

## 외부 경계

로컬 검증 종료 후 직접 만든 API8093·WEB3003 Orca 터미널과 검수 탭 2개를 닫았다. 3003/8093 리스너가 없고 사용자 WEB3000(node 53831)·API8090(java 32369)은 같은 PID로 유지됐다. 임시 웹 캐시의 tsconfig 변경을 제거한 뒤 `tsc --noEmit --incremental false`도 통과했다. 새 워크스페이스는 만들지 않았다.

실제 PG·운영 주문/입찰·외부 메시지·실기기 접근성은 검증하지 않았다. main·클라우드·OAuth 설정·결제와 공개 잠금은 변경하지 않았다. 웹 상세의 읽기 실패와 판매 행동/레이아웃만 변경하며 API/core 계약은 유지한다.
