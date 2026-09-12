# 관심태그 매물 알림톡 운영 절차

관심태그 매물 알림톡은 기본적으로 꺼져 있다. `GOLE_INTEREST_TAG_ALIMTALK_ENABLED=true`는
FANOUT 적재와 워커 처리를 함께 켜므로, 템플릿과 발송 채널을 먼저 준비한 뒤 마지막에 활성화한다.
로컬·dev에서는 `COOLSMS_ENABLED=false`를 유지하고 `LoggingAlimtalkSenderAdapter`로만 검증한다.

## 활성화 순서

1. 카카오에서 알림톡 템플릿을 승인받고 템플릿의 변수명이 `#{테마}`, `#{매물명}`,
   `#{링크}`와 일치하는지 확인한다. 다르면 아래 변수 환경변수를 승인된 표기와 정확히 맞춘다.
2. CoolSMS API 자격증명과 카카오 채널 연동 ID를 배포 Secret에 넣고
   `COOLSMS_ENABLED=true`로 설정한다. 값은 저장소·명령 로그·운영 기록에 남기지 않는다.
3. 아래 관심태그 알림톡 설정을 배포 환경에 넣는다.

   - `GOLE_INTEREST_TAG_ALIMTALK_TEMPLATE_ID` — 승인된 템플릿 ID
   - `GOLE_INTEREST_TAG_ALIMTALK_THEME_VARIABLE` — 기본 `#{테마}`
   - `GOLE_INTEREST_TAG_ALIMTALK_TITLE_VARIABLE` — 기본 `#{매물명}`
   - `GOLE_INTEREST_TAG_ALIMTALK_LINK_VARIABLE` — 기본 `#{링크}`
   - `GOLE_INTEREST_TAG_ALIMTALK_LISTING_URL_PREFIX` — 기본 `https://gole.co.kr/listings/`
   - `GOLE_INTEREST_TAG_ALIMTALK_DAILY_LIMIT` — 계정당 시간창 상한, 기본 3
   - `GOLE_INTEREST_TAG_ALIMTALK_QUOTA_WINDOW` — 고정 시간창, 기본 `P1D`

4. 템플릿·자격증명·설정이 반영된 뒤 마지막으로
   `GOLE_INTEREST_TAG_ALIMTALK_ENABLED=true`를 설정하고 API를 재기동한다.
5. 새 테마 매물을 등록해 FANOUT과 DELIVERY가 `DELIVERED`로 끝나는지 확인한다. 아웃박스
   DELIVERY 문서에 `phoneNumber` 필드가 없어야 하며, 로그에도 원문 전화번호와 템플릿 변수 맵을
   남기지 않는다.

활성화된 모든 환경에서 템플릿 ID가 비어 있거나 일일 상한이 1보다 작으면 기동이 실패한다.
`staging`, `production`, `prod`에서는 CoolSMS가 꺼져 있어도 기동이 실패한다. 이 가드를 피하려고
스위치를 나누거나 빈 템플릿으로 먼저 활성화하지 않는다.

## DEAD_LETTER 확인

MongoDB에서 개인정보를 projection에 포함하지 않고 다음과 같이 확인한다.

```javascript
db.interest_tag_alimtalk_outbox
  .find(
    { state: "DEAD_LETTER" },
    {
      _id: 1,
      type: 1,
      listingId: 1,
      recipientAccountId: 1,
      attempts: 1,
      lastErrorCode: 1,
      completedAt: 1,
    },
  )
  .sort({ completedAt: -1 });
```

`RATE_LIMITED`와 `PROVIDER_FAILURE`는 설정된 최대 시도 횟수까지 자동 재시도한다.
`INVALID_REQUEST`, `AUTHENTICATION`, `PROVIDER_REJECTED`, `ACCEPTANCE_UNKNOWN`, sender 미등록은
즉시 DEAD_LETTER가 된다. 특히 `ACCEPTANCE_UNKNOWN`은 공급자 접수 여부가 불명확해 중복 발송
위험이 있으므로 재시도하지 않는다.

관리자 재시도 API는 제공하지 않는다. 장애가 해소되어도 Mongo 문서를 직접 `PENDING`으로 바꾸거나
임의로 재발송하지 않는다. 수동 재발송 절차가 필요하면 발송 직전 동의·계정 상태·관심태그를 다시
검증하고 감사 가능한 별도 설계를 먼저 승인받는다.

## 쿼터 확인

Redis 키는 다음 형식이다.

```text
gole:interest-tag-alimtalk-quota:{accountId}:{bucket}
bucket = floor(epochMillis / quotaWindowMillis)
```

TTL은 시간창의 2배다. 쿼터는 FANOUT이 DELIVERY를 적재하기 직전에 선소모하며, 이후 자격 상실로
`SKIPPED`되거나 발송이 실패해도 반환하지 않는다. 재시도마다 다시 소모하지 않기 위한 보수적 정책이다.
운영 중 상한을 우회하려고 키를 임의 삭제하거나 값을 낮추지 않는다. Redis 장애는 fail-closed로
FANOUT 재시도에 맡기며, 결정적 DELIVERY ID가 같은 수신자의 중복 적재를 막는다.

페이지 처리 도중 장애로 같은 커서를 다시 처리하면 결정적 ID가 DELIVERY 중복은 막지만, 장애 시점에
따라 쿼터는 보수적으로 한 번 더 소모될 수 있다. 상한이 예상보다 빨리 소진된 흔적은 데이터 유실로
숨기지 말고 장애 기록에 남긴다.

## 비활성화와 보존

비상 중지는 `GOLE_INTEREST_TAG_ALIMTALK_ENABLED=false`로 적재와 워커를 함께 멈춘 뒤 API를
재기동한다. 종료 상태인 `DELIVERED`, `SKIPPED`, `DEAD_LETTER` 문서에만 `expiresAt`이 생기며 기본
30일 뒤 TTL로 정리된다. `PENDING`이나 `IN_FLIGHT` 문서를 삭제해 장애를 숨기지 않는다.

공급자가 발송을 수락한 직후 프로세스가 종료되거나 Mongo 완료 기록이 실패하면 lease 회수 뒤 같은
DELIVERY가 다시 처리될 수 있다. 실제 수신 중복 여부를 확인할 수 없는 상태에서 수동 재발송하지 않고,
이 경계는 공급자 조회·멱등 키를 포함하는 후속 설계 대상으로 기록한다.
