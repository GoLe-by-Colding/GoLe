import { expect, test, type Locator, type Page } from "@playwright/test";

async function submitButton(page: Page): Promise<Locator> {
  await page.goto("/login");
  const button = page
    .locator("form button")
    .filter({ hasText: /로그인/ })
    .first();
  await expect(button).toBeVisible();
  return button;
}

function scaleOf(button: Locator): Promise<number> {
  return button.evaluate((element) => {
    const scale = getComputedStyle(element).scale;
    return scale === "none" ? 1 : Number(scale);
  });
}

async function pressAndHold(page: Page, button: Locator): Promise<number> {
  const box = await button.boundingBox();
  if (box === null) throw new Error("버튼 위치를 읽지 못했다");
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  await page.mouse.down();
  await page.waitForTimeout(400);
  const pressed = await scaleOf(button);
  await page.mouse.up();
  return pressed;
}

test.describe("공용 버튼 누름 반동", () => {
  test("누르는 동안 작아지고 크기 변화가 전환으로 이어진다", async ({ page }) => {
    const button = await submitButton(page);
    const transition = await button.evaluate(
      (element) => getComputedStyle(element).transitionProperty,
    );
    expect(
      transition.split(", "),
      "scale 이 전환 목록에 없으면 누름이 애니메이션 없이 튄다",
    ).toContain("scale");

    expect(await pressAndHold(page, button)).toBeLessThan(0.99);
    await expect.poll(() => scaleOf(button)).toBe(1);
  });

  test("비활성 버튼은 눌러도 크기가 그대로다", async ({ page }) => {
    const button = await submitButton(page);
    await button.evaluate((element) => element.setAttribute("disabled", ""));

    expect(await pressAndHold(page, button)).toBe(1);
  });

  test("모션을 줄인 사용자에게는 크기 반동을 주지 않는다", async ({ page }) => {
    await page.emulateMedia({ reducedMotion: "reduce" });
    const button = await submitButton(page);

    expect(await pressAndHold(page, button)).toBe(1);
  });
});

test("상호작용 카드는 누르는 동안 살짝 작아지고 전환으로 이어진다", async ({ page }) => {
  test.skip(process.env.E2E_WITH_BACKEND !== "1", "실제 매물 목록이 필요한 통합 테스트");
  await page.goto("/search");
  const card = page.getByTestId("listing-card").first();
  await expect(card).toBeVisible();
  const transition = await card.evaluate((element) => getComputedStyle(element).transitionProperty);
  expect(transition.split(", ")).toContain("scale");

  expect(await pressAndHold(page, card)).toBeLessThan(1);
});

test("관리자 대시보드 카드는 hover 때 떠오르는 움직임을 전환으로 잇는다", async ({ page }) => {
  const email = process.env.GOLE_ADMIN_EMAIL ?? "";
  test.skip(
    process.env.E2E_WITH_BACKEND !== "1" || email === "",
    "실제 관리자 세션이 필요한 통합 테스트",
  );
  const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
  const signIn = await page.request.post(`${apiBaseUrl}/api/v1/accounts/sessions`, {
    data: { email, password: process.env.GOLE_ADMIN_PASSWORD ?? "" },
  });
  expect(signIn.ok()).toBeTruthy();
  const { accountId, sessionToken, role } = (await signIn.json()) as Record<string, string>;
  await page.addInitScript(
    (session) => window.localStorage.setItem("gole.session", session),
    JSON.stringify({ accountId, sessionToken, role }),
  );

  await page.goto("/admin");
  const card = page.locator("[class*='hover:-translate-y-0.5']").first();
  await expect(card).toBeVisible();
  const transition = await card.evaluate((element) => getComputedStyle(element).transitionProperty);
  expect(transition.split(", "), "translate 가 없으면 떠오름이 애니메이션 없이 튄다").toContain(
    "translate",
  );
});
