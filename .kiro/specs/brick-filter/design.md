# 브릭 필터 설계

상세 quota·멱등성·원문 보관 계약은 [[README]]를 따른다.

- `ManageBrickFilterUseCase`가 웹 어댑터용 입력 경계와 Quota/View 응답을 소유한다.
- `BrickFilterService`는 port/in을 구현하고 port/out만 의존한다.
- Mongo/HTTP/gRPC/이미지 정규화 어댑터는 adapter/out에 둔다.
- `NormalizeImageUseCase`는 바이트 입력을 안전한 래스터로 정규화하는 media 공개 계약이다. media 서비스가 MIME 판별과 기존 ImageProcessorPort 위임을 맡는다.
- BrickImageSanitizer는 반환된 크기를 다시 제한하고 1024px PNG로 변환한다. provider 결과는 기존 독립 크기/형식 검증을 유지한다.
- 패키지 이동은 외부 REST/proto 응답 계약을 바꾸지 않는다. 실제 Mongo quota 경합 및 gRPC 상호운용 테스트로 회귀를 확인한다.
