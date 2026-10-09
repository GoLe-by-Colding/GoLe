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
  "M80.2 57.2C86.7 73.5 89 88.3 116.5 88.3L214 88.3C244.4 88.3 269 112.9 269 143.3C269 173.6 244.4 198.3 214 198.3L159 198.3C109 198.3 71.5 180.8 62.8 145.8C59 133.3 59.4 92.6 50 69.4C44.9 56.7 16.2 46.7 5.8 46.5C3.2 46.4 1 44.7 0.4 42.3C-0.3 39.9 0.7 37.3 2.8 35.9C17.4 26.5 35.8 23.8 55.7 40.1C58.7 14.6 73.8 3.7 90.9 0.3C93.3 -0.2 95.9 1 97.1 3.2C98.2 5.5 97.9 8.2 96.1 10C88.7 17.4 75 44.6 80.2 57.2ZM119 90.8V78.5A6.5 6.5 0 0 1 125.5 72H137.5A6.5 6.5 0 0 1 144 78.5V90.8ZM189 90.8V78.5A6.5 6.5 0 0 1 195.5 72H207.5A6.5 6.5 0 0 1 214 78.5V90.8ZM216.5 135.8A12.5 12.5 0 1 0 241.5 135.8A12.5 12.5 0 1 0 216.5 135.8Z";
const MARK_GOLD_D = "M154 90.8V78.5A6.5 6.5 0 0 1 160.5 72H172.5A6.5 6.5 0 0 1 179 78.5V90.8Z";

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

      {/* 흰 고래 + 골드 스터드 한 점 — 앱 아이콘과 같은 조합. */}
      <div style={{ display: "flex", alignItems: "center", paddingBottom: "40px" }}>
        <svg width="380" height="280" viewBox="0 0 269 198.3" fill="none">
          <path d={MARK_GOLD_D} fill="#facc15" />
          <path d={MARK_BODY_D} fill="#ffffff" />
        </svg>
      </div>
    </div>,
    size,
  );
}
