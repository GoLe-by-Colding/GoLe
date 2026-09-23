# 설계

2026-09-24 첫 CD 재시도에서 이전 `prepared` 원장 복구가 `gole-nginx`의 healthcheck 부재로 실패했다. 운영 서비스는 기존 릴리스로 정상 응답했지만 `verify_pre_snapshot_lkg_runtime`은 모든 모드에 `running:healthy`를 강제했다. 기존 이미지 복원 검증에는 이미 legacy Nginx만 `running:missing`을 허용하는 분기가 있다.

복구 전 검증도 동일한 모드별 판정을 적용한다. 단순히 healthcheck를 생략하지 않고 기존 Nginx의 실행 상태, 정확한 legacy 템플릿과 공개 전송을 별도로 검증한다. strict는 기존 검증을 유지한다. 검증이 모두 통과한 뒤에만 helper가 이전 원장을 정리한다.

Docker fixture에 실제 legacy 상태를 넣어 회귀를 재현한다. 성공 시 서비스 재기동·정지·VM 종료가 없고 env/version/SHA가 그대로인지 확인한다. healthcheck 결여 허용 범위를 벗어난 상태는 원장을 보존하며 실패하는지 확인한다.

## Docker 이미지 식별자 이전

CD #35887681306은 예산 감시 컨테이너의 과거 `.Image`가 더 이상 로컬 image ID로 조회되지 않아 스냅샷에서 중단됐다. 실행 컨테이너의 `ImageManifestDescriptor`는 보존됐으며, 현재 Compose 태그의 `docker image inspect --platform linux/amd64` 결과와 manifest digest가 일치함을 운영에서 읽기 전용으로 확인했다.

기존 ID가 조회되는 경우에는 그대로 사용한다. 조회되지 않는 legacy-adoption에서만 컨테이너의 manifest digest와 플랫폼을 읽고, Compose 이미지 참조를 먼저 불변 ID로 고정한 뒤 그 ID의 플랫폼 manifest와 비교한다. 이 순서는 조회 중 mutable 태그 변경을 실행 이미지로 오인하지 않게 한다. 복구 검증도 스냅샷의 불변 ID를 같은 방식으로 대조한다. manifest가 같다는 증명 없이 태그를 따르거나 컨테이너를 다시 만들지 않는다.
