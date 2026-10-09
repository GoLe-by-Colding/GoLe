#!/usr/bin/env node
/**
 * 브랜드 아이콘 생성기.
 *
 * 정본 마크는 `apps/web/src/shared/ui/logo/mark.svg` 하나다. 이 스크립트는 거기서 색과 배치만
 * 바꾼 플랫폼 변형(웹 favicon·apple-icon, 모바일 아이콘·스플래시)을 찍어내고, Logo 컴포넌트와
 * OG 이미지에 복사된 경로가 정본과 같은지 검사한다. Next에 설치된 sharp로 PNG를 만든다.
 *
 * 사용: node scripts/build-brand-icons.mjs          # 생성 + 검사
 *       node scripts/build-brand-icons.mjs --check  # 생성 없이 경로 동기화만 검사
 */
import { createRequire } from "node:module";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const MARK_PATH = "apps/web/src/shared/ui/logo/mark.svg";
/** 정본 경로를 그대로 복사해 쓰는 파일. 마크를 고치면 여기도 같이 고쳐야 한다. */
const COPIES = ["apps/web/src/shared/ui/logo/logo.tsx", "apps/web/src/app/opengraph-image.tsx"];

/** 브랜드 단색. 그라데이션은 `brand-identity.md`에서 금지한다. */
const BRAND = "#1D4ED8"; // brand-600
const WHITE = "#FFFFFF";
/** 파란 면 위 골드는 한 단계 밝은 accent-400이 흰 스터드 사이에서 같은 무게로 읽힌다. */
const GOLD_ON_BRAND = "#FACC15";

// ── 정본 읽기 ──
function readMark() {
  const svg = readFileSync(join(ROOT, MARK_PATH), "utf8");
  const vb = svg.match(/viewBox="0 0 ([\d.]+) ([\d.]+)"/);
  const paths = Object.fromEntries(
    [...svg.matchAll(/<path id="gole-(gold|body)" d="([^"]+)"/g)].map((m) => [m[1], m[2]]),
  );
  if (!vb || !paths.gold || !paths.body)
    throw new Error(`${MARK_PATH}: viewBox·gole-gold·gole-body를 찾지 못함`);
  return { w: Number(vb[1]), h: Number(vb[2]), ...paths };
}

function checkCopies(mark) {
  const stale = COPIES.filter((file) => {
    const src = readFileSync(join(ROOT, file), "utf8");
    return !src.includes(mark.body) || !src.includes(mark.gold);
  });
  if (stale.length > 0) {
    throw new Error(
      `정본(${MARK_PATH})과 경로가 다른 파일: ${stale.join(", ")} — 경로 문자열을 다시 복사해야 함`,
    );
  }
  console.log(`  경로 동기화 확인: ${COPIES.length}개 파일이 정본과 같음`);
}

// ── 배치 ──
/**
 * 마크를 캔버스 가운데에 목표 너비로 앉힌다. 몸통이 오른쪽 아래에 무겁고 꼬리는 왼쪽 위로 가벼우므로
 * 경계 상자 중심에 두면 눈에는 오른쪽 아래로 처져 보인다. 그래서 마크 너비 기준 2%만 왼쪽 위로 당긴다.
 */
function placed(mark, { canvas, width, body, gold }) {
  const s = width / mark.w;
  const tx = canvas / 2 - (mark.w / 2) * s - width * 0.02;
  const ty = canvas / 2 - (mark.h / 2) * s - width * 0.02;
  return `  <g transform="translate(${tx.toFixed(3)} ${ty.toFixed(3)}) scale(${s.toFixed(5)})">
    <path d="${mark.gold}" fill="${gold}"/>
    <path d="${mark.body}" fill="${body}"/>
  </g>`;
}

function svgDoc(canvas, inner) {
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${canvas} ${canvas}" width="${canvas}" height="${canvas}" fill="none">
${inner}
</svg>
`;
}
const solid = (canvas, fill) => `  <rect width="${canvas}" height="${canvas}" fill="${fill}"/>`;

/**
 * Android 어댑티브는 108dp 캔버스 중 지름 66dp 원만 어떤 마스크에서도 남는다.
 * 마크 폭 56dp에서 가장 먼 점(꼬리 끝)이 중심에서 약 32dp다 — 생성 후 PNG를 실측해 33dp를 넘으면 멈춘다.
 */
const FG_WIDTH = 56;

function variants(mark) {
  const color = { body: WHITE, gold: GOLD_ON_BRAND };
  const mono = { body: WHITE, gold: WHITE };
  return [
    // iOS: 1024² 무알파, 자체 라운딩 없음 — 모서리는 OS가 마스크한다.
    {
      out: "apps/mobile/assets/brand/icon.svg",
      png: "apps/mobile/assets/images/icon.png",
      size: 1024,
      flatten: true,
      svg: svgDoc(
        1024,
        `${solid(1024, BRAND)}\n${placed(mark, { canvas: 1024, width: 680, ...color })}`,
      ),
    },
    {
      out: "apps/mobile/assets/brand/android-icon-foreground.svg",
      png: "apps/mobile/assets/images/android-icon-foreground.png",
      size: 512,
      safeZone: true,
      svg: svgDoc(108, placed(mark, { canvas: 108, width: FG_WIDTH, ...color })),
    },
    {
      out: "apps/mobile/assets/brand/android-icon-background.svg",
      png: "apps/mobile/assets/images/android-icon-background.png",
      size: 512,
      svg: svgDoc(108, solid(108, BRAND)),
    },
    // 런처가 알파로 테마 색을 입힌다. 골드 스터드도 흰 실루엣에 합쳐지고 눈은 구멍으로 남는다.
    {
      out: "apps/mobile/assets/brand/android-icon-monochrome.svg",
      png: "apps/mobile/assets/images/android-icon-monochrome.png",
      size: 432,
      safeZone: true,
      svg: svgDoc(108, placed(mark, { canvas: 108, width: FG_WIDTH, ...mono })),
    },
    // 배경색은 app.json(expo-splash-screen)이 brand-600으로 따로 준다. 그래서 흰 고래다.
    {
      out: "apps/mobile/assets/brand/splash-icon.svg",
      png: "apps/mobile/assets/images/splash-icon.png",
      size: 512,
      svg: svgDoc(256, placed(mark, { canvas: 256, width: 208, ...color })),
    },
  ];
}

/** 브라우저 탭용 favicon. 탭 바 색에 묻히지 않게 둥근 브랜드 타일 위에 흰 고래를 올린다. */
function faviconSvg(mark) {
  return svgDoc(
    64,
    `  <rect width="64" height="64" rx="14" fill="${BRAND}"/>\n${placed(mark, { canvas: 64, width: 54, body: WHITE, gold: GOLD_ON_BRAND })}`,
  );
}

// ── PNG ──
function loadSharp() {
  const webRequire = createRequire(join(ROOT, "apps/web/package.json"));
  return createRequire(webRequire.resolve("next/package.json"))("sharp");
}

/** 불투명 픽셀 중 캔버스 중심에서 가장 먼 거리(dp, 108dp 기준). */
async function farthestDp(sharp, pngPath) {
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
  return (max / info.width) * 108;
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
checkCopies(mark);
if (process.argv.includes("--check")) process.exit(0);

const sharp = loadSharp();
for (const dir of ["apps/mobile/assets/brand", "apps/mobile/assets/images", "apps/web/src/app"]) {
  mkdirSync(join(ROOT, dir), { recursive: true });
}

for (const v of variants(mark)) {
  writeFileSync(join(ROOT, v.out), v.svg, "utf8");
  let raster = sharp(Buffer.from(v.svg), {
    density: 72 * (v.size / Number(v.svg.match(/width="(\d+)"/)[1])),
  }).resize(v.size, v.size);
  if (v.flatten) raster = raster.flatten({ background: BRAND }).removeAlpha();
  await raster.png().toFile(join(ROOT, v.png));
  let note = "";
  if (v.flatten) {
    const meta = await sharp(join(ROOT, v.png)).metadata();
    if (meta.hasAlpha) throw new Error(`${v.png}: 알파 채널이 남아 있음`);
    note = " · 무알파";
  }
  if (v.safeZone) {
    const dp = await farthestDp(sharp, join(ROOT, v.png));
    if (dp > 33)
      throw new Error(`${v.png}: 중심에서 ${dp.toFixed(1)}dp — 세이프존(반지름 33dp)을 벗어남`);
    note = ` · 최원점 ${dp.toFixed(1)}dp/33dp`;
  }
  console.log(`  ${v.out.split("/").pop()} → ${v.png.split("/").pop()} (${v.size}px${note})`);
}

// 웹: favicon(SVG + ICO)과 홈 화면용 apple-icon(무알파 180²)
const favicon = faviconSvg(mark);
writeFileSync(join(ROOT, "apps/web/src/app/icon.svg"), favicon, "utf8");
const icoPngs = await Promise.all(
  [16, 32, 48].map(async (size) => ({
    size,
    data: await sharp(Buffer.from(favicon), { density: 72 * (size / 64) * 4 })
      .resize(size, size)
      .png()
      .toBuffer(),
  })),
);
writeFileSync(join(ROOT, "apps/web/src/app/favicon.ico"), ico(icoPngs));
const appleSvg = variants(mark)[0].svg;
await sharp(Buffer.from(appleSvg))
  .resize(180, 180)
  .flatten({ background: BRAND })
  .removeAlpha()
  .png()
  .toFile(join(ROOT, "apps/web/src/app/apple-icon.png"));
console.log("  icon.svg · favicon.ico(16/32/48) · apple-icon.png(180px · 무알파)");
console.log("웹·모바일 브랜드 아이콘 생성 완료.");
