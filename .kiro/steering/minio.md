# MinIO 오브젝트 스토리지

GoLe에서 이미지/파일 업로드가 필요할 때 사용하는 S3 호환 스토리지.
로컬 개발은 루트 `docker-compose.yml`의 MinIO를 사용하고, 운영 환경은 Mac Mini 호스트의
Homebrew MinIO를 사용한다.

## 접속 정보

| 항목 | 값 |
|---|---|
| Access Key | `minioadmin` |
| Secret Key | `minioadmin` |
| 로컬 Spring Boot → Docker MinIO | `http://localhost:9000` |
| 운영 컨테이너 → 호스트 MinIO | `http://host.docker.internal:9000` |
| 로컬 콘솔 UI | `http://localhost:9001` |
| 운영 콘솔 UI | `https://minio.kscold.com` |
| 운영 데이터 경로 | `/opt/homebrew/var/minio` |
| 운영 로그 | `/opt/homebrew/var/log/minio.log` |

## 현재 버킷 목록

| 버킷 | 용도 |
|---|---|
| `blog` | kscold 블로그 파일 |
| `congbang` | 콩방 프로젝트 파일 |
| `slacord` | 슬라코드 파일 |
| `galjido` | 구 갈지도 (미사용) |

GoLe 전용 버킷이 필요하면 `gole` 버킷을 생성한다:
```bash
mc mb local/gole
mc anonymous set download local/gole   # 공개 읽기 필요 시
```

## Spring Boot에서 연결 (ubuntu-gole 컨테이너 내부)

`application.yml` 또는 환경변수:
```yaml
cloud:
  aws:
    credentials:
      access-key: minioadmin
      secret-key: minioadmin
    region:
      static: us-east-1
    s3:
      endpoint: http://host.docker.internal:9000
      path-style-access: true
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

## 운영 서비스 관리 (Mac 호스트에서)

```bash
# 상태 확인
brew services info minio

# 재시작
brew services restart minio

# 로그 확인
tail -f /opt/homebrew/var/log/minio.log
```

## 주의사항

- 운영 MinIO는 Docker 컨테이너가 아니라 Mac 호스트 프로세스임.
- 운영 컨테이너(ubuntu-gole)에서 접근 시 반드시 `host.docker.internal:9000` 사용.
- 로컬 Compose 이미지는 팀원 간 재현성을 위해 digest로 고정한다.
- `minio.kscold.com`은 콘솔 UI(9001)만 프록시 — S3 API(9000)는 직접 접근 불가.
- S3 API를 외부에 열어야 할 경우 별도 nginx 설정 필요.
