import type { Metadata } from "next";
import { fetchSellerRating, type SellerRating } from "@entities/review";
import { SellerShopPage } from "@views/seller-shop";
import { env } from "@shared/config";
import { JsonLd } from "@shared/ui";
import { breadcrumbJsonLd } from "@shared/lib";

interface PageParams {
  readonly params: Promise<{ readonly sellerId: string }>;
}

/** 셀러샵 동적 메타데이터. (SEO 스펙 R3.1) */
export async function generateMetadata({ params }: PageParams): Promise<Metadata> {
  const { sellerId } = await params;
  let rating: SellerRating | null = null;
  try {
    rating = await fetchSellerRating(sellerId);
  } catch {
    rating = null;
  }

  const title = `${sellerId} 판매자 샵`;
  const description =
    rating !== null && rating.count > 0
      ? `평점 ${rating.average.toFixed(1)}점 · 후기 ${rating.count}건. ${sellerId} 판매자의 레고 중고 매물을 확인하세요.`
      : `${sellerId} 판매자의 레고 중고 매물을 확인하세요.`;

  return {
    title,
    description,
    alternates: { canonical: `/shops/${sellerId}` },
    openGraph: { title, description, url: `/shops/${sellerId}`, type: "profile" },
  };
}

export default async function Page({ params }: PageParams) {
  const { sellerId } = await params;

  // 평점은 구조화 데이터용으로만 조회한다. 화면 렌더는 뷰가 자체 로딩한다.
  let rating: SellerRating | null = null;
  try {
    rating = await fetchSellerRating(sellerId);
  } catch {
    rating = null;
  }

  const profile: Record<string, unknown> = {
    "@context": "https://schema.org",
    "@type": "ProfilePage",
    url: `${env.siteUrl}/shops/${sellerId}`,
    mainEntity: {
      "@type": "Person",
      "@id": `${env.siteUrl}/shops/${sellerId}#seller`,
      name: sellerId,
      url: `${env.siteUrl}/shops/${sellerId}`,
      // 후기가 0건이면 aggregateRating을 넣지 않는다. API가 count 0에 average 0.0을
      // 돌려주는데, 그대로 선언하면 별점 0점짜리 리치 스니펫이 노출된다.
      ...(rating !== null && rating.count > 0
        ? {
            aggregateRating: {
              "@type": "AggregateRating",
              ratingValue: rating.average,
              reviewCount: rating.count,
              bestRating: 5,
              worstRating: 1,
            },
          }
        : {}),
    },
  };

  return (
    <>
      <SellerShopPage sellerId={sellerId} />
      <JsonLd data={profile} />
      <JsonLd
        data={breadcrumbJsonLd(
          [
            { name: "홈", path: "/" },
            { name: `${sellerId} 샵`, path: `/shops/${sellerId}` },
          ],
          env.siteUrl,
        )}
      />
    </>
  );
}
