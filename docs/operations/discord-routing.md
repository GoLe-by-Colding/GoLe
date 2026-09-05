# Discord 운영 라우팅

GoLe 운영 Discord는 비밀 URL을 코드에 저장하지 않고 root-owned
`/etc/gole/discord.env` overlay로 연결합니다. CD/Secret Sync가 고정 형식 stdin으로만
전달하면 root helper가 exact Discord webhook URL을 검증해 0600으로 원자 설치합니다.
모든 커스텀 webhook은 `https://gole.co.kr/icon.svg` 파비콘을 프로필로 사용하고
`allowed_mentions`를 비워 사용자·역할 멘션을 만들지 않습니다.

## 채널과 발신자

| 채널 | 발신자 | 환경변수 | 사건 |
| --- | --- | --- | --- |
| `github-활동` | GitHub 공식 앱 + CI webhook | `DISCORD_CI_WEBHOOK_URL` | push, PR, issue, Actions 결과 |
| `배포-장애` | 배포/헬스체크 + 애플리케이션 | `DISCORD_DEPLOY_WEBHOOK_URL`, `DISCORD_OPERATIONS_WEBHOOK_URL` | 배포 시작·완료·실패, readiness 장애, 서버 오류, 관리자 조치 |
| `가입-알림` | 애플리케이션 | `DISCORD_ACCOUNT_WEBHOOK_URL` | 신규 일반·소셜 가입 |
| `결제-알림` | 애플리케이션 | `DISCORD_PAYMENT_WEBHOOK_URL` | 결제 승인·실패·검토, 환불 접수·완료, PortOne 웹훅·재조정 오류 |
| `문의-알림` | 애플리케이션 | `DISCORD_SUPPORT_WEBHOOK_URL` | 신규 운영 문의, 문의자의 후속 답변(본문·사용자 식별자 제외) |

`GOLE_DISCORD_ALERTS_ENABLED=true`일 때 operations/account/payment 목적지는 필수이며
누락되면 overlay 설치와 운영 부팅이 fail-closed됩니다. 사용자 요구대로 역할별 목적지는
같은 GoLe Discord room을 가리켜도 됩니다. deploy/support 전용 secret이 비어 있으면
operations webhook을 명시적으로 사용하고, 전용 secret이 생기면 다음 atomic refresh에서
자동 우선합니다. 그 외 임의 URL·목적지 누락·unknown key는 root helper가 거부합니다.

## 문의 알림 전달 보장

신규 문의와 문의자의 후속 답변은 문의 저장과 같은 MongoDB 트랜잭션에서
`support_notification_outbox`에 먼저 기록합니다. 원장에는 문의방·계정 ID, 제목, 본문,
이메일, 전화번호를 넣지 않고 무작위 이벤트 ID와 문의 유형·상태만 둡니다. Discord에는
이 이벤트 ID와 관리자 문의함 경로만 전달합니다.

worker는 원장을 lease로 한 건씩 선점하고 Discord webhook을 `wait=true`로 호출합니다.
Discord가 2xx로 수락한 뒤에만 완료 처리하며 429·5xx·전송 장애는 지수 backoff로
재시도합니다. 마지막 실패와 영구적인 4xx는 dead-letter로 남기고, 만료된 lease는 다른
worker가 회수합니다. Discord 수락 직후 프로세스가 종료되면 동일 이벤트 ID가 한 번 더
전송될 수 있습니다. 완료·dead-letter 영수증은 문의와 연결할 수 없는 값만 담고 기본 30일
뒤 MongoDB TTL로 정리하며 `GOLE_SUPPORT_NOTIFICATION_OUTBOX_TERMINAL_RETENTION`으로
운영 보존기간을 조정합니다.

webhook 설정 또는 Discord 장애를 복구한 뒤 dead-letter를 다시 보낼 때는 DB를 직접
수정하지 않습니다. ADMIN 세션으로
`POST /api/admin/support-notifications/{eventId}/requeue`를 호출하고 본문에 정확한
`confirmation: "REQUEUE:{eventId}"`와 아래 정형 `reasonCode` 중 하나를 보냅니다.

- `WEBHOOK_CONFIGURATION_RESTORED`
- `DISCORD_INCIDENT_RESOLVED`
- `MANUAL_DELIVERY_RETRY_APPROVED`

이 경로는 dead-letter 한 건만 원자적으로 `PENDING`으로 바꾸고 시도 횟수와 lease를
초기화합니다. 이미 대기·전송 중이면 멱등 응답을 반환하고, 이미 전달된 이벤트는 거부하며,
성공한 재큐잉만 비식별 이벤트 ID·정형 사유로 관리자 감사 로그에 남깁니다.

## 알림 음소거

`DISCORD_SUPPRESS_NOTIFICATIONS=true`이면 메시지는 운영 기록으로 남지만 Discord 푸시
알림은 울리지 않습니다. 야간 검증이 끝난 뒤 repository variable을 `false`로 바꾸고
CD 또는 Secret Sync를 실행해 root overlay와 백엔드 런타임을 함께 갱신합니다.

## 안전한 확인 순서

1. GitHub repository secret 이름만 확인하고 URL 값은 터미널·로그에 출력하지 않습니다.
2. 백엔드는 로컬 HTTP 서버를 대상으로 역할 선택, 파비콘, 무멘션, 무음 플래그,
   rate-limit/5xx/timeout 재시도를 테스트합니다.
3. 야간에는 외부 webhook 호출과 저장소 push를 하지 않습니다.
4. 운영 시간에 채널별 한 건씩 무멘션 테스트를 보내고, Discord에서 발신 프로필과
   목적 채널을 확인합니다.
5. 확인 후 테스트 메시지는 운영 감사 정책에 따라 보존하거나 관리자가 직접 정리합니다.
