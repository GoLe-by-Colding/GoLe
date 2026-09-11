# 관심태그 테마 매물 알림톡 — 구현 태스크

## 스펙

- [x] 1. 수신 자격, 2단계 팬아웃, 실패 분류, 개인정보 요구사항 확정
- [x] 2. 컨텍스트 배치와 포트/어댑터 의존 방향 설계
- [x] 3. 운영 설정, 가드, 검증 범위 확정

## 백엔드 — listing

- [ ] 4. `listing.InterestTag` 14종과 파리티 테스트 추가
- [ ] 5. `Listing`/명령/웹 DTO/응답/영속성/시드에 nullable 관심 테마 추가
- [ ] 6. 관심 테마 notifier 포트와 예외 흡수 어댑터 추가
- [ ] 7. 매물 등록 트랜잭션에 FANOUT 한 건 적재 연결

## 백엔드 — account

- [ ] 8. 태그 기반 수신 계정 ID 커서 조회와 발송 직전 자격 재검증 추가
- [ ] 9. 관심태그 복합 인덱스와 쿼리 통합 테스트 추가

## 백엔드 — notification

- [ ] 10. FANOUT/DELIVERY 도메인 이벤트와 Mongo 아웃박스 추가
- [ ] 11. lease, continueFanout, retry, skipped, dead-letter 상태 전이 구현
- [ ] 12. Redis 계정별 일일 고정 시간창 쿼터 구현
- [ ] 13. 수신자/listing 스냅샷 컨텍스트 어댑터 구현
- [ ] 14. 2단계 팬아웃과 DELIVERY 워커 구현
- [ ] 15. 설정 바인딩과 fail-closed 기동 가드 추가
- [ ] 16. 단위·통합 회귀 테스트 추가

## 프론트엔드

- [ ] 17. core 매물 타입·카탈로그·등록 API 입력에 관심 테마 추가
- [ ] 18. 웹 매물 등록 폼에 선택 UI와 발송 안내 추가
- [ ] 19. 테마 선택 등록 후 상세 응답 E2E 추가

## 운영·검증

- [ ] 20. 설정 활성화 순서, DEAD_LETTER 조회, 쿼터 키 운영 절차 문서화
- [ ] 21. API 단위·통합 테스트와 Spotless 실행
- [ ] 22. web typecheck/lint/fsd:lint와 E2E 실행
- [ ] 23. `COOLSMS_ENABLED=false` 로깅 스텁 수동 검증

