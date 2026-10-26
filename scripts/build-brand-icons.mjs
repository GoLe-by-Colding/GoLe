#!/usr/bin/env node
/**
 * 브랜드 아이콘 생성기.
 *
 * 정본은 `apps/web/src/shared/ui/logo/assembled-mark.json`이다. 승인된 프리셋의 좌표·색을
 * 웹·OG와 함께 읽어 mark.svg 및 플랫폼 변형(웹 favicon·apple-icon, 모바일 아이콘·스플래시·
 * Android 알림 아이콘, 카카오 앱 아이콘)을 만든다. 다른 프리셋은 바꾸지 않는다.
 * Next에 설치된 sharp로 PNG를 만든다.
 *
 * 사용: node scripts/build-brand-icons.mjs          # 생성 + 검사
 *       node scripts/build-brand-icons.mjs --check  # 쓰기 없이 정본·산출물·안전 영역·알파 검사
 */
import { createRequire } from "node:module";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const SOURCE_PATH = "apps/web/src/shared/ui/logo/assembled-mark.json";
const MARK_PATH = "apps/web/src/shared/ui/logo/mark.svg";
/** 두 소비자는 공용 렌더러를 사용하므로 SVG 경로 사본을 두지 않는다. */
const CONSUMERS = [
  "apps/web/src/shared/ui/logo/presets/side-brick-assembled.tsx",
  "apps/web/src/app/opengraph-image.tsx",
];
const CHECK = process.argv.includes("--check");

/** 브랜드 단색. 그라데이션은 `brand-identity.md`에서 금지한다. */
const BRAND = "#1D4ED8"; // brand-600
const WHITE = "#FFFFFF";
/** 원본 눈·미소와 같은 워드마크 색. */
const FACE = "#131E4F";

// ── 정본 읽기 ──
function readMark() {
  const mark = JSON.parse(readFileSync(join(ROOT, SOURCE_PATH), "utf8"));
  if (!(mark.width > 0 && mark.height > 0 && mark.elements?.length > 0))
    throw new Error(`${SOURCE_PATH}: 마크 크기 또는 SVG 요소가 없음`);
  const ids = new Set();
  for (const element of mark.elements) {
    if (
      ids.has(element.id) ||
      !/^[a-z-]+$/.test(element.id) ||
      !["path", "rect", "circle"].includes(element.tag) ||
      !["body", "cutout", "glint"].includes(element.monochrome)
    )
      throw new Error(
        `${SOURCE_PATH}: 지원하지 않거나 중복된 요소 ${element.id}`,
      );
    ids.add(element.id);
  }
  return { ...mark, w: mark.width, h: mark.height };
}

function checkConsumers() {
  const stale = CONSUMERS.filter((file) => {
    const src = readFileSync(join(ROOT, file), "utf8");
    return (
      !/(?:<AssembledMark|AssembledMark\(\{\}\))/.test(src) ||
      !src.includes("ASSEMBLED_MARK_VIEW_BOX")
    );
  });
  if (stale.length > 0)
    throw new Error(
      `공용 조립 고래 렌더러를 사용하지 않는 파일: ${stale.join(", ")}`,
    );
  console.log(
    `  정본 연결 확인: 웹 프리셋·OG가 공용 조립 고래 렌더러를 사용함`,
  );
}

/** React SVG 속성을 직렬화한다. CSS 테마 클래스는 웹에서만 쓰고 정적 파일은 원본 색을 쓴다. */
function svgElement({ id, tag, props }, color) {
  const attributes = Object.entries(props)
    .filter(([key]) => key !== "className")
    .map(([key, value]) => {
      const name = key.replace(
        /[A-Z]/g,
        (letter) => `-${letter.toLowerCase()}`,
      );
      const raw = color && (key === "fill" || key === "stroke") ? color : value;
      const escaped = String(raw)
        .replaceAll("&", "&amp;")
        .replaceAll('"', "&quot;");
      return `${name}="${escaped}"`;
    });
  return `<${tag} id="gole-${id}" ${attributes.join(" ")}/>`;
}

function markArt(mark, mono = false) {
  if (!mono)
    return mark.elements.map((element) => svgElement(element)).join("\n");
  // 알파만 쓰는 시스템 아이콘은 흰 윤곽 + 눈·미소·관절 구멍이다. 원본의 반짝임도 그대로 남긴다.
  const bodies = mark.elements.filter(
    (element) => element.monochrome === "body",
  );
  const details = mark.elements.filter(
    (element) => element.monochrome !== "body",
  );
  return `<defs>
  <mask id="gole-mono" maskUnits="userSpaceOnUse" x="0" y="0" width="${mark.w}" height="${mark.h}">
    <rect width="${mark.w}" height="${mark.h}" fill="${WHITE}"/>
${details.map((element) => svgElement(element, element.monochrome === "glint" ? WHITE : "#000000")).join("\n")}
  </mask>
</defs>
<g mask="url(#gole-mono)">
${bodies.map((element) => svgElement(element, WHITE)).join("\n")}
</g>`;
}

/** 생성 파일은 직접 고치지 않는다. --check는 파일 쓰기 없이 오래된 출력도 잡는다. */
function output(file, content) {
  const path = join(ROOT, file);
  const expected = Buffer.isBuffer(content) ? content : Buffer.from(content);
  if (CHECK) {
    let actual;
    try {
      actual = readFileSync(path);
    } catch {
      throw new Error(`${file}: 출력이 없음 — pnpm brand:icons를 실행해야 함`);
    }
    if (!actual.equals(expected))
      throw new Error(
        `${file}: 정본과 출력이 다름 — pnpm brand:icons를 실행해야 함`,
      );
  } else {
    mkdirSync(dirname(path), { recursive: true });
    writeFileSync(path, expected);
  }
}

// ── 배치 ──
/**
 * 마크를 캔버스 가운데에 목표 너비로 앉힌다. 옆모습 브릭 고래는 머리·몸이 왼쪽에 무겁고 꼬리 플레이트가
 * 가늘고 스터드 줄이 위에 있어 경계 상자 중심에 두면 눈에는 왼쪽 위로 쏠려 보인다.
 * 기존 광학 보정(너비 2.4% 오른쪽·높이 1.6% 아래)을 유지한다.
 * 실제 안전 영역은 생성한 PNG의 불투명 픽셀로 다시 검사한다.
 */
const NUDGE_X = 0.024;
const NUDGE_Y = 0.016;
function placed(mark, { canvas, width, mono = false }) {
  const s = width / mark.w;
  const tx = canvas / 2 - (mark.w / 2) * s + mark.w * s * NUDGE_X;
  const ty = canvas / 2 - (mark.h / 2) * s + mark.h * s * NUDGE_Y;
  return `  <g transform="translate(${tx.toFixed(3)} ${ty.toFixed(3)}) scale(${s.toFixed(5)})">
${markArt(mark, mono)}
  </g>`;
}

function svgDoc(canvas, inner) {
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${canvas} ${canvas}" width="${canvas}" height="${canvas}" fill="none">
${inner}
</svg>
`;
}
const solid = (canvas, fill) =>
  `  <rect width="${canvas}" height="${canvas}" fill="${fill}"/>`;

/**
 * Android 어댑티브는 108dp 캔버스 중 지름 66dp 원만 어떤 마스크에서도 남는다.
 * 조립 고래도 기존 폭 58dp를 유지한다. 생성 후 PNG를 실측해 반지름 33dp를 넘으면 멈춘다.
 */
const FG_WIDTH = 58;
/** iOS 1024² 캔버스에서 마크 폭. 가로로 긴 마크라 정면 마크(640)보다 넓혀 시각 무게를 맞춘다. */
const IOS_WIDTH = 700;

function variants(mark) {
  const mono = { mono: true };
  return [
    // iOS: 1024² 무알파, 자체 라운딩 없음 — 모서리는 OS가 마스크한다.
    {
      out: "apps/mobile/assets/brand/icon.svg",
      png: "apps/mobile/assets/images/icon.png",
      size: 1024,
      flatten: true,
      svg: svgDoc(
        1024,
        `${solid(1024, WHITE)}\n${placed(mark, { canvas: 1024, width: IOS_WIDTH })}`,
      ),
    },
    {
      out: "apps/mobile/assets/brand/android-icon-foreground.svg",
      png: "apps/mobile/assets/images/android-icon-foreground.png",
      size: 512,
      safeZone: true,
      transparent: true,
      svg: svgDoc(108, placed(mark, { canvas: 108, width: FG_WIDTH })),
    },
    {
      out: "apps/mobile/assets/brand/android-icon-background.svg",
      png: "apps/mobile/assets/images/android-icon-background.png",
      size: 512,
      svg: svgDoc(108, solid(108, WHITE)),
    },
    // 런처가 알파로 테마 색을 입힌다. 골드 스터드는 흰 실루엣에 합쳐져 스터드 줄이 되고,
    // 눈·미소·힌지 핀 고리는 구멍으로 남아 테마 아이콘에서도 얼굴과 조립 결이 보인다.
    {
      out: "apps/mobile/assets/brand/android-icon-monochrome.svg",
      png: "apps/mobile/assets/images/android-icon-monochrome.png",
      size: 432,
      safeZone: true,
      transparent: true,
      monochrome: true,
      svg: svgDoc(108, placed(mark, { canvas: 108, width: FG_WIDTH, ...mono })),
    },
    // 네이티브 스플래시. 흰 배경(app.json expo-splash-screen)이라 정본 색 고래 + GoLe 워드마크다.
    // 288 단위 = Android 288dp 아이콘 캔버스(app.json imageWidth 288). PNG는 xxxhdpi(×4)를 덮는다.
    {
      out: "apps/mobile/assets/brand/splash-icon.svg",
      png: "apps/mobile/assets/images/splash-icon.png",
      size: SPLASH_CANVAS * 4,
      splashCircle: true,
      transparent: true,
      svg: svgDoc(SPLASH_CANVAS, splashArt(mark)),
    },
    // Android 상태 표시줄 알림 아이콘. 시스템이 알파만 보고 단색으로 칠하므로 흰 실루엣 + 투명 배경이다.
    // 24dp 중 가장자리 1dp를 비운다. 광학 보정까지 포함해 96px 캔버스에 폭 86px로 앉힌다.
    // app.json의 expo-notifications `icon`이 읽는다. 지정하지 않으면 Android 8+에서 아이콘이 비어 보인다.
    {
      out: "apps/mobile/assets/brand/notification-icon.svg",
      png: "apps/mobile/assets/images/notification-icon.png",
      size: 96,
      transparent: true,
      monochrome: true,
      padding: 4,
      svg: svgDoc(96, placed(mark, { canvas: 96, width: 86, ...mono })),
    },
  ];
}

// ── 네이티브 스플래시 ──
/** Android 12+ 스플래시 아이콘 캔버스(dp). 시스템이 가운데 지름 192dp 원만 남긴다(배경 없는 아이콘). */
const SPLASH_CANVAS = 288;
/** 원 반지름 96dp에서 안티앨리어싱 여유 2dp를 뺀 상한. 생성 후 실측해 넘으면 멈춘다. */
const SPLASH_RADIUS_DP = 94;
/** 고래 폭(dp). 기존 76dp의 두 배 남짓 — 흰 화면에서 첫눈에 고래가 주인공이다. */
const SPLASH_WHALE = 150;
/** 고래와 워드마크 사이(dp). */
const SPLASH_GAP = 16;
/** 워드마크 배율. 원래 좌표는 글자 높이 34·폭 119.75 단위다. */
const WORDMARK_SCALE = 0.95;

/**
 * `GoLe` 워드마크. 글꼴 파일 없이 둥근 선(굵기 6.5)으로 그린 기하 글자라 어느 머신의 librsvg든 같은 PNG가 나오고,
 * 둥근 끝이 브릭 고래의 둥근 모서리와 어울린다. 좌표는 기준선 y=0, 대문자 높이 34·소문자 높이 25(선 바깥 기준)이고
 * 소문자의 속공간(반지름 6)이 작은 크기에서도 막히지 않는 굵기다.
 * 웹 로고처럼 `Go`는 짙은 남색, `Le`는 브랜드 파랑이다.
 */
function wordmark(go, le) {
  const stroke = `stroke-width="6.5" stroke-linecap="round" stroke-linejoin="round" fill="none"`;
  // G: 오른쪽 위(45°)에서 위·왼쪽·아래를 돌아 오른쪽 가운데로 온 뒤 안쪽으로 가로획.
  const g = "M26.72 -26.72A13.75 13.75 0 1 0 30.75 -17L21 -17";
  // o: 소문자 높이 25의 원.
  const o =
    "M60.25 -12.5A9.25 9.25 0 1 0 41.75 -12.5A9.25 9.25 0 1 0 60.25 -12.5Z";
  // L: 세로획 + 바닥 가로획.
  const l = "M71.25 -30.75L71.25 -3.25L87 -3.25";
  // e: 가운데 가로획에서 위·왼쪽·아래로 돌아 오른쪽 아래(40°)에서 끝난다.
  const e = "M98 -12.5L116.5 -12.5A9.25 9.25 0 1 0 114.34 -6.55";
  return [
    `<path d="${g} ${o}" stroke="${go}" ${stroke}/>`,
    `<path d="${l} ${e}" stroke="${le}" ${stroke}/>`,
  ];
}
const WORDMARK_W = 119.75;
const WORDMARK_H = 34;

/** 고래(정본 색) 위, 워드마크 아래로 쌓은 묶음을 288dp 캔버스 가운데에 둔다. */
function splashArt(mark) {
  const s = SPLASH_WHALE / mark.w;
  const whaleH = mark.h * s;
  const wordW = WORDMARK_W * WORDMARK_SCALE;
  const wordH = WORDMARK_H * WORDMARK_SCALE;
  const top = (SPLASH_CANVAS - (whaleH + SPLASH_GAP + wordH)) / 2;
  const whaleX = (SPLASH_CANVAS - SPLASH_WHALE) / 2 + SPLASH_WHALE * NUDGE_X;
  const wordX = (SPLASH_CANVAS - wordW) / 2;
  const baseline = top + whaleH + SPLASH_GAP + wordH;
  const word = wordmark(FACE, BRAND);
  return `  <g transform="translate(${whaleX.toFixed(3)} ${top.toFixed(3)}) scale(${s.toFixed(5)})">
${markArt(mark)}
  </g>
  <g transform="translate(${wordX.toFixed(3)} ${baseline.toFixed(3)}) scale(${WORDMARK_SCALE})">
${word.map((path) => `    ${path}`).join("\n")}
  </g>`;
}

/** 탭 바 색과 무관하게 층진 파랑이 읽히도록 흰 타일 위에 정본 색 고래를 올린다. */
function faviconSvg(mark) {
  return svgDoc(
    64,
    `  <rect width="64" height="64" rx="14" fill="${WHITE}"/>\n${placed(mark, { canvas: 64, width: 52 })}`,
  );
}

// ── PNG ──
function loadSharp() {
  const webRequire = createRequire(join(ROOT, "apps/web/package.json"));
  return createRequire(webRequire.resolve("next/package.json"))("sharp");
}

/** 불투명 픽셀 중 캔버스 중심에서 가장 먼 거리(dp, `canvasDp` 기준 — 어댑티브 108dp, 스플래시 288dp). */
async function farthestDp(sharp, pngPath, canvasDp = 108) {
  const { data, info } = await sharp(pngPath)
    .ensureAlpha()
    .raw()
    .toBuffer({ resolveWithObject: true });
  const c = info.width / 2;
  let max = 0;
  for (let y = 0; y < info.height; y++) {
    for (let x = 0; x < info.width; x++) {
      if (data[(y * info.width + x) * 4 + 3] > 8)
        max = Math.max(max, Math.hypot(x + 0.5 - c, y + 0.5 - c));
    }
  }
  return (max / info.width) * canvasDp;
}

/** 플랫폼의 실제 PNG 크기·알파·단색·알림 여백을 검사한다. */
async function checkRaster(sharp, png, variant) {
  const {
    png: path,
    size,
    flatten,
    transparent,
    monochrome,
    padding = 0,
  } = variant;
  const meta = await sharp(png).metadata();
  if (meta.width !== size || meta.height !== size)
    throw new Error(`${path}: ${size}×${size} 크기가 아님`);
  if (flatten && meta.hasAlpha)
    throw new Error(`${path}: 알파 채널이 남아 있음`);
  if (!transparent) return;
  const { data, info } = await sharp(png)
    .ensureAlpha()
    .raw()
    .toBuffer({ resolveWithObject: true });
  if (!meta.hasAlpha || data[3] !== 0 || data[data.length - 1] !== 0)
    throw new Error(`${path}: 투명 배경이 아님`);
  for (let y = 0; y < info.height; y++) {
    for (let x = 0; x < info.width; x++) {
      const offset = (y * info.width + x) * 4;
      if (data[offset + 3] <= 8) continue;
      if (
        monochrome &&
        data.subarray(offset, offset + 3).some((value) => value !== 255)
      )
        throw new Error(`${path}: 단색 알파 아이콘에 흰색 외 RGB가 있음`);
      if (
        x < padding ||
        y < padding ||
        x >= size - padding ||
        y >= size - padding
      )
        throw new Error(`${path}: 가장자리 ${padding}px 안전 여백을 벗어남`);
    }
  }
}

/** PNG 여러 장을 담은 ICO. 2007년 이후 브라우저·OS는 PNG 페이로드를 그대로 읽는다. */
function ico(pngs) {
  const header = Buffer.alloc(6 + pngs.length * 16);
  header.writeUInt16LE(0, 0);
  header.writeUInt16LE(1, 2);
  header.writeUInt16LE(pngs.length, 4);
  let offset = header.length;
  pngs.forEach(({ size, data }, i) => {
    const e = 6 + i * 16;
    header.writeUInt8(size >= 256 ? 0 : size, e);
    header.writeUInt8(size >= 256 ? 0 : size, e + 1);
    header.writeUInt16LE(1, e + 4); // 색 평면
    header.writeUInt16LE(32, e + 6); // 픽셀당 비트
    header.writeUInt32LE(data.length, e + 8);
    header.writeUInt32LE(offset, e + 12);
    offset += data.length;
  });
  return Buffer.concat([header, ...pngs.map((p) => p.data)]);
}

// ── 실행 ──
const mark = readMark();
checkConsumers();
output(
  MARK_PATH,
  `<!-- assembled-mark.json에서 생성함. 수정 후 pnpm brand:icons 실행. -->
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${mark.w} ${mark.h}" fill="none">
${markArt(mark)}
</svg>
`,
);

const sharp = loadSharp();

for (const v of variants(mark)) {
  let raster = sharp(Buffer.from(v.svg), {
    density: 72 * (v.size / Number(v.svg.match(/width="(\d+)"/)[1])),
  }).resize(v.size, v.size);
  if (v.flatten) raster = raster.flatten({ background: WHITE }).removeAlpha();
  const png = await raster.png().toBuffer();
  await checkRaster(sharp, png, v);
  let note = v.flatten ? " · 무알파" : "";
  if (v.safeZone) {
    const dp = await farthestDp(sharp, png);
    if (dp > 33)
      throw new Error(
        `${v.png}: 중심에서 ${dp.toFixed(1)}dp — 세이프존(반지름 33dp)을 벗어남`,
      );
    note = ` · 최원점 ${dp.toFixed(1)}dp/33dp`;
  }
  if (v.splashCircle) {
    const dp = await farthestDp(sharp, png, SPLASH_CANVAS);
    if (dp > SPLASH_RADIUS_DP)
      throw new Error(
        `${v.png}: 중심에서 ${dp.toFixed(1)}dp — Android 12 스플래시 원(반지름 ${SPLASH_RADIUS_DP}dp)을 벗어남`,
      );
    note = ` · 최원점 ${dp.toFixed(1)}dp/${SPLASH_RADIUS_DP}dp`;
  }
  if (v.padding) note = ` · 흰 단색·투명 배경·여백 ${v.padding}px`;
  output(v.out, v.svg);
  output(v.png, png);
  console.log(
    `  ${v.out.split("/").pop()} → ${v.png.split("/").pop()} (${v.size}px${note})`,
  );
}

// 웹: favicon(SVG + ICO)과 홈 화면용 apple-icon(무알파 180²)
const favicon = faviconSvg(mark);
output("apps/web/src/app/icon.svg", favicon);
const icoPngs = await Promise.all(
  [16, 32, 48].map(async (size) => ({
    size,
    data: await sharp(Buffer.from(favicon), { density: 72 * (size / 64) * 4 })
      .resize(size, size)
      .png()
      .toBuffer(),
  })),
);
output("apps/web/src/app/favicon.ico", ico(icoPngs));
const appleSvg = variants(mark)[0].svg;
// 카카오 디벨로퍼스 앱 아이콘(외부 콘솔에 손으로 올리는 파일). iOS 아이콘과 같은 그림을 128²·무알파로 줄인다.
// 카카오가 모서리를 따로 마스킹하므로 자체 라운딩·글자를 넣지 않는다(`.kiro/specs/brand-icon/design.md`).
for (const v of [
  { png: "apps/web/src/app/apple-icon.png", size: 180, flatten: true },
  { png: ".kiro/specs/brand-icon/kakao-app-128.png", size: 128, flatten: true },
]) {
  const png = await sharp(Buffer.from(appleSvg))
    .resize(v.size, v.size)
    .flatten({ background: WHITE })
    .removeAlpha()
    .png()
    .toBuffer();
  await checkRaster(sharp, png, v);
  output(v.png, png);
}
console.log(
  "  icon.svg · favicon.ico(16/32/48) · apple-icon.png(180px · 무알파)",
);
console.log("  kakao-app-128.png(128px · 무알파, 카카오 디벨로퍼스 업로드용)");
console.log(
  CHECK
    ? "웹·모바일 브랜드 아이콘 정합성 검사 완료(파일 쓰기 없음)."
    : "웹·모바일 브랜드 아이콘 생성 완료.",
);
