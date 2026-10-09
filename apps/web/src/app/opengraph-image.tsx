import { ImageResponse } from "next/og";

/**
 * 전역 OG/트위터 카드 이미지(런타임 PNG 생성). Next가 og:image·twitter:image 메타를
 * 자동 연결한다. satori 기본 폰트가 한글을 포함하지 않으므로 텍스트는 영문으로 구성한다.
 */
export const alt = "GoLe — Brick Marketplace";
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

/*
 * 고래 마크 경로는 `shared/ui/logo/mark.svg`에서 그대로 복사했다. OG 라우트는 런타임에 파일을
 * 읽지 않도록 문자열로 들고 있고, `scripts/build-brand-icons.mjs`가 정본과 같은지 검사한다.
 */
const MARK_BODY_D =
  "M48 11L158 11C167.9 11 176 19.1 176 29L176 77C176 98 159 115 138 115L40 115C17.9 115 0 97.1 0 75L0 59C0 32.5 21.5 11 48 11ZM61 0L79 0C81.2 0 83 1.8 83 4L83 17L57 17L57 4C57 1.8 58.8 0 61 0ZM133 0L151 0C153.2 0 155 1.8 155 4L155 17L129 17L129 4C129 1.8 130.8 0 133 0ZM179 52L193 52C197.4 52 201 55.6 201 60L201 78C201 82.4 197.4 86 193 86L179 86C174.6 86 171 82.4 171 78L171 60C171 55.6 174.6 52 179 52ZM192.9 44.1L214.1 22.9C218.8 18.2 226.4 18.2 231.1 22.9C235.8 27.6 235.8 35.2 231.1 39.9L209.9 61.1C205.2 65.8 197.6 65.8 192.9 61.1C188.2 56.4 188.2 48.8 192.9 44.1ZM208.6 76.8L226.7 85.3C232.2 87.8 234.6 94.4 232 99.9C229.5 105.4 222.9 107.8 217.4 105.2L199.3 96.7C193.8 94.2 191.4 87.6 194 82.1C196.5 76.6 203.1 74.2 208.6 76.8ZM86.5 105.7L108.3 115.8C112.3 117.7 114 122.4 112.1 126.5C110.3 130.5 105.5 132.2 101.5 130.3L79.7 120.2C75.7 118.3 74 113.6 75.9 109.5C77.7 105.5 82.5 103.8 86.5 105.7ZM192 62C188.1 62 185 65.1 185 69C185 72.9 188.1 76 192 76C195.9 76 199 72.9 199 69C199 65.1 195.9 62 192 62ZM192 64.6C194.4 64.6 196.4 66.6 196.4 69C196.4 71.4 194.4 73.4 192 73.4C189.6 73.4 187.6 71.4 187.6 69C187.6 66.6 189.6 64.6 192 64.6Z";
const MARK_TOP_D =
  "M4.4 39C12.2 21.9 29.2 11 48 11L158 11C167.9 11 176 19.1 176 29L176 39L4.4 39ZM61 0L79 0C81.2 0 83 1.8 83 4L83 17L57 17L57 4C57 1.8 58.8 0 61 0ZM133 0L151 0C153.2 0 155 1.8 155 4L155 17L129 17L129 4C129 1.8 130.8 0 133 0Z";
const MARK_GOLD_D = "M97 0L115 0C117.2 0 119 1.8 119 4L119 17L93 17L93 4C93 1.8 94.8 0 97 0Z";
const MARK_FACE_D =
  "M50 55C45 55 41 59.5 41 65C41 70.5 45 75 50 75C55 75 59 70.5 59 65C59 59.5 55 55 50 55ZM41.9 84.2C42.7 83.2 42.6 81.8 41.6 81C40.7 80.2 39.3 80.3 38.5 81.3C36.6 83.5 33.9 84.8 31 84.8C28.1 84.8 25.4 83.5 23.5 81.3C22.7 80.3 21.3 80.2 20.4 81C19.4 81.8 19.3 83.2 20.1 84.2C22.8 87.4 26.8 89.2 31 89.2C35.2 89.2 39.2 87.4 41.9 84.2Z";
const MARK_GLINT_D =
  "M53 58C54.7 58 56 59.3 56 61C56 62.7 54.7 64 53 64C51.3 64 50 62.7 50 61C50 59.3 51.3 58 53 58Z";

export default function OpengraphImage() {
  return new ImageResponse(
    <div
      style={{
        display: "flex",
        width: "100%",
        height: "100%",
        padding: "80px",
        alignItems: "stretch",
        justifyContent: "space-between",
        // 브랜드 규칙: 단색 면. 그라데이션 배경은 쓰지 않는다.
        background: "#1d4ed8",
        color: "#ffffff",
        fontFamily: "sans-serif",
      }}
    >
      <div style={{ display: "flex", flexDirection: "column", justifyContent: "space-between" }}>
        <div style={{ display: "flex", fontSize: "46px", fontWeight: 800, letterSpacing: "-1px" }}>
          Go<span style={{ color: "#facc15" }}>Le</span>
        </div>

        <div style={{ display: "flex", flexDirection: "column", gap: "18px" }}>
          <div
            style={{
              display: "flex",
              flexDirection: "column",
              fontSize: "92px",
              fontWeight: 800,
              letterSpacing: "-4px",
              lineHeight: 1.02,
            }}
          >
            <span>Brick</span>
            <span>Marketplace</span>
          </div>
          <div style={{ fontSize: "32px", opacity: 0.85 }}>
            Prices · Direct Chat · Collection · Community
          </div>
        </div>

        <div style={{ fontSize: "28px", opacity: 0.7 }}>gole.co.kr</div>
      </div>

      {/* 흰 옆모습 브릭 고래 + 골드 스터드 한 점 — 앱 아이콘과 같은 조합. 힌지 핀 고리로 파란 면이 비친다. */}
      <div style={{ display: "flex", alignItems: "center", paddingBottom: "24px" }}>
        <svg width="420" height="235" viewBox="0 0 235.8 132.2" fill="none">
          <path d={MARK_GOLD_D} fill="#facc15" />
          <path d={MARK_BODY_D} fill="#ffffff" />
          <path d={MARK_TOP_D} fill="#ffffff" />
          <path d={MARK_FACE_D} fill="#131e4f" />
          <path d={MARK_GLINT_D} fill="#ffffff" />
        </svg>
      </div>
    </div>,
    size,
  );
}
