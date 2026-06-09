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
- [ ] D1 커밋(feat) → PR → 머지
- [ ] D2 컨테이너 git pull + bootJar(빌드 게이트) + 프론트 build + pm2 restart
- [ ] D3 MinIO `gole` 버킷 확인/생성, 업로드 POST → URL → GET 스트리밍 스모크 테스트

## 후속 백로그
- [ ] N1 업로드 인증(세션 토큰) + 사용자별 레이트리밋
- [x] N2 다중 이미지 업로드 — 배치 엔드포인트 `POST /api/v1/media/images/batch`(최대 10장) + 프론트 다중 선택/미리보기/삭제(create-listing·create-post)
- [x] N2a 이미지 썸네일(온더플라이 리사이즈 ?w= + MinIO 캐시); 프론트 카드 thumbnailUrl
- [ ] N3 presigned URL/CDN 전환(트래픽 확장 시)
