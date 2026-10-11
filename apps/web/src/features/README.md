# features

사용자 시나리오/행동 단위 슬라이스를 둔다. (예: `follow-seller`, `wishlist-toggle`, `place-bid`)

- entities, shared 만 import 가능
- 슬라이스 외부 노출은 `index.ts` public API로만
- 묶이지 않은 슬라이스가 20개를 넘으면 steiger `fsd/excessive-slicing`이 CI를 막는다. 같은 주제의
  슬라이스는 **슬라이스 그룹**으로 묶는다 — `auth/`, `onboarding/`, `part-request/`. 그룹 폴더에는
  `index.ts`를 두지 않고 슬라이스만 담으며, `@features/<그룹>/<슬라이스>`로 가져온다.
