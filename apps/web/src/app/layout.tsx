import type { Metadata } from "next";
import type { ReactNode } from "react";
import { env } from "@shared/config";
import { JsonLd } from "@shared/ui";
import "./globals.css";

const SITE_NAME = "GoLe";
const SITE_TITLE = "GoLe — 브릭 중고거래 플랫폼";
const SITE_DESCRIPTION =
  "브릭 중고거래·실시간 시세·컬렉션·커뮤니티를 한 곳에서. 판매자와 대화해 거래하고, 지원되는 경우 구매확정 기반 결제를 이용하며, 나만의 브릭 컬렉션을 자랑하세요.";
const KEYWORDS = [
  "브릭",
  "브릭 중고",
  "브릭 중고거래",
  "브릭 시세",
  "브릭 마켓플레이스",
  "조립 블록",
  "MOC",
  "브릭 컬렉션",
  "브릭 직거래",
  "브릭 거래 채팅",
];

export const metadata: Metadata = {
  metadataBase: new URL(env.siteUrl),
  title: {
    default: SITE_TITLE,
    template: `%s · ${SITE_NAME}`,
  },
  description: SITE_DESCRIPTION,
  keywords: KEYWORDS,
  applicationName: SITE_NAME,
  alternates: { canonical: "/" },
  openGraph: {
    type: "website",
    siteName: SITE_NAME,
    title: SITE_TITLE,
    description: SITE_DESCRIPTION,
    url: env.siteUrl,
    locale: "ko_KR",
  },
  twitter: {
    card: "summary_large_image",
    title: SITE_TITLE,
    description: SITE_DESCRIPTION,
  },
  robots: {
    index: true,
    follow: true,
    googleBot: { index: true, follow: true, "max-image-preview": "large" },
  },
  category: "shopping",
};

/** GEO/SEO: 생성형 검색·검색엔진을 위한 조직/사이트 구조화 데이터(JSON-LD). */
function StructuredData() {
  const json = {
    "@context": "https://schema.org",
    "@graph": [
      {
        "@type": "Organization",
        "@id": `${env.siteUrl}/#organization`,
        name: SITE_NAME,
        url: env.siteUrl,
        description: SITE_DESCRIPTION,
      },
      {
        "@type": "WebSite",
        "@id": `${env.siteUrl}/#website`,
        name: SITE_TITLE,
        url: env.siteUrl,
        inLanguage: "ko-KR",
        publisher: { "@id": `${env.siteUrl}/#organization` },
        potentialAction: {
          "@type": "SearchAction",
          target: {
            "@type": "EntryPoint",
            urlTemplate: `${env.siteUrl}/search?query={search_term_string}`,
          },
          "query-input": "required name=search_term_string",
        },
      },
    ],
  };
  return <JsonLd data={json} />;
}

export default function RootLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="ko" data-scroll-behavior="smooth">
      <body>
        {children}
        <StructuredData />
      </body>
    </html>
  );
}
