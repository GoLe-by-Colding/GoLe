import { createHash } from "node:crypto";
import { expect, test } from "@playwright/test";
import {
  COLDING_ART,
  makerConsoleMessage,
} from "../src/widgets/maker-credit/model/console-message";

const names = ["김승찬", "김가원", "김수민"];
const accounts = ["kscold", "wongakim-99", "codemaker-kim"];

test("확정 아트 원문과 8색·폭·단일 콘솔 서식을 보존함", () => {
  expect(createHash("sha256").update(COLDING_ART).digest("hex")).toBe(
    "20c552a4e4ad5180d7a2a42780b9b595df2b8d30d95cce87c2818ff2018c2d6b",
  );
  expect(COLDING_ART.split("\n")).toHaveLength(8);
  for (const line of COLDING_ART.split("\n")) expect([...line].length).toBeLessThanOrEqual(70);
  const [message, ...styles] = makerConsoleMessage();
  expect(message.match(/%c/g)).toHaveLength(styles.length);
  for (const [i, color] of [
    "#e0f2fe",
    "#bae6fd",
    "#7dd3fc",
    "#7dd3fc",
    "#38bdf8",
    "#0ea5e9",
    "#0284c7",
    "#64748b",
  ].entries()) {
    expect(styles[i]).toContain(`color:${color}`);
    expect(styles[i]).toContain("line-height:1;");
    expect(styles[i]).toContain("monospace");
  }
  for (const name of names) expect(message).toContain(name);
  for (const account of accounts) expect(message).toContain(`https://github.com/${account}`);
  expect(message).toContain("Blog: https://kscold.com");
  expect(message).toContain("https://kscold.com/info");
  expect(message).not.toMatch(/mailto:|@gmail|010-/);
});

test("humans.txt는 공개 UTF-8 텍스트로 세 명과 블로그만 안내함", async ({ request }) => {
  const response = await request.get("/humans.txt", { maxRedirects: 0 });
  expect(response.status()).toBe(200);
  expect(response.headers()["content-type"]).toMatch(/^text\/plain;\s*charset=utf-8/i);
  const body = await response.text();
  expect(body).toContain("/* TEAM */");
  expect(body).toContain("/* SITE */");
  expect(body.match(/GitHub:/g)).toHaveLength(3);
  for (const name of names) expect(body).toContain(name);
  for (const account of accounts) expect(body).toContain(`https://github.com/${account}`);
  expect(body).toContain("Blog: https://kscold.com");
  expect(body).not.toMatch(/mailto:|@|\d{2,3}-\d{3,4}-\d{4}/);
  expect((await request.head("/humans.txt")).status()).toBe(200);
  const robots = await request.get("/robots.txt");
  expect(await robots.text()).not.toMatch(/Disallow:\s*\/humans/i);
});

test("초기 소스의 제작자 메타데이터와 기존 조직 그래프를 연결함", async ({ page, request }) => {
  const response = await request.get("/search");
  const html = await response.text();
  expect(html).toContain('rel="author" href="/humans.txt"');
  for (const name of names) expect(html).toContain(`name="author" content="${name}"`);
  await page.goto("/search");
  const documents = await page.locator('script[type="application/ld+json"]').allTextContents();
  const graph = documents.flatMap(
    (text) => (JSON.parse(text) as { "@graph"?: Array<Record<string, unknown>> })["@graph"] ?? [],
  );
  expect(graph.filter((node) => node["@type"] === "Organization")).toHaveLength(1);
  expect(graph.filter((node) => node["@type"] === "WebSite")).toHaveLength(1);
  const people = graph.filter((node) => node["@type"] === "Person");
  expect(people).toHaveLength(3);
  const founder = people.find((person) => person["name"] === "김승찬");
  expect(founder).toMatchObject({
    name: "김승찬",
    url: "https://kscold.com",
    sameAs: "https://github.com/kscold",
  });
  expect(graph.find((node) => node["@type"] === "Organization")?.["founder"]).toEqual({
    "@id": founder?.["@id"],
  });
  expect(graph.find((node) => node["@type"] === "WebSite")?.["creator"]).toEqual(
    people.map((person) => ({ "@id": person["@id"] })),
  );
});

test("콘솔은 문서당 한 번이고 푸터 제작자 링크는 숨기지 않음", async ({ page }) => {
  const messages: string[] = [];
  const hydrationErrors: string[] = [];
  await page.route("**/*", (route) =>
    ["GET", "HEAD", "OPTIONS"].includes(route.request().method())
      ? route.continue()
      : route.abort(),
  );
  page.on("console", (message) => {
    if (message.text().includes("🧊 Made by COLDING")) messages.push(message.text());
    if (
      message.type() === "error" &&
      /hydration|did not match|server rendered/i.test(message.text())
    )
      hydrationErrors.push(message.text());
  });
  page.on("pageerror", (error) => hydrationErrors.push(error.message));
  await page.goto("/search");
  await expect.poll(() => messages.length).toBe(1);
  const footer = page.locator("footer");
  const credit = footer.getByRole("link", { name: "Made by kscold", exact: true });
  await expect(credit).toBeVisible();
  await expect(credit).toHaveAttribute("href", "https://kscold.com");
  expect((await credit.getAttribute("rel")) ?? "").not.toMatch(/nofollow/);
  await footer.getByRole("link", { name: "커뮤니티", exact: true }).click();
  await expect(page).toHaveURL(/\/community$/);
  await expect(page.getByRole("heading", { name: "커뮤니티", exact: true })).toBeVisible();
  expect(messages).toHaveLength(1);
  await page.reload();
  await expect.poll(() => messages.length).toBe(2);
  expect(hydrationErrors).toEqual([]);
});
