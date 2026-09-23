# 배포 전 기존 서비스 복구 요구사항

- R1. `prepared`에서 중단된 첫 운영 전환은 기존 컨테이너를 재생성하지 않고 배포 원장을 복구한다.
- R2. 검증된 legacy-adoption 모드의 기존 Nginx에만 Docker healthcheck 부재를 허용한다. Nginx 설정·HTTP·HTTPS·백엔드·프론트 readiness 검증은 유지한다.
- R3. strict 모드의 healthcheck 부재, 실제 unhealthy, 다른 서비스의 healthcheck 부재는 계속 거부한다.
- R4. legacy 전송 검증은 채택한 SHA의 정확한 설정 템플릿과 결박한다. 새 canonical redirect 규칙을 과거 릴리스에 소급하지 않는다.
- R5. 운영 원장·metadata marker를 수동 삭제하거나 비용 가드를 끄지 않는다. 수정된 호스트 코드는 main CI와 검토된 bootstrap 경로를 거쳐야 한다.
- R6. legacy 컨테이너의 과거 image ID가 로컬 이미지 저장소에 없더라도, Docker가 기록한 실행 manifest와 플랫폼이 보존된 이미지의 manifest와 정확히 일치하면 그 불변 image ID로 백업할 수 있다.
- R7. 태그 이름만으로 같은 이미지라고 추정하지 않는다. manifest·플랫폼 부재나 불일치, strict 모드의 누락된 image ID는 거부한다.
- R8. 이미지 ID를 정규화한 뒤 빌드 전에 실패해도 기존 컨테이너를 재생성하지 않고 같은 manifest를 검증해 복구한다.
