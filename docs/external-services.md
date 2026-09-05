# 외부 서비스 대장

계정·프로젝트·식별자를 한 곳에 모은다. 어디를 열어야 하는지 찾는 데 쓴다.

> **시크릿은 여기 적지 않는다.** 값이 필요하면 운영 키 볼트(`control.kscold.com`)나
> 로컬 `.env` 를 본다. 이 문서에는 **공개해도 되는 식별자만** 둔다.
> 키 이름은 적어도 되고, 키 값은 적지 않는다.

---

## Firebase / Google Cloud

| 항목 | 값 |
|---|---|
| 프로젝트 ID | `gole-prod` |
| 프로젝트 번호 | `704790528027` |
| Android 앱 | `kr.gole.app` |
| iOS 앱 | `kr.gole.app` |
| 콘솔 | https://console.firebase.google.com/project/gole-prod |

**푸시 발송 계정**: `gole-fcm-sender@gole-prod.iam.gserviceaccount.com`
커스텀 역할 `goleFcmSender` 하나만 붙어 있고 권한은 `cloudmessaging.messages.create` 뿐이다.
Admin SDK 전체 권한(`firebase.sdkAdminServiceAgent`)을 주지 않으려고 만든 역할이다.

**API 키 제한** — 클라이언트 키는 앱 바이너리에 담겨 배포되는 공개 식별자다. 대신 좁혀 두었다.

| 키 | API 허용 | 앱 제한 |
|---|---|---|
| iOS | `fcmregistrations` · `firebase` · `firebaseinstallations` | 번들 ID `kr.gole.app` |
| Android | 같음 | **없음** — 서명 키스토어가 생기면 SHA-1 을 추가한다 |

Firebase Auth·Firestore·Storage 는 쓰지 않으므로 허용 목록에서 뺐다.
**이 목록에 없는 Firebase 기능을 새로 쓰려면 키 제한부터 넓혀야 한다.**

### 백엔드 환경변수

| 이름 | 없으면 |
|---|---|
| `FCM_ENABLED` | no-op 어댑터로 기동. 푸시만 닫히고 서비스는 정상 |
| `FCM_PROJECT_ID` | 위와 같음 |
| `FCM_CREDENTIALS_PATH` 또는 `FCM_CREDENTIALS_BASE64` | 위와 같음. 파일 경로가 우선 |

운영 키 볼트가 환경변수만 다루므로 서버에서는 base64 쪽을 쓴다.

---

## Apple Developer

| 항목 | 값 |
|---|---|
| 팀 ID | `BWRD8QZVDN` |
| App ID | `kr.gole.app` (Push Notifications 활성) |
| APNs 인증키 | `GoLe APNs Key` / Key ID `U5GSKYBD9V` |
| 키 구성 | Sandbox & Production · **Team Scoped** |

**Team Scoped 인 이유는 할당량이다.** 앱 하나로 제한하는 Topic Specific 은 단일 환경에서만
고를 수 있어 Sandbox·Production 각각 키가 필요한데, Apple 은 팀당 APNs 인증키를 **2개까지만**
허용하고 그중 하나는 이미 다른 앱이 쓰고 있다.
→ **이 키가 유출되면 같은 팀의 다른 앱까지 영향을 받는다.**

`.p8` 은 **발급 시 한 번만 내려받을 수 있다.** 저장소에는 없고(gitignore) Firebase 의
개발·프로덕션 슬롯에 업로드되어 있다.

**번들 ID 가 `com.gole.app` 이 아닌 이유**: Apple 번들 ID 는 전 세계에서 유일해야 하는데
`com.gole.app` 은 다른 개발자 계정이 이미 선점하고 있었다.

---

## PortOne (결제)

| 채널 | 유형 | PG | 상태 |
|---|---|---|---|
| `kscold-kakao` | TEST | KAKAOPAY | 사용 중 |
| `kscold-kg` | TEST | INICIS_V2 | 카드. 이니시스 공용 테스트 MID 라 실계약 없음 |
| (이름 없음) | LIVE | KAKAOPAY | 타 프로젝트 |

`PORTONE_CHANNEL_TYPE` 은 **전역 하나**다. 카드만 LIVE 로 열 수 없다.
콘솔: https://admin.portone.io

---

## 소셜 로그인

**아직 운영에 구성되어 있지 않다.** 운영 키 볼트에 `GOOGLE_OAUTH_CLIENT_ID`·`KAKAO_*`·`NAVER_*`
가 없어서 로그인 버튼이 비활성으로 나온다 — provider 는 client-id 가 비면 **조용히** 꺼진다.

앱에 붙이려면 백엔드 선행 작업이 있다. `OAuthProperties` 가 provider 당 client-id 를 하나만
갖는데 **Google 은 웹과 iOS/Android 의 client_id 가 서로 다르다.** 플랫폼별 등록을 추가해야 한다.
(`.kiro/specs/mobile-app/tasks.md` 8.0)

---

## 운영 인프라

| 항목 | 위치 |
|---|---|
| 배포 대상 | `gole.kscold.com` (`GOLE_ENVIRONMENT: staging`) |
| 운영 키 볼트 | https://control.kscold.com |
| 러너 | self-hosted (`gole-gcp-production`) |

CD 는 `vars.GOLE_PRODUCTION_HOST_READY == 'true'` 를 요구한다. 지금은 이 변수가 없어
main 에 머지해도 **배포 job 이 만들어지지 않는다.**
