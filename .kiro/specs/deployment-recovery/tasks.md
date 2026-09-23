# 작업 목록

- [x] legacy Nginx healthcheck 부재를 재현하고 모드별 검증을 수정한다.
- [x] 기존 전송 설정 검증과 strict 거부 경계를 확인한다.
- [x] 관련 Docker runtime 회귀 검사를 실행한다.
- [x] 운영 장애 시각·복구·남은 적용 조건을 팀 볼트에 기록한다.

검증: deployment-rollback-mode-runtime, legacy-adopted-transport-runtime,
deployment-mutation-cleanup-runtime, deployment-runtime-verifier의 Docker 검사 통과함.
강제 SIGKILL 출력은 중단 복구 검사의 의도된 장애 주입임.
