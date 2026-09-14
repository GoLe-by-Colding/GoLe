# 브릭 필터 요구사항

- 회원 인증으로 확정된 owner만 사용하며 두 모드를 합산해 서울 날짜 기준 하루 성공/예약 3회를 보장한다.
- 예약·멱등 키·동시성·실패 환급·늦은 완료 차단·24시간 결과 보관은 기존 README 계약을 유지한다.
- Python Brain/Hands/Session과 HTTP/gRPC transport를 분리하고 외부 생성 자동 재시도를 하지 않는다.
- Java는 domain → port/in,out → service → adapter/out,in 구조를 사용한다.
- 컨텍스트 간 이미지 정규화는 media NormalizeImageUseCase만 참조한다. media의 out port와 HEIF 도메인 판별은 해당 컨텍스트 안에 숨긴다.
- 실제 유료 모델 호출과 운영 활성화는 이 검증 범위에 포함하지 않는다.
