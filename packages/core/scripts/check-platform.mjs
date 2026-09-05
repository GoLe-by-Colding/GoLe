/**
 * 코어의 플랫폼 중립성 검사 (R1.1).
 *
 * 타입 lib으로는 막을 수 없다 — fetch·FormData·URL은 웹과 RN 모두에 있어 DOM lib이 필요하고,
 * 그 lib을 켜면 window·document까지 타입이 통과하기 때문이다. 그래서 실제 결합만 골라 금지한다.
 */
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";

/** 이 식별자가 코어에 등장하면 웹 또는 앱 한쪽에만 존재하는 것에 의존한 것이다. */
const BANNED = [
  { pattern: /\bwindow\b/, why: "브라우저 전역" },
  { pattern: /\bdocument\b/, why: "브라우저 전역" },
  { pattern: /\blocalStorage\b/, why: "브라우저 저장소 — SessionStore로 주입한다" },
  { pattern: /\bsessionStorage\b/, why: "브라우저 저장소" },
  { pattern: /\bnavigator\b/, why: "브라우저 전역" },
  { pattern: /\bEventSource\b/, why: "RN에 없다 — 플랫폼 어댑터로 넘긴다" },
  { pattern: /from ["']react["']/, why: "코어는 UI 프레임워크를 모른다" },
  { pattern: /from ["']react-native/, why: "코어는 플랫폼을 모른다" },
  { pattern: /from ["']next\//, why: "코어는 Next를 모른다" },
  { pattern: /process\.env/, why: "환경값은 configureCore()로 주입한다" },
];

function walk(dir) {
  return readdirSync(dir).flatMap((name) => {
    const full = join(dir, name);
    return statSync(full).isDirectory() ? walk(full) : full.endsWith(".ts") ? [full] : [];
  });
}

const violations = [];
for (const file of walk("src")) {
  const lines = readFileSync(file, "utf8").split("\n");
  lines.forEach((line, i) => {
    // 주석은 설명이지 의존이 아니다.
    const code = line.replace(/\/\*.*?\*\//g, "").replace(/^\s*(\/\/|\*|\/\*).*/, "");
    for (const { pattern, why } of BANNED) {
      if (pattern.test(code)) {
        violations.push(`${file}:${i + 1}  ${pattern.source} — ${why}\n    ${line.trim()}`);
      }
    }
  });
}

if (violations.length > 0) {
  console.error(`플랫폼 중립성 위반 ${violations.length}건:\n`);
  console.error(violations.join("\n"));
  process.exit(1);
}
console.log("✔ 코어는 플랫폼 중립이다.");
