import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { fetchLegoSetForPage, type LegoSet } from "@entities/lego-set";
import { fetchListingsBySet, type Listing } from "@entities/listing";
import { fetchPriceStatisticsForPage, type PriceStatistics } from "@entities/pricing";
import { SetDetailPage } from "@views/set-detail";
import { env } from "@shared/config";
import { JsonLd } from "@shared/ui";
import { absoluteUrl, breadcrumbJsonLd, schemaItemCondition } from "@shared/lib";

interface PageParams {
  readonly params: Promise<{ readonly setNumber: string }>;
}

/**
 * 세트 상세 — 롱테일 검색 착지 페이지. (SEO 스펙 R1)
 *
 * title은 실제 검색 쿼리 형태(`레고 10307 에펠탑 중고 시세`)를 그대로 담는다.
 * 브랜드명은 layout의 template(`%s · GoLe`)이 뒤에 붙인다.
 */
export async function generateMetadata({ params }: PageParams): Promise<Metadata> {
  const { setNumber } = await params;
  const set = await fetchLegoSetForPage(setNumber).catch(() => null);
  if (set === null) {
    // 알려진 한계: 이 앱에서 notFound()는 404 UI를 렌더하지만 응답 상태는 200으로 나간다
    // (soft 404). 기존 `views/listing-detail`도 동일하다. 상태코드를 고칠 때까지
    // noindex로 색인만은 확실히 막는다. (SEO 스펙 R1.6 / 미해결 이슈)
    return { title: "세트를 찾을 수 없습니다", robots: { index: false, follow: false } };
  }

  const title = `레고 ${set.setNumber} ${set.name} 중고 시세·매물`;
  const description =
    `레고 ${set.name}(${set.setNumber}) 중고 매물과 실제 체결가 시세를 확인하세요. ` +
    `${set.theme} 테마 · ${set.pieceCount.toLocaleString()}피스 · ${set.releaseYear}년 출시.`;
  const canonical = `/sets/${set.setNumber}`;

  return {
    title,
    description,
    alternates: { canonical },
    openGraph: {
      title,
      description,
      url: canonical,
      type: "website",
      ...(set.imageUrl === null
        ? {}
        : { images: [{ url: absoluteUrl(set.imageUrl, env.siteUrl), alt: set.name }] }),
    },
  };
}

/** 세트 + 매물 + 시세 구조화 데이터. 실제 데이터에서만 파생한다(R5.2). */
function setJsonLd(
  set: LegoSet,
  listings: readonly Listing[],
  statistics: PriceStatistics | null,
): Record<string, unknown> {
  const base: Record<string, unknown> = {
    "@context": "https://schema.org",
    "@type": "Product",
    "@id": `${env.siteUrl}/sets/${set.setNumber}#product`,
    name: `레고 ${set.name}`,
    sku: set.setNumber,
    productID: set.setNumber,
    category: set.theme,
    url: `${env.siteUrl}/sets/${set.setNumber}`,
    ...(set.imageUrl === null ? {} : { image: absoluteUrl(set.imageUrl, env.siteUrl) }),
    additionalProperty: [
      { "@type": "PropertyValue", name: "부품 수", value: set.pieceCount },
      { "@type": "PropertyValue", name: "출시 연도", value: set.releaseYear },
      { "@type": "PropertyValue", name: "테마", value: set.theme },
    ],
  };

  // 활성 매물이 있을 때만 offer를 선언한다. 매물 0건에 offer를 붙이면
  // 구조화 데이터 정책 위반(재고 없는 상품을 판매 중으로 표기)이다.
  if (listings.length > 0) {
    const prices = listings.map((l) => l.price);
    base["offers"] = {
      "@type": "AggregateOffer",
      priceCurrency: "KRW",
      lowPrice: Math.min(...prices),
      highPrice: Math.max(...prices),
      offerCount: listings.length,
      availability: "https://schema.org/InStock",
      itemCondition: schemaItemCondition(listings[0]!.condition),
      url: `${env.siteUrl}/sets/${set.setNumber}`,
    };
  }

  if (statistics !== null && statistics.hasData && statistics.transactionCount > 0) {
    base["additionalProperty"] = [
      ...(base["additionalProperty"] as unknown[]),
      {
        "@type": "PropertyValue",
        name: "체결 거래 수",
        value: statistics.transactionCount,
      },
    ];
  }

  return base;
}

export default async function Page({ params }: PageParams) {
  const { setNumber } = await params;

  // 카탈로그에 없는 세트는 404 — 빈 페이지가 색인되는 것을 막는다(R1.6).
  // notFound()는 try/catch 밖에서 호출한다. catch 블록 안에서 던지면 Next의 404 신호가
  // 렌더 스트림이 시작된 뒤에 전달돼 상태코드가 200으로 굳는다(soft 404).
  const set = await fetchLegoSetForPage(setNumber).catch(() => null);
  if (set === null) {
    notFound();
  }

  // 매물·시세는 실패해도 페이지를 살린다(부분 실패 허용).
  const [listings, statistics] = await Promise.all([
    fetchListingsBySet(set.setNumber).catch((): readonly Listing[] => []),
    fetchPriceStatisticsForPage(set.setNumber).catch((): PriceStatistics | null => null),
  ]);

  return (
    <>
      <SetDetailPage set={set} listings={listings} statistics={statistics} />
      <JsonLd data={setJsonLd(set, listings, statistics)} />
      <JsonLd
        data={breadcrumbJsonLd(
          [
            { name: "홈", path: "/" },
            { name: `레고 ${set.setNumber} ${set.name}`, path: `/sets/${set.setNumber}` },
          ],
          env.siteUrl,
        )}
      />
    </>
  );
}
