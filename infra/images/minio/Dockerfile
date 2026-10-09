# MinIO 공식 이미지(quay.io/minio/*, docker.io/minio/*)와 dl.min.io 바이너리가 2026-10 공개 배포에서
# 사라졌다. 운영과 같은 릴리스 태그를 업스트림 소스(github.com/minio/minio·mc, 보관 저장소)에서 직접
# 컴파일한다. 구성(환경변수·엔트리포인트·라이선스)은 그 태그의 공식 Dockerfile.release 를 따르고,
# 바이너리를 내려받는 대신 컴파일하는 것만 다르다. 버전을 올릴 때는 태그·커밋·버전 세 값을 함께 바꾼다.

# Go 는 빌드 플랫폼에서 교차 컴파일한다(에뮬레이션 없음).
FROM --platform=$BUILDPLATFORM golang:1.24-alpine@sha256:8bee1901f1e530bfb4a7850aa7a479d17ae3a18beb6e09064ed54cfd245b7191 AS build
ARG TARGETOS
ARG TARGETARCH
ARG MINIO_RELEASE=RELEASE.2025-09-07T16-13-09Z
ARG MINIO_COMMIT=07c3a429bfed433e49018cb0f78a52145d4bedeb
ARG MINIO_VERSION=2025-09-07T16:13:09Z
ARG MC_RELEASE=RELEASE.2025-08-13T08-35-41Z
ARG MC_COMMIT=7394ce0dd2a80935aded936b09fa12cbb3cb8096
ARG MC_VERSION=2025-08-13T08:35:41Z
ENV CGO_ENABLED=0
RUN apk add --no-cache git
WORKDIR /src
# 태그가 옮겨져도 다른 코드를 빌드하지 않도록 커밋까지 맞춘다.
RUN git clone --depth 1 --branch "$MINIO_RELEASE" https://github.com/minio/minio.git minio \
 && test "$(git -C minio rev-parse HEAD)" = "$MINIO_COMMIT" \
 && git clone --depth 1 --branch "$MC_RELEASE" https://github.com/minio/mc.git mc \
 && test "$(git -C mc rev-parse HEAD)" = "$MC_COMMIT"
# 업스트림 Makefile 과 같은 플래그. 버전 인자를 주면 ReleaseTag 가 공식 릴리스와 같아진다.
RUN cd minio && GOOS="$TARGETOS" GOARCH="$TARGETARCH" go build -tags kqueue -trimpath \
      -ldflags "$(go run buildscripts/gen-ldflags.go "$MINIO_VERSION")" -o /out/minio
RUN cd mc && GOOS="$TARGETOS" GOARCH="$TARGETARCH" go build -tags kqueue -trimpath \
      -ldflags "$(go run buildscripts/gen-ldflags.go "$MC_VERSION")" -o /out/mc

FROM alpine:3.22@sha256:5291449c3df73caf6ed85e649dec1b9e818b39a5d8c871e97afc13e9cd5e8fa8 AS minio
ARG MINIO_RELEASE=RELEASE.2025-09-07T16-13-09Z
LABEL org.opencontainers.image.source="https://github.com/GoLe-by-Colding/GoLe" \
      org.opencontainers.image.description="MinIO ${MINIO_RELEASE} built from upstream source" \
      org.opencontainers.image.licenses="AGPL-3.0-only" \
      version="${MINIO_RELEASE}" \
      release="${MINIO_RELEASE}"
ENV MINIO_ACCESS_KEY_FILE=access_key \
    MINIO_SECRET_KEY_FILE=secret_key \
    MINIO_ROOT_USER_FILE=access_key \
    MINIO_ROOT_PASSWORD_FILE=secret_key \
    MINIO_KMS_SECRET_KEY_FILE=kms_master_key \
    MINIO_UPDATE_MINISIGN_PUBKEY="RWTx5Zr1tiHQLwG9keckT0c45M3AGeHD6IvimQHpyRywVWGbP1aVSGav" \
    MINIO_CONFIG_ENV_FILE=config.env \
    MC_CONFIG_DIR=/tmp/.mc
# compose 헬스체크가 curl 을 쓴다(공식 이미지는 정적 curl 을 넣었다).
RUN apk add --no-cache ca-certificates curl
COPY --from=build /out/minio /out/mc /usr/bin/
COPY --from=build /src/minio/CREDITS /src/minio/LICENSE /licenses/
COPY --from=build /src/minio/dockerscripts/docker-entrypoint.sh /usr/bin/docker-entrypoint.sh
EXPOSE 9000
VOLUME ["/data"]
ENTRYPOINT ["/usr/bin/docker-entrypoint.sh"]
CMD ["minio"]

# minio-init·백업 헬퍼는 --entrypoint /bin/sh 로 mc 명령을 잇는다.
FROM alpine:3.22@sha256:5291449c3df73caf6ed85e649dec1b9e818b39a5d8c871e97afc13e9cd5e8fa8 AS mc
ARG MC_RELEASE=RELEASE.2025-08-13T08-35-41Z
LABEL org.opencontainers.image.source="https://github.com/GoLe-by-Colding/GoLe" \
      org.opencontainers.image.description="MinIO Client ${MC_RELEASE} built from upstream source" \
      org.opencontainers.image.licenses="AGPL-3.0-only" \
      version="${MC_RELEASE}" \
      release="${MC_RELEASE}"
RUN apk add --no-cache ca-certificates
COPY --from=build /out/mc /usr/bin/mc
COPY --from=build /src/mc/CREDITS /src/mc/LICENSE /licenses/
ENTRYPOINT ["mc"]
