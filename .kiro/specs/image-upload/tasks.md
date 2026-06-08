# 이미지 업로드 (MinIO) — 구현 태스크

## 선결
- [x] 0. `gole` 버킷 생성 + 컨테이너→MinIO 접근 확인(host.docker.internal:9000 health 200)

## 백엔드 (media 컨텍스트)
- [ ] 1. build.gradle.kts: `software.amazon.awssdk:s3`(BOM) 의존성 추가
- [ ] 2. out-port `ObjectStoragePort` + `MinioStorageAdapter`(S3Client, endpoint override, path-style)
- [ ] 3. in-port `UploadImageUseCase`/`LoadObjectUseCase` + `MediaService`(MIME/용량 검증)
- [ ] 4. `MediaController` POST `/api/v1/uploads`(multipart) / GET `/api/v1/uploads/{key}` 스트리밍
- [ ] 5. application.yml `storage.*` + multipart 한도

## 프론트
- [ ] 6. `shared/api.uploadImage(file)`
- [ ] 7. `create-listing-form` 파일 업로드 + 미리보기로 교체(최소 1장, 공식 이미지 금지 안내)

## 검증/배포
- [ ] 8. 백엔드 컴파일/test + 프론트 build/lint + e2e
- [ ] 9. 커밋(scope별)+push, deploy.sh, 업로드→서빙 200 라이브 확인

## 후속
- [ ] 업로드 인증 가드(세션 필수)
- [ ] 썸네일/리사이즈, EXIF 제거
- [ ] 커뮤니티 게시글 이미지 업로드 재사용
