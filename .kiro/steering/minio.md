# MinIO 오브젝트 스토리지

GoLe에서 이미지/파일 업로드가 필요할 때 사용하는 S3 호환 스토리지.
Mac Mini 호스트에서 Homebrew로 직접 실행 중 (Docker 컨테이너 아님).

## 접속 정보

| 항목 | 값 |
|---|---|
| Access Key | `minioadmin` |
| Secret Key | `minioadmin` |
| S3 API (컨테이너 내부) | `http://host.docker.internal:9000` |
| S3 API (호스트 내부) | `http://localhost:9000` |
| 콘솔 UI | `https://minio.kscold.com` |
| 데이터 경로 | `/opt/homebrew/var/minio` |
| 로그 | `/opt/homebrew/var/log/minio.log` |

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

## 서비스 관리 (Mac 호스트에서)

```bash
# 상태 확인
brew services info minio

# 재시작
brew services restart minio

# 로그 확인
tail -f /opt/homebrew/var/log/minio.log
```

## 주의사항

- MinIO는 Docker 컨테이너가 아니라 Mac 호스트 프로세스임.
- 컨테이너(ubuntu-gole)에서 접근 시 반드시 `host.docker.internal:9000` 사용.
- `minio.kscold.com`은 콘솔 UI(9001)만 프록시 — S3 API(9000)는 직접 접근 불가.
- S3 API를 외부에 열어야 할 경우 별도 nginx 설정 필요.
