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

/** 누르는 동안의 세로 이동(px). 브릭 버튼은 턱 깊이만큼 내려앉는다. */
function dropOf(element: Locator): Promise<number> {
  return element.evaluate((node) => {
    const translate = getComputedStyle(node).translate;
    return translate === "none" ? 0 : Number.parseFloat(translate.split(" ")[1] ?? "0");
  });
}

async function hold<T>(page: Page, target: Locator, read: () => Promise<T>): Promise<T> {
  // 늦게 그려지는 요소(소셜 로그인 등)가 레이아웃을 밀 수 있으니 좌표를 미리 재지 않고 요소 위로 옮긴 뒤 누른다.
  await target.hover();
  await page.mouse.down();
  await page.waitForTimeout(400);
  const pressed = await read();
  await page.mouse.up();
  return pressed;
}

function pressAndHold(page: Page, target: Locator): Promise<number> {
  return hold(page, target, () => scaleOf(target));
}

test.describe("공용 버튼 브릭 누름", () => {
  test("아래 턱이 보이고, 누르는 동안 턱만큼 내려앉았다가 전환으로 돌아온다", async ({ page }) => {
    const button = await submitButton(page);
    const style = await button.evaluate((element) => {
      const computed = getComputedStyle(element);
      return { transition: computed.transitionProperty, shadow: computed.boxShadow };
    });
    expect(style.shadow, "브릭 턱(아래 그림자)이 있어야 한다").toMatch(/0px 4px 0px/);
    expect(
      style.transition.split(", "),
      "translate 가 전환 목록에 없으면 누름이 애니메이션 없이 튄다",
    ).toContain("translate");

    expect(await hold(page, button, () => dropOf(button))).toBeGreaterThan(2);
    await expect.poll(() => dropOf(button)).toBe(0);
  });

  test("비활성 버튼은 눌러도 내려앉지 않는다", async ({ page }) => {
    const button = await submitButton(page);
    await button.evaluate((element) => element.setAttribute("disabled", ""));

    expect(await hold(page, button, () => dropOf(button))).toBe(0);
  });

  test("모션을 줄인 사용자에게는 내려앉는 움직임을 주지 않는다", async ({ page }) => {
    await page.emulateMedia({ reducedMotion: "reduce" });
    const button = await submitButton(page);

    expect(await hold(page, button, () => dropOf(button))).toBe(0);
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
