# 설계

2026-10-25 운영 chrony는 active지만 내부 NTP reach 0·Stratum 0이었다. 같은 UDP123 요청이 root에서는 48바이트 stratum2 응답을 받고 `_chrony`에서는 PermissionError였다. 기존 OUTPUT 체인은 DNS와 root만 통과시켜 비root chrony도 거부했다.

IPv4 `169.254.169.254/32`로 한정된 호스트 OUTPUT 체인에 UDP123 RETURN을 추가한다. IPv6·컨테이너 PREROUTING은 변경하지 않는다. UDP123은 NTP이며 HTTP 메타데이터 토큰 경로를 열지 않는다. 호스트 검증기에 해당 규칙을 요구하고 실제 생성 규칙을 첫 일치 방식으로 평가해 DNS·NTP·HTTP·컨테이너 경계를 검증한다.

운영 적용은 성공한 main CI와 호환 CD 이후 검토한 SHA의 공식 bootstrap으로 한다. 이후 chronyc의 내부 서버 선택·동기화와 broker heartbeat·watchdog를 관찰한다. 원격 시각을 강제로 쓰지 않는다.

근거: https://docs.cloud.google.com/compute/docs/instances/time-synchronization/configure-ntp
