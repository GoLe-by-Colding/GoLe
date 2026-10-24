import { expect, test } from "@playwright/test";
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

// 실제 소비자가 해석하는 패키지를 검사한다. 별도 테스트용 Zod를 설치하지 않는다.
const steigerEntry = fileURLToPath(import.meta.resolve("steiger"));
const steigerRequire = createRequire(steigerEntry);
const zodRoot = dirname(steigerRequire.resolve("zod/package.json"));

for (const version of ["v3", "v4"] as const) {
  for (const format of ["esm", "cjs"] as const) {
    const entry = join(
      zodRoot,
      version === "v3" ? "v3" : ".",
      format === "esm" ? "index.js" : "index.cjs",
    );
    const load = async () =>
      format === "esm" ? import(pathToFileURL(entry).href) : steigerRequire(entry);

    test(`${version} ${format}: 상한 초과 배열·집합은 원소와 후속 refinement를 실행하지 않는다`, async () => {
      const { z } = await load();
      for (const asynchronous of [false, true]) {
        for (const bound of ["max", "exact"] as const) {
          for (const kind of ["array", "set"] as const) {
            for (const limit of [0, 1]) {
              let visits = 0;
              let collectionVisits = 0;
              const visit = () => {
                visits++;
              };
              const element = z.number().superRefine(asynchronous ? async () => visit() : visit);
              const collection = kind === "array" ? z.array(element) : z.set(element);
              const bounded =
                bound === "max"
                  ? collection.max(limit, "개수 상한")
                  : kind === "array"
                    ? collection.length(limit, "개수 상한")
                    : collection.size(limit, "개수 상한");
              const schema = z.object({
                items: bounded.superRefine(() => {
                  collectionVisits++;
                }),
              });
              const values = Array.from({ length: 1_000 }, (_, index) => index);
              const input = { items: kind === "array" ? values : new Set(values) };
              const result = asynchronous
                ? await schema.safeParseAsync(input)
                : schema.safeParse(input);

              expect(result.success).toBe(false);
              expect(result.error.issues).toEqual([
                expect.objectContaining({
                  code: "too_big",
                  maximum: limit,
                  path: ["items"],
                  message: "개수 상한",
                }),
              ]);
              expect(visits).toBe(0);
              expect(collectionVisits).toBe(0);
            }
          }
        }
      }
    });

    test(`${version} ${format}: 정상·무제한·하한·원소 오류 검증을 유지한다`, async () => {
      const { z } = await load();
      for (const asynchronous of [false, true]) {
        const parse = (schema: ReturnType<typeof z.array>, value: unknown) =>
          asynchronous ? schema.safeParseAsync(value) : schema.safeParse(value);
        let visits = 0;
        const element = z.number().transform((value: number) => {
          visits++;
          return value + 1;
        });
        expect(await parse(z.array(element).max(2), [1, 2])).toMatchObject({
          success: true,
          data: [2, 3],
        });
        expect(visits).toBe(2);
        expect(await parse(z.set(element).size(2), new Set([1, 2]))).toMatchObject({
          success: true,
          data: new Set([2, 3]),
        });
        const values = Array.from({ length: 1_000 }, (_, index) => index);
        const before = visits;
        expect((await parse(z.array(element), values)).success).toBe(true);
        expect((await parse(z.set(element), new Set(values))).success).toBe(true);
        expect(visits - before).toBe(2_000);

        for (const [schema, input] of [
          [z.array(z.number()).min(2), [1]],
          [z.array(z.number()).length(2), [1]],
          [z.set(z.number()).min(2), new Set([1])],
          [z.set(z.number()).size(2), new Set([1])],
        ]) {
          const result = await parse(schema, input);
          expect(result.success).toBe(false);
          expect(result.error.issues).toEqual([
            expect.objectContaining({ code: "too_small", minimum: 2 }),
          ]);
        }
        for (const [schema, input, path] of [
          [z.array(z.number()).max(2), ["문자"], [0]],
          [z.set(z.number()).max(2), new Set(["문자"]), version === "v3" ? [0] : []],
        ]) {
          const result = await parse(schema, input);
          expect(result.success).toBe(false);
          expect(result.error.issues).toEqual([
            expect.objectContaining({ code: "invalid_type", path }),
          ]);
        }
      }
    });
  }
}

test("Steiger는 부분 규칙 설정·함수 반환 검증·오류 포매터를 유지한다", async () => {
  const { processConfiguration } = await import("steiger");
  const plugin = {
    meta: { name: "보안 호환 검증", version: "1" },
    ruleDefinitions: [
      { name: "test/one", check: () => ({ diagnostics: [] }) },
      { name: "test/two", check: () => ({ diagnostics: [] }) },
    ],
  };
  const parsed = processConfiguration([plugin, { rules: { "test/one": "warn" } }], "/tmp");
  expect(parsed).toHaveLength(2);
  expect(parsed[0].ruleDefinitions[0].check()).toEqual({ diagnostics: [] });
  const invalidReturn = processConfiguration(
    [
      { ...plugin, ruleDefinitions: [{ name: "test/one", check: () => ({ diagnostics: 1 }) }] },
      { rules: { "test/one": "warn" } },
    ],
    "/tmp",
  );
  expect(() => invalidReturn[0].ruleDefinitions[0].check()).toThrow();

  let validationError: unknown;
  try {
    processConfiguration([plugin, { rules: { "test/one": "invalid" } }], "/tmp");
  } catch (error) {
    validationError = error;
  }
  expect(validationError).toBeDefined();
  const formatterEntry = steigerRequire.resolve("zod-validation-error");
  const formatters = [
    steigerRequire(formatterEntry),
    await import(pathToFileURL(join(dirname(formatterEntry), "index.mjs")).href),
  ];
  for (const { fromError } of formatters) {
    const formatted = fromError(validationError);
    expect(formatted.message).toContain("rules");
    expect(formatted.message).toContain("invalid");
  }
});

test("Expo CLI의 플러그인·명령 스키마는 v3 오류와 입력 계약을 유지한다", () => {
  const mobileRequire = createRequire(new URL("../../mobile/package.json", import.meta.url));
  const expoRequire = createRequire(mobileRequire.resolve("expo/package.json"));
  const cliRequire = createRequire(expoRequire.resolve("@expo/cli/package.json"));
  const cliRoot = dirname(expoRequire.resolve("@expo/cli/package.json"));
  const { PluginSchema } = cliRequire(
    join(cliRoot, "build/src/start/server/DevToolsPlugin.schema.js"),
  );
  const { createMCPDevToolsExtensionSchema } = cliRequire(
    join(cliRoot, "build/src/start/server/createMCPDevToolsExtensionSchema.js"),
  );
  const plugin = PluginSchema.parse({
    packageName: "gole-test",
    packageRoot: "/tmp/gole-test",
    cliExtensions: {
      description: "실행 없이 스키마만 검증",
      entryPoint: "test.js",
      commands: [
        {
          name: "inspect",
          title: "확인",
          environments: ["cli", "mcp"],
          parameters: [
            { name: "count", type: "number" },
            { name: "enabled", type: "confirm" },
          ],
        },
      ],
    },
  });
  const schema = createMCPDevToolsExtensionSchema(plugin);
  expect(schema.safeParse({ command: "inspect", count: 2, enabled: true }).success).toBe(true);
  for (const input of [
    { command: "unknown" },
    { command: "inspect", count: "2" },
    { command: "inspect", enabled: "yes" },
    { command: "inspect", extra: true },
  ]) {
    const result = schema.safeParse(input);
    expect(result.success).toBe(false);
    expect(result.error).toBeInstanceOf(cliRequire("zod/v3").ZodError);
  }
  expect(PluginSchema.safeParse({ ...plugin, packageName: "" }).success).toBe(false);
  expect(cliRequire.resolve("zod/package.json")).toBe(steigerRequire.resolve("zod/package.json"));
});
