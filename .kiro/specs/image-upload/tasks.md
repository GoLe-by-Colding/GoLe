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

## 로컬 개발 환경 갭 (2026-08-03 실측 발견)
- [ ] L1 **`docker-compose.yml`에 MinIO 서비스가 없다**(grep 0건). 로컬에서 `infra:up`만
      실행하면 9000 포트가 비어 `MediaSeeder`가 전부 실패한다
      (`[seed] media: 'community/titanic.svg' 업로드 실패: UnknownHostException`).
      로컬 개발자는 이미지가 전부 깨진 상태로 작업하게 된다. MinIO 서비스 + 버킷 초기화를
      compose에 추가해야 한다.
- [ ] L2 `storage.s3.endpoint` 기본값이 `http://host.docker.internal:9000`이라
      호스트에서 직접 `bootRun` 할 때 해석되지 않는다. 로컬 기본값 정리 필요.

## 후속 백로그
- [ ] N1 업로드 인증(세션 토큰) + 사용자별 레이트리밋
      ※ 실측 확인: `MediaController`의 `POST /images`·`POST /images/batch`에 인증·레이트리밋이
      **전혀 없다**(세션/Authorization 참조 0건). 누구나 무제한 업로드 가능한 상태이므로
      후속이 아니라 **보안 우선순위 항목**으로 취급해야 한다.
- [x] N2 다중 이미지 업로드 — 배치 엔드포인트 `POST /api/v1/media/images/batch`(최대 10장) + 프론트 다중 선택/미리보기/삭제(create-listing·create-post)
- [x] N2a 이미지 썸네일(온더플라이 리사이즈 ?w= + MinIO 캐시); 프론트 카드 thumbnailUrl
- [ ] N3 presigned URL/CDN 전환(트래픽 확장 시)
