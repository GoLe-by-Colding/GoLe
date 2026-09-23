# 설계

2026-09-24 첫 CD 재시도에서 이전 `prepared` 원장 복구가 `gole-nginx`의 healthcheck 부재로 실패했다. 운영 서비스는 기존 릴리스로 정상 응답했지만 `verify_pre_snapshot_lkg_runtime`은 모든 모드에 `running:healthy`를 강제했다. 기존 이미지 복원 검증에는 이미 legacy Nginx만 `running:missing`을 허용하는 분기가 있다.

복구 전 검증도 동일한 모드별 판정을 적용한다. 단순히 healthcheck를 생략하지 않고 기존 Nginx의 실행 상태, 정확한 legacy 템플릿과 공개 전송을 별도로 검증한다. strict는 기존 검증을 유지한다. 검증이 모두 통과한 뒤에만 helper가 이전 원장을 정리한다.

Docker fixture에 실제 legacy 상태를 넣어 회귀를 재현한다. 성공 시 서비스 재기동·정지·VM 종료가 없고 env/version/SHA가 그대로인지 확인한다. healthcheck 결여 허용 범위를 벗어난 상태는 원장을 보존하며 실패하는지 확인한다.
