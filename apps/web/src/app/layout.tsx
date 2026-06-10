import type { Metadata } from "next";
import type { ReactNode } from "react";
import { env } from "@shared/config";
import "./globals.css";

const SITE_NAME = "GoLe";
const SITE_TITLE = "GoLe — 레고 중고거래 플랫폼";
const SITE_DESCRIPTION =
  "레고 중고거래·실시간 시세·검수 안전결제·컬렉션·커뮤니티를 한 곳에서. 에스크로로 안전하게 사고팔고, 체결가 기반 시세를 확인하고, 나만의 레고 컬렉션을 자랑하세요.";
const KEYWORDS = [
  "레고",
  "레고 중고",
  "레고 중고거래",
  "레고 시세",
  "레고 마켓플레이스",
  "LEGO",
  "MOC",
  "레고 컬렉션",
  "안전거래",
  "에스크로",
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
  return (
    <script
      type="application/ld+json"
      // 구조화 데이터는 신뢰된 정적 콘텐츠다.
      dangerouslySetInnerHTML={{ __html: JSON.stringify(json) }}
    />
  );
}

export default function RootLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="ko">
      <body>
        {children}
        <StructuredData />
      </body>
    </html>
  );
}
