// GoLe PM2 프로세스 정의 (infra-as-code) — ubuntu-gole 컨테이너 전용.
//
// 현재 운영 중인 프로세스 정의를 그대로 코드화한 것이다.
//   - gole-backend : Spring Boot 실행 jar (Java 21)
//   - gole-frontend: Next.js production 서버 (Node 22)
//
// 배포는 scripts/deploy.sh 가 이 파일로 `pm2 reload` 한다.
// 실행 형태(bash -c)는 현재 운영과 동일하게 유지해 런타임 거동 변화를 막는다.

const APP_ROOT = "/app";

module.exports = {
  apps: [
    {
      name: "gole-backend",
      cwd: APP_ROOT,
      script: "/usr/bin/bash",
      args: ["-c", "java -Xmx512m -jar /app/apps/api/build/libs/api-0.0.1-SNAPSHOT.jar"],
      interpreter: "none",
      exec_mode: "fork",
      autorestart: true,
      max_memory_restart: "700M",
      // 백엔드는 application.yml 기본값(localhost Mongo/Redis)을 사용한다.
    },
    {
      name: "gole-frontend",
      cwd: `${APP_ROOT}/apps/web`,
      script: "/usr/bin/bash",
      args: ["-c", "pnpm exec next start -p 3000"],
      interpreter: "none",
      exec_mode: "fork",
      autorestart: true,
      max_memory_restart: "500M",
      env: {
        NODE_ENV: "production",
      },
    },
  ],
};
