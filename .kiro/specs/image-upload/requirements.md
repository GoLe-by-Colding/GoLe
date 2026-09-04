# Image Upload (MinIO/S3) — Requirements

> 매물·게시글 이미지를 URL 직접 입력 대신 **파일 업로드**로 받는다. 저장은 MinIO(S3 호환), 공개는 백엔드 스트리밍 경유.

## 배경 / 제약
- 로컬 개발은 루트 Docker Compose MinIO(`localhost:9000`), 운영은 GCP
  `gole-production`의 Compose MinIO(`minio:9000`)와 전용 `gole` 버킷을 사용한다.
  운영 S3 API와 콘솔은 외부에 공개하지 않는다(`.kiro/steering/minio.md`).
- 따라서 브라우저가 MinIO에 직접 접근할 수 없다 → 이미지는 **백엔드(`/api/v1/media/...`)를 통해 공개**한다(기존 nginx `/api/` 라우팅 재사용, 신규 인프라 불필요).

## 요구사항 M1 — 이미지 업로드
**스토리:** 사용자로서, 매물/게시글에 직접 촬영한 사진을 파일로 업로드하고 싶다.
- M1.1 WHEN 사용자가 이미지 파일을 `multipart/form-data`(필드명 `file`)로 업로드하면, 시스템은 객체를 `gole` 버킷에 저장하고 공개 조회 URL과 키를 반환해야 한다.
- M1.2 IF 파일이 비었거나 `image/*` 컨텐트 타입이 아니면, 시스템은 400 `INVALID_IMAGE`로 거부해야 한다.
- M1.3 IF 파일 크기가 한도(기본 5MB)를 초과하면, 시스템은 400 `IMAGE_TOO_LARGE`로 거부해야 한다.
- M1.4 시스템은 충돌하지 않는 객체 키(`images/<uuid>.<ext>`)를 생성해야 하며, 원본 파일명을 신뢰하지 않아야 한다.
- M1.5 시스템은 대상 버킷이 없으면 생성(ensure)해야 한다.

## 요구사항 M2 — 이미지 조회(공개)
**스토리:** 누구나 업로드된 이미지를 도메인 URL로 볼 수 있어야 한다.
- M2.1 WHEN `GET /api/v1/media/images/{key}`를 호출하면, 시스템은 MinIO에서 객체를 스트리밍하고 올바른 `Content-Type`을 설정해야 한다.
- M2.2 IF 키에 해당하는 객체가 없으면, 시스템은 404 `IMAGE_NOT_FOUND`를 반환해야 한다.
- M2.3 시스템은 조회 응답에 캐시 헤더(`Cache-Control`)를 설정해 반복 조회를 줄여야 한다.

## 요구사항 M3 — 프론트 연동
**스토리:** 등록/작성 화면에서 파일을 선택하면 자동 업로드되고 미리보기가 보여야 한다.
- M3.1 WHEN 매물 등록 폼에서 이미지를 선택하면, 프론트는 업로드 후 반환 URL을 `photoUrls`에 넣어 매물을 등록해야 한다.
- M3.2 WHEN 게시글 작성 폼에서 이미지를 선택하면, 프론트는 업로드 후 반환 URL을 `imageUrls`에 넣어 게시글을 작성해야 한다.
- M3.3 업로드 진행/실패 상태를 사용자에게 표시해야 하며, 업로드 중에는 제출을 막아야 한다.

## 비기능
- 업로드 엔드포인트는 로그인 세션을 요구하고 계정별 원자적 할당량을 적용한다. 이미지 조회만 공개한다.
- 헥사고날: `media` 컨텍스트는 `UploadImageUseCase`(in)/`LoadImageUseCase`(in)와 `ObjectStoragePort`(out)로 구성하고 S3 어댑터로 MinIO에 연결한다.
