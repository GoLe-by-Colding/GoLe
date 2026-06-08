# 이미지 업로드 (MinIO) — 설계

## 컨텍스트: `media` (신규, 헥사고날)

```
com.gole.api.media/
├── application/
│   ├── port/in/UploadImageUseCase     # upload(bytes, contentType, originalName) → key
│   ├── port/in/LoadObjectUseCase      # load(key) → StoredObject(bytes, contentType)
│   └── port/out/ObjectStoragePort     # put(key, bytes, contentType) / get(key)
│   └── service/MediaService
└── adapter/
    ├── in/web/MediaController         # POST /api/v1/uploads, GET /api/v1/uploads/{key}
    └── out/storage/MinioStorageAdapter (AWS SDK v2 S3, endpoint override)
```

## 저장소 어댑터 (AWS SDK v2)

- 의존성: `software.amazon.awssdk:s3`(BOM으로 버전 관리).
- `S3Client`: `endpointOverride(http://host.docker.internal:9000)`, `forcePathStyle(true)`,
  `region(us-east-1)`, creds `minioadmin/minioadmin` (env: `STORAGE_*`).
- `@ConditionalOnProperty(storage.enabled=true)` — 미설정 시 비활성.

## REST API

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/v1/uploads` | multipart `file` 업로드 → `{ key, url }` |
| GET | `/api/v1/uploads/{key}` | 객체 스트리밍(Content-Type 유지) |

- 키: `images/{uuid}.{ext}`. URL: `/api/v1/uploads/images/{uuid}.{ext}` (동일 오리진, nginx가 /api 프록시).
- 검증: content-type in (image/jpeg, image/png, image/webp), size ≤ 8MB → 초과/불일치 시 400.
- multipart 한도: `spring.servlet.multipart.max-file-size=8MB`.

## 프론트 (FSD)

- `shared/api`: `uploadImage(file): Promise<{ url }>` (fetch multipart, FormData).
- `features/create-listing`: 파일 `<input type=file>` → 선택 시 업로드 → 미리보기 + photoUrl 설정.
  공식 이미지 금지 안내 유지. 최소 1장.
- (후속) 커뮤니티 게시글 이미지도 동일 업로드 재사용.

## 설정 (env / application.yml)

```
storage.enabled=${STORAGE_ENABLED:true}
storage.endpoint=${STORAGE_ENDPOINT:http://host.docker.internal:9000}
storage.bucket=${STORAGE_BUCKET:gole}
storage.access-key=${STORAGE_ACCESS_KEY:minioadmin}
storage.secret-key=${STORAGE_SECRET_KEY:minioadmin}
```

## 검증
- 통합: 업로드 → 반환 URL GET 200 + Content-Type 일치.
- e2e: 판매 등록에서 파일 선택 → 매물 생성 → 상세에 사진 표시.
