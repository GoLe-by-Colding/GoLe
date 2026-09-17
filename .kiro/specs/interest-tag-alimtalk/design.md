# 관심태그 테마 매물 알림톡 — 설계

## 컨텍스트와 의존 방향

아웃박스·워커·팬아웃은 기존 알림톡 발송 포트를 소유한 `notification` 컨텍스트에 둔다.
`listing`은 알림 요청용 아웃바운드 포트만 소유하며, `notification`은 `account`와 `listing`의
도메인 타입을 직접 가져오지 않고 자기 아웃바운드 포트와 상대 인바운드 포트 사이의 어댑터로
연결한다.

```
ListingService.create
 └─ InterestTagListingNotifierPort
      └─ NotificationInterestTagListingNotifierAdapter
           └─ EnqueueInterestTagAlimtalkUseCase
                └─ InterestTagAlimtalkService / InterestTagAlimtalkOutboxWorker
                     ├─ InterestTagAlimtalkOutboxPort → Mongo
                     ├─ InterestTagRecipientPort → account 인바운드 포트
                     ├─ ListingSnapshotPort → listing 인바운드 포트
                     ├─ AlimtalkDailyQuotaPort → Redis
                     └─ AlimtalkSenderPort → CoolSMS 또는 로깅 어댑터
```

## 매물 관심 테마

`listing.domain.model.InterestTag`에 카탈로그 14종의 key/label을 복제한다. 계정 컨텍스트 타입을
직접 의존하지 않으며 `InterestTagParityTest`로 두 집합의 일치를 강제한다. `fromKey`는 null/blank를
미지정으로 처리하고, API 입력의 미지 키는 `BadRequestException("INVALID_INTEREST_TAG")`로 거부한다.
DB 읽기에서 미지 값은 기존 문서 호환을 위해 null로 흡수한다.

`Listing`과 `CreateListingCommand`의 기존 생성자는 유지하고 관심 테마를 받는 오버로드를 추가한다.
웹 API는 케밥 키를 그대로 입출력한다. `ListingDocument.interestTag`에는 단일 필드 인덱스를 둔다.

매물 수정 API는 현재 없으므로 수정 시 재발송 문제는 발생하지 않는다. 향후 수정 기능을 만들 때는
관심 테마 변경이 재발송을 유발할지 별도 정책을 먼저 정해야 한다.

## 수신자 역조회

`AccountRepositoryPort`는 다음 두 쿼리를 제공한다.

- 팬아웃: 태그·전화인증·마케팅동의·ACTIVE 조건을 만족하는 계정 ID만 `_id` 오름차순으로 조회한다.
  `_id > cursor`, limit, `_id` projection을 사용하며 전화번호는 읽지 않는다.
- 발송 직전: 계정 ID와 태그로 동일 자격을 재검증하고 그때만 전화번호를 읽는다.

`AccountDocument`에는 `{'interestTags':1,'status':1,'_id':1}` 복합 인덱스를 둔다. 판매자 제외는
서비스에서 수행한다.

## 2단계 아웃박스

하나의 `interest_tag_alimtalk_outbox` 컬렉션에서 `FANOUT`과 `DELIVERY`를 처리한다.

1. 등록 트랜잭션은 `fanout:{listingId}` 한 건만 적재한다.
2. 워커는 FANOUT을 점유하고 매물 활성 상태를 재확인한다.
3. `_id` 커서로 수신자를 페이지 조회하고 판매자를 제외한다.
4. Redis 일일 쿼터를 획득한 수신자에만 `listingId:recipientAccountId` DELIVERY를 적재한다.
5. 마지막 페이지면 `DELIVERED`, lease당 페이지 제한에 닿으면 시도 횟수를 올리지 않는
   `continueFanout`으로 커서와 적재 수를 저장한다.
6. 누적 수신자가 `max-recipients-per-listing`에 닿으면 무조건 종료한다.

결정적 문서 ID와 중복 키 무시는 크래시 후 같은 페이지 재처리에도 중복 DELIVERY가 생기지 않게
한다. `claimNext`는 만료 lease를 회수한 뒤 `findAndModify`로 원자 점유하고, 상태 변경은
`_id + IN_FLIGHT + leaseToken` 조건으로 lease 소유권을 확인한다.

상태는 `PENDING`, `IN_FLIGHT`, `DELIVERED`, `SKIPPED`, `DEAD_LETTER`다. 종료 상태에만
`expiresAt`을 설정해 30일 뒤 TTL로 제거한다. 전화번호는 어느 아웃박스 문서에도 저장하지 않는다.

## 워커 실패 정책

DELIVERY는 발송 직전 수신 자격과 매물 활성 상태를 다시 확인한다. 자격 상실이나 매물 비활성은
`SKIPPED`, sender 미등록은 `ALIMTALK_SENDER_UNAVAILABLE` 즉시 DEAD_LETTER다.

| FailureType | 처리 |
| --- | --- |
| `RATE_LIMITED`, `PROVIDER_FAILURE` | 지수 백오프 후 재시도 |
| `INVALID_REQUEST`, `AUTHENTICATION`, `PROVIDER_REJECTED` | 즉시 DEAD_LETTER |
| `ACCEPTANCE_UNKNOWN` | 접수 여부 불명으로 중복 가능성이 있어 즉시 DEAD_LETTER |

백오프는 `initialBackoff * 2^(attempts-1)`을 `maximumBackoff`로 제한한다. 오류 코드는
`^[A-Z0-9_]{1,80}$`만 허용하고 아니면 안전한 폴백 코드를 기록한다. 로그에는 event/type/listing/
recipient account/attempts/errorCode만 남긴다.

## 쿼터

Redis 고정 시간창 키는 `gole:interest-tag-alimtalk-quota:{accountId}:{bucket}`이고 bucket은 현재
epoch millis를 window millis로 나눈 몫이다. 팬아웃 단계의 DELIVERY 적재 직전에 한 번만 소모한다.
Redis 오류는 fail-closed로 팬아웃 전체를 재시도한다. 이후 DELIVERY가 SKIPPED여도 이미 소모한
쿼터를 돌려주지 않는다.

## 설정과 기동 가드

`gole.interest-tag-alimtalk.enabled` 하나가 적재와 워커를 함께 켠다. 템플릿 ID, 변수명 3개,
매물 URL prefix, 일일 상한, 쿼터 창, 페이지·lease·재시도·보존 설정을 둔다. 활성화한 모든 환경에서
템플릿 ID가 필요하고, staging/production/prod에서는 CoolSMS도 활성화되어야 한다. 일일 상한은
1 이상이어야 한다.

제목은 40자를 넘으면 말줄임하고 blank면 `새 매물`을 사용한다. CoolSMS가 기본 비활성이므로
로컬/dev 검증은 `LoggingAlimtalkSenderAdapter`만 사용하며 실제 알림톡을 발송하지 않는다.

## 프론트엔드

`packages/core`에 `ListingInterestTag`, `LISTING_INTEREST_TAGS`, nullable 응답 필드를 추가한다.
웹 등록 폼은 카테고리 바로 아래에 `선택 안 함`을 포함한 select와 발송 가능성을 설명하는 문구를
표시한다. 모바일, 검색, 필터, 정렬은 변경하지 않는다.

카탈로그를 API에서 동적으로 조회하는 안은 컨텍스트 복제를 피할 수 있지만, 등록 폼이 별도 요청과
로딩·실패 상태를 가져야 하므로 선택하지 않았다. 백엔드는 파리티 테스트로 드리프트를 막고,
프론트 목록 동기화는 코드 리뷰와 타입 검사로 관리한다.

## 알려진 한계

- 판매자당 일일 팬아웃 상한은 v1 범위 밖이다.
- 카카오 템플릿 승인과 운영 자격증명이 없어 `COOLSMS_ENABLED=false` 동안 운영 발송은 0건이다.

