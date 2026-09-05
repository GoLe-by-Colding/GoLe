# 러닝북 — 증상별 대응

**증상에서 출발한다.** 원인을 안다면 이 문서가 아니라 해당 스펙이나 steering 문서를 본다.

여기 적힌 것 대부분은 실제로 시간을 잡아먹었던 것들이다. 공통점이 하나 있다 —
**실패가 실패처럼 보이지 않는다.** 초록으로 보이거나, 조용히 넘어가거나, 엉뚱한 곳을 가리킨다.

---

## 초록인데 실은 안 돈 것

가장 위험한 부류다. "통과했다"고 보고하기 전에 확인한다.

### Gradle 테스트가 실행되지 않았다

입력이 바뀌지 않으면 `test` 태스크는 `UP-TO-DATE`로 **건너뛴다.** 로그에 성공처럼 보인다.

```bash
cd apps/api && ./gradlew cleanTest test      # 또는 --rerun-tasks
# 실제 실행 여부는 결과 XML로 확인한다
ls apps/api/build/test-results/test/*.xml | wc -l
```

### 테스트가 스킵됐다

스킵은 실패가 아니라 **초록**이다. 개수를 본다.

- 관리자 권한 E2E 는 `GOLE_ADMIN_EMAIL`/`GOLE_ADMIN_PASSWORD` 가 없으면 조용히 skip 된다.
  이 값들은 `ci.yml` 에 없다 — **"CI 통과 = 권한 검증됨"이 아니다.**
- E2E 스킵 수가 평소와 다르면 환경변수를 먼저 의심한다.

### 앱 라우트 오타를 CI 가 못 잡는다

expo-router 의 라우트 타입(`.expo/types/router.d.ts`)은 `expo start` 가 만들고 `.expo/` 는
gitignore 대상이다. **CI 에는 그 파일이 아예 없어** 잘못된 라우트 문자열이 typecheck 를 통과한다.
반대로 로컬에서는 라우트를 추가한 뒤 dev 서버를 재시작하기 전까지 **낡은 타입이 오탐**을 낸다.

→ 라우트를 늘렸으면 `pnpm dev:mobile` 을 한 번 돌려 타입을 다시 만든다.

---

## 배포·기동

### 배포됐는데 기능이 안 열린다

**"배포됨"과 "기능이 열림"은 다르다.** `NEXT_PUBLIC_*` 은 **빌드 시점에 번들로 인라인**된다.
pm2 `--update-env` 로는 반영되지 않는다 — 프론트를 **재빌드**해야 한다.

운영은 `NEXT_PUBLIC_PORTONE_*` 없이 빌드되어 결제 버튼이 disabled 다. 회귀가 아니라 의도된 구성이다.
확인하려면 배포된 JS 번들에서 키 문자열을 찾아본다.

소셜 로그인 키는 백엔드 런타임 값이라 재빌드가 필요 없다 — `NEXT_PUBLIC_*` 만 다르게 동작한다.

### main 에 머지했는데 CD 가 안 돈다

두 겹의 게이트가 있다.

1. **CI 가 깨지면 CD 는 아예 안 돈다.** PR 에서 초록이어도 `main` **푸시**여야 한다.
2. `cd.yml` 은 `vars.GOLE_PRODUCTION_HOST_READY == 'true'` 를 요구한다. **이 변수가 없으면
   배포 job 자체를 만들지 않고 `skipped` 로 끝난다.** 호스트 이관 전까지의 의도된 안전장치다.

`gh run list --branch main --workflow CD` 로 `skipped` 인지 `failure` 인지 먼저 구분한다.

### `/actuator/health` 가 503 인데 앱은 멀쩡하다

`spring.mail.host` 가 정의되면 Spring 이 `MailHealthIndicator` 를 자동 등록한다. SMTP 가 없는
환경에서는 **그 지표 하나가 health 전체를 DOWN** 으로 만들고, 배포 헬스 게이트가 정상 배포를
실패로 처리한다.

→ `MANAGEMENT_HEALTH_MAIL_ENABLED=false`

### 백엔드가 안 뜬다

- `pnpm infra:up` 을 먼저 돌렸는가. Mongo 는 replica set `rs0` 로 초기화되어야 멀티도큐먼트
  트랜잭션이 된다. `docker exec gole-mongo-1 mongosh --quiet --eval "rs.status().ok"` 가 `1` 이어야 한다.
- 결제 설정이 없는데 운영 프로필인가. `PaymentConfigurationGuard` 가 스텁 기동을 **일부러 막는다.**

---

## 화면·데이터

### 이미지가 안 뜬다

이미지 공개 주소는 **MinIO 가 아니라 API 원점**이다(`STORAGE_PUBLIC_BASE_URL`).
MinIO 주소를 넣으면 브라우저 CSP `img-src` 가 그 원점을 막아 조용히 깨진다.

### Next 가 3001 로 떴다

3000 이 이미 점유된 것이다. 엉뚱한 화면을 검증하게 되니 포트부터 확인한다.

```bash
lsof -ti:8080 -ti:3000 -ti:8081
```

### E2E 가 시작부터 실패한다

- 인프라와 **API 서버는 직접 띄워야 한다**(web dev 서버만 Playwright 가 자동 기동).
- 쓰기 플로우(`create-listing`·`purchase`)는 서버가 검증하는 실제 세션이 필요하다 →
  `pnpm e2e:seed` 를 한 번 돌린다(멱등).
- 시드는 `gole_e2e` DB 를 쓴다. **API 도 e2e 프로필이어야 한다** → `pnpm dev:api:e2e`.
  `pnpm dev:api`(local 프로필)로 띄우면 DB 가 달라 전부 어긋난다.
- 첫 실행의 flaky 는 대개 dev 서버 콜드 스타트다. 같은 파일을 `--repeat-each=2` 로 다시 돌려본다.

---

## 결제

### 결제가 전부 "수동 검토"로 떨어진다

서버는 원장의 **채널 키로 어느 허용 채널인지 정한 뒤 그 채널이 낼 수 있는 결제수단만** 인정한다.
프론트의 `NEXT_PUBLIC_PORTONE_*` 과 백엔드의 `PORTONE_*` 이 다르면 결제창은 열리지만
검증에서 전부 `PAYMENT_REVIEW` 로 간다. **실패가 아니라 적체로 나타나므로 조용하다.**

### 카드 결제를 LIVE 로 열고 싶다

`PORTONE_CHANNEL_TYPE` 은 채널별이 아니라 **전역 하나**다. 카드만 LIVE 로 열 수 없고
카카오페이도 함께 전환된다. 현재 카드 채널은 이니시스 공용 테스트 MID 라 실계약이 없다.

---

## 모바일

### 푸시가 안 온다

1. **Expo Go 로는 안 된다.** FCM 토큰은 **개발 빌드의 실기기**에서만 발급된다.
   시뮬레이터·에뮬레이터도 발급되지 않는다.
2. 백엔드에 `FCM_ENABLED`·`FCM_PROJECT_ID`·자격증명이 있는가. 없으면 no-op 어댑터가 뜬다 —
   **오류가 아니라 조용히 아무것도 하지 않는다.**
3. 로그인 상태인가. 토큰 등록은 세션이 있을 때만 발화한다.
4. **API 키 제한을 의심한다.** Firebase 클라이언트 키는 API 허용 목록이 3개
   (`fcmregistrations`·`firebase`·`firebaseinstallations`)로 좁혀져 있다.
   여기 없는 Firebase 기능을 새로 쓰면 권한 오류가 난다 →
   [외부 서비스 대장](../external-services.md) 참고.

### 앱에서 API 호출이 401 로만 떨어진다

코어는 설정을 **호출 시점**에 읽는다. 부트스트랩(`configureCore`·`setSessionStore`)이
첫 요청보다 먼저 끝났는지 확인한다. 앱은 SecureStore 읽기가 비동기라 부트스트랩 완료 전에는
화면을 띄우지 않는다.

### Android 에서만 서버에 못 붙는다

에뮬레이터의 `localhost` 는 **에뮬레이터 자신**이다. 호스트를 가리키려면 `10.0.2.2` 를 쓴다
(개발 기본값에 이미 반영되어 있다).
