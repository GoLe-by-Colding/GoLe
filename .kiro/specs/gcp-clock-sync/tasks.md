# 작업

- [x] 운영 chrony와 root·비root NTP 요청으로 차단 재현
- [x] UDP123 예외와 호스트 검증·경계 회귀 추가
- [x] metadata runtime·migration·bootstrap·ShellCheck 검증
- [ ] 정상 릴리스·bootstrap 후 실제 동기화 확인 및 볼트 기록

2026-10-25: 새 NTP 규칙 회귀를 수정 전 실패로 재현하고 수정 후 통과했다. metadata firewall·migration·ratchet runtime, bootstrap 정적 계약 및 Python 136건, ShellCheck·git diff --check 통과. 운영 동기화는 공식 bootstrap 이후 별도 확인한다.
