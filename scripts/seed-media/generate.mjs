// @ts-check
/**
 * GoLe 시드 미디어 — 오리지널 커버 아트 생성기.
 *
 * 공식 LEGO 제품 이미지를 사용/호스팅하지 않는다(ip-safe-content 정책). 대신 고래+브릭
 * 모티프의 GoLe 오리지널 SVG 커버를 세트/게시글마다 생성해 MinIO 시드에 사용한다.
 *
 * 출력: apps/api/src/main/resources/seed-media/{catalog,community}/*.svg
 * 실행: node scripts/seed-media/generate.mjs
 */
import { mkdirSync, writeFileSync, rmSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const OUT_ROOT = resolve(__dirname, "../../apps/api/src/main/resources/seed-media");

/** 테마별 배경 팔레트(브랜드 코발트 기반 + 테마 포인트). */
const THEME = {
  Icons: { from: "#1b2f66", to: "#2f56e6", chip: "#fbb500" },
  "Star Wars": { from: "#0b1020", to: "#1c2540", chip: "#5c83ff" },
  Technic: { from: "#7a1d1d", to: "#c83a23", chip: "#ffc62a" },
  "Harry Potter": { from: "#2a1d4d", to: "#5b3aa6", chip: "#ffd24d" },
  Ideas: { from: "#0f3d3a", to: "#137a6e", chip: "#ffd24d" },
};

function escapeXml(s) {
  return s.replace(/[<>&'"]/g, (c) =>
    ({ "<": "&lt;", ">": "&gt;", "&": "&amp;", "'": "&apos;", '"': "&quot;" })[c],
  );
}

/**
 * 고래 + 브릭 스터드 + 분수 마크(로고 0..40 좌표계)를 지정 위치/크기로 배치.
 * 커버는 흰 고래 + 테마색 스터드/분수로 렌더한다.
 */
function whaleMark(x, y, scale, { body, fin, stud, studTop, spout, eye, line } = {}) {
  body = body ?? "#1d4ed8";
  fin = fin ?? "#1a3fc0";
  stud = stud ?? "#3b5cf2";
  studTop = studTop ?? "#6082f7";
  spout = spout ?? "#eab308";
  eye = eye ?? "#1b2f66";
  line = line ?? "rgba(27,47,102,0.35)";
  return `<g transform="translate(${x} ${y}) scale(${scale})">
    <path d="M30 20.5C32.5 19 34.5 16.5 36 15C35.6 17.5 35 19.3 34.2 20.8C36 21 37.6 22.2 39 24C36.5 24.2 34 23.2 31.8 21.6C31.2 22 30.6 22.2 30 22Z" fill="${fin}"/>
    <path d="M5 21C5 15.5 9.5 13 16 13L26.5 13C30 13 32.5 15.5 32.5 19.5C32.5 24.5 28 28.5 19.5 28.5C10 28.5 5 26 5 21Z" fill="${body}"/>
    <path d="M14.5 27C16 30.5 19.5 31.5 22 30C20.3 28 17.5 27 14.5 27Z" fill="${fin}"/>
    <path d="M6.5 20.6H30" stroke="${line}" stroke-width="0.8" stroke-linecap="round"/>
    <path d="M5 22.4C6.6 23.9 8.7 23.9 10.2 22.8" stroke="${line}" stroke-width="0.9" stroke-linecap="round" fill="none"/>
    <g stroke="${spout}" stroke-width="1.3" stroke-linecap="round" fill="none">
      <path d="M9.6 12.4C9.2 9.4 8.6 7.4 7.6 5.8"/>
      <path d="M10.7 12.4C10.7 9.6 10.7 7.4 10.7 5.6"/>
      <path d="M11.7 12.6C12.3 9.8 13.1 7.8 14 6.4"/>
    </g>
    <circle cx="7.4" cy="5.4" r="0.7" fill="${spout}"/>
    <circle cx="10.7" cy="5.2" r="0.7" fill="${spout}"/>
    <circle cx="14.2" cy="6" r="0.7" fill="${spout}"/>
    <ellipse cx="13" cy="12.3" rx="2.1" ry="1" fill="${stud}"/>
    <ellipse cx="13" cy="11.6" rx="2.1" ry="1" fill="${studTop}"/>
    <ellipse cx="18.5" cy="12.3" rx="2.1" ry="1" fill="${stud}"/>
    <ellipse cx="18.5" cy="11.6" rx="2.1" ry="1" fill="${studTop}"/>
    <ellipse cx="24" cy="12.3" rx="2.1" ry="1" fill="${stud}"/>
    <ellipse cx="24" cy="11.6" rx="2.1" ry="1" fill="${studTop}"/>
    <circle cx="9.5" cy="19.8" r="1.45" fill="#ffffff"/>
    <circle cx="9.2" cy="19.6" r="0.6" fill="${eye}"/>
  </g>`;
}

/** 상단 브릭 스터드 띠(장식). */
function studRow(count, y, color, opacity) {
  const w = 1200 / count;
  let out = "";
  for (let i = 0; i < count; i += 1) {
    const cx = i * w + w / 2;
    out += `<circle cx="${cx}" cy="${y}" r="14" fill="${color}" opacity="${opacity}"/>`;
  }
  return out;
}

/** 카탈로그/커뮤니티 공통 커버(1200x900, 4:3). */
function cover({ setNumber, name, theme, chipLabel }) {
  const palette = THEME[theme] ?? THEME.Icons;
  const idText = setNumber ? `#${escapeXml(setNumber)}` : "GoLe";
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 900" width="1200" height="900" role="img" aria-label="${escapeXml(name)}">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="${palette.from}"/>
      <stop offset="1" stop-color="${palette.to}"/>
    </linearGradient>
    <radialGradient id="glow" cx="0.8" cy="0.2" r="0.9">
      <stop offset="0" stop-color="#ffffff" stop-opacity="0.18"/>
      <stop offset="1" stop-color="#ffffff" stop-opacity="0"/>
    </radialGradient>
  </defs>
  <rect width="1200" height="900" fill="url(#bg)"/>
  <rect width="1200" height="900" fill="url(#glow)"/>
  ${studRow(12, 56, "#ffffff", 0.08)}
  ${whaleMark(560, 470, 13, {
    body: "#ffffff",
    fin: "rgba(27,47,102,0.16)",
    stud: palette.chip,
    studTop: "#ffffff",
    spout: palette.chip,
    eye: palette.from,
    line: "rgba(27,47,102,0.25)",
  })}
  <g font-family="-apple-system, 'Apple SD Gothic Neo', 'Noto Sans KR', sans-serif">
    <rect x="64" y="120" rx="22" ry="22" width="${120 + chipLabel.length * 34}" height="60" fill="#ffffff" opacity="0.16"/>
    <text x="${64 + 28}" y="160" font-size="34" font-weight="700" fill="#ffffff">${escapeXml(chipLabel)}</text>
    <text x="64" y="780" font-size="64" font-weight="800" fill="#ffffff" letter-spacing="-1">${escapeXml(name)}</text>
    <text x="64" y="848" font-size="40" font-weight="700" fill="#ffffff" opacity="0.7" font-family="'SF Mono', ui-monospace, monospace">${idText}</text>
  </g>
</svg>
`;
}

/** 카탈로그 세트 — CatalogSeeder와 동일 목록. */
const CATALOG = [
  ["10307", "에펠탑", "Icons"],
  ["75192", "밀레니엄 팰컨 UCS", "Star Wars"],
  ["10294", "타이타닉", "Icons"],
  ["42143", "페라리 데이토나 SP3", "Technic"],
  ["71043", "호그와트 성", "Harry Potter"],
  ["10300", "백 투 더 퓨처 타임머신", "Icons"],
  ["21330", "나 홀로 집에", "Ideas"],
  ["75313", "AT-AT UCS", "Star Wars"],
  ["10276", "콜로세움", "Icons"],
  ["21318", "트리하우스", "Ideas"],
  ["92176", "NASA 아폴로 새턴 V", "Ideas"],
  ["10497", "갤럭시 익스플로러", "Icons"],
];

/** 커뮤니티 데모 게시글 — CommunitySeeder의 placehold.co label과 대응(slug, 표시명, 테마). */
const COMMUNITY = [
  ["eiffel", "에펠타워 자랑", "Icons"],
  ["falcon", "밀레니엄 팰컨 자랑", "Star Wars"],
  ["moc-lighthouse", "커스텀 등대 MOC", "Ideas"],
  ["titanic", "타이타닉 진열", "Icons"],
  ["moc-ferriswheel", "미니 관람차 MOC", "Technic"],
];

function clean(dir) {
  rmSync(dir, { recursive: true, force: true });
  mkdirSync(dir, { recursive: true });
}

const catalogDir = resolve(OUT_ROOT, "catalog");
const communityDir = resolve(OUT_ROOT, "community");
clean(catalogDir);
clean(communityDir);

for (const [setNumber, name, theme] of CATALOG) {
  const svg = cover({ setNumber, name, theme, chipLabel: theme });
  writeFileSync(resolve(catalogDir, `${setNumber}.svg`), svg, "utf8");
}

for (const [slug, name, theme] of COMMUNITY) {
  const svg = cover({ setNumber: "", name, theme, chipLabel: "커뮤니티" });
  writeFileSync(resolve(communityDir, `${slug}.svg`), svg, "utf8");
}

console.log(
  `[seed-media] catalog ${CATALOG.length}개, community ${COMMUNITY.length}개 SVG 생성 → ${OUT_ROOT}`,
);
