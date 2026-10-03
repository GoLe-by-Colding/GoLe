# MinIO 이미지 (소스 빌드)

2026-10-03, MinIO 공식 이미지가 공개 레지스트리에서 사라졌다.

| 출처 | 상태 |
|---|---|
| `quay.io/minio/minio`·`quay.io/minio/mc` | `no such manifest` (`latest` 포함) |
| `docker.io/minio/minio`·`docker.io/minio/mc` | `denied` (2026-09-12 부터, PR #98 로 quay 이전) |
| `dl.min.io` 릴리스 바이너리 | `410 Gone` |

로컬·운영 호스트에는 캐시된 이미지가 남아 있어 겉으로는 멀쩡했지만, 캐시가 없는 CI 에서 E2E 의
`Start infra` 와 Infra 의 `backup-minio-integration.test.sh` 가 pull 에서 실패했다.

## 무엇을 하나

운영과 **같은 릴리스 태그**를 업스트림 소스(`github.com/minio/minio`, `github.com/minio/mc` — 보관됐지만
공개)에서 컴파일해 GHCR 에 올린다. 구성은 그 태그의 공식 `Dockerfile.release` 를 따르고, 바이너리를
내려받는 대신 컴파일하는 것만 다르다.

| 이미지 | 릴리스 | 업스트림 커밋 |
|---|---|---|
| `ghcr.io/gole-by-colding/minio` | `RELEASE.2025-09-07T16-13-09Z` (운영과 같음) | `07c3a429` |
| `ghcr.io/gole-by-colding/mc` | `RELEASE.2025-08-13T08-35-41Z` | `7394ce0d` |

빌드·푸시는 `.github/workflows/minio-image.yml` 이 한다(이 폴더나 워크플로를 바꿔 push 하거나 수동 실행).
compose 는 워크플로 요약에 찍히는 digest 로 고정한다. 라이선스는 AGPL-3.0 이고 이미지 `/licenses/` 에
원문과 CREDITS 를 넣었다. 소스는 위 태그 그대로다.

## 어디에 쓰나

- 루트 `docker-compose.yml`(로컬 `pnpm infra:up`·CI E2E)
- `infra/gcp/tests/backup-minio-integration.test.sh`(CI Infra)

**운영(`infra/gcp/docker-compose.yml`·`validate-production-compose.py`·`backup-data.sh`)은 아직
`quay.io` digest 그대로다.** 호스트에 캐시돼 있어 지금은 돈다. 운영을 옮기면 CD 가 데이터 이미지
업그레이드 경로(백업 → pull → 재생성 → 동일성 검증)를 타므로 따로 계획한다.
