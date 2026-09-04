# PortOne 카카오페이 운영 체크리스트

이 문서는 설정 **이름과 확인 순서만** 기록합니다. 실제 Store ID, Channel Key, API Secret,
Webhook Secret은 저장소·이슈·Discord·스크린샷에 남기지 않습니다.

> 현재 공개 운영은 사용자 모집 단계라 결제·정산이 코드 정책으로 비활성화되어 있습니다.
> 아래 절차는 결제 재활성화 전용이며, Secret 값만 바꿔서는 웹훅이나 환불 경로가 열리지
> 않습니다. CD·Compose·운영 validator 변경 PR과 최소 금액 실결제/환불 검증이 함께 필요합니다.

## 0. 주문·결제 공개 전 법적 표시 게이트

- 현재 저장소에는 사용자가 제공한 통신판매업 신고번호가 없습니다. 사업자등록번호로 신고번호를
  추정하거나 `준비 중` 같은 임시값을 넣지 않습니다.
- 관할 지방자치단체 또는 정부24에서 통신판매업 신고를 완료하고 실제 신고번호를 교부받습니다.
- 공정거래위원회 통신판매사업자 조회에 상호·대표자·주소·도메인이 올바르게 반영됐는지 확인합니다.
- 실제 신고번호를 `BUSINESS_INFO`와 푸터·약관에 반영하고, 초기 화면의 공정거래위원회
  사업자정보확인 링크를 브라우저에서 검증합니다.
- 위 확인과 약관·개인정보처리방침, 구매안전서비스·환불 절차 검증이 끝나기 전에는 관리자
  `businessDisclosure` 준비 상태를 승인하거나 Stage 2로 전환하지 않습니다.

판단 근거는 국가법령정보센터의 전자상거래법 제10조·제13조, 같은 법 시행령 제11조의4,
시행규칙 제7조와 공정거래위원회 통신판매사업자 조회 안내입니다.

- https://www.law.go.kr/법령/전자상거래등에서의소비자보호에관한법률
- https://www.law.go.kr/법령/전자상거래등에서의소비자보호에관한법률시행령
- https://www.law.go.kr/법령/전자상거래등에서의소비자보호에관한법률시행규칙
- https://www.ftc.go.kr/www/selectBizCommList.do?key=254

## 1. 테스트 결제 환경변수

백엔드 런타임:

```dotenv
PORTONE_ENABLED=true
PORTONE_STORE_ID=<PortOne 테스트 상점 ID>
PORTONE_CHANNEL_KEY=<카카오페이 TEST 채널 키>
PORTONE_CHANNEL_TYPE=TEST
PORTONE_API_SECRET=<PortOne V2 API Secret>
PORTONE_WEBHOOK_SECRET=<PortOne Standard Webhook Secret>
```

프론트엔드 빌드 시점:

```dotenv
NEXT_PUBLIC_PAYMENT_MODE=portone-test
NEXT_PUBLIC_PORTONE_STORE_ID=<동일한 테스트 상점 ID>
NEXT_PUBLIC_PORTONE_CHANNEL_KEY=<동일한 카카오페이 TEST 채널 키>
```

`NEXT_PUBLIC_*` 값은 Next.js 빌드 결과에 포함됩니다. 값 변경 후에는 웹 앱을 다시
빌드해야 합니다. Secret은 절대 `NEXT_PUBLIC_*` 변수에 넣지 않습니다.

## 2. 배포 전 확인

- 주문 생성 시 서버가 `POST /payments/{paymentId}/pre-register`로 Store ID, KRW,
  주문 금액을 먼저 고정하고, 사전 등록 실패 시 주문 저장과 매물 선점을 모두 되돌리는지
  확인합니다.
- PortOne Standard Webhook URL을
  `https://<서비스 호스트>/api/v1/payments/portone/webhook`으로 등록합니다.
- PortOne 콘솔의 테스트 모드에서 웹훅 버전은 **결제모듈 V2 / 2024-04-25**,
  Content-Type은 `application/json`으로 설정하고 저장 후 **호출 테스트**를 실행합니다.
- 관리자 대시보드의 **결제 연동** 카드가 `테스트 설정 준비`, `TEST`, `KAKAOPAY`,
  `KRW`로 표시되는지 확인합니다.
- 카드에 환경변수 이름이 표시되면 해당 설정은 누락되었거나 값이 잘못된 것입니다.
- 관리자 API 응답과 로그에는 Store ID, Channel Key, API/Webhook Secret 원문이 나오지
  않는지 확인합니다.
- Discord 결제 알림 채널에는 테스트 전임을 알리고 멘션 없이 검증합니다.

## 3. 카카오페이 TEST 시나리오

1. PC에서 정상 결제 후 주문이 `FUNDS_HELD`로 바뀌는지 확인합니다.
2. 모바일에서 카카오페이 리다이렉트 후 같은 주문으로 복귀하고 서버 원장 검증이
   수행되는지 확인합니다.
3. 사용자 취소 시 결제 성공으로 처리되지 않는지 확인합니다.
4. 브라우저를 결제 직후 닫아도 서명된 웹훅으로 주문 상태가 복구되는지 확인합니다.
5. 다른 Store ID, Channel Key/Type, 통화, 결제수단, 제공자, 금액 응답은
   `PAYMENT_REVIEW`로 보존되는지 확인합니다.
6. 전액 환불 요청과 취소 웹훅 후 `REFUNDED`까지 전이되는지 확인합니다.

## 4. LIVE 전환 게이트

- PortOne 콘솔에서 카카오페이 LIVE 채널과 운영 상점을 별도로 확인합니다.
- 백엔드는 운영 Store ID/Channel Key로 교체하고 `PORTONE_CHANNEL_TYPE=LIVE`로
  설정합니다.
- 프론트엔드는 동일한 운영 공개 키로 교체하고
  `NEXT_PUBLIC_PAYMENT_MODE=portone-live`로 다시 빌드합니다.
- 관리자 대시보드가 `실결제 설정 준비`와 `LIVE`를 표시하기 전에는 결제 버튼을
  공개하지 않습니다.
- 최소 금액 실결제·웹훅·환불을 한 건씩 검증한 후 일반 사용자에게 공개합니다.
