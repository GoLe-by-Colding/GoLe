# Discord 운영 라우팅

GoLe 운영 Discord는 비밀 URL을 코드에 저장하지 않고 역할별 webhook 환경변수로만
연결합니다. 모든 커스텀 webhook은 `https://gole.co.kr/icon.svg` 파비콘을 프로필로
사용하고 `allowed_mentions`를 비워 사용자·역할 멘션을 만들지 않습니다.

## 채널과 발신자

| 채널 | 발신자 | 환경변수 | 사건 |
| --- | --- | --- | --- |
| `github-활동` | GitHub 공식 앱 + CI webhook | `DISCORD_CI_WEBHOOK_URL` | push, PR, issue, Actions 결과 |
| `배포-장애` | 배포/헬스체크 + 애플리케이션 | `DISCORD_DEPLOY_WEBHOOK_URL`, `DISCORD_OPERATIONS_WEBHOOK_URL` | 배포 시작·완료·실패, readiness 장애, 서버 오류, 관리자 조치 |
| `가입-알림` | 애플리케이션 | `DISCORD_ACCOUNT_WEBHOOK_URL` | 신규 일반·소셜 가입 |
| `결제-알림` | 애플리케이션 | `DISCORD_PAYMENT_WEBHOOK_URL` | 결제 승인·실패·검토, 환불 접수·완료, PortOne 웹훅·재조정 오류 |

`GOLE_DISCORD_ALERTS_ENABLED=true`일 때 애플리케이션이 역할별 목적지를 검증합니다.
누락된 목적지가 있으면 운영 서버가 부팅되지 않으므로 잘못된 단일 채널 폴백을 조기에
발견할 수 있습니다.

## 알림 음소거

`DISCORD_SUPPRESS_NOTIFICATIONS=true`이면 메시지는 운영 기록으로 남지만 Discord 푸시
알림은 울리지 않습니다. 야간 검증이 끝난 뒤 알림을 다시 받을 때는 백엔드 런타임과
GitHub Actions repository variable을 모두 `false`로 바꿉니다.

## 안전한 확인 순서

1. GitHub repository secret 이름만 확인하고 URL 값은 터미널·로그에 출력하지 않습니다.
2. 백엔드는 로컬 HTTP 서버를 대상으로 역할 선택, 파비콘, 무멘션, 무음 플래그,
   rate-limit/5xx/timeout 재시도를 테스트합니다.
3. 야간에는 외부 webhook 호출과 저장소 push를 하지 않습니다.
4. 운영 시간에 채널별 한 건씩 무멘션 테스트를 보내고, Discord에서 발신 프로필과
   목적 채널을 확인합니다.
5. 확인 후 테스트 메시지는 운영 감사 정책에 따라 보존하거나 관리자가 직접 정리합니다.
