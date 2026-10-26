import { crc32, deflateSync } from "node:zlib";
import { test, expect, type Page, type Route } from "@playwright/test";
import {
  DEFAULT_MASCOT_KEY,
  isMascotPresetKey,
  isSelectableMascotPresetKey,
  MASCOT_PRESET_KEYS,
  MASCOT_PRESETS,
  parseActiveMascot,
  SELECTABLE_MASCOT_PRESET_KEYS,
  SELECTABLE_MASCOT_PRESETS,
} from "@gole/core/mascot";

/**
 * 운영자 콘솔 · 마스코트 — 업로드·삭제·경합·이력 화면 동작. (mascot-assets R2·R3)
 *
 * 프리셋 적용의 기본 흐름은 admin-console.spec 이 본다. 여기서는 합성 관리자 세션과 API 응답(fixture)으로
 * 업로드(밝은·어두운 배경), 지우기와 그 경합, 적용 충돌, 이력 페이지를 본다. 실제 API 검증이 아니다.
 */

const ADMIN = { accountId: "admin-1", email: "admin@gole.test", role: "ADMIN" } as const;
const LIGHT_KEY = "images/11111111-1111-4111-8111-111111111111.png";
const DARK_KEY = "images/22222222-2222-4222-8222-222222222222.png";

/** 단색 RGB PNG. 화면이 naturalWidth/Height 로 치수를 읽으므로 실제로 디코딩되는 파일이어야 한다. */
function png(width: number, height: number): Buffer {
  const chunk = (type: string, data: Buffer) => {
    const body = Buffer.concat([Buffer.from(type, "ascii"), data]);
    const length = Buffer.alloc(4);
    length.writeUInt32BE(data.length);
    const crc = Buffer.alloc(4);
    crc.writeUInt32BE(crc32(body));
    return Buffer.concat([length, body, crc]);
  };
  const header = Buffer.alloc(13);
  header.writeUInt32BE(width, 0);
  header.writeUInt32BE(height, 4);
  header[8] = 8; // 비트 깊이
  header[9] = 2; // RGB
  const row = Buffer.concat([Buffer.from([0]), Buffer.alloc(width * 3, 0x33)]);
  const pixels = Buffer.concat(Array.from({ length: height }, () => row));
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk("IHDR", header),
    chunk("IDAT", deflateSync(pixels)),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}

function selection(revision: number, assetId: string, assetName: string) {
  return {
    revision,
    assetId,
    assetName,
    actorId: "admin-1",
    reason: revision === 0 ? "" : `revision ${revision} 사유`,
    action: revision === 0 ? "DEFAULT" : "PUBLISH",
    publishedAt: "2026-10-10T12:00:00Z",
  };
}

function upload(id: string, name: string, darkImageKey: string | null = null) {
  return {
    id,
    name,
    description: "",
    imageKey: LIGHT_KEY,
    imageUrl: `/api/v1/media/${LIGHT_KEY}`,
    darkImageKey,
    darkImageUrl: darkImageKey === null ? null : `/api/v1/media/${darkImageKey}`,
    width: 120,
    height: 80,
    createdBy: "admin-1",
    createdAt: "2026-10-10T12:00:00Z",
  };
}

interface EditorState {
  current: ReturnType<typeof selection>;
  uploads: ReturnType<typeof upload>[];
  history: ReturnType<typeof selection>[];
  presets?: readonly string[] | null;
}

function activeOf(state: EditorState) {
  const found = state.uploads.find((asset) => asset.id === state.current.assetId);
  return {
    revision: state.current.revision,
    asset:
      found === undefined
        ? { id: state.current.assetId, kind: "PRESET", name: state.current.assetName }
        : {
            id: found.id,
            kind: "UPLOAD",
            name: found.name,
            imageUrl: found.imageUrl,
            darkImageUrl: found.darkImageUrl,
            width: found.width,
            height: found.height,
          },
  };
}

/** 합성 관리자 세션 + 셸이 부르는 전역 요청 격리 + 마스코트 조회 응답을 상태에서 만든다. */
async function openMascotConsole(page: Page, state: EditorState) {
  await page.addInitScript(
    (value) => {
      window.localStorage.setItem("gole.session", value);
    },
    JSON.stringify({ ...ADMIN, sessionToken: "admin-test-token" }),
  );
  await page.route("**/api/v1/accounts/me", (route) => route.fulfill({ json: ADMIN }));
  await page.route("**/api/v1/accounts/me/onboarding", (route) =>
    route.fulfill({
      json: {
        required: false,
        legacyExempt: true,
        nicknameCompleted: true,
        nickname: "e2e",
        phoneCompleted: true,
        maskedPhoneNumber: "010-****-0000",
        interestTagsCompleted: true,
        interestTags: [],
        privacyConsented: true,
        marketingConsented: false,
      },
    }),
  );
  await page.route(/\/api\/v1\/users\/[^/]+\/notifications\/unread-count(?:\?.*)?$/, (route) =>
    route.fulfill({ json: { unreadCount: 0 } }),
  );
  await page.route("**/api/admin/overview", (route) => route.fulfill({ json: { counts: {} } }));
  await page.route("**/api/v1/config/mascot", (route) => route.fulfill({ json: activeOf(state) }));
  // 업로드 그림은 실제 미디어 서버 대신 같은 PNG 를 돌려준다.
  await page.route("**/api/v1/media/images/*.png**", (route) =>
    route.fulfill({ contentType: "image/png", body: png(120, 80) }),
  );
  await page.route("**/api/admin/mascot", (route) =>
    route.fulfill({
      json: {
        current: state.current,
        active: activeOf(state),
        presets: state.presets === undefined ? SELECTABLE_MASCOT_PRESET_KEYS : state.presets,
        uploads: state.uploads,
      },
    }),
  );
  await page.route("**/api/admin/mascot/history**", (route) => {
    const before = Number(new URL(route.request().url()).searchParams.get("before") ?? Infinity);
    const rows = state.history.filter((row) => row.revision < before).slice(0, 25);
    return route.fulfill({ json: rows });
  });
}

const pageHasNoOverflow = (page: Page) =>
  page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth);

test.describe("운영자 콘솔 · 마스코트", () => {
  test("전체 프리셋은 이전 키를 읽고 선택 가능 목록만 새 후보와 제외 정책을 따른다", () => {
    expect(MASCOT_PRESET_KEYS).toHaveLength(17);
    expect(MASCOT_PRESETS.map((preset) => preset.key)).toEqual(MASCOT_PRESET_KEYS);
    expect(SELECTABLE_MASCOT_PRESET_KEYS).toHaveLength(13);
    expect(SELECTABLE_MASCOT_PRESETS.map((preset) => preset.key)).toEqual(
      SELECTABLE_MASCOT_PRESET_KEYS,
    );
    expect(SELECTABLE_MASCOT_PRESET_KEYS.slice(0, 3)).toEqual([
      "side-brick-assembled",
      "side-brick-pocket",
      "side-brick-plate",
    ]);
    expect(DEFAULT_MASCOT_KEY).toBe("side-brick-soft");
    for (const key of ["first-sketch", "chubby", "kawaii-brick", "three-studs"]) {
      expect(isMascotPresetKey(key)).toBe(true);
      expect(isSelectableMascotPresetKey(key)).toBe(false);
      const parsed = parseActiveMascot({ revision: 7, asset: { kind: "PRESET", id: key } });
      expect(parsed.revision).toBe(7);
      expect(parsed.asset.id).toBe(key);
      expect(parsed.asset.name).toBe(MASCOT_PRESETS.find((preset) => preset.key === key)?.name);
    }
    expect(isSelectableMascotPresetKey("unknown")).toBe(false);
    expect(isSelectableMascotPresetKey(null)).toBe(false);
  });

  test("상단에서 모든 후보와 업로드를 비교해도 사이트 적용본은 바뀌지 않는다", async ({ page }) => {
    const state: EditorState = {
      current: selection(0, "side-brick", "옆모습 브릭 고래"),
      uploads: [upload("asset-1", "비교용 업로드")],
      history: [],
    };
    await openMascotConsole(page, state);
    let publishRequests = 0;
    await page.route("**/api/admin/mascot/publish", (route) => {
      publishRequests += 1;
      return route.fulfill({ status: 500, json: {} });
    });
    await page.goto("/admin/mascot");
    const preview = page.getByRole("region", { name: "마스코트 비교", exact: true });
    const presets = page.getByRole("region", { name: "기본 제공 고래", exact: true });
    const current = preview.getByRole("figure", { name: "현재 적용 중인 마스코트" });
    await expect(current).toContainText("옆모습 브릭 고래");
    await expect(presets.getByRole("button")).toHaveCount(SELECTABLE_MASCOT_PRESETS.length);
    for (const name of ["첫 고래", "통통 고래", "카와이 브릭 고래", "스터드 세 개 고래"]) {
      await expect(presets.getByRole("button", { name, exact: true })).toHaveCount(0);
    }
    for (const [index, name] of [
      "조립 브릭 고래",
      "포켓 브릭 고래",
      "플레이트 브릭 고래",
    ].entries()) {
      await expect(presets.getByRole("button").nth(index)).toHaveAccessibleName(name);
    }
    const previewBox = await preview.boundingBox();
    const presetBox = await presets.boundingBox();
    expect(previewBox!.y).toBeLessThan(presetBox!.y);

    for (const candidate of SELECTABLE_MASCOT_PRESETS) {
      await presets
        .getByRole("button", { name: candidate.name, exact: candidate.key !== "side-brick" })
        .click();
      await expect(
        preview.getByRole("heading", { name: candidate.name, exact: true }),
      ).toBeVisible();
      await expect(preview.getByRole("heading")).toBeFocused();
      await expect(current).toContainText("옆모습 브릭 고래");
      if (candidate.key !== "side-brick") {
        await expect(preview.getByText("적용 전", { exact: true })).toBeVisible();
      }
    }
    await page.getByRole("button", { name: "비교용 업로드", exact: true }).click();
    await expect(preview.getByRole("heading", { name: "비교용 업로드" })).toBeVisible();
    await expect(preview).toContainText("아직 사이트에 적용하지 않은 미리보기입니다");
    await expect(current).toContainText("옆모습 브릭 고래");
    expect(publishRequests).toBe(0);
    expect(state.current.revision).toBe(0);
  });

  for (const [key, name] of [
    ["first-sketch", "첫 고래"],
    ["chubby", "통통 고래"],
    ["kawaii-brick", "카와이 브릭 고래"],
    ["three-studs", "스터드 세 개 고래"],
  ] as const) {
    test(`제외된 현재본 ${name}의 그림과 이력은 유지하고 새 조립 후보를 적용할 수 있다`, async ({
      page,
    }) => {
      const current = selection(7, key, name);
      const state: EditorState = {
        current,
        uploads: [],
        history: [current, selection(6, "deleted-upload", "삭제된 업로드")],
      };
      await openMascotConsole(page, state);
      const requests: unknown[] = [];
      await page.route("**/api/admin/mascot/publish", async (route) => {
        requests.push(route.request().postDataJSON());
        state.current = selection(8, "side-brick-assembled", "조립 브릭 고래");
        state.history.unshift(state.current);
        await route.fulfill({ json: state.current });
      });
      await page.goto("/admin/mascot");
      const preview = page.getByRole("region", { name: "마스코트 비교", exact: true });
      const applied = preview.getByRole("figure", { name: "현재 적용 중인 마스코트" });
      await expect(applied).toContainText(name);
      await expect(applied.locator("svg")).toHaveAttribute("viewBox", "0 0 40 40");
      await expect(preview.getByRole("heading")).toHaveText("조립 브릭 고래");
      await expect(
        page.getByText("현재 적용 중인 그림은 새 선택에서 제외되었습니다.", { exact: false }),
      ).toBeVisible();
      await expect(
        page
          .getByRole("region", { name: "기본 제공 고래", exact: true })
          .getByRole("button", { name }),
      ).toHaveCount(0);

      const history = page
        .getByRole("heading", { name: "적용 이력 · 감사 기록" })
        .locator("xpath=../..");
      await expect(
        history
          .getByRole("listitem")
          .filter({ hasText: `revision 7 · ${name}` })
          .getByRole("button", { name: "선택 제외" }),
      ).toBeDisabled();
      await expect(
        history
          .getByRole("listitem")
          .filter({ hasText: "삭제된 업로드" })
          .getByRole("button", { name: "지운 그림" }),
      ).toBeDisabled();
      const apply = page.getByRole("button", { name: "이 마스코트 적용" });
      await expect(apply).toBeDisabled();
      await page.getByLabel("변경 사유 (감사 기록, 필수)").fill("조립 브릭 고래로 변경");
      await page.getByRole("checkbox").check();
      await page.getByRole("button", { name: "다시 불러오기", exact: true }).click();
      await expect(applied).toContainText(name);
      await expect(preview.getByRole("heading")).toHaveText("조립 브릭 고래");
      await expect(page.getByRole("checkbox")).not.toBeChecked();
      await expect(apply).toBeDisabled();
      await page.getByRole("checkbox").check();
      await apply.click();
      await expect(applied).toContainText("조립 브릭 고래");
      await expect(page.getByRole("checkbox")).not.toBeChecked();
      expect(requests).toEqual([
        { expectedRevision: 7, assetId: "side-brick-assembled", reason: "조립 브릭 고래로 변경" },
      ]);
    });
  }

  for (const { label, presets } of [
    {
      label: "구버전",
      presets: MASCOT_PRESET_KEYS.filter(
        (key) => !key.startsWith("side-brick-a") && !key.startsWith("side-brick-p"),
      ),
    },
    { label: "빈", presets: [] },
    { label: "null", presets: null },
    { label: "알 수 없는", presets: ["unknown", "first-sketch"] },
  ]) {
    test(`${label} 서버 목록에서는 새 후보를 미리보기만 허용하고 업로드 적용은 유지한다`, async ({
      page,
    }) => {
      await openMascotConsole(page, {
        current: selection(0, "side-brick-soft", "미소 브릭 고래"),
        uploads: [upload("asset-1", "업로드 고래")],
        history: [],
        presets,
      });
      let publishRequests = 0;
      await page.route("**/api/admin/mascot/publish", (route) => {
        publishRequests += 1;
        return route.fulfill({ status: 500, json: {} });
      });
      await page.goto("/admin/mascot");
      await page.getByLabel("변경 사유 (감사 기록, 필수)").fill("지원 여부 확인");
      const preview = page.getByRole("region", { name: "마스코트 비교", exact: true });
      const apply = page.getByRole("button", { name: "이 마스코트 적용" });
      for (const name of ["조립 브릭 고래", "포켓 브릭 고래", "플레이트 브릭 고래"]) {
        await page.getByRole("button", { name, exact: true }).click();
        await expect(preview.getByRole("heading")).toHaveText(name);
        await expect(
          page.getByText("아직 적용할 수 없는 후보예요. 미리보기로 비교해 주세요."),
        ).toBeVisible();
        await expect(page.getByRole("checkbox")).toBeDisabled();
        await expect(apply).toBeDisabled();
      }
      if (label === "구버전") {
        await page.getByRole("button", { name: "말랑 브릭 고래", exact: true }).click();
        await page.getByRole("checkbox").check();
        await expect(apply).toBeEnabled();
      }
      await page.getByRole("button", { name: "업로드 고래", exact: true }).click();
      await expect(page.getByRole("checkbox")).not.toBeChecked();
      await page.getByRole("checkbox").check();
      await expect(apply).toBeEnabled();
      expect(publishRequests).toBe(0);
    });
  }

  test("키보드로 이전·다음 후보를 비교하면 검토 확인이 해제되고 초점은 버튼에 남는다", async ({
    page,
  }) => {
    await openMascotConsole(page, {
      current: selection(0, "side-brick", "옆모습 브릭 고래"),
      uploads: [],
      history: [],
    });
    await page.goto("/admin/mascot");
    await page.getByRole("button", { name: "둥근 아기 고래", exact: true }).click();
    await page.getByLabel("변경 사유 (감사 기록, 필수)").fill("작은 크기의 표정을 비교");
    const reviewed = page.getByRole("checkbox");
    await reviewed.check();
    const apply = page.getByRole("button", { name: "이 마스코트 적용" });
    await expect(apply).toBeEnabled();
    const next = page.getByRole("button", { name: "다음 후보", exact: true });
    await next.focus();
    await page.keyboard.press("Enter");
    await expect(reviewed).not.toBeChecked();
    await expect(apply).toBeDisabled();
    await expect(next).toBeFocused();
    const previous = page.getByRole("button", { name: "이전 후보", exact: true });
    await previous.press("Enter");
    await expect(
      page.getByRole("region", { name: "마스코트 비교", exact: true }).getByRole("heading"),
    ).toHaveText("둥근 아기 고래");
    await expect(reviewed).not.toBeChecked();
    await expect(previous).toBeFocused();
  });

  test("홈 히어로 모드는 큰 그림의 분수만 바꾸고 분수 없는 후보도 비교할 수 있다", async ({
    page,
  }) => {
    await openMascotConsole(page, {
      current: selection(0, "side-brick", "옆모습 브릭 고래"),
      uploads: [],
      history: [],
    });
    await page.goto("/admin/mascot");
    await page.getByRole("button", { name: "말랑 브릭 고래", exact: true }).click();
    const preview = page.getByRole("region", { name: "마스코트 비교", exact: true });
    const large = preview
      .getByRole("figure", { name: "선택한 마스코트 큰 미리보기" })
      .locator("svg");
    const header = preview
      .getByRole("figure")
      .filter({ has: page.getByText("헤더", { exact: true }) })
      .locator("svg");
    const markBox = await large.getAttribute("viewBox");
    const headerBox = await header.getAttribute("viewBox");
    await page.getByRole("button", { name: "홈 히어로", exact: true }).click();
    await expect(large).not.toHaveAttribute("viewBox", markBox!);
    await expect(header).toHaveAttribute("viewBox", headerBox!);
    await expect(page.getByRole("button", { name: "홈 히어로", exact: true })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    await page.getByRole("button", { name: "일반 로고", exact: true }).click();
    await expect(large).toHaveAttribute("viewBox", markBox!);
    await page.getByRole("button", { name: "한 획 플랫 고래", exact: true }).click();
    const plainBox = await large.getAttribute("viewBox");
    await page.getByRole("button", { name: "홈 히어로", exact: true }).click();
    await expect(large).toHaveAttribute("viewBox", plainBox!);
    await expect(large).toBeVisible();
  });

  test("설정 로딩과 오류를 구분하고 다시 불러오기로 미리보기를 복구한다", async ({ page }) => {
    const state: EditorState = {
      current: selection(0, "side-brick", "옆모습 브릭 고래"),
      uploads: [],
      history: [],
    };
    await openMascotConsole(page, state);
    let release!: () => void;
    const pending = new Promise<void>((resolve) => {
      release = resolve;
    });
    await page.route("**/api/admin/mascot", async (route) => {
      await pending;
      await route.fulfill({ status: 503, json: {} });
    });
    await page.goto("/admin/mascot");
    await expect(page.getByText("마스코트 미리보기를 불러오는 중…")).toBeVisible();
    await expect(page.getByRole("button", { name: /둥근 아기 고래/ })).toBeDisabled();
    release();
    await expect(
      page.getByRole("alert").filter({ hasText: "마스코트 설정을 불러오지 못했습니다" }),
    ).toBeVisible();
    await expect(page.getByText("마스코트 미리보기를 불러오는 중…")).toHaveCount(0);
    await page.route("**/api/admin/mascot", (route) =>
      route.fulfill({
        json: {
          current: state.current,
          active: activeOf(state),
          presets: SELECTABLE_MASCOT_PRESET_KEYS,
          uploads: [],
        },
      }),
    );
    await page.getByRole("button", { name: "다시 불러오기" }).click();
    await expect(
      page.getByRole("region", { name: "마스코트 비교", exact: true }).getByRole("heading"),
    ).toHaveText("옆모습 브릭 고래");
    await expect(page.getByRole("button", { name: /둥근 아기 고래/ })).toBeEnabled();
  });

  test("최신 설정을 불러오는 동안 후보 선택을 막는다", async ({ page }) => {
    const state: EditorState = {
      current: selection(0, "side-brick", "옆모습 브릭 고래"),
      uploads: [],
      history: [],
    };
    await openMascotConsole(page, state);
    await page.goto("/admin/mascot");
    const candidate = page.getByRole("button", { name: /둥근 아기 고래/ });
    await expect(candidate).toBeVisible();
    let release!: () => void;
    const pending = new Promise<void>((resolve) => {
      release = resolve;
    });
    await page.route("**/api/admin/mascot", async (route) => {
      await pending;
      await route.fulfill({
        json: { current: state.current, active: activeOf(state), presets: [], uploads: [] },
      });
    });
    await page.getByRole("button", { name: "다시 불러오기" }).click();
    try {
      await expect(candidate).toBeDisabled();
    } finally {
      release();
    }
    await expect(candidate).toBeEnabled();
  });

  test("저장 응답을 기다리는 동안 선택과 중복 적용을 막고 저장한 후보를 유지한다", async ({
    page,
  }) => {
    const state: EditorState = {
      current: selection(0, "side-brick", "옆모습 브릭 고래"),
      uploads: [],
      history: [],
    };
    await openMascotConsole(page, state);
    let release!: () => void;
    const pending = new Promise<void>((resolve) => {
      release = resolve;
    });
    const requests: unknown[] = [];
    await page.route("**/api/admin/mascot/publish", async (route) => {
      requests.push(route.request().postDataJSON());
      await pending;
      state.current = selection(1, "baby-round", "둥근 아기 고래");
      state.history = [state.current];
      await route.fulfill({ json: state.current });
    });
    await page.goto("/admin/mascot");
    await page.getByRole("button", { name: "둥근 아기 고래", exact: true }).click();
    await page.getByLabel("변경 사유 (감사 기록, 필수)").fill("미리보기 확인");
    await page.getByRole("checkbox").check();
    const apply = page.getByRole("button", { name: "이 마스코트 적용" });
    await apply.click();
    try {
      await expect(apply).toBeDisabled();
      await expect(page.getByRole("button", { name: "다음 후보" })).toBeDisabled();
      await expect(page.getByRole("button", { name: "꼬리 든 고래", exact: true })).toBeDisabled();
      await expect(page.getByLabel("이름 (필수)")).toBeDisabled();
    } finally {
      release();
    }
    const preview = page.getByRole("region", { name: "마스코트 비교", exact: true });
    await expect(preview.getByRole("figure", { name: "현재 적용 중인 마스코트" })).toContainText(
      "둥근 아기 고래",
    );
    await expect(preview.getByRole("heading")).toHaveText("둥근 아기 고래");
    await expect(preview.getByText("현재 적용본", { exact: true })).toBeVisible();
    expect(requests).toHaveLength(1);
  });

  test("밝은·어두운 배경 그림을 올리면 치수와 두 키로 등록하고 새 에셋을 미리 본다", async ({
    page,
  }) => {
    const state: EditorState = {
      current: selection(0, "side-brick", "옆모습 브릭 고래"),
      uploads: [],
      history: [],
    };
    await openMascotConsole(page, state);
    const uploaded: string[] = [];
    await page.route("**/api/v1/media/images", (route) => {
      const key = uploaded.length === 0 ? LIGHT_KEY : DARK_KEY;
      uploaded.push(key);
      return route.fulfill({ json: { key, url: `/api/v1/media/${key}` } });
    });
    let registered: unknown = null;
    await page.route("**/api/admin/mascot/assets", (route) => {
      registered = route.request().postDataJSON();
      state.uploads.push(upload("asset-1", "가을 고래", DARK_KEY));
      return route.fulfill({ status: 201, json: state.uploads[0] });
    });

    await page.goto("/admin/mascot");
    const submit = page.getByRole("button", { name: "올리기", exact: true });
    await expect(submit).toBeDisabled();
    await page.getByLabel("이름 (필수)").fill("가을 고래");
    await page
      .getByLabel("마스코트 이미지 (필수)")
      .setInputFiles({ name: "light.png", mimeType: "image/png", buffer: png(120, 80) });
    await page
      .getByLabel("어두운 배경용 이미지 (선택)")
      .setInputFiles({ name: "dark.png", mimeType: "image/png", buffer: png(120, 80) });
    await expect(page.getByText("120×80px", { exact: true })).toBeVisible();
    await submit.click();

    await expect(page.getByText('"가을 고래"을(를) 올렸습니다.', { exact: false })).toBeVisible();
    expect(uploaded).toEqual([LIGHT_KEY, DARK_KEY]);
    expect(registered).toEqual({
      name: "가을 고래",
      description: "",
      imageKey: LIGHT_KEY,
      darkImageKey: DARK_KEY,
      width: 120,
      height: 80,
    });
    // 올린 그림이 선택되고, 푸터(어두운 배경) 미리보기는 어두운 배경용 이미지를 쓴다.
    await expect(page.getByRole("button", { name: /가을 고래/, pressed: true })).toBeVisible();
    const footer = page.getByRole("figure").filter({ hasText: "푸터 (어두운 배경)" });
    await expect(footer.locator("img")).toHaveAttribute("src", new RegExp(`${DARK_KEY}\\?w=`));
    const header = page.getByRole("figure").filter({ hasText: "헤더" });
    await expect(header.locator("img")).toHaveAttribute("src", new RegExp(`${LIGHT_KEY}\\?w=`));
  });

  test("SVG 와 너무 작은 이미지는 올리기 전에 막는다", async ({ page }) => {
    await openMascotConsole(page, {
      current: selection(0, "side-brick", "옆모습 브릭 고래"),
      uploads: [],
      history: [],
    });
    await page.goto("/admin/mascot");
    const main = page.getByLabel("마스코트 이미지 (필수)");
    await main.setInputFiles({
      name: "whale.svg",
      mimeType: "image/svg+xml",
      buffer: Buffer.from('<svg xmlns="http://www.w3.org/2000/svg"/>'),
    });
    await expect(page.getByText("PNG 또는 JPEG 파일만 올릴 수 있습니다.")).toBeVisible();
    await main.setInputFiles({ name: "tiny.png", mimeType: "image/png", buffer: png(8, 8) });
    await expect(page.getByText("가로·세로 16~4096px 이미지만 올릴 수 있습니다.")).toBeVisible();
    await page.getByLabel("이름 (필수)").fill("작은 고래");
    await expect(page.getByRole("button", { name: "올리기", exact: true })).toBeDisabled();
  });

  test("적용 충돌(409)이면 고른 에셋을 두고 검토 확인을 다시 받는다", async ({ page }) => {
    const state: EditorState = {
      current: selection(0, "side-brick", "옆모습 브릭 고래"),
      uploads: [],
      history: [],
    };
    await openMascotConsole(page, state);
    await page.route("**/api/admin/mascot/publish", (route) => {
      // 다른 관리자가 먼저 리비전 1을 적용했다.
      state.current = selection(1, "tail-up", "꼬리 든 고래");
      state.history = [state.current];
      return route.fulfill({
        status: 409,
        json: { code: "MASCOT_REVISION_CONFLICT", message: "다른 관리자가 먼저 바꿨습니다." },
      });
    });

    await page.goto("/admin/mascot");
    await page.getByRole("button", { name: /둥근 아기 고래/ }).click();
    await page.getByLabel("변경 사유 (감사 기록, 필수)").fill("가을 이벤트");
    const reviewed = page.getByRole("checkbox");
    await reviewed.check();
    const apply = page.getByRole("button", { name: "이 마스코트 적용" });
    await apply.click();

    await expect(page.locator("p[role=alert]")).toContainText("다른 관리자가 먼저 바꿨습니다");
    await expect(apply).toBeDisabled();
    await expect(page.getByRole("button", { name: /둥근 아기 고래/, pressed: true })).toBeVisible();
    // 사이트 상태가 바뀌었으니 예전 확인은 무효다 — 최신 값을 불러온 뒤 다시 확인해야 적용할 수 있다.
    await expect(reviewed).not.toBeChecked();
    await page.getByRole("button", { name: "최신 값 불러오기" }).click();
    await expect(
      page.getByText("변경 버전 1 · 지금 적용: 꼬리 든 고래", { exact: false }),
    ).toBeVisible();
    await expect(apply).toBeDisabled();
    await reviewed.check();
    await expect(apply).toBeEnabled();
  });

  test("지우기가 경합으로 거절되면(409·404) 최신 상태를 다시 불러온다", async ({ page }) => {
    const state: EditorState = {
      current: selection(0, "side-brick", "옆모습 브릭 고래"),
      uploads: [upload("asset-1", "가을 고래"), upload("asset-2", "겨울 고래")],
      history: [],
    };
    await openMascotConsole(page, state);
    await page.route("**/api/admin/mascot/assets/*", (route) => {
      const id = route.request().url().split("/").at(-1);
      if (id === "asset-1") {
        // 다른 관리자가 그 사이 가을 고래를 적용했다.
        state.current = selection(1, "asset-1", "가을 고래");
        state.history = [state.current];
        return route.fulfill({
          status: 409,
          json: { code: "MASCOT_ASSET_IN_USE", message: "적용 중인 마스코트는 지울 수 없습니다." },
        });
      }
      // 다른 관리자가 그 사이 겨울 고래를 지웠다.
      state.uploads = state.uploads.filter((asset) => asset.id !== "asset-2");
      return route.fulfill({
        status: 404,
        json: { code: "MASCOT_ASSET_NOT_FOUND", message: "마스코트 에셋을 찾을 수 없습니다." },
      });
    });
    /** 앱 확인 모달에서 지우기를 확정한다. */
    const confirmRemove = () =>
      page
        .getByRole("alertdialog", { name: "마스코트 지우기" })
        .getByRole("button", { name: "지우기" })
        .click();

    await page.goto("/admin/mascot");
    const uploads = page.getByRole("heading", { name: "업로드한 마스코트" }).locator("xpath=../..");
    const autumn = uploads.getByRole("listitem").filter({ hasText: "가을 고래" });
    await autumn.getByRole("button", { name: "지우기" }).click();
    await confirmRemove();
    await expect(page.locator("p[role=alert]")).toContainText(
      "적용 중인 마스코트는 지울 수 없습니다.",
    );
    await expect(autumn.getByRole("button", { name: "적용 중이라 지울 수 없음" })).toBeDisabled();
    await expect(
      page.getByText("변경 버전 1 · 지금 적용: 가을 고래", { exact: false }),
    ).toBeVisible();

    await uploads
      .getByRole("listitem")
      .filter({ hasText: "겨울 고래" })
      .getByRole("button", { name: "지우기" })
      .click();
    await confirmRemove();
    await expect(page.locator("p[role=alert]")).toContainText("마스코트 에셋을 찾을 수 없습니다.");
    await expect(uploads.getByRole("listitem").filter({ hasText: "겨울 고래" })).toHaveCount(0);
  });

  test("이력은 끝까지 넘기면 더 보기를 숨기고, 지운 그림은 다시 고를 수 없다", async ({ page }) => {
    const history = Array.from({ length: 27 }, (_, index) => {
      const revision = 27 - index;
      return revision === 3
        ? selection(revision, "deleted-asset", "지운 고래")
        : selection(
            revision,
            revision % 2 === 0 ? "baby-round" : "tail-up",
            revision % 2 === 0 ? "둥근 아기 고래" : "꼬리 든 고래",
          );
    });
    const state: EditorState = { current: history[0]!, uploads: [], history };
    await openMascotConsole(page, state);

    await page.goto("/admin/mascot");
    const rows = page
      .getByRole("heading", { name: "적용 이력 · 감사 기록" })
      .locator("xpath=../..");
    await expect(rows.getByRole("listitem")).toHaveCount(25);
    await rows.getByRole("button", { name: "이전 이력 더 보기" }).click();
    await expect(rows.getByRole("listitem")).toHaveCount(27);
    await expect(rows.getByRole("button", { name: "이전 이력 더 보기" })).toHaveCount(0);

    const deleted = rows.getByRole("listitem").filter({ hasText: "revision 3 · 지운 고래" });
    await expect(deleted.getByRole("button", { name: "지운 그림" })).toBeDisabled();

    await rows
      .getByRole("listitem")
      .filter({ hasText: "revision 26 · 둥근 아기 고래" })
      .getByRole("button", { name: "다시 고르기" })
      .click();
    await expect(page.getByLabel("변경 사유 (감사 기록, 필수)")).toHaveValue(
      'revision 26의 "둥근 아기 고래"(으)로 되돌림',
    );
    await expect(page.getByRole("button", { name: /둥근 아기 고래/, pressed: true })).toBeVisible();
  });

  for (const width of [320, 375, 414, 768, 1440]) {
    test(`${width}px 폭에서 긴 이름의 업로드 에셋과 이력이 가로로 넘치지 않는다`, async ({
      page,
    }) => {
      await page.setViewportSize({ width, height: 900 });
      const longName = "W".repeat(40);
      const state: EditorState = {
        current: selection(1, "asset-1", longName),
        uploads: [upload("asset-1", longName, DARK_KEY), upload("asset-2", "가".repeat(40))],
        history: [selection(1, "asset-1", longName)],
      };
      await openMascotConsole(page, state);
      await page.goto("/admin/mascot");
      await expect(page.getByRole("heading", { name: longName })).toBeVisible();
      await page.getByRole("button", { name: /가가가/ }).click();
      expect(await pageHasNoOverflow(page)).toBe(true);
      const preview = page.getByRole("region", { name: "마스코트 비교", exact: true });
      const titleBox = await preview.getByRole("heading").boundingBox();
      const previewBox = await preview.boundingBox();
      // 이전·다음 버튼이 제목 폭을 한 글자 너비로 밀어내면 넘침 없이도 읽을 수 없어진다.
      expect(titleBox!.width).toBeGreaterThan(previewBox!.width * 0.45);
      const artworkFits = await page
        .getByRole("region", { name: "기본 제공 고래", exact: true })
        .getByRole("button")
        .locator("svg, img")
        .evaluateAll((images) =>
          images.every((image) => {
            const imageBox = image.getBoundingClientRect();
            const slot = image.parentElement!.parentElement!.getBoundingClientRect();
            return imageBox.left >= slot.left - 1 && imageBox.right <= slot.right + 1;
          }),
        );
      expect(artworkFits).toBe(true);
      const removeButtons = page
        .getByRole("region", { name: "업로드한 마스코트", exact: true })
        .getByRole("button", { name: /지우기|지울 수 없음/ });
      for (const button of await removeButtons.all()) {
        expect((await button.boundingBox())!.height).toBeGreaterThanOrEqual(44);
      }
    });
  }
});
