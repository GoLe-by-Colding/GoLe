# GCP plan 예산 기간 검증 설계

## 검토된 조합

공통 프로젝트는 `project-72a52bf1-06aa-4519-b2c`, 번호는 `336721527881`, 금액은
`370000` KRW다. 이 세 값도 계정·Budget과 함께 허용 조합의 키에 포함한다.

| 결제 계정 | Budget ID | 시작 | 종료 |
| --- | --- | --- | --- |
| `01B490-1BC53A-33E611` | `b645c912-d766-43fc-8923-bff70ecfe8d8` | 2026-09-01 | 2026-10-28 |
| `013E1F-1D7BF5-E66D50` | `e987694c-e6bc-48ed-90fa-942a74e56e04` | 2026-10-24 | 2027-01-22 |

`verify-terraform-plan.py` 안의 허용 표에서 전체 기대값 조합을 조회한다. 등록되지 않은
조합은 거부하며, CLI에서 기간을 임의로 넘기는 옵션은 추가하지 않는다. 조회한 기간으로
Budget before/after를 각각 대조하고 날짜의 연·월·일은 JSON 정수만 인정한다.

기존 Budget identity·scope·amount·notification 검증과 before/after의 알림 복구 외 변경
거부는 그대로 조합한다. 새 프로젝트나 결제 기간은 코드와 회귀 테스트를 통한 별도 검토가 필요하다.

## 검증

기존 전체 plan fixture에서 신규 Budget의 계정·ID·기간과 VM의 Budget metadata만 바꾼
fixture를 만든다. 신규 정확 조합의 no-op·알림 복구 성공, before/after 각각의 잘못된
식별자·기간 거부, CLI 기대값까지 맞춘 임의 조합 거부, 구 기간 전체 회귀를 확인한다.
CLI 검증에는 합성 JSON을 stdin으로 전달하고 성공·거부 메시지에 payload가 없는지 확인한다.
운영 saved plan은 감독자가 별도 준비해 공식 CLI로 대조한다.
