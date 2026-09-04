# MinIO 오브젝트 스토리지

GoLe에서 이미지/파일 업로드에 사용하는 S3 호환 스토리지다. 로컬 개발은 루트
`docker-compose.yml`, 실제 운영은 GCP `gole-production` VM의
`infra/gcp/docker-compose.yml`에 포함된 전용 MinIO를 사용한다. 예전 Mac Homebrew
MinIO와 `minio.kscold.com`은 GoLe 운영 저장소가 아니다.

## 접속 정보

| 항목 | 값 |
|---|---|
| 로컬 Access Key 기본값 | `minioadmin` |
| 로컬 Secret Key 기본값 | `minioadmin` |
| 로컬 Spring Boot → Docker MinIO | `http://localhost:9000` |
| 운영 Backend → 운영 MinIO | `http://minio:9000` |
| 로컬 콘솔 UI | `http://localhost:9001` |
| 운영 호스트 바인딩 | 없음(Compose internal `data` network 전용) |
| 운영 데이터 | GCP 영구 디스크의 Docker volume `minio_data` |

## GoLe 버킷

GoLe는 `STORAGE_S3_BUCKET`으로 지정한 전용 버킷(기본 `gole`)만 사용한다. 로컬과 운영
Compose의 `minio-init`이 버킷을 멱등 생성하고 anonymous policy를 `none`으로 유지한다.
브라우저가 MinIO에 직접 접근하지 않으며, 공개 가능한 파일도 GoLe Backend가 권한을 확인한
뒤 same-origin `/api/v1/media/**`로만 proxy한다. 다른 프로젝트의 버킷이나 자격증명을
공유하지 않는다.

## Spring Boot 연결

`application.yml` 또는 환경변수:
```yaml
storage:
  s3:
    endpoint: ${STORAGE_S3_ENDPOINT:http://localhost:9000}
    access-key: ${STORAGE_S3_ACCESS_KEY:minioadmin}
    secret-key: ${STORAGE_S3_SECRET_KEY:minioadmin}
    region: ${STORAGE_S3_REGION:us-east-1}
    bucket: ${STORAGE_S3_BUCKET:gole}
```

의존성 (`build.gradle.kts`):
```kotlin
implementation("io.awspring.cloud:spring-cloud-aws-starter-s3")
```

## MinIO Client (mc) 명령

```bash
# 버킷 목록
mc ls local

# 파일 업로드
mc cp ./image.jpg local/gole/images/image.jpg

# 버킷 생성
mc mb local/gole

# 파일 목록
mc ls local/gole
```

## 로컬 개발 서비스 관리

```bash
pnpm infra:up
pnpm infra:down
```

로컬 MinIO 데이터는 Docker 볼륨 `minio_data`에 보존된다. 백엔드의 local 프로필은
`STORAGE_S3_ENDPOINT=http://localhost:9000`을 기본으로 사용하며, `gole` 버킷은 백엔드가
시작할 때 자동 생성한다.

포트 충돌이 있으면 루트 `.env.example`을 `.env`로 복사해 `MINIO_API_PORT`와
`MINIO_CONSOLE_PORT`를 변경한다. 사용자/비밀번호를 변경하는 경우 같은 `.env`의
`MINIO_ROOT_USER`/`MINIO_ROOT_PASSWORD`를 백엔드 local 프로필도 함께 사용한다.

## 운영 서비스 확인 (GCP VM에서)

```bash
cd /app
sudo docker compose \
  --env-file /etc/gole/infra.env \
  --env-file /etc/gole/gole.env \
  -f infra/gcp/docker-compose.yml ps minio minio-init
sudo docker compose \
  --env-file /etc/gole/infra.env \
  --env-file /etc/gole/gole.env \
  -f infra/gcp/docker-compose.yml logs --tail=100 minio minio-init
```

## 주의사항

- 운영 자격증명은 GCP Secret Manager에서 주입하며 예제 기본값을 사용하지 않음.
- 운영 Backend는 Compose 내부 서비스명 `http://minio:9000`으로만 접근함.
- 운영의 9000/9001 포트는 host에 publish하지 않고 internal `data` network에서만 사용함.
- 로컬 Compose 이미지는 팀원 간 재현성을 위해 digest로 고정한다.
- 공개 가능한 파일도 GoLe Backend authorization을 거쳐 `/api/v1/media/**` 경로로 제공함.
