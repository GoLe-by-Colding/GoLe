#!/usr/bin/env node
/**
 * 모바일 브랜드 아이콘 생성기.
 *
 * 정본 마크는 `apps/web/src/app/icon.svg`(고래 + 골드 브릭 스터드)다. 그 도형을 여기 한 번만
 * 두고 플랫폼별 변형을 찍어낸다 — 규격이 서로 달라(iOS 정방형·무알파 / Android 세이프존 /
 * 모노크롬 실루엣) 파일을 손으로 관리하면 반드시 어긋난다.
 *
 * 사용: node scripts/build-brand-icons.mjs        (ImageMagick `magick` 필요)
 */
import { execFileSync } from "node:child_process";
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const SRC_DIR = join(ROOT, "apps/mobile/assets/brand");
const OUT_DIR = join(ROOT, "apps/mobile/assets/images");

/** 브랜드 단색. 그라데이션은 `brand-identity.md`에서 금지한다. */
const BRAND = "#1d4ed8";
const GOLD = "#facc15";

/** 마크의 원본 좌표계 경계 — 배치 계산의 기준이다. */
const BOX = { x: 3.7, y: 10.5, w: 33.3, h: 18.3 };
const CENTER = { x: BOX.x + BOX.w / 2, y: BOX.y + BOX.h / 2 };

/** 얼굴(눈·입)은 흰 몸통을 파내는 브랜드 색이다. 실루엣 변형에서는 뺀다. */
function markBody(mono) {
  const skin = mono ? "#ffffff" : "#ffffff";
  const stud = mono ? "#ffffff" : GOLD;
  return `
    <rect x="9.5" y="10.5" width="5" height="3.2" rx="1.3" fill="${skin}"/>
    <rect x="15.55" y="10.5" width="5" height="3.2" rx="1.3" fill="${stud}"/>
    <rect x="21.6" y="10.5" width="5" height="3.2" rx="1.3" fill="${skin}"/>
    <path d="M9 13 L26.3 13 C28.4 13 29.4 14.9 29.8 17.4 C30.8 16 32.6 14.8 35 14.4 C36.4 14.2 37.2 15.6 36.4 16.8 C35.4 18.3 34.2 19.4 32.8 20.1 C34.3 20.9 35.6 22.1 36.6 23.7 C37.4 24.9 36.5 26.3 35.1 26 C32.7 25.5 30.8 24.2 29.7 22.4 C28.9 25.6 26.4 28.8 21.8 28.8 L13 28.8 C7.5 28.8 3.7 25.2 3.7 20.6 C3.7 16.5 5.7 13.4 9 13 Z" fill="${skin}"/>`;
}

function markFace() {
  return `
    <circle cx="9.6" cy="19.6" r="2.1" fill="${BRAND}"/>
    <circle cx="10.4" cy="18.9" r="0.62" fill="#ffffff" opacity="0.9"/>
    <path d="M6 22.6 Q8.3 24.6 11 23.2" stroke="${BRAND}" stroke-width="1.1" stroke-linecap="round" fill="none" opacity="0.5"/>`;
}

/** 캔버스 한가운데에 목표 너비로 마크를 앉힌다. */
function placedMark({ canvas, targetWidth, mono = false }) {
  const scale = targetWidth / BOX.w;
  const tx = canvas / 2 - CENTER.x * scale;
  const ty = canvas / 2 - CENTER.y * scale;
  const body = markBody(mono) + (mono ? "" : markFace());
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

  // 밀도는 캔버스 대비로 잡는다 — 고정값을 쓰면 캔버스가 큰 변형에서 수만 픽셀을 렌더링한다.
  const density = Math.round((72 * v.png * 2) / v.canvas);
  const args = ["-background", "none", "-density", String(density), svgPath, "-resize", `${v.png}x${v.png}`];
  // iOS 아이콘은 알파가 있으면 App Store Connect가 업로드를 거부한다.
  if (v.flatten) args.push("-background", BRAND, "-alpha", "remove", "-alpha", "off");
  args.push(pngPath);
  execFileSync("magick", args, { stdio: "inherit" });
  console.log(`  ${v.name}.svg → ${v.name}.png (${v.png}px)`);
}
console.log("완료.");
