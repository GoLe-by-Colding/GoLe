# GoLe 배포 가이드

## 서버 구성

| 항목 | 값 |
|---|---|
| 컨테이너 이름 | `ubuntu-gole` |
| SSH 포트 | `2223` (host → container 22) |
| SSH 비밀번호 | `gole` |
| API 포트 | `8081` → container `8080` (Spring Boot) |
| Web 포트 | `3004` → container `3000` (Next.js) |
| 공인 도메인 | `https://gole.kscold.com` |
| Docker 소켓 | `unix:///Users/kscold/.colima/default/docker.sock` |

모든 docker 명령은 반드시 앞에 `DOCKER_HOST=unix:///Users/kscold/.colima/default/docker.sock` 를 붙여야 한다.

---

## 표준 배포 (권장 — git 기반 재현 가능 패턴)

`/app` 은 `origin/main` 을 추적하는 정식 git 체크아웃이며, PM2 프로세스는 `ecosystem.config.js`(infra-as-code)로 정의된다. 배포는 `scripts/deploy.sh` 한 줄로 수행한다.

```bash
# 로컬에서 코드 push 후
git push origin main

# ubuntu-gole 컨테이너에서 표준 배포 (git pull → 빌드 → pm2 reload → health)
DOCKER_HOST=unix:///Users/kscold/.colima/default/docker.sock \
  docker exec ubuntu-gole bash -lc "cd /app && bash scripts/deploy.sh all"
# 일부만:  ... scripts/deploy.sh backend   |   ... scripts/deploy.sh frontend
```

- 이 절차는 **`ubuntu-gole` 컨테이너 한정**이며 다른 컨테이너/호스트에 영향을 주지 않는다.
- 프로세스 정의 변경이 필요하면 `ecosystem.config.js` 를 수정해 커밋한다(런타임 형태는 `bash -c` 유지).
- 아래의 수동 절차는 스크립트가 막힐 때를 위한 참고용으로 남겨둔다.

---

## 컨테이너 내부 구조

```
/app/
├── apps/
│   ├── api/          # Spring Boot 백엔드
│   │   └── build/libs/api-0.0.1-SNAPSHOT.jar   # 배포 jar
│   └── web/          # Next.js 프론트엔드
├── pnpm-workspace.yaml
└── package.json
```

PM2 프로세스:
- `gole-backend`  — `java -jar /app/apps/api/build/libs/api-0.0.1-SNAPSHOT.jar`
- `gole-frontend` — `pnpm exec next start -p 3000` (cwd: `/app/apps/web`)

---

## 배포 절차 (표준)

### 1단계 — 로컬에서 커밋 & 푸시

```bash
cd /Users/kscold/Desktop/GoLe
git add <변경된 파일>
git commit -m "feat(backend): ..."   # 한국어 커밋, feat/fix/refactor + (backend)/(frontend) 스코프
git push origin main
```

커밋 컨벤션:
- `feat(backend): ...` / `feat(frontend): ...`
- `fix(backend): ...` / `fix(frontend): ...`
- `refactor(backend): ...` / `refactor(frontend): ...`

### 2단계 — 컨테이너에서 pull & 빌드

```bash
DOCKER_HOST=unix:///Users/kscold/.colima/default/docker.sock \
  docker exec ubuntu-gole bash -c "cd /app && git pull origin main"
```

#### 백엔드만 변경된 경우
```bash
DOCKER_HOST=unix:///Users/kscold/.colima/default/docker.sock \
  docker exec ubuntu-gole bash -c "
    cd /app/apps/api && ./gradlew bootJar --no-daemon -q
    pm2 restart gole-backend
    pm2 logs gole-backend --lines 20 --nostream
  "
```

#### 프론트엔드만 변경된 경우
```bash
DOCKER_HOST=unix:///Users/kscold/.colima/default/docker.sock \
  docker exec ubuntu-gole bash -c "
    cd /app && pnpm --filter web build
    pm2 restart gole-frontend
    pm2 logs gole-frontend --lines 20 --nostream
  "
```

#### 백엔드 + 프론트엔드 모두 변경된 경우
```bash
DOCKER_HOST=unix:///Users/kscold/.colima/default/docker.sock \
  docker exec ubuntu-gole bash -c "
    cd /app/apps/api && ./gradlew bootJar --no-daemon -q
    cd /app && pnpm --filter web build
    pm2 restart gole-backend gole-frontend
    pm2 logs --lines 30 --nostream
  "
```

### 3단계 — 배포 확인

```bash
# 헬스체크
curl -s http://localhost:8081/actuator/health
# 또는 도메인으로
curl -s https://gole.kscold.com/actuator/health

# PM2 상태
DOCKER_HOST=unix:///Users/kscold/.colima/default/docker.sock \
  docker exec ubuntu-gole pm2 list
```

---

## 파일 직접 복사 방식 (git pull 불가할 때)

GitHub 인증 없이 특정 파일만 바꿔야 할 때:

```bash
DOCKER_HOST=unix:///Users/kscold/.colima/default/docker.sock \
  docker cp /Users/kscold/Desktop/GoLe/apps/api/src ubuntu-gole:/app/apps/api/src
```

---

## 로그 확인

```bash
# 실시간 백엔드 로그
DOCKER_HOST=unix:///Users/kscold/.colima/default/docker.sock \
  docker exec ubuntu-gole pm2 logs gole-backend --lines 50

# 실시간 프론트 로그
DOCKER_HOST=unix:///Users/kscold/.colima/default/docker.sock \
  docker exec ubuntu-gole pm2 logs gole-frontend --lines 50

# 파일 로그
DOCKER_HOST=unix:///Users/kscold/.colima/default/docker.sock \
  docker exec ubuntu-gole bash -c "tail -100 /var/log/gole-backend.log"
```

---

## SSH 직접 접속

```bash
ssh -p 2223 root@localhost   # 비밀번호: gole
# 또는
ssh -p 2223 root@kscold.iptime.org
```

---

## nginx (리버스 프록시)

nginx 설정 파일: `/Users/kscold/Desktop/kscold-control/nginx/conf.d/gole.kscold.com.conf`

라우팅:
- `https://gole.kscold.com/api/` → `ubuntu-gole:8080`
- `https://gole.kscold.com/actuator/` → `ubuntu-gole:8080`
- `https://gole.kscold.com/` → `ubuntu-gole:3000`

nginx 리로드:
```bash
DOCKER_HOST=unix:///Users/kscold/.colima/default/docker.sock \
  docker exec kscold-nginx sh -c "nginx -t && nginx -s reload"
```

---

## 주의사항

- `ubuntu-congbang` 컨테이너는 절대 건드리지 않는다.
- `docker rm`, `docker stop`은 명시적 지시 없이 실행 금지.
- 빌드 시 `--no-daemon` 옵션 필수 (컨테이너 내 Gradle daemon 불안정).
- 프론트엔드 실행은 반드시 `pnpm exec next start -p 3000` 사용 (`pnpm --filter web start -- -p 3000` 금지 — Next.js가 `-p`를 디렉토리로 오인).
- API 경로 prefix: `/api/v1/...`
- MongoDB는 replica set rs0로 실행 중 (멀티도큐먼트 트랜잭션 지원).
