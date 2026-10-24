#!/usr/bin/env bash
set -Eeuo pipefail

image="${1:?usage: web-image-runtime.smoke.sh IMAGE}"
container=""
cleanup() {
  local status=$?
  trap - EXIT
  if [ -n "$container" ]; then
    if [ "$status" -ne 0 ]; then
      docker logs --tail 100 "$container" >&2 || true
    fi
    docker rm --force "$container" >/dev/null 2>&1 || true
  fi
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# 개발 포트·운영 API 없이 실제 운영 제한으로 최종 이미지의 시작을 검증한다.
container="$(docker run --detach --network none --cpus 0.75 --memory 640m \
  --security-opt no-new-privileges:true "$image")"
docker exec -i "$container" node - <<'NODE'
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { setTimeout: delay } = require('node:timers/promises');

async function verify() {
  assert.equal(process.getuid(), 10001, '운영 웹은 gole 사용자로 실행해야 한다');
  const manifest = require('/app/package.json');
  for (const patch of Object.values(manifest.pnpm.patchedDependencies)) {
    assert.ok(fs.statSync(path.resolve('/app', patch)).isFile(), `패치 누락: ${patch}`);
  }
  for (const directory of ['test-results', 'playwright-report']) {
    assert.ok(!fs.existsSync(`/app/apps/web/${directory}`), `테스트 산출물 포함: ${directory}`);
  }
  let lastError;
  for (let attempt = 0; attempt < 20; attempt += 1) {
    try {
      const response = await fetch('http://127.0.0.1:3000/icon.svg', {
        signal: AbortSignal.timeout(2000),
      });
      assert.equal(response.status, 200);
      assert.match(await response.text(), /<svg[\s>]/);
      console.log('운영 웹 이미지: 일반 사용자·패치 파일·아이콘 HTTP 응답 확인');
      return;
    } catch (error) {
      lastError = error;
      await delay(1000);
    }
  }
  throw lastError;
}
verify().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
NODE
