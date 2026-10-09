import { test, expect, type Locator, type Page } from "@playwright/test";
// 시세 기간 필터는 웹·앱 공유 코어에 있다(@gole/core). 파사드가 아니라 원본을 직접 가져와
// 이 단위 검증이 재수출 계층을 거치지 않게 한다.
import {
  filterPricePointsByPeriod,
  listingPriceGap,
  priceGapLabel,
  type ConditionValuation,
  type PricePoint,
  type PriceSnapshot,
} from "@gole/core/pricing";
import type { Listing } from "@gole/core/listing";
// 목록 카드 문구 규칙은 웹 위젯 모델에 있다(화면 컴포넌트가 아니라 순수 함수만 가져온다).
import {
  buildPriceNotes,
  priceNoteSetNumbers,
} from "../src/widgets/listing-grid/model/price-notes";

const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
const HOUR_MS = 60 * 60 * 1000;
const DAY_MS = 24 * HOUR_MS;
const MONTH_DAYS = 31; // 화면의 "1개월" 탭(price-explorer PERIODS)과 같은 길이
const CHART = { name: "시세 추이 차트" } as const;

interface PricedSet {
  readonly setNumber: string;
  /** 화면과 같은 순서(오래된 것부터)의 미개봉 체결. */
  readonly points: readonly PricePoint[];
  /** 가장 최근 체결 시각(ms). 브라우저의 "지금"을 이 근처로 고정한다. */
  readonly latest: number;
}

/**
 * 기간 탭 검사용 결정적 표본. 화면과 같은 스냅샷 API를 읽기만 해서, 상태별 시세가 확정됐고 최신 체결 직전
 * 1개월 안에 체결이 2건 이상인 세트를 고른다. 날짜는 아래에서 브라우저 시계를 그 최신 체결 근처로 고정해
 * 맞춘다 — 로컬 표본이 오래돼도(2026-10-09 로컬: 최신 체결 08-21) 1개월 창이 비지 않는다.
 * 가격·표본을 만들거나 DB를 바꾸지 않는다.
 */
async function pricedSet(page: Page): Promise<PricedSet> {
  const trending = await page.request.get(`${apiBaseUrl}/api/v1/pricing/trending?limit=10`);
  expect(trending.ok(), "시세 트렌딩 API").toBeTruthy();
  for (const { setNumber } of (await trending.json()) as Array<{ setNumber: string }>) {
    const response = await page.request.get(
      `${apiBaseUrl}/api/v1/pricing/sets/${encodeURIComponent(setNumber)}/snapshot`,
    );
    if (!response.ok()) continue;
    const snapshot = (await response.json()) as PriceSnapshot;
    const latestPoint = snapshot.observations[0];
    if (snapshot.state !== "ESTABLISHED" || latestPoint === undefined) continue;
    const latest = Date.parse(latestPoint.executedAt);
    const points = [...snapshot.observations].reverse();
    if (filterPricePointsByPeriod(points, MONTH_DAYS, latest + 12 * HOUR_MS).length >= 2) {
      return { setNumber, points, latest };
    }
  }
  throw new Error(
    "최신 체결 직전 1개월에 미개봉 체결이 2건 이상인 확정 시세 세트가 없다 — 시세 시드가 필요한 검사다",
  );
}

/**
 * 서버 렌더 HTML(세트 이름·"6개월" 눌림)은 하이드레이션 전에도 보인다. 그때 누른 버튼은 React 이벤트가 아직 붙지 않아
 * 아무 일도 하지 않는다 — CI run 37924434438 첫 시도의 "1개월 체결 없음" 미표시가 이것이었다(CPU 6배 감속에서 10/10 재현,
 * 클릭 시점에 React 속성 없음·클릭 뒤 aria-pressed=false). React가 요소에 이벤트 속성(`__reactProps$…`)을 붙였는지로
 * 하이드레이션을 기다린 뒤에 누른다. 기대값은 그대로다.
 */
async function waitForHydration(target: Locator): Promise<void> {
  await expect
    .poll(
      () =>
        target.evaluate((element) =>
          Object.keys(element).some((key) => key.startsWith("__reactProps$")),
        ),
      { message: "하이드레이션(React 이벤트 연결) 대기", timeout: 15_000 },
    )
    .toBe(true);
}

/** 세트를 고정해 연다. 시계는 하이드레이션 뒤에 고정해야 서버 렌더(서버 시각)와 어긋나지 않는다. */
async function openPrices(page: Page, set: PricedSet, now?: number): Promise<void> {
  await page.goto(`/prices?set=${encodeURIComponent(set.setNumber)}`);
  await expect(page.getByText(new RegExp(`^#${set.setNumber} ·`))).toBeVisible();
  await expect(page.getByRole("button", { name: "6개월" })).toHaveAttribute("aria-pressed", "true");
  await waitForHydration(page.getByRole("button", { name: "1개월" }));
  if (now !== undefined) await page.clock.setFixedTime(now);
}

test("기간 필터는 0~1건이어도 전체 데이터로 되돌아가지 않는다", () => {
  const now = Date.parse("2026-08-30T00:00:00Z");
  const point = (executedAt: string, price: number): PricePoint => ({
    executedAt,
    price,
    quantity: 1,
    source: "platform_payment",
    condition: "new_sealed",
  });
  const points = [point("2025-01-01T00:00:00Z", 100), point("2026-08-20T00:00:00Z", 200)];

  expect(filterPricePointsByPeriod(points, 31, now)).toEqual([points[1]]);
  expect(filterPricePointsByPeriod(points, 1, now)).toEqual([]);
});

test("매물 판매가는 같은 등급의 실제 표본 추정 시세와만 비교한다", () => {
  const valuation = (
    condition: ConditionValuation["condition"],
    basis: ConditionValuation["basis"],
    fairPrice: number,
  ): ConditionValuation => ({
    condition,
    basis,
    depreciationPct: 0,
    fairPrice,
    sellPrice: Math.round(fairPrice * 0.96),
    buyPrice: Math.round(fairPrice * 1.05),
    sampleCount: basis === "model" ? 0 : 19,
    basedOnRealData: basis !== "model",
  });
  const snapshot = (
    state: PriceSnapshot["state"],
    conditions: ConditionValuation[],
    demo = false,
  ): PriceSnapshot => ({
    setNumber: "75192",
    state,
    minimumSamples: 3,
    sampleCount: 31,
    observations: [],
    statistics: null,
    valuation: { setNumber: "75192", hasData: true, marketPrice: 1_313_743, conditions },
    provenance: { mode: demo ? "DEMO" : "FIRST_PARTY", includedSources: [], demo },
  });
  const established = snapshot("ESTABLISHED", [
    valuation("used_good", "grade", 1_063_651),
    valuation("like_new", "group", 1_200_017),
    valuation("damaged", "model", 571_768),
  ]);

  const higher = listingPriceGap(1_250_000, "used_good", established);
  expect(higher?.fairPrice).toBe(1_063_651);
  expect(priceGapLabel(higher!.ratio)).toBe("판매가 17.5% 높음");
  expect(priceGapLabel(listingPriceGap(1_137_000, "like_new", established)!.ratio)).toBe(
    "판매가 5.3% 낮음",
  );
  expect(priceGapLabel(listingPriceGap(1_080_000, "used_good", established)!.ratio)).toBe(
    "시세와 비슷",
  );
  // 표본 없는 감가 모델·참고 단계·조회 실패·없는 등급과는 비교하지 않는다.
  expect(listingPriceGap(600_000, "damaged", established)).toBeNull();
  expect(listingPriceGap(1_250_000, "used_good", snapshot("OBSERVATIONS_ONLY", []))).toBeNull();
  expect(listingPriceGap(1_250_000, "used_good", null)).toBeNull();
  expect(listingPriceGap(1_250_000, "new_sealed", established)).toBeNull();
  // 출처 경고는 비교와 함께 다닌다.
  expect(
    listingPriceGap(
      1_250_000,
      "used_good",
      snapshot("ESTABLISHED", [valuation("used_good", "grade", 1_063_651)], true),
    )?.evidenceWarning,
  ).toBe("데모 포함");
  expect(priceGapLabel(higher!.ratio, "추정 시세보다")).toBe("추정 시세보다 17.5% 높음");

  // 목록 카드: 판매 중·예약 중 세트 매물만, 출처 경고가 있으면 "참고용"을 붙인다.
  const listing = (id: string, status: Listing["status"], setNumber: string | null) =>
    ({
      id,
      status,
      price: 1_250_000,
      condition: "used_good",
      catalogSetNumber: setNumber,
    }) as Listing;
  const listings = [
    listing("a", "active", "75192"),
    listing("b", "reserved", "75192"),
    listing("c", "sold", "75192"),
    listing("d", "active", null),
    listing("e", "active", "10276"),
  ];
  expect(priceNoteSetNumbers(listings)).toEqual(["75192", "10276"]);
  expect(
    buildPriceNotes(listings, {
      "75192": established,
      "10276": snapshot("ESTABLISHED", [valuation("used_good", "grade", 1_000_000)], true),
    }),
  ).toEqual({
    a: "추정 시세보다 17.5% 높음",
    b: "추정 시세보다 17.5% 높음",
    e: "추정 시세보다 25% 높음 · 참고용",
  });
});

// 시세 페이지: 차트·기간 탭·상태별(감가/빠른 판매·구매 추정) 테이블·정렬. (데이터가 있는 환경 대상)
test.describe("Prices (KREAM-style)", () => {
  // 느린 기기·CI의 하이드레이션 지연을 재현하는 손잡이. 기본은 꺼져 있다(예: E2E_CPU_THROTTLE=6, chromium만).
  test.beforeEach(async ({ page, browserName }) => {
    const rate = Number(process.env.E2E_CPU_THROTTLE ?? 0);
    if (rate > 1 && browserName === "chromium") {
      const cdp = await page.context().newCDPSession(page);
      await cdp.send("Emulation.setCPUThrottlingRate", { rate });
    }
  });

  test("시세 차트와 상태별 시세 테이블이 보인다", async ({ page }) => {
    const set = await pricedSet(page);
    await openPrices(page, set);
    await expect(page.getByRole("heading", { name: "시세" })).toBeVisible();

    // 인터랙티브 차트. 기본 6개월 창은 오늘 날짜에 따라 비므로, 날짜와 무관한 "전체"로 차트 자체를 본다.
    await page.getByRole("button", { name: "전체" }).click();
    await expect(page.getByRole("img", CHART).first()).toBeVisible();

    // API가 주는 상대 미디어 URL도 로컬/운영 환경의 공개 원점에서 정상 로드되어야 한다.
    const catalogImages = page.locator('img[src*="/api/v1/media/catalog/"]');
    await expect(catalogImages.first()).toBeVisible();
    await expect(page.locator('[data-image-fallback="true"]')).toHaveCount(0);

    // 상태별 추정 시세 헤더. 빠른 판매·구매 값은 추정 시세 × 고정 스프레드라 체결가·입찰가가 아니다.
    // 구매 입찰의 실제 "즉시 판매" 기능과 헷갈리지 않게 표는 "즉시판매/즉시구매"라고 부르지 않는다.
    await expect(page.getByRole("columnheader", { name: "추정 시세" }).first()).toBeVisible();
    await expect(page.getByRole("columnheader", { name: "빠른 판매 추정" }).first()).toBeVisible();
    await expect(page.getByRole("columnheader", { name: "빠른 구매 추정" }).first()).toBeVisible();
    await expect(page.getByRole("columnheader", { name: "즉시판매" })).toHaveCount(0);
    await expect(
      page.getByText("실제 체결가나 지금 받을 수 있는 입찰가가 아니에요").first(),
    ).toBeVisible();
    // 데스크톱은 표, 휴대폰은 쌓인 행으로 같은 내용을 그린다(sm 기준). 이 검사는 데스크톱 표를 본다.
    await expect(page.getByRole("table").getByText("미개봉 새상품").first()).toBeVisible();
  });

  test("기간 탭은 고정한 지금 기준으로 그 기간의 체결만 차트에 그린다", async ({ page }) => {
    const set = await pricedSet(page);
    // 최신 체결 12시간 뒤를 "지금"으로 고정한다. 1개월 창의 기대 건수는 화면과 같은 필터로 계산한다.
    const now = set.latest + 12 * HOUR_MS;
    const expected = filterPricePointsByPeriod(set.points, MONTH_DAYS, now).length;
    expect(expected).toBeGreaterThanOrEqual(2);
    await openPrices(page, set, now);

    const month = page.getByRole("button", { name: "1개월" });
    await month.click();
    await expect(month).toHaveAttribute("aria-pressed", "true");
    await expect(page.getByRole("img", CHART).first()).toBeVisible();
    await expect(page.getByText("차트를 가리켜 날짜별 가격 확인")).toBeVisible();
    // 기간 거래량이 정확히 그 창의 체결 수다 — 다른 기간·전체로 몰래 되돌아가지 않았다는 뜻이다.
    await expect(
      page.locator("dl", { hasText: "거래량" }).getByText(`${expected}건`, { exact: true }),
    ).toBeVisible();
  });

  test("체결이 없는 기간은 전체로 되돌리지 않고 비었다고 알린다", async ({ page }) => {
    const set = await pricedSet(page);
    // 최신 체결보다 62일 뒤를 "지금"으로 고정하면 1개월 창은 반드시 비고 전체 창에는 체결이 남는다.
    const now = set.latest + 62 * DAY_MS;
    expect(filterPricePointsByPeriod(set.points, MONTH_DAYS, now)).toHaveLength(0);
    await openPrices(page, set, now);

    await page.getByRole("button", { name: "1개월" }).click();
    await expect(page.getByText("1개월 체결 없음")).toBeVisible();
    await expect(page.getByText("이 기간에는 체결이 없어요")).toBeVisible();
    await expect(page.getByRole("img", CHART)).toHaveCount(0);

    await page.getByRole("button", { name: "전체" }).click();
    await expect(page.getByRole("img", CHART).first()).toBeVisible();
    await expect(
      page
        .locator("dl", { hasText: "거래량" })
        .getByText(`${set.points.length}건`, { exact: true }),
    ).toBeVisible();
  });

  test("정렬을 변경할 수 있다", async ({ page }) => {
    const set = await pricedSet(page);
    await openPrices(page, set);
    await page.getByLabel("정렬").selectOption("recent");
    // 정렬을 바꿔도 고른 세트와 차트가 그대로다. 차트는 날짜와 무관한 "전체" 창으로 본다.
    await expect(page.getByText(new RegExp(`^#${set.setNumber} ·`))).toBeVisible();
    await page.getByRole("button", { name: "전체" }).click();
    await expect(page.getByRole("img", CHART).first()).toBeVisible();
  });

  test("홈에서 전달한 세트를 바로 선택하고 목록 선택을 URL에 반영한다", async ({ page }) => {
    await page.goto("/");

    // 홈 상단 시세 티커는 무한 마퀴(animate-market-ticker)라 클릭이 "요소가 멈출 때까지"
    // 대기에 걸린다. 정적인 "지금 뜨는 세트" 목록에서 집는다.
    const trending = page
      .locator("section")
      .filter({ has: page.getByRole("heading", { name: "지금 뜨는 세트" }) });
    const trendingLink = trending.locator('a[href^="/prices?set="]').first();
    const initialSet = new URL(
      (await trendingLink.getAttribute("href"))!,
      "http://localhost",
    ).searchParams.get("set")!;

    await trendingLink.click();
    await expect(page).toHaveURL(new RegExp(`/prices\\?set=${initialSet}$`));
    // 홈의 "지금 뜨는 세트"는 거래량 기준이라 추천(featured) 목록과 다르다. 추천이 아닌
    // 세트로 들어와도 요청한 세트가 그대로 보여야 한다(조용히 다른 세트를 보여주면 안 된다).
    await expect(page.getByText(new RegExp(`^#${initialSet} ·`))).toBeVisible();

    // 목록에서 다른 세트를 고르면 URL이 따라온다(세트 목록만 ol 안의 aria-pressed 버튼).
    // 하이드레이션 전 클릭은 아무 일도 하지 않으므로 이벤트가 붙은 뒤에 누른다.
    const other = page.locator('ol button[aria-pressed="false"]').first();
    await waitForHydration(other);
    await other.click();
    await expect(page).toHaveURL(/\/prices\?set=[^&]+$/);
    const pickedSet = new URL(page.url()).searchParams.get("set")!;
    expect(pickedSet).not.toBe(initialSet);
    await expect(page.getByText(new RegExp(`^#${pickedSet} ·`))).toBeVisible();
  });
});
