# 설계

2026-09-24 첫 CD 재시도에서 이전 `prepared` 원장 복구가 `gole-nginx`의 healthcheck 부재로 실패했다. 운영 서비스는 기존 릴리스로 정상 응답했지만 `verify_pre_snapshot_lkg_runtime`은 모든 모드에 `running:healthy`를 강제했다. 기존 이미지 복원 검증에는 이미 legacy Nginx만 `running:missing`을 허용하는 분기가 있다.

복구 전 검증도 동일한 모드별 판정을 적용한다. 단순히 healthcheck를 생략하지 않고 기존 Nginx의 실행 상태, 정확한 legacy 템플릿과 공개 전송을 별도로 검증한다. strict는 기존 검증을 유지한다. 검증이 모두 통과한 뒤에만 helper가 이전 원장을 정리한다.

Docker fixture에 실제 legacy 상태를 넣어 회귀를 재현한다. 성공 시 서비스 재기동·정지·VM 종료가 없고 env/version/SHA가 그대로인지 확인한다. healthcheck 결여 허용 범위를 벗어난 상태는 원장을 보존하며 실패하는지 확인한다.

## Docker 이미지 식별자 이전

CD #35887681306은 예산 감시 컨테이너의 과거 `.Image`가 더 이상 로컬 image ID로 조회되지 않아 스냅샷에서 중단됐다. 실행 컨테이너의 `ImageManifestDescriptor`는 보존됐으며, 현재 Compose 태그의 `docker image inspect --platform linux/amd64` 결과와 manifest digest가 일치함을 운영에서 읽기 전용으로 확인했다.

기존 ID가 조회되는 경우에는 그대로 사용한다. 조회되지 않는 legacy-adoption에서만 컨테이너의 manifest digest와 플랫폼을 읽고, Compose 이미지 참조를 먼저 불변 ID로 고정한 뒤 그 ID의 플랫폼 manifest와 비교한다. 이 순서는 조회 중 mutable 태그 변경을 실행 이미지로 오인하지 않게 한다. 복구 검증도 스냅샷의 불변 ID를 같은 방식으로 대조한다. manifest가 같다는 증명 없이 태그를 따르거나 컨테이너를 다시 만들지 않는다.

## 제한된 CPU의 문의 에이전트 readiness

CD #35892855977은 이미지 스냅샷과 빌드를 통과했으나, 문의 에이전트 healthcheck가 매번 3초를 초과해 자동 복구했다. 기존 서비스의 공개 health 200과 원장 정리를 확인했다. 동일 운영 이미지의 격리된 일회성 프로세스를 0.25 CPU·192 MiB로 측정하니 gRPC 모듈 import만 3.407초였다. 서버는 이미 SERVING 상태로 기동했으므로 RPC 요청 전 준비 시간이 검사 예산을 소진한 것이다.

빌드 때 의존성과 서비스 코드를 bytecode로 미리 컴파일해 매 검사마다 파싱하지 않게 한다. Docker 검사 전체는 10초, 초기 기동 유예는 30초로 두며 실제 RPC 제한 2초와 서비스 이름별 SERVING 확인은 유지한다. Compose와 이미지 기본 healthcheck를 맞추고 root Compose 정책에도 같은 계약을 반영한다. 이전 3초 설정은 검증된 LKG 복구 모드에서만 허용한다.

CI에서 실제 이미지를 외부 연결·호스트 포트 없이 운영과 같은 자원 제한으로 띄운다. Docker가 healthy로 판단하는지와 실제 문의 RPC 응답을 확인하고, NOT_SERVING·무응답 fixture에 같은 healthcheck를 실행해 실패하는지 검증한다. 테스트가 끝나면 생성한 컨테이너만 제거한다.

## GitHub CI 완료 목록 반영 지연

main `c328a7c4`의 CI #35898208316과 check suite는 completed·success였지만, `status=completed` 또는 `status=success`를 붙인 workflow 목록에서는 10분 넘게 해당 실행이 빠졌다. 같은 `branch=main&event=push&head_sha=<SHA>` 조회는 즉시 정확한 성공 실행을 반환했다. bootstrap과 release verifier가 이 목록 필터에 의존해 설치 전 단계에서 중단됐다.

상태 필터를 제거하고 정확한 SHA로 조회한다. 반환된 각 실행의 head_sha·head_branch=main·event=push·status=completed·conclusion=success를 모두 확인한다. root 소유 저장소의 현재 main 일치, 과거 릴리스의 main 조상 검증, immutable archive와 파일 권한 검증은 그대로 유지한다. bootstrap 본문·README 진입 명령·설치 release verifier 모두 같은 판정을 사용한다. 상태 필터를 쓰면 과거 목록만 주는 fixture로 재현하고, 미완료·실패·다른 브랜치/이벤트/SHA·잘못된 응답은 거부한다.

## 실제 Docker inspect 계약

CD #35903369477은 문의 에이전트·API·웹·Nginx·비용 가드 모두 healthy가 된 뒤 `strict runtime network boundary changed: mongo`로 자동 복구했다. Docker의 `println` 템플릿과 CLI가 각각 개행을 추가해 `sort | paste` 결과 앞에 쉼표가 생겼다. 기존 fixture는 이 마지막 빈 줄을 재현하지 못했다. 네트워크를 JSON 객체로 읽고 모든 키를 정렬해 정확한 허용목록과 비교한다.

실제 Mongo 이미지가 선언한 `/data/configdb` 익명 볼륨도 운영·개발 inspect에서 확인했다. strict 검증은 이미 승인 이미지의 불변 ID를 대조하므로, mongo의 해당 경로에 한해 64자리 소문자 hex 이름·local driver·쓰기 가능한 volume 하나를 인정한다. 주 데이터의 `gole_mongo-data:/data/db` 계약은 유지하며 bind·다른 이름/driver·읽기 전용·중복·다른 경로는 거부한다. 새 데이터 볼륨을 생성하거나 기존 운영 데이터를 옮기지 않는다.

실제 고정 Mongo 이미지로 실행하지 않는 일회성 컨테이너를 만들어 원본 inspect 출력을 파서에 전달하고, 정상 마운트와 변조된 마운트를 검사한다. 생성한 컨테이너와 익명 볼륨만 정리한다. CD 진입 검증에도 R11과 같은 정확한 SHA 조회 및 응답 검증을 적용한다.
