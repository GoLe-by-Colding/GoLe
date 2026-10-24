# GCP plan 예산 기간 검증 요구사항

## 문제와 범위

결제 이전 Terraform은 새 예산 기간을 선언하지만 saved-plan 검증기는 구 기간만
허용한다. 신규 Budget을 import한 뒤 metadata만 바꾸는 plan도 예산 검증에서 거부된다.
관련 정책은 [GCP 결제 이전](../gcp-billing-migration/design.md)을 따른다.

- 검증된 프로젝트 ID·프로젝트 번호·결제 계정·Budget ID·370000 KRW의 전체 조합으로
  예산 기간을 선택한다. CLI 기대값을 함께 바꿔 임의 계정이나 예산을 승인할 수 없어야 한다.
- 구 조합의 `2026-09-01~2026-10-28`과 새 조합의 `2026-10-24~2027-01-22`를 허용한다.
- 구·신 식별자나 날짜를 섞은 조합, 기간 연장, 잘못된 날짜 타입을 거부한다.
- Budget의 before와 after를 모두 검증한다. 신규 Budget의 no-op과 기존 알림 수신자·경로
  복구만 허용하는 계약을 유지한다.
- VM·디스크·IP·IAM·예산 금액·Pub/Sub·startup-script 해시의 기존 검증을 유지한다.
- stdin JSON CLI에서 성공·거부를 확인하고 plan payload는 출력하지 않는다.

## 범위 밖

Terraform 리소스·호스트 bootstrap·비용 state는 변경하지 않는다. 실제 saved plan 검증과
클라우드 적용, main 반영은 감독자가 별도로 맡는다. 구매 PR254는 보류한다.
