# Image Upload (MinIO/S3) — Tasks

## 백엔드 (media 컨텍스트)
- [x] B1 domain: `StoredImage`, 예외(`InvalidImageException`/`ImageTooLargeException`/`ImageNotFoundException`)
- [x] B2 port-in: `UploadImageUseCase`, `LoadImageUseCase`
- [x] B3 port-out: `ObjectStoragePort`(ensureBucket/put/get)
- [x] B4 service: `MediaService`(검증 M1.2/M1.3, 키생성 M1.4, URL 조립)
- [x] B5 adapter-out: `S3Config`(S3Client/StorageProperties) + `S3ObjectStorageAdapter`(MinIO, path-style, ensureBucket)
- [x] B6 adapter-in: `MediaController`(POST multipart, GET streaming `/images/**`)
- [x] B7 build.gradle: `software.amazon.awssdk:s3`(BOM), application.yml `storage.*` + multipart 한도
- [x] B8 test: `MediaServiceTest`(가짜 ObjectStoragePort — 검증/키/URL)

## 프론트 (FSD)
- [x] F1 `shared/api/upload-client.ts` `uploadImage(file)` + 공개 API export
- [x] F2 `create-listing-form`: 파일 업로드 + 미리보기 + photoUrls 연동
- [x] F3 `create-post-form`: 파일 업로드 + 미리보기 + imageUrls 연동
- [x] F4 typecheck/lint/fsd:lint 통과 + 프로덕션 build 성공

## 통합/배포
- [x] D1 커밋(feat) → PR → 머지 (2026-08-03 실측 감사로 소급 체크)
- [x] D2 컨테이너 배포 완료
- [x] D3 MinIO `gole` 버킷 + 스트리밍 검증 — 프로덕션 `GET /api/v1/listings` 응답의
      `photoUrls`가 `/api/v1/media/community/moc-lighthouse.svg` 형태로 서빙 중

## 로컬 개발 환경 갭 (2026-08-03 실측 발견, 해소됨)
- [x] L1 `docker-compose.yml`에 MinIO + healthcheck + `minio-init` 버킷/익명 읽기 초기화 추가.
- [x] L2 호스트 `bootRun` 기본 endpoint를 `http://localhost:9000`으로 정리하고,
      컨테이너 실행은 `STORAGE_S3_ENDPOINT=http://minio:9000`으로 명시하도록 분리.

## 후속 백로그
- [x] N1 업로드 인증 + 사용자별 레이트리밋 — 전역 `UserAuthInterceptor`가 모든 미디어 POST에
      인증 계정 속성을 주입하고, Redis 고정 시간창으로 기본 10분당 30장을 제한한다. 배치 요청은
      요청 횟수가 아니라 실제 파일 수만큼 차감하며 초과 시 `429 MEDIA_UPLOAD_RATE_LIMITED`와
      `Retry-After`를 반환한다. 공개 이미지 GET은 그대로 인증 없이 제공한다.
- [x] N2 다중 이미지 업로드 — 배치 엔드포인트 `POST /api/v1/media/images/batch`(최대 10장) + 프론트 다중 선택/미리보기/삭제(create-listing·create-post)
- [x] N2a 이미지 썸네일(온더플라이 리사이즈 ?w= + MinIO 캐시); 프론트 카드 thumbnailUrl
- [ ] N3 presigned URL/CDN 전환(트래픽 확장 시)
