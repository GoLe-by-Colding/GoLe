# 사용자 미디어 접근·삭제 수명주기

사용자 업로드 버킷은 private이다. 브라우저가 MinIO, presigned URL, CDN을 직접 읽는 경로는 두지
않으며 모든 조회는 same-origin `GET /api/v1/media/{key}`와 Mongo 접근 원장을 함께 통과한다.

## 신뢰 경계

1. 업로드 API는 인증 계정을 `ownerId`로 고정하고 UUID 객체 키를 만든다.
2. 입력 바이트는 최대 5MB이며 JPEG/PNG 정지 이미지만 허용한다. 저장 전 헤더의 폭·높이·총
   픽셀을 각각 8192·8192·1600만 이하로 확인하고, 픽셀로 완전 디코딩한 뒤 표준 RGB/ARGB에
   메타데이터 없이 재인코딩한다. 원본 EXIF/GPS/ICC/코멘트 바이트는 저장하지 않는다. JDK에서
   동일 안전 계약을 보장할 수 없는 GIF/WebP와 APNG는 정지 여부와 관계없이 거부한다.
3. 객체 저장 뒤 `media_assets`에 `STAGED` 원장을 기록한다. 원장 기록 실패 시 저장 객체를 즉시
   보상 삭제하며, 삭제 실패 객체도 원장이 없어 조회할 수 없다.
4. 매물은 `photoKeys`, 커뮤니티는 `imageKeys`만 입력받는다. URL, 상대 공개 경로, 타인 키,
   만료 키, 중복 키는 모두 `MEDIA_ASSET_NOT_ATTACHABLE`로 거부한다.
5. 콘텐츠 저장과 `STAGED -> PUBLIC/PRIVATE` 전이는 같은 Mongo 트랜잭션이다. 한 키는 한 소유자와 한
   콘텐츠에만 연결된다.
6. 응답에서만 키를 `/api/v1/media/{key}`로 바꾼다. 레거시 외부 URL과 올바르지 않은 경로는
   응답에서 제거해 추적 픽셀과 임의 원격 이미지를 즉시 격리한다.
7. 익명 조회는 `PUBLIC`만 허용한다. 로그인 조회는 본인의 만료 전 `STAGED`와 연결된 `PRIVATE`를
   허용하며 PRIVATE에는 staging TTL을 적용하지 않는다. `REVOKED`,
   `QUARANTINED`, orphan, 직접 파생물 경로는 객체가 남아 있어도 404이다. 존재 여부를 숨기기
   위해 타인 STAGED도 403 대신 404로 응답한다.
8. 사용자 미디어 응답은 `Cache-Control: no-store`이다. 프론트 CSP의 `img-src`에는 `https:`
   와일드카드가 없고 self, data/blob, 명시적인 개발 API origin만 허용한다.
9. 썸네일 폭은 240/480/960/1600만 허용한다. 파생물 저장 직후 원본 조회 권한을 다시 확인하고,
   resize 도중 revoke됐다면 새 파생물을 즉시 보상 삭제한다. 보상 삭제도 실패하면 이미 COMPLETED인
   journal까지 같은 키의 PENDING으로 되돌려 worker가 다시 물리 삭제한다.
10. 관리자 카탈로그 이미지도 `/api/v1/media/catalog/*.svg`만 저장한다. 외부 URL과 사용자 미디어
    경로는 입력 단계에서 거부하고 레거시 외부 값은 도메인 응답에서 null로 격리한다.

## 삭제 계약

매물·게시글 삭제/운영자 내림 및 게시글 사진 교체는 콘텐츠 상태 변경, 원장 `REVOKED`,
`media_deletion_journal` outbox 삽입을 한 Mongo 트랜잭션으로 처리한다. 커뮤니티 삭제 문서는
상태·식별용 최소 필드만 남기고 본문, 이미지 참조, 좋아요 계정 목록을 즉시 비운다. 거래 관련
법정 보존 원장은 이 미디어 원장과 분리하며 미디어 바이트를 복제하지 않는다.

삭제 worker는 원본과 `derivatives/{원본 키}/` 전체를 멱등 삭제한다. 실패는 지수 backoff로
재시도하고 설정 횟수 뒤 `DEAD_LETTER`로 둔다. provider 오류 원문이나 키는 로그에 남기지 않는다.
`COMPLETED` 항목은 TTL로 지우지 않고 복구용 삭제 journal로 보존한다. 연결되지 않은 STAGED는
기본 24시간 뒤 같은 폐기 흐름에 들어간다.

운영 확인 항목:

- `media_deletion_journal.status=DEAD_LETTER` 수를 경보하고 원인을 고친 뒤 `PENDING`으로만 재개함
- `media_assets.status=STAGED`의 최고 나이가 24시간을 넘는지 경보함
- MinIO 익명 정책이 `none/private`인지 배포마다 `minio-init`으로 멱등 확인함
- `scripts/__tests__/media-storage-boundary.test.sh`를 CI에서 실행함

## 기존 데이터 이행

소유 원장이 없던 시기의 URL에서 업로더를 추론해 공개 전환하지 않는다. URL을 게시한 계정과
파일을 업로드한 계정이 같다는 증거가 없기 때문이다. 첫 전환은 다음 순서를 지킨다.

1. Mongo와 MinIO 일관 스냅샷을 만든 뒤 외부 쓰기를 잠근다.
2. `listings.photoUrls`, `posts.imageUrls`를 집계하되 원문 URL은 로그로 반출하지 않고 유형별
   개수와 해시만 기록한다.
3. 패키지 SVG(`catalog/*.svg`, `community/*.svg`)는 유지한다.
4. 이미 `media_assets`에 같은 owner/target의 PUBLIC 원장이 있는 canonical 키만 유지한다.
5. 외부 URL, 원장 없는 키, 여러 콘텐츠에 재사용된 키, owner/target이 다른 키는 콘텐츠 참조에서
   제거하고 `QUARANTINED`로 둔다. 검증할 수 없는 객체는 공개하지 않으며 보존 사유가 없으면
   삭제 outbox로 넘긴다.
6. 격리 개수와 대상 콘텐츠 개수만 대조한 뒤 쓰기를 다시 연다. 원문·파일은 운영 로그나 Discord로
   보내지 않는다.

현재 새 생성 API는 키 전용이라 이행 뒤 URL 값이 다시 생기지 않는다. 자동 owner 추론 backfill은
금지한다. 실제 운영 데이터가 0건이어도 위 집계가 0임을 배포 기록에 남긴다.

## 스냅샷 복구와 삭제 부활 방지

동일 boot disk의 오래된 스냅샷만 복원하면 스냅샷 이후 완료된 삭제 journal도 함께 사라진다.
따라서 최신 `media_deletion_journal`을 별도 암호화 저장소에 append-only로 내보내는 백업이
준비되기 전에는 오래된 프로덕션 스냅샷 복구를 완료 처리하면 안 된다.

복구 순서는 고정한다.

1. 외부 80/443 트래픽과 background worker를 닫은 상태로 데이터 스냅샷을 복구한다.
2. 별도 저장소의 최신 삭제 journal을 Mongo에 멱등 import한다.
3. 복구 스냅샷 시각을 ISO-8601로 `GOLE_MEDIA_REPLAY_COMPLETED_SINCE`에 넣는다.
4. 애플리케이션 worker가 그 시각 이후 COMPLETED 원본·파생물을 모두 재삭제하고 PENDING을
   소진했는지 확인한다.
5. 삭제 대상 GET이 전부 404이고 DEAD_LETTER가 0일 때만 외부 트래픽을 연다.
6. 정상화 뒤 replay 환경변수를 비우고 재배포한다.

별도 journal 백업·복원 자동화와 복원 연습 테스트가 아직 없다면 이는 스냅샷 기능의 P1 운영
차단 조건이며, 문서 절차만으로 안전하다고 간주하지 않는다.
