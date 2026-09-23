#!/usr/bin/env node
/**
 * 모바일 브랜드 아이콘 생성기.
 *
 * 정본 마크는 웹 Logo 컴포넌트다. Next에 설치된 sharp로 PNG를 생성한다.
 * 사용: node scripts/build-brand-icons.mjs
 */
import { createRequire } from "node:module";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const webRequire = createRequire(join(ROOT, "apps/web/package.json"));
const sharp = createRequire(webRequire.resolve("next/package.json"))("sharp");
const SRC_DIR = join(ROOT, "apps/mobile/assets/brand");
const OUT_DIR = join(ROOT, "apps/mobile/assets/images");

/** 브랜드 단색. 그라데이션은 `brand-identity.md`에서 금지한다. */
const BRAND = "#EFF3FF";

/** 마크의 원본 좌표계 경계 — 배치 계산의 기준이다. */
const BOX = { x: 8, y: -5, w: 260, h: 151 };
const CENTER = { x: BOX.x + BOX.w / 2, y: BOX.y + BOX.h / 2 };

/** 웹의 현재 고래 도형을 정본으로 사용한다. 분수는 히어로 전용이므로 제외한다. */
function currentMark(mono) {
  const source = readFileSync(join(ROOT, "apps/web/src/shared/ui/logo/logo.tsx"), "utf8");
  const start = source.indexOf('<polygon points="204,74');
  const end = source.indexOf("</svg>", start);
  if (start < 0 || end < 0) throw new Error("Logo 정적 도형 경계를 확인해야 함");
  const mark = source.slice(start, end)
    .replace(/\{\/\*[\s\S]*?\*\/\}/g, "")
    .replaceAll("strokeWidth", "stroke-width")
    .replaceAll("strokeLinecap", "stroke-linecap")
    .replace(/[ \t]+$/gm, "");
  if (/[{}]/.test(mark)) throw new Error("동적 JSX 도형은 직접 변환할 수 없음");
  return mono ? mark.replace(/(fill|stroke)="#[0-9a-f]+"/gi, '$1="#ffffff"') : mark;
}

/** 캔버스 한가운데에 목표 너비로 마크를 앉힌다. */
function placedMark({ canvas, targetWidth, mono = false }) {
  const scale = targetWidth / BOX.w;
  const tx = canvas / 2 - CENTER.x * scale;
  const ty = canvas / 2 - CENTER.y * scale;
  const body = currentMark(mono);
  return `  <g transform="translate(${tx.toFixed(3)} ${ty.toFixed(3)}) scale(${scale.toFixed(4)})">${body}
  </g>`;
}

function svg({ canvas, background, targetWidth, mono = false }) {
  const bg = background === null ? "" : `  <rect width="${canvas}" height="${canvas}" fill="${background}"/>\n`;
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${canvas} ${canvas}" width="${canvas}" height="${canvas}" fill="none">
${bg}${placedMark({ canvas, targetWidth, mono })}
</svg>
`;
}

/**
 * Android 어댑티브는 108dp 캔버스 중 가운데 72dp만 보장된다. 마크를 60dp로 잡아
 * 원형 마스크에서도 잘리지 않게 한다.
 */
const VARIANTS = [
  { name: "icon", canvas: 1024, background: BRAND, targetWidth: 635, png: 1024, flatten: true },
  { name: "android-icon-foreground", canvas: 108, background: null, targetWidth: 60, png: 512 },
  { name: "android-icon-background", canvas: 108, background: BRAND, targetWidth: 0, png: 512 },
  { name: "android-icon-monochrome", canvas: 108, background: null, targetWidth: 60, png: 432, mono: true },
  { name: "splash-icon", canvas: 256, background: null, targetWidth: 150, png: 512 },
];

mkdirSync(SRC_DIR, { recursive: true });
mkdirSync(OUT_DIR, { recursive: true });

for (const v of VARIANTS) {
  const body =
    v.targetWidth === 0
      ? `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${v.canvas} ${v.canvas}" width="${v.canvas}" height="${v.canvas}"><rect width="${v.canvas}" height="${v.canvas}" fill="${v.background}"/></svg>\n`
      : svg(v);
  const svgPath = join(SRC_DIR, `${v.name}.svg`);
  const pngPath = join(OUT_DIR, `${v.name}.png`);
  writeFileSync(svgPath, body, "utf8");

  let raster = sharp(Buffer.from(body)).resize(v.png, v.png);
  if (v.flatten) raster = raster.flatten({ background: BRAND }).removeAlpha();
  await raster.png().toFile(pngPath);
  console.log(`  ${v.name}.svg → ${v.name}.png (${v.png}px)`);
}
writeFileSync(join(ROOT, "apps/web/src/app/icon.svg"), svg({ canvas: 64, background: BRAND, targetWidth: 52 }), "utf8");
console.log("웹 favicon까지 생성 완료.");
