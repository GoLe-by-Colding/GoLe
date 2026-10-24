# GCP 결제 이전 설계

## 대상과 전환

- 운영 프로젝트: project-72a52bf1-06aa-4519-b2c (336721527881).
- 구 결제: 01B490-1BC53A-33E611. 연결 프로젝트 1개, 약정 구매 목록 0개 확인.
- 신 결제: 013E1F-1D7BF5-E66D50. coding.ai.dev@gmail.com이 billing.admin이며 무료체험 크레딧 407626 KRW / 2027-01-23 만료 표시를 확인함.
- 서버 gole-production / e2-standard-2 / 100 GiB pd-balanced / asia-northeast3-a / 35.216.80.123을 유지함. VM 생성·삭제·DNS 변경은 이 방식에 필요하지 않음.
- 기존 운영자에는 새 결제의 billing.user + billing.costsManager만 추가하여 프로젝트 연결·예산 관리를 수행함. 신규 계정 소유자 등록은 콘솔의 외부 소유자 초대 절차를 따름. 기존 팀원 권한을 삭제하지 않음.

## 안전 순서

1. 실제 호스트 구성·실행 상태·논리 백업 checksum·구/신 결제 상태와 프로젝트 연결을 기록함.
2. 새 결제 예산과 기존 Pub/Sub 전달 권한을 준비함.
3. 기존 프로젝트의 billingAccountName을 새 계정으로 변경하고 enabled·서비스·IP·TLS를 확인함. 실패하면 기존 활성 결제를 유지하며, 변경 후 장애면 기존 계정이 열려 있는 동안 연결을 되돌림.
4. 새 결제 기간의 비용 정책은 소스·회귀 테스트·성공한 main CI를 거친 정확한 SHA로 적용함. 과거 root state를 보존하며 새 기간 분리를 검증함.
5. 알림 경로·운영 정상과 구 계정의 연결 프로젝트 0개를 확인한 후 구 결제를 닫음.

## 현재 확인된 적용 정책

운영 bootstrap 5cf0f18bd02eb02814695d15e7e2873a58caa7ab, 배포 e5419542f80bda12bb636e09b02515584dc30fad. 실제 broker 절대 정지 2026-10-28T01:50:00+09:00 / deadline 2026-10-28T23:59:59+09:00. 이 계약은 아직 변경하지 않았으며 단순 결제 연결 변경으로 연장되지 않음.

## 2026-10-24 실행 근거

- 22:55 KST 전후 gcloud billing projects link 성공. 재조회에서 billingEnabled=true / billingAccounts/013E1F-1D7BF5-E66D50 확인. 구 결제의 연결 프로젝트 목록은 빈 배열.
- 새 예산 e987694c-e6bc-48ed-90fa-942a74e56e04 생성. 370000 KRW / gross / 2026-10-24~2027-01-22 / 기존 gole-billing-budget topic 및 Google publisher IAM 유지.
- 운영 컨테이너 8개 healthy, root broker/watchdog active, 공개 health UP, apex/www DNS 35.216.80.123, www HTTPS 301 확인.
- 신규 계정 owner 초대 수락 뒤 해당 계정의 getIamPolicy로 신규·기존·팀원 owner 4개 확인.
- 23:05 KST 기존 결제 폐쇄 후 open=false와 연결 프로젝트 0개를 API로 확인함. 새 비용 기간 호스트 정책 적용은 아직 미완료. 실제 청구/크레딧 차감 내역은 게시 지연으로 미확인.

## 새 비용 가드 계약 (구현·검증 중)

- 새 기간은 2026-10-24 00:00 KST부터 계산한다. 실제 결제 전환보다 약 23시간 앞서므로 전환일 로컬 추정은 보수적이다.
- 신규 계정/프로젝트/예산 ID를 정확히 고정하고 기존 September arm도 이전 배포 검증용으로 유지한다. 임의 계정 변경으로 기간을 초기화할 수 없다.
- 신규 root state는 `state-013E1F-1D7BF5-E66D50-2026-10-24.json`이다. 최초 생성 시 이전 `state.json`의 유효한 네트워크 누계를 이어받으며 구 비용·경고 원본은 변경하지 않는다. 새 파일 재시작은 새 비용을 유지하고 손상 파일은 초기화하지 않는다.
- 기존 원가 상한을 유지하고 수동 snapshot 2개를 각각 100 GiB full-copy로 계산한다. 실제 incremental storage 약 31 GiB와 달리 scheduled 3개+manual 2개 전체 500 GiB를 예약하는 보수 상한이다.
- 절대 정지 2026-12-03 00:00 KST / 최대 960h / all-in 340000 KRW / Billing 320000 KRW / 네트워크 30 GiB 중 먼저 닿으면 정지한다. 런타임 경고는 840h·936h다. 90일 운영을 보장하지 않는다.
- 비용 tail은 2027-01-22 00:00 KST까지 계산한다. 콘솔의 실제 만료일 2027-01-23보다 앞선 운영 대응 시각이며, relay의 date-only 표시 `2027-01-21`은 같은 경계 1µs 전이다.
- 960h·네트워크30GiB·수동2개·정지 뒤 disk/IP/manual 유지 및 scheduled72h tail을 모두 포함한 VAT10% 상한이 34만원 미만인지 회귀 검증한다. 잔여 약67626원은 가격변동/추가SKU/보고지연 여유다.
- 현재 확인된 Catalog 단가는 기존 상한보다 낮지만 할인/스냅샷 압축/무료 IP 시간을 크레딧 연장 근거로 쓰지 않는다. 정상 사용료가 게시되면 별도 검토로 비용 추정을 조정할 수 있으며 자동 연장은 하지 않는다.
- Terraform의 budget address는 기존 state를 비밀 출력 없이 백업한 후 새 계정 Budget을 import한다. 삭제/재생성 0건 plan 검토 후 VM metadata만 갱신한다. 데이터·IP·기존 owner를 제거하지 않는다.

## 배포 순서와 복구 (독립 리뷰 반영)

구 운영 Compose는 high/transition/manual 값을 상수로 가지므로 신규 infra.env를 먼저 적용하면 LKG 검증이 실패한다. 먼저 신규 main SHA를 구 비용 arm으로 CD해 deployed.sha와 새 Compose/relay 이미지를 확정한다. 구 helper 호환 문제가 있으면 신규 main SHA bootstrap을 구 arm으로 먼저 적용한다. 그 다음 같은 SHA bootstrap을 신규 arm으로 실행하고 변수 mirror·동일 SHA CD를 적용한다. 최초 신규 arm은 deployed.sha 불일치 시 quiesce 전에 거부한다. 신규 기간 초기화 영수증을 먼저 fsync해 새 state 삭제/초기 쓰기 실패를 기간 재시작으로 처리하지 않는다.
