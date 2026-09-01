import { ImageResponse } from "next/og";

/**
 * 전역 OG/트위터 카드 이미지(런타임 PNG 생성). Next가 og:image·twitter:image 메타를
 * 자동 연결한다. satori 기본 폰트가 한글을 포함하지 않으므로 텍스트는 영문으로 구성한다.
 */
export const alt = "GoLe — Brick Marketplace";
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

export default function OpengraphImage() {
  return new ImageResponse(
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        width: "100%",
        height: "100%",
        padding: "80px",
        justifyContent: "space-between",
        background: "linear-gradient(135deg, #1d4ed8 0%, #1c2f7c 100%)",
        color: "#ffffff",
        fontFamily: "sans-serif",
      }}
    >
      <div style={{ display: "flex", alignItems: "center", gap: "20px" }}>
        <div style={{ display: "flex", gap: "10px" }}>
          <div
            style={{ width: "26px", height: "26px", borderRadius: "9999px", background: "#facc15" }}
          />
          <div
            style={{
              width: "26px",
              height: "26px",
              borderRadius: "9999px",
              background: "rgba(255,255,255,0.55)",
            }}
          />
        </div>
        <div style={{ fontSize: "46px", fontWeight: 800, letterSpacing: "-1px" }}>GoLe</div>
      </div>

      <div style={{ display: "flex", flexDirection: "column", gap: "18px" }}>
        <div style={{ fontSize: "92px", fontWeight: 800, letterSpacing: "-4px", lineHeight: 1.02 }}>
          Brick Marketplace
        </div>
        <div style={{ fontSize: "36px", opacity: 0.85 }}>
          Prices · Escrow · Collection · Community
        </div>
      </div>

      <div style={{ fontSize: "28px", opacity: 0.7 }}>gole.co.kr</div>
    </div>,
    size,
  );
}
