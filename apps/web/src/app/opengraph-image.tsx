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
  "M111.6 20C165.6 20 209.6 56 209.6 108C209.6 134 195.6 170 163.6 170C133.6 173 89.6 173 59.6 170C27.6 170 13.6 134 13.6 108C13.6 56 57.6 20 111.6 20ZM64.3 39.1C67.3 47.4 65.6 55.6 60.4 57.5C55.2 59.4 48.5 54.2 45.5 45.9C42.5 37.6 44.2 29.3 49.4 27.4C54.6 25.6 61.3 30.8 64.3 39.1ZM54.6 28.2C55.5 34.3 48.6 40.2 39.3 41.5C30 42.8 21.8 39 21 33C20.1 27 27 21 36.3 19.7C45.6 18.4 53.8 22.2 54.6 28.2ZM74.8 13.4C78 18.5 74.2 26.8 66.2 31.7C58.2 36.7 49.2 36.6 45.9 31.4C42.7 26.3 46.6 18.1 54.5 13.1C62.5 8.1 71.6 8.2 74.8 13.4ZM30.4 142.5C27.5 147.5 18.5 147.8 10.4 143.1C2.3 138.4 -2 130.5 0.9 125.5C3.8 120.5 12.8 120.2 20.9 124.9C29 129.6 33.3 137.5 30.4 142.5ZM222.4 125.5C225.3 130.5 221 138.4 212.9 143.1C204.8 147.8 195.8 147.5 192.9 142.5C190 137.5 194.3 129.6 202.4 124.9C210.5 120.2 219.5 120.5 222.4 125.5ZM84.1 108C84.1 101.1 79.4 95.5 73.6 95.5C67.8 95.5 63.1 101.1 63.1 108C63.1 114.9 67.8 120.5 73.6 120.5C79.4 120.5 84.1 114.9 84.1 108ZM160.1 108C160.1 101.1 155.4 95.5 149.6 95.5C143.8 95.5 139.1 101.1 139.1 108C139.1 114.9 143.8 120.5 149.6 120.5C155.4 120.5 160.1 114.9 160.1 108ZM127.5 129.3C129.1 128 129.4 125.7 128.1 124C126.9 122.4 124.5 122.1 122.9 123.4C116.3 128.5 107 128.5 100.4 123.4C98.8 122.1 96.4 122.4 95.1 124C93.9 125.7 94.1 128 95.8 129.3C105.1 136.6 118.2 136.6 127.5 129.3Z";
const MARK_GOLD_D =
  "M94.6 0L128.6 0C133.1 0 136.6 3.6 136.6 8L136.6 22C136.6 26.4 133.1 30 128.6 30L94.6 30C90.2 30 86.6 26.4 86.6 22L86.6 8C86.6 3.6 90.2 0 94.6 0Z";

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
        <svg width="420" height="324" viewBox="0 0 223.3 172.3" fill="none">
          <path d={MARK_GOLD_D} fill="#facc15" />
          <path d={MARK_BODY_D} fill="#ffffff" />
        </svg>
      </div>
    </div>,
    size,
  );
}
