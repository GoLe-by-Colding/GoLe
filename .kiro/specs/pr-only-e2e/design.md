# 설계

- ci.yml e2e 잡에 pull_request 이벤트 조건을 추가함. 워크플로 트리거는 dev/main PR과 main push 그대로임.
- notify는 always()와 needs를 유지하여 의도된 E2E 스킵 뒤에도 실행됨. main push + E2E skipped 조합만 정상으로 분류하고 원래 skipped 값을 알림에 남김.
- 공식 조건부 잡 동작에 따라 skipped는 워크플로 실패를 만들지 않음. CD의 workflow_run 성공 조건과 정확한 main SHA 검증은 변경하지 않음.
- 현재 main 보호의 E2E (Playwright) 필수 체크와 strict=true를 유지함. dev는 필수 상태 체크 없음, 추가 ruleset 없음(2026-10-27 읽기 전용 조회).
- 계약 테스트는 실제 워크플로의 알림 판정 셸을 추출·실행하여 성공/실패/취소/스킵 조합을 검사함. 네트워크·Discord 전송은 실행하지 않음.
- GitHub 공식 근거: https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/control-jobs-with-conditions

## 확인 기준

- 두 PR에서 E2E가 실제 실행되어 성공해야 함.
- main push에서는 E2E만 skipped이고 CI conclusion=success여야 함.
- 동일 main SHA의 CD 성공·운영 헬스 UP 및 제작자 표시 유지 확인 후 결과를 기록함.
