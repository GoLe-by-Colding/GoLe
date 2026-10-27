import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import test from "node:test";

const ci = readFileSync(
  new URL("../../.github/workflows/ci.yml", import.meta.url),
  "utf8",
);
const cd = readFileSync(
  new URL("../../.github/workflows/cd.yml", import.meta.url),
  "utf8",
);
const e2e = ci.split("\n  e2e:\n")[1]?.split("\n  mobile:\n")[0];
const notify = ci.split("\n  notify:\n")[1];
assert.ok(e2e && notify);

test("E2E는 기존 이름으로 dev/main PR에서만 실행함", () => {
  assert.match(ci, /push:\s*\n\s*branches: \[main\]/);
  assert.match(ci, /pull_request:\s*\n\s*branches: \[main, dev\]/);
  assert.match(e2e, /name: E2E \(Playwright\)/);
  assert.match(e2e, /if: \$\{\{ github\.event_name == 'pull_request' \}\}/);
  assert.match(e2e, /run: pnpm --filter web e2e/);
});

test("알림은 E2E 생략 뒤에도 실행하며 원래 결과를 표시함", () => {
  assert.match(
    notify,
    /if: \$\{\{ always\(\) && github\.event_name == 'push' \}\}/,
  );
  assert.match(
    notify,
    /needs: \[web, mobile, api, support_agent, e2e, infra\]/,
  );
  assert.match(notify, /e2e=\$E2E_RESULT/);
});

test("CD는 여전히 성공한 main push CI와 정확한 SHA를 요구함", () => {
  assert.match(cd, /workflows: \[CI\]/);
  assert.match(cd, /github\.event\.workflow_run\.event == 'push'/);
  assert.match(cd, /github\.event\.workflow_run\.head_branch == 'main'/);
  assert.match(cd, /github\.event\.workflow_run\.conclusion == 'success'/);
  assert.match(cd, /current_main != sha/);
});

// 실제 알림의 판정 부분만 실행한다. curl·시크릿·외부 전송은 포함하지 않는다.
const decision = notify
  .split("        run: |\n")[1]
  ?.split('          if [ "$DISCORD_SUPPRESS_NOTIFICATIONS"')[0];
assert.ok(decision);
const results = [
  "WEB_RESULT",
  "MOBILE_RESULT",
  "API_RESULT",
  "SUPPORT_AGENT_RESULT",
  "INFRA_RESULT",
];
function status(overrides = {}) {
  return execFileSync(
    "/bin/bash",
    [
      "--noprofile",
      "--norc",
      "-eu",
      "-c",
      `${decision}\nprintf '%s' "$status"`,
    ],
    {
      encoding: "utf8",
      env: {
        PATH: process.env.PATH,
        GITHUB_EVENT_NAME: "push",
        GITHUB_REF: "refs/heads/main",
        ...Object.fromEntries(results.map((name) => [name, "success"])),
        E2E_RESULT: "skipped",
        ...overrides,
      },
    },
  );
}

test("main push의 E2E skipped만 정상 생략으로 인정함", () => {
  assert.equal(status(), "✅ CI 통과");
  assert.equal(status({ E2E_RESULT: "success" }), "✅ CI 통과");
  assert.equal(status({ GITHUB_EVENT_NAME: "pull_request" }), "❌ CI 실패");
  assert.equal(status({ GITHUB_REF: "refs/heads/dev" }), "❌ CI 실패");
  assert.equal(
    status({ GITHUB_EVENT_NAME: "workflow_dispatch" }),
    "❌ CI 실패",
  );
});

for (const result of ["failure", "cancelled", "", "unknown"]) {
  test(`E2E의 ${result || "빈"} 결과를 성공으로 숨기지 않음`, () => {
    assert.equal(status({ E2E_RESULT: result }), "❌ CI 실패");
  });
}

for (const name of results) {
  for (const result of ["failure", "cancelled", "skipped", ""]) {
    test(`${name}의 ${result || "빈"} 결과를 성공으로 숨기지 않음`, () => {
      assert.equal(status({ [name]: result }), "❌ CI 실패");
    });
  }
}
