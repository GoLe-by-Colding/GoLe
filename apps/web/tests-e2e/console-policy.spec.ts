import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { expect, test } from "@playwright/test";
import { loadBindings, transform } from "next/dist/build/swc/index.js";
import { transpileModule, ModuleKind } from "typescript";

function consolePolicy(environment: string): unknown {
  const source = readFileSync(new URL("../next.config.ts", import.meta.url), "utf8");
  const output = transpileModule(source, {
    compilerOptions: { module: ModuleKind.CommonJS },
  }).outputText;
  const module = { exports: {} };
  runInNewContext(output, {
    module,
    exports: module.exports,
    process: { env: { NODE_ENV: environment } },
  });
  return (module.exports as { default: { compiler: { removeConsole: unknown } } }).default.compiler
    .removeConsole;
}

test("일반 콘솔 제거는 운영에만 적용하고 오류와 경고만 보존함", () => {
  expect(consolePolicy("production")).toEqual({ exclude: ["error", "warn"] });
  expect(consolePolicy("development")).toBe(false);
  expect(consolePolicy("test")).toBe(false);
});

test("실제 Next 컴파일러가 일반 로그를 지우고 이스터에그 간접 참조만 보존함", async () => {
  await loadBindings();
  const result = await transform(
    "console.log('GOLE_LOG_CANARY');console.info('GOLE_INFO_CANARY');console.debug('GOLE_DEBUG_CANARY');" +
      "console.warn('GOLE_WARN_CANARY');console.error('GOLE_ERROR_CANARY');" +
      "const print=Function.prototype.bind.call(console.log,console);print('GOLE_EGG_CANARY');",
    {
      filename: "console-contract.js",
      jsc: { parser: { syntax: "ecmascript" }, target: "es2022" },
      removeConsole: consolePolicy("production"),
    },
  );
  expect(result.code).not.toMatch(/GOLE_(LOG|INFO|DEBUG)_CANARY/);
  expect(result.code).toContain("GOLE_WARN_CANARY");
  expect(result.code).toContain("GOLE_ERROR_CANARY");
  expect(result.code).toContain("GOLE_EGG_CANARY");
  expect(result.code).toContain("Function.prototype.bind.call(console.log, console)");
});
