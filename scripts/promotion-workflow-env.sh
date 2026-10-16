#!/usr/bin/env bash
# runner env 헤더 대신 실행 중 이벤트 파일에서 홍보 설정을 읽는다.
# source한 호출 셸이 값을 사용하며 전역 환경으로 export하지 않는다.
# shellcheck disable=SC2034
GOLE_MINIO_IMAGE=ghcr.io/gole-by-colding/minio:RELEASE.2025-09-07T16-13-09Z@sha256:fd4bf518becea030ae14e629d74ac3c41182c548689c3896937b72439d2d1ef8
GOLE_MC_IMAGE=ghcr.io/gole-by-colding/mc:RELEASE.2025-08-13T08-35-41Z@sha256:47a390e3b5f38ad99c025f1f956b04344feee74f802673f9379452b6e617b69f
SERVICE=false
if [ "$GITHUB_EVENT_NAME" = schedule ] || jq -e '.inputs.service == true or .inputs.service == "true"' "$GITHUB_EVENT_PATH" >/dev/null; then
  SERVICE=true
fi
SHA=$(jq -r '.workflow_run.head_sha // .inputs.sha // ""' "$GITHUB_EVENT_PATH")
if [ "$SERVICE" = true ]; then
  SHA="$GITHUB_SHA"
fi
REUSE=false
TARGET="$RUNNER_TEMP/target"
if [ -f "$RUNNER_TEMP/promotion/reuse" ]; then
  REUSE=true
  TARGET="$GITHUB_WORKSPACE"
fi
