import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { fetchLegoSetForPage, type LegoSet } from "@entities/lego-set";
import { fetchListingsBySet, type Listing } from "@entities/listing";
import { fetchPriceSnapshotForPage, type PriceSnapshot } from "@entities/pricing";
import { SetDetailPage } from "@views/set-detail";
import { isApiNotFoundError } from "@shared/api";
import { env } from "@shared/config";
import { JsonLd } from "@shared/ui";
import { absoluteUrl, breadcrumbJsonLd, schemaItemCondition } from "@shared/lib";

interface PageParams {
  readonly params: Promise<{ readonly setNumber: string }>;
}

async function loadSet(setNumber: string): Promise<LegoSet | null> {
  try {
    return await fetchLegoSetForPage(setNumber);
  } catch (cause) {
    if (isApiNotFoundError(cause)) return null;
    throw cause;
  }
}

/**
 * 세트 상세 — 롱테일 검색 착지 페이지. (SEO 스펙 R1)
 *
 * title은 브랜드 오인 없이 세트번호·이름·거래 의도를 담는다.
 * 브랜드명은 layout의 template(`%s · GoLe`)이 뒤에 붙인다.
 */
export async function generateMetadata({ params }: PageParams): Promise<Metadata> {
  const { setNumber } = await params;
  const set = await loadSet(setNumber);
  if (set === null) {
    return { title: "세트를 찾을 수 없습니다", robots: { index: false, follow: false } };
  }

  const title = `브릭 세트 ${set.setNumber} ${set.name} 중고 시세·매물`;
  const description =
    `브릭 세트 ${set.name}(${set.setNumber}) 중고 매물과 검증된 거래 근거의 시세를 확인하세요. ` +
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
  snapshot: PriceSnapshot | null,
): Record<string, unknown> {
  const base: Record<string, unknown> = {
    "@context": "https://schema.org",
    "@type": "Product",
    "@id": `${env.siteUrl}/sets/${set.setNumber}#product`,
    name: `브릭 세트 ${set.name}`,
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

  if (snapshot?.state === "ESTABLISHED" && snapshot.statistics?.hasData) {
    base["additionalProperty"] = [
      ...(base["additionalProperty"] as unknown[]),
      {
        "@type": "PropertyValue",
        name: "체결 거래 수",
        value: snapshot.statistics.transactionCount,
      },
    ];
  }

  return base;
}

export default async function Page({ params }: PageParams) {
  const { setNumber } = await params;

  // Proxy가 렌더 전에 404를 처리하지만, HEAD 미지원 등을 대비해 렌더 경계도 닫는다.
  // 명시적 404만 누락으로 보고 5xx·네트워크 장애는 전역 오류 경계로 전파한다.
  const set = await loadSet(setNumber);
  if (set === null) {
    notFound();
  }

  // 매물·시세는 실패해도 페이지를 살린다(부분 실패 허용).
  const [listings, snapshot] = await Promise.all([
    fetchListingsBySet(set.setNumber).catch((): readonly Listing[] => []),
    fetchPriceSnapshotForPage(set.setNumber).catch((): PriceSnapshot | null => null),
  ]);

  return (
    <>
      <SetDetailPage set={set} listings={listings} snapshot={snapshot} />
      <JsonLd data={setJsonLd(set, listings, snapshot)} />
      <JsonLd
        data={breadcrumbJsonLd(
          [
            { name: "홈", path: "/" },
            { name: `브릭 세트 ${set.setNumber} ${set.name}`, path: `/sets/${set.setNumber}` },
          ],
          env.siteUrl,
        )}
      />
    </>
  );
}
