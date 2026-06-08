# Image Upload (MinIO/S3) — Design

## 1. 흐름
```
[Browser] --(multipart file)--> POST /api/v1/media/images  --> MediaService.upload --> ObjectStoragePort.put --> MinIO(gole)
                                                          <-- { url, key }
[Browser] <img src=".../api/v1/media/images/{key}">     --> GET /api/v1/media/images/{key} --> ObjectStoragePort.get --> stream bytes
```
- 이미지는 백엔드를 통해 공개되므로 MinIO S3 API를 외부에 열 필요가 없다(기존 nginx `/api/` 라우팅 사용).

## 2. 백엔드 — `com.gole.api.media` (헥사고날)
```
media/
├── domain/model/StoredImage.java        # key, contentType, size, url (값 객체)
├── domain/exception/InvalidImageException.java, ImageTooLargeException.java, ImageNotFoundException.java
├── application/
│   ├── port/in/UploadImageUseCase.java   # upload(UploadImageCommand{bytes/inputStream, contentType, size, originalFilename}) -> StoredImage
│   ├── port/in/LoadImageUseCase.java      # load(key) -> LoadedImage{stream, contentType, size}
│   ├── port/out/ObjectStoragePort.java    # ensureBucket(), put(key, bytes, contentType), get(key) -> Optional<StoredObject>
│   └── service/MediaService.java          # 검증(M1.2/M1.3), 키 생성(M1.4), URL 조립
└── adapter/
    ├── in/web/MediaController.java         # POST(MultipartFile), GET(streaming)
    └── out/s3/S3ObjectStorageAdapter.java  # AWS SDK v2 S3Client(MinIO endpoint override, path-style)
        └── config/S3Config.java            # S3Client 빈, StorageProperties
```

### 검증/규칙
- 허용: `contentType`이 `image/`로 시작. 한도: `storage.max-image-bytes`(기본 5_242_880).
- 키: `images/{UUID}.{ext}` — ext는 contentType 매핑(jpeg→jpg, png→png, webp→webp, gif→gif), 미상이면 `bin`. 원본 파일명 미신뢰(M1.4).
- 공개 URL: `${storage.public-base-url}/api/v1/media/images/{key}`. `public-base-url` 미설정 시 상대경로 반환.

### 포트 경계
- `ObjectStoragePort`는 S3/파일시스템 등 구현 무관. 어댑터만 교체하면 저장소 변경 가능(헥사고날).

## 3. S3 어댑터(MinIO) 설정
AWS SDK v2 `software.amazon.awssdk:s3`. `S3Client`:
- endpoint override: `storage.s3.endpoint`(컨테이너: `http://host.docker.internal:9000`)
- region: `us-east-1`, credentials: static(`minioadmin`/`minioadmin`)
- `forcePathStyle(true)` (MinIO 필수)
- 시작 시 `ensureBucket("gole")`(없으면 createBucket).

`application.yml`:
```yaml
storage:
  s3:
    endpoint: ${STORAGE_S3_ENDPOINT:http://host.docker.internal:9000}
    access-key: ${STORAGE_S3_ACCESS_KEY:minioadmin}
    secret-key: ${STORAGE_S3_SECRET_KEY:minioadmin}
    region: ${STORAGE_S3_REGION:us-east-1}
    bucket: ${STORAGE_S3_BUCKET:gole}
  public-base-url: ${STORAGE_PUBLIC_BASE_URL:https://gole.kscold.com}
  max-image-bytes: ${STORAGE_MAX_IMAGE_BYTES:5242880}
```
멀티파트 한도도 Spring에 맞춰 설정(`spring.servlet.multipart.max-file-size/max-request-size`).

## 4. API
| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/v1/media/images` | multipart `file` → `{ "key": "images/<uuid>.ext", "url": "https://.../api/v1/media/images/<uuid>.ext" }` |
| GET | `/api/v1/media/{*key}` | 객체 스트리밍(Content-Type 설정, Cache-Control). key 예: `images/<uuid>.ext` |

- 공개 URL = `${storage.public-base-url}/api/v1/media/{key}` (key 자체가 `images/...` 를 포함하므로 프리픽스에서 중복하지 않음).
- 키에 `/`가 포함되므로 GET은 `/{*key}`로 매핑하고 와일드카드 경로를 키로 사용.

## 5. 프론트 — FSD
- `shared/api/upload-client.ts`: `uploadImage(file: File): Promise<{ key: string; url: string }>` — `FormData` + `fetch`(JSON 클라이언트와 분리, Content-Type 자동). `shared/api`의 공개 API에 export.
- `features/create-listing`: 대표 이미지 입력을 `<input type="file" accept="image/*">`로 교체. 선택 시 업로드 → 미리보기 + `photoUrls=[url]`.
- `features/create-post`: 동일 패턴으로 `imageUrls=[url]`.
- 업로드 중 `submitting`/`uploading` 상태로 제출 차단(M3.3). 실패 시 메시지.

## 6. 보안 / 트레이드오프
- 현재 업로드는 비인증 공개 엔드포인트(MVP). 후속: 세션 토큰 검증 + 사용자별 레이트리밋 + 바이러스/이미지 검증. design에 명시하고 tasks 백로그로 둔다.
- 백엔드 스트리밍 방식은 단순/안전(인프라 무변경)하나 대용량 트래픽 시 비효율 → 후속에 presigned URL/CDN 전환 여지(포트 유지로 어댑터 교체 가능).
- 이미지 메타데이터는 별도 DB에 저장하지 않는다(객체 스토리지가 진실원천). 필요 시 후속 도입.
