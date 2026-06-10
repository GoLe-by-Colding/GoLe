import { ImageResponse } from "next/og";
import { env } from "@shared/config";

export const alt = "GoLe 상품";
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

export default async function OpengraphImage({
  params,
}: {
  readonly params: Promise<{ readonly id: string }>;
}) {
  const { id } = await params;

  // 백엔드에서 매물 정보를 가져온다. 실패 시 기본 OG 폴백.
  let title = "GoLe 레고 매물";
  let price = "";
  let category = "";
  try {
    const res = await fetch(`${env.apiBaseUrl}/api/v1/listings/${id}`, {
      next: { revalidate: 60 },
    });
    if (res.ok) {
      const data = (await res.json()) as {
        title?: string;
        price?: number;
        category?: string;
      };
      if (data.title) title = data.title;
      if (data.price) price = `₩${data.price.toLocaleString("ko-KR")}`;
      if (data.category) category = data.category.toUpperCase();
    }
  } catch {
    // 폴백
  }

  return new ImageResponse(
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        width: "100%",
        height: "100%",
        padding: "72px",
        justifyContent: "space-between",
        background: "linear-gradient(135deg, #1d4ed8 0%, #1c2f7c 100%)",
        color: "#ffffff",
        fontFamily: "sans-serif",
      }}
    >
      <div style={{ display: "flex", alignItems: "center", gap: "16px" }}>
        <div style={{ fontSize: "32px", fontWeight: 800 }}>GoLe</div>
        {category ? (
          <div
            style={{
              background: "rgba(255,255,255,0.2)",
              borderRadius: "8px",
              padding: "4px 12px",
              fontSize: "22px",
            }}
          >
            {category}
          </div>
        ) : null}
      </div>

      <div style={{ display: "flex", flexDirection: "column", gap: "16px" }}>
        <div style={{ fontSize: "64px", fontWeight: 800, lineHeight: 1.1 }}>{title}</div>
        {price ? (
          <div style={{ fontSize: "48px", fontWeight: 700, color: "#facc15" }}>{price}</div>
        ) : null}
      </div>

      <div style={{ fontSize: "24px", opacity: 0.7 }}>gole.kscold.com</div>
    </div>,
    size,
  );
}
