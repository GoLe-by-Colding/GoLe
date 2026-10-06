# 컬렉션 자산 추이 — Spec

> 레고 차별화 3순위. `EstimateCollectionValueUseCase`가 "지금 얼마"는 내지만 시간축이 없다.
> 하루 한 번 스냅샷을 남겨 "내 레고 자산" 추이를 그린다.

## 왜 필요한가

- 수집가에게 컬렉션은 자산이다. "이번 달 +4%"는 재방문 이유이고, 단종 이벤트와 맞물리면 공유 동기가 된다.
- 지금 값을 다시 계산하는 것만으로는 과거를 복원할 수 없다(체결 데이터가 바뀌므로). 그날의 값을 남겨야 한다.

## Requirements (EARS)

- H1. 시스템은 사용자별·날짜별(Asia/Seoul) 스냅샷을 남긴다: `ownedValue`, `ownedCount`(보유 세트 수),
  `pricedCount`(시세가 잡혀 값에 들어간 세트 수), `capturedAt`. 같은 날 다시 찍으면 덮어쓴다(유일 키
  `{userId, date}`).
- H2. 매일 `gole.collection.value-snapshot.cron`(기본 `0 30 4 * * *`, zone `Asia/Seoul`)에 보유(`OWNED`)
  항목이 있는 모든 사용자를 찍는다. `gole.collection.value-snapshot.enabled`(기본 `true`)로 끈다.
  사용자 하나의 실패가 나머지를 막지 않는다. 결과 요약을 로그로 남긴다.
- H3. `GET /api/v1/collections/{userId}/value-history?days=90`(1~365, 기본 90) → `{points: [{date,
  ownedValue, ownedCount, pricedCount}]}` 날짜 오름차순. 경로 `userId`는 무시하고 세션 계정을 쓴다
  (기존 컬렉션 API와 같다).
- H4. WHEN H3을 부르면, 시스템은 **오늘 스냅샷을 먼저 갱신**한다. 스케줄러가 돌기 전에 처음 들어온
  사용자도 바로 점 하나를 본다.
- H5. 값 산출은 기존 추정가와 같다(보유 세트별 최신 체결가 합, 시세 없는 세트는 0). 이를 위해 서비스에
  `valuate(userId) → CollectionValuation(ownedValue, ownedCount, pricedCount)`를 두고 기존
  `estimateOwnedValue`는 그 값을 쓴다.
- H6. 스냅샷은 400일 보관한다(`capturedAt` TTL 인덱스).
- H7. 계정 삭제 시 스냅샷을 지운다.

## Design

- collection 컨텍스트 안. domain `CollectionValueSnapshot`, `CollectionValuation`.
- port-in `GetCollectionValueHistoryUseCase.history(userId, days)`,
  `RecordCollectionValueSnapshotsUseCase.recordAll(Instant now)` → 요약(대상·성공·실패 수).
- port-out `CollectionValueSnapshotRepositoryPort.upsert(snapshot)`·`findRange(userId, from, to)`,
  `CollectionRepositoryPort.findUserIdsWithStatus(OWNED, afterUserId, limit)`(커서 페이지 — 새 쿼리).
- 스케줄러 `CollectionValueSnapshotScheduler` — 첫 `cron` 잡이다. 운영은 백엔드 컨테이너 하나라 분산 잠금을
  두지 않는다. upsert라 두 번 돌아도 결과가 같다.
- persistence `collection_value_snapshots` — 유일 `{userId:1, date:1}`, TTL `capturedAt` 400일.

### 프론트

- core `collection`: `CollectionValuePoint` 타입, `fetchCollectionValueHistory(userId, days)`.
- 컬렉션 화면 "보유 추정가" 카드 아래에 추이 차트(`shared/ui` `LineChart` 재사용), 30·90·365일 전환,
  기간 첫 점 대비 증감. 점이 2개 미만이면 "내일부터 추이가 쌓여요". `pricedCount < ownedCount`면
  "시세가 잡힌 세트 n/m개 기준"을 함께 적는다.

## 알아둘 점

- 추정가는 시세 근거 정책(기본 `platform_payment`만)을 따른다. 운영에서는 체결이 적어 대부분 0이고,
  추이도 한동안 평평하다. 화면이 `pricedCount`를 드러내는 이유다. 데모 시드는 로컬에서만 값이 잡힌다.

## 범위에서 뺀 것

- 컬렉션 항목의 상태(미개봉·개봉) 반영 — `CollectionItem`에 상태 필드가 없다.
- 공유 이미지(OG) 생성.
- 앱 네이티브 차트 — 앱은 컬렉션 화면이 없다.

## Tasks

- [ ] B1 domain·`valuate`·port
- [ ] B2 persistence(upsert·범위 조회·사용자 커서 쿼리·인덱스)
- [ ] B3 스케줄러 + 조회 API(오늘 갱신 포함) + 단위 테스트
- [ ] B4 계정 삭제 정리, 통합 테스트(upsert 멱등·범위)
- [ ] F1 core API·타입, 컬렉션 차트
- [ ] V1 로컬 실검증: 보유 추가 → 조회 시 오늘 점 생성, 스케줄러 수동 실행
