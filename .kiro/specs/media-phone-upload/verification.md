# 휴대폰 HEIC/HEIF 업로드 — 구현 및 검증

2026-09-08, OWNER C, task_6f41b0615900 / ctx_250276788323.

## 현재 단계

구현·로컬 실제 native 변환 검증 완료. 운영 배포 및 실행 중 개발 서버 재시작은 하지 않았다. 현재 실행 중 서버가 이 코드를 제공한다고 주장하지 않는다. 최종 API 런타임 전체 이미지 배포 검증은 통합 담당자의 다음 단계다.

기존 ImageProcessorPort와 MediaService API 시그니처를 유지한다. 공통 media 경로를 사용하는 프로필/피드/브릭은 동일한 정규화 결과를 받는다. HEIC/HEIF 입력의 결과 contentType은 image/jpeg이며 저장 확장자도 .jpg다. 소비자는 입력 MIME을 보존하지 말고 SanitizedImage.contentType을 사용해야 한다. 현재 존재하는 피드/판매글 업로드 폼 accept와 안내를 갱신했다. 공통 upload-client는 이미 FormData와 서버 오류 메시지를 전달하므로 변경하지 않았다.

## 안전 처리

- 확장자 대신 bounded ISO BMFF ftyp에서 HEVC still-image brand heic/heix를 확인한다. AVIF/sequence brand와 단독 mif1은 거부한다. JPEG/PNG 기존 signature 검증 유지.
- image/heif는 image/heic로 정규화한다. 빈 MIME/octet-stream은 HEIF signature가 확인된 경우에만 허용한다. HEIC를 PNG로 위장한 업로드는 거부한다.
- HEIF irot/imir는 libheif가 적용한다. Pillow의 HEIF EXIF orientation 초기화와 중복 회전하지 않는다. JPEG EXIF orientation 1–8은 서버에서 적용한다.
- fresh RGB 버퍼로 JPEG 재인코딩하고 Java ImageIO에서 다시 검증·재인코딩한다. EXIF/GPS/ICC 및 원본 metadata를 저장하지 않는다.
- 기본 입력/결과 각각 5,242,880 bytes, 8192×8192 이내 및 16,000,000 pixels. 48MP 원본은 제한 초과로 거부한다. 단일 still image만 허용하며 동영상/다중 프레임 확장은 포함하지 않는다.
- JVM당 동시 native 변환 2개, 초과는 즉시 재시도 안내. 자식 프로세스 address space 1GiB, CPU 8초, 파일 32MiB, FD 32, core dump 금지. 전체 timeout 기본 10초(설정 상한 15초), timeout 시 descendants/parent 종료와 임시 파일 정리.
- shell 없이 고정 인자로 실행한다. 사용자 URL/파일명을 실행 경로에 사용하지 않는다. 자식 환경을 비워 서버 credential을 전달하지 않고 stderr/stdout도 노출하지 않는다.
- 프로세스 자원 제한은 별도 OS 보안 sandbox가 아니다. 배포 메모리는 JVM 최대 1.5GiB와 최대 2개 native 프로세스/기타 메모리를 고려해야 한다.
- 디코더가 없거나 libheif가 1.23.4 미만이면 원본 저장 없이 fail closed하고 JPEG/PNG로 변환해 다시 올리라는 오류를 반환한다.

## 배포 / 로컬 설정

infra/gcp/docker/api.Dockerfile에 media-build 및 media-decoder 단계를 추가했다. Python 경로 기본값 /opt/heif/bin/python은 이미지 내부에서 제공한다. Pillow 12.3.0 / pillow-heif 1.7.0을 고정하며 pillow-heif는 libheif 1.23.4에 대해 source build한다. 공개 wheel의 bundled libheif 1.23.3을 사용하지 않는다. libheif source SHA256: ce7739356637b7371dcc0ae876027f6f692de9c9ace8cd0e9ed8d79a01ea61fe.

설정은 서버 환경변수 STORAGE_HEIF_PYTHON(신뢰하는 절대 실행 경로), STORAGE_HEIF_TIMEOUT(예: 10s)이며 자격증명은 필요 없다. Docker 외 실행은 동일한 patched decoder 설치 경로가 있어야 한다. 이번 작업은 전역 설치·환경파일 변경·서버 재시작을 하지 않았다. build/heif-venv는 합성 fixture 생성용의 무시된 로컬 도구이고 bundled 1.23.3이므로 서비스 디코더로 사용하면 안 된다.

이미지 빌드 자체에서 실제 HEIC fixture를 변환하고 크기/색상방향/EXIF 제거를 assert한다. decoder target은 로컬 Linux arm64에서 빌드 및 실행했다. apt 시스템 패키지는 Jammy 저장소를 따르므로 전체 이미지의 bit-for-bit 재현성을 주장하지 않는다.

## 완료 증거

- `GOLE_HEIF_TEST_PYTHON=/Users/kscold/Desktop/GoLe/apps/api/build/heif-docker-python ./apps/api/gradlew -p apps/api test --tests 'com.gole.api.media.*'`: BUILD SUCCESSFUL, XML 합산 55 tests / 0 failures / 0 errors / 0 skipped.
- 실제 HEIC → MediaService → JPEG 저장 테스트는 mock decoder가 아니다. 실제 Docker media-decoder target을 network none / memory 1g / cpus 1로 실행했다. storage만 fake이며 native 변환은 실제다.
- fixture 80×48 + 회전 결과 48×80, 위쪽 빨강/아래쪽 파랑, EXIF/GPS description 제거 및 저장 MIME/.jpg 검증.
- corrupt HEIF, pixel cap, signature 위장, MIME aliases, decoder 부재, 입력 byte cap, timeout/임시파일 정리, JPEG EXIF orientation 및 기존 PNG/APNG/GIF/WebP 회귀 포함.
- `docker build --target media-decoder -f infra/gcp/docker/api.Dockerfile -t gole-media-decoder:test .`: 성공. native libheif 1.23.4 assert 및 빌드 중 실제 fixture 변환 성공.
- 소유 Java 11개 파일 Spotless, 수정한 두 웹 폼 Prettier 및 ESLint, `pnpm --filter web typecheck`: 성공.
- `git diff --check`: 성공. 테스트 도구/venv/build 로그는 apps/api/build 아래 gitignored. 다른 worker 파일 변경 없음.

실제 native 테스트는 GOLE_HEIF_TEST_PYTHON이 없으면 skip되므로 이후 검증에서 반드시 해당 변수를 설정하거나 Docker 빌드 smoke 검사를 실행해야 한다. 테스트 실행용 Docker wrapper는 아래 형태이며 호스트/UID는 실행 환경에 맞춘다.

```sh
#!/bin/sh
media_temp_dir=$(/usr/bin/dirname "$2")
exec /usr/local/bin/docker --host unix:///Users/kscold/.docker/run/docker.sock run --rm --network none --memory 1g --cpus 1 --user 501:20 -v "$media_temp_dir:$media_temp_dir" gole-media-decoder:test /opt/heif/bin/python "$@"
```

## Fixture 출처와 공식 근거

apps/api/src/test/resources/media/phone-oriented-gps.heic는 이 작업에서 생성한 946-byte 합성 색상 이미지다. 실제 사진/사람/개인 위치정보가 없고 GPS 값은 테스트 상수다. 재생성 스크립트는 이 디렉터리의 generate-fixture.py에 있다. 외부 사진의 저작권이나 라이선스는 없다.

- https://pillow-heif.readthedocs.io/en/stable/installation.html — 시스템 libheif 및 source build
- https://pillow-heif.readthedocs.io/en/latest/workaround-orientation.html — HEIF orientation 처리
- https://pillow-heif.readthedocs.io/en/stable/reference/HeifImage.html — HEIF API
- https://github.com/strukturag/libheif/releases — 1.23.4 security fixes
- https://github.com/strukturag/libheif/blob/master/SECURITY.md — native untrusted input 보안

라이브러리 라이선스: Pillow MIT-CMU, pillow-heif BSD-3-Clause, libheif LGPLv3 (다운로드한 1.23.4 COPYING 확인). 배포 이미지 외부 재배포 시 해당 라이선스/소스 제공 의무를 배포 절차에 반영한다.
