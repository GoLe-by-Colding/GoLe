# GoLe GCP 비용 가드

Cloud Billing Budget Pub/Sub 알림을 운영 Discord로 전달하고, Billing 보고 지연과
별개인 전용 스레드에서 호스트 가동시간·송신량을 10초 주기로 계산해
`gole-production` VM을 자동 정지하는 표준 라이브러리 전용 Python 서비스다.
상태와 정지 잠금은 `/state`에 원자적으로 저장한다.

Cloud Billing Budget은 비용 상한이 아니고 실제비용 알림도 지연될 수 있다. 따라서
다음 중 하나라도 먼저 충족하면 Compute Engine Stop API를 호출한다.

- 유효한 현재 Budget 실제비용이 `320,000원`에 도달함
- 고정비·송신비·정지 후 디스크/IP·VAT를 합친 추정액이 `350,000원`에 도달함
- 호스트 송신량이 `30 GiB`에 도달함
- 가드 경과시간이 `1,320시간`에 도달함
- `2026-10-26T19:50:00+09:00`에 도달함
- 호스트 비용 계측 파일을 읽지 못해 비용을 안전하게 계산할 수 없음

정지 조건은 Discord보다 먼저 실행된다. Webhook이 느리거나 실패해도 정지를
막지 않는다. 한 번 정지한 `ARM_ID`는 영구 잠금되므로 VM을 수동으로 다시 켜도
최대 5분 안에 다시 정지한다.

Budget `costAmount`는 게시된 세금이 포함될 수 있는 값으로 그대로 사용한다.
로컬 CPU·RAM·disk·송신 모델만 세전 단가에 VAT를 적용해 세금을 이중 계산하지
않는다. 상태 볼륨이 손상되거나 쓰기 불가능해도 컨테이너만 종료하지 않고 VM
정지를 먼저 시도하며, Compose healthcheck가 35초 이내 heartbeat를 확인한다.
호스트 systemd watchdog은 30초 간격의 검사에서 두 번 연속 health 실패를 확인하면
컨테이너 밖에서 VM을 종료하므로 Python thread 정지와 bind mount 실패도 fail-closed다.

## 운영 환경변수

배포 워크플로가 다음 값을 Compose에 전달한다.

| 변수 | 현재 값 | 용도 |
|---|---:|---|
| `GCP_BUDGET_PUBSUB_SUBSCRIPTION` | `gole-billing-budget-discord` | Budget pull subscription |
| `GCP_PROJECT_ID` | `project-72a52bf1-06aa-4519-b2c` | 정지할 VM의 프로젝트 |
| `GCP_CREDIT_AMOUNT_KRW` | `395600.60` | 전체 크레딧 |
| `GCP_CREDIT_DEADLINE` | `2026-10-28` | 크레딧 만료일 |
| `GCP_FIXED_HOURLY_COST_KRW` | `231.249894200` | CPU·RAM·100 GiB pd-balanced 세전 시간당 고정비 |
| `GCP_HARD_STOP_BILLING_COST_KRW` | `320000` | 지연된 Billing 실제비용 보조 정지선 |
| `GCP_HARD_STOP_ALL_IN_COST_KRW` | `350000` | VAT 포함 보수 추정 정지선 |
| `GCP_HARD_STOP_MIN_RESERVE_KRW` | `75000` | Billing 정지선의 최소 크레딧 여유 |
| `GCP_HARD_STOP_NETWORK_GIB` | `30` | 누적 호스트 송신량 정지선 |
| `GCP_HARD_STOP_MAX_RUNTIME_HOURS` | `1320` | 경과시간 정지선 |
| `GCP_HARD_STOP_AT` | `2026-10-26T19:50:00+09:00` | 절대 정지 시각 |
| `GCP_HARD_STOP_ARM_ID` | `2026-09-credit-v1` | 현재 크레딧 기간의 영속 잠금 ID |
| `GCP_HARD_STOP_BUDGET_ID` | 현재 Budget UUID | 허용할 Budget 식별자 |
| `GCP_HARD_STOP_BILLING_ACCOUNT_ID` | 현재 결제 계정 ID | 허용할 결제 계정 식별자 |
| `DISCORD_OPERATIONS_WEBHOOK_URL` | secret | 운영 채널 Webhook |

코드는 Budget 메시지의 `budgetId`, `billingAccountId`, `schemaVersion`, 표시명,
통화, 금액, 기간 시작일을 모두 확인한다. 이전 크레딧 기간이나 다른 Budget의
중복·역순 메시지는 알림 및 정지 계산에 반영하지 않고 ACK한다.

비용 모델 기본값은 VAT `10%`, 외부 송신 최고 단가
`318.154399937원/GiB`, 정지 후 디스크·미사용 고정 IP
`45.725088879원/시간`이다. 송신량은 호스트 `ens4`의 부팅 후 전체 TX 바이트를
포함해 실제 목적지와 관계없이 최고 단가로 계산한다.

## 최소 권한

VM에는 키 없는 전용 서비스 계정을 연결하고 OAuth scope는 `cloud-platform`으로
둔다. IAM 권한은 리소스별로 다음 두 개만 부여한다.

- 해당 subscription의 `pubsub.subscriptions.consume`
- 해당 `gole-production` 인스턴스의 `compute.instances.stop`

최초 GTS 인증서 EAB 생성 권한은 발급할 때만 임시 부여하고 발급 직후 제거한다.

## 계정·프로젝트 이전

새 크레딧으로 이전할 때는 가격과 Budget을 다시 조회한 뒤 GitHub repository
variables의 프로젝트, 결제 계정, Budget ID, 기간, VM 시작 시각, 종료 시각을
교체한다. 마지막으로 이전과 다른 `GCP_HARD_STOP_ARM_ID`를 지정한다. 기존 ID를
재사용하면 의도적으로 이전 정지 잠금이 유지된다.

정지한 뒤에도 디스크와 미사용 고정 IP는 과금될 수 있으므로 데이터 이전을
확인한 뒤 이전 프로젝트의 디스크 삭제와 IP 해제를 별도로 완료해야 한다.

## 로컬 검증

```bash
python3 -m unittest discover -s tests -v
docker build -t gole-budget-relay .
```

상태 저장은 Discord 성공 후 Pub/Sub ACK보다 먼저 수행해 재전송을 중복 억제한다.
Discord가 응답한 직후 프로세스가 종료되는 극히 짧은 구간에는 Webhook 특성상
알림 한 건이 중복될 수 있다.
