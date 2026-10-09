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
  "M48.7 66.2C45.2 53.3 17.2 45.4 6.6 42.5C3 41.6 0.4 38.4 0 34.7C-0.3 31 1.7 27.5 5.1 25.9C21.7 18.2 41.1 17.4 59.1 34.1C66.2 10.7 83.4 1.6 101.6 0C105.4 -0.3 108.9 1.8 110.5 5.1C112 8.5 111.3 12.5 108.7 15.2C100.9 22.9 80.6 43.8 84.1 56.8L102 123.8L66.6 133.3ZM38 189.5C38 125.7 82.5 89.6 138 89.6C193.5 89.6 237.9 125.7 237.9 189.5C237.9 220.1 218.5 239.5 187.9 239.5L88 239.5C57.5 239.5 38 220.1 38 189.5ZM51.9 215.9C45 227 33.9 238.1 20 242.3C11.7 245 4.7 240.9 6.1 232.5C8.9 218.7 24.2 204.8 43.6 200.6ZM232.3 200.6C251.8 204.8 267 218.7 269.8 232.5C271.2 240.9 264.3 245 255.9 242.3C242.1 238.1 231 227 224 215.9ZM82.5 159C82.5 168.2 89.9 175.6 99.1 175.6C108.3 175.6 115.8 168.2 115.8 159C115.8 149.8 108.3 142.3 99.1 142.3C89.9 142.3 82.5 149.8 82.5 159ZM160.2 159C160.2 168.2 167.6 175.6 176.8 175.6C186 175.6 193.5 168.2 193.5 159C193.5 149.8 186 142.3 176.8 142.3C167.6 142.3 160.2 149.8 160.2 159ZM77.5 205.2C93.4 221.5 115.2 230.6 138 230.6C160.7 230.6 182.5 221.5 198.4 205.2C200.4 203.3 200.3 200.1 198.4 198.2C196.4 196.2 193.2 196.3 191.3 198.2C177.3 212.5 158 220.6 138 220.6C117.9 220.6 98.7 212.5 84.6 198.2C83.4 197 81.6 196.5 79.8 196.9C78.1 197.3 76.7 198.7 76.3 200.4C75.8 202.1 76.3 204 77.5 205.2Z";
const MARK_GOLD_D =
  "M116.3 68.5L159.6 68.5C164.5 68.5 168.5 72.5 168.5 77.4L168.5 97.4C168.5 102.3 164.5 106.3 159.6 106.3L116.3 106.3C111.4 106.3 107.4 102.3 107.4 97.4L107.4 77.4C107.4 72.5 111.4 68.5 116.3 68.5Z";

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

      {/* 흰 고래 + 골드 스터드 한 점 — 앱 아이콘과 같은 조합. 눈·입 구멍으로 파란 면이 비친다. */}
      <div style={{ display: "flex", alignItems: "center", paddingBottom: "24px" }}>
        <svg width="360" height="324" viewBox="0 0 270 243.1" fill="none">
          <path d={MARK_GOLD_D} fill="#facc15" />
          <path d={MARK_BODY_D} fill="#ffffff" />
        </svg>
      </div>
    </div>,
    size,
  );
}
