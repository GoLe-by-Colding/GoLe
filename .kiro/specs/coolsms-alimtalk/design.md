# CoolSMS 알림톡 발송 — 설계

## 구조

기존 `notification` 컨텍스트에 출력 포트와 외부 어댑터만 추가한다.

```
notification/
├── application/port/out/
│   ├── AlimtalkSenderPort
│   └── AlimtalkSendException
└── adapter/out/coolsms/
    ├── CoolsmsAlimtalkAdapter
    ├── CoolsmsConfig
    └── CoolsmsProperties
```

도메인과 애플리케이션 포트는 CoolSMS SDK를 참조하지 않는다. SDK 모델·예외 변환은 어댑터가
전담한다.

## 포트 계약

```
AlimtalkSenderPort.send(SendAlimtalkCommand) -> AlimtalkAcceptance

SendAlimtalkCommand(to, templateId, variables)
AlimtalkAcceptance(groupId, messageId, statusCode, statusMessage)
```

실패는 `AlimtalkSendException`과 다음 유형으로 표현한다.

- `INVALID_REQUEST`: 잘못된 번호·템플릿·변수 또는 공급자 요청 거부
- `AUTHENTICATION`: API 키/시크릿 인증 실패
- `RATE_LIMITED`: CoolSMS 호출 제한 초과
- `PROVIDER_REJECTED`: 메시지 등록 실패
- `ACCEPTANCE_UNKNOWN`: 타임아웃·빈 응답 등 접수 여부 불명
- `PROVIDER_FAILURE`: 분류되지 않은 공급자/SDK 실패

자동 재시도는 하지 않는다. `RATE_LIMITED`만 재시도 안전 가능성이 명확한 유형으로 표시하고,
`ACCEPTANCE_UNKNOWN`은 중복 가능성 때문에 재시도 안전으로 표시하지 않는다.

## CoolSMS 어댑터

- 공식 SDK `com.solapi:sdk:1.1.0`의 공유 `DefaultMessageService`를 사용한다.
- 각 호출에서 새 `Message`, `KakaoOption`, `SendRequestConfig`를 만든다.
- `MessageType.ATA`, `autoTypeDetect=false`, `disableSms=true`, `showMessageList=true`를 명시한다.
- 변수 키와 값은 공백 여부만 검증하고 승인 템플릿의 표기 그대로 SDK에 전달한다.
- 응답의 등록 실패 목록이 비어 있고, 단건 메시지 상태가 `2000`이며, 그룹/메시지 ID가 모두
  존재할 때만 성공을 반환한다.
- SDK 1.1.0은 요청의 `strict` 필드를 노출하지 않으므로 `strict=true`는 적용하지 않는다.

## 설정

```yaml
coolsms:
  enabled: false
  api-key: ${COOLSMS_API_KEY:}
  api-secret: ${COOLSMS_API_SECRET:}
  pf-id: ${COOLSMS_PF_ID:}
```

어댑터는 `coolsms.enabled=true`일 때만 생성한다. 생성 시 필수값을 검증하므로 잘못 활성화된
환경은 외부 호출 전 시작 단계에서 실패한다.

## 동시성·운영

- 어댑터에는 불변 설정과 SDK 서비스 참조만 두고 요청 객체를 공유하지 않는다.
- 공급자 발송 제한(기본 5초당 100요청)은 계정 전체에 적용되므로 JVM 로컬 세마포어를 두지 않는다.
- 실패 로그에는 마스킹한 번호, 템플릿 ID, 공급자 상태 코드만 기록하고 변수와 자격증명은 제외한다.
- 접수 성공은 최종 수신 성공을 뜻하지 않는다. 최종 상태 추적이 필요해질 때 SINGLE-REPORT 웹훅과
  영속 이력을 별도 기능으로 설계한다.

