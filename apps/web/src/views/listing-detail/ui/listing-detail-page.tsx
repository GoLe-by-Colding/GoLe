import { notFound } from "next/navigation";
import {
  completenessLabel,
  conditionLabel,
  fetchListingById,
  formatPriceKrw,
  ListingGallery,
  LISTING_CATEGORY_LABEL,
  type Listing,
} from "@entities/listing";
import { fetchLaunchConfig } from "@entities/launch";
import { OfficialLegoLink } from "@entities/lego-set";
import { ApiError } from "@shared/api";
import { isPaymentRuntimeAvailable } from "@shared/config";
import { Badge, Container, Heading, LinkButton } from "@shared/ui";
import { PurchaseButton } from "@features/purchase";
import { WishlistButton } from "@features/wishlist-toggle";
import { ChatButton } from "@features/chat-listing";
import { ReportButton } from "@features/report-content";
import { SetPriceInsight } from "@widgets/set-price-insight";
import { ListingQna } from "@widgets/listing-qna";
import { SellerMiniCard } from "@widgets/seller-mini-card";

async function loadListing(id: string): Promise<Listing> {
  try {
    return await fetchListingById(id);
  } catch (cause) {
    if (cause instanceof ApiError && cause.status === 404) {
      notFound();
    }
    throw cause;
  }
}

export interface ListingDetailPageProps {
  readonly listingId: string;
  readonly openChat?: boolean;
}

export async function ListingDetailPage({ listingId, openChat = false }: ListingDetailPageProps) {
  const [listing, launch] = await Promise.all([loadListing(listingId), fetchLaunchConfig()]);
  const isAvailable = listing.status === "active";
  const sellerTradingOpen = launch.sellerIdentityVerificationReady;
  const paymentsOpen =
    sellerTradingOpen &&
    launch.features.payments &&
    launch.stage >= 2 &&
    isPaymentRuntimeAvailable();

  return (
    <Container width="lg">
      <div className="grid grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)] gap-10 pt-8 max-[820px]:grid-cols-1 max-[820px]:gap-6">
        <ListingGallery photos={listing.photoUrls} alt={listing.title} />
        <div className="flex flex-col gap-4">
          <div className="flex flex-wrap gap-2">
            <Badge tone="brand">{LISTING_CATEGORY_LABEL[listing.category]}</Badge>
            <Badge tone="neutral">{conditionLabel(listing.condition)}</Badge>
            <Badge tone="brand">{completenessLabel(listing.completeness)}</Badge>
            {listing.catalogSetNumber !== null ? (
              <Badge tone="neutral">#{listing.catalogSetNumber}</Badge>
            ) : null}
            {listing.hasMissingParts ? <Badge tone="warning">부품 누락</Badge> : null}
            {!isAvailable ? <Badge tone="danger">거래완료</Badge> : null}
          </div>
          <Heading level={1}>{listing.title}</Heading>
          <span className="text-3xl font-bold tracking-tight">{formatPriceKrw(listing.price)}</span>
          <div className="flex flex-col gap-2">
            <span className="text-sm font-semibold text-neutral-800">상품 설명</span>
            <p className="whitespace-pre-wrap leading-relaxed text-neutral-600">
              {listing.description}
            </p>
          </div>

          {/* 상태 고지: 구매자가 구매 전에 확인 */}
          <div className="flex flex-col gap-2 rounded-lg border border-neutral-200 bg-neutral-50 p-4 text-sm">
            <span className="font-semibold text-neutral-800">판매자 상태 고지</span>
            <div className="flex flex-wrap gap-x-4 gap-y-1 text-neutral-600">
              <span>구성: {completenessLabel(listing.completeness)}</span>
              <span>박스: {listing.hasBox ? "있음" : "없음"}</span>
              <span>설명서: {listing.hasManual ? "있음" : "없음"}</span>
              <span>누락 부품: {listing.hasMissingParts ? "있음" : "없음"}</span>
            </div>
            {listing.hasMissingParts && listing.missingPartsNote.length > 0 ? (
              <p className="text-warning">누락: {listing.missingPartsNote}</p>
            ) : null}
            {listing.defectsNote.length > 0 ? (
              <p className="text-neutral-600">하자/손상: {listing.defectsNote}</p>
            ) : null}
            <p className="border-t border-neutral-200 pt-2 text-xs leading-relaxed text-neutral-500">
              사진은 판매자가 직접 등록한 실물 이미지입니다. GoLe는 제조사 공식 이미지를 복제해
              제공하지 않습니다.
            </p>
            {listing.catalogSetNumber !== null ? (
              <OfficialLegoLink
                setNumber={listing.catalogSetNumber}
                className="inline-flex w-fit items-center gap-1 text-xs font-semibold text-brand-700 hover:underline"
              />
            ) : null}
          </div>
          <div className="mt-2 flex gap-3">
            {!sellerTradingOpen ? (
              <div className="flex flex-col gap-2 rounded-lg border border-warning/30 bg-warning/5 px-4 py-3">
                <span className="text-sm font-semibold text-neutral-900">
                  현재는 상품 열람만 가능해요
                </span>
                <span className="text-sm leading-relaxed text-neutral-600">
                  판매자 신원확인 절차를 준비 중이라 이 매물의 새 결제와 거래 대화를 시작할 수
                  없습니다. 이미 만든 대화는 채팅 화면에서 계속 확인할 수 있습니다.
                </span>
                <div className="flex flex-wrap gap-2">
                  <LinkButton href="/chat" size="sm" variant="secondary">
                    기존 대화 보기
                  </LinkButton>
                  <LinkButton href="/chat?compose=support&category=TRADE" size="sm" variant="ghost">
                    운영 문의
                  </LinkButton>
                </div>
              </div>
            ) : paymentsOpen ? (
              <PurchaseButton
                listingId={listing.id}
                sellerId={listing.sellerId}
                available={isAvailable}
              />
            ) : (
              <div className="flex flex-col gap-1.5 rounded-lg border border-brand-100 bg-brand-50 px-4 py-3">
                <span className="text-sm font-semibold text-brand-900">
                  지금은 판매자와 직접 거래해요
                </span>
                <span className="text-sm leading-relaxed text-brand-700">
                  채팅으로 상품 상태와 거래 장소 또는 배송 방법을 합의하세요. 플랫폼 결제는 아직
                  받지 않으며, 안전결제가 열리면 찜한 매물로 알려드릴게요.
                </span>
              </div>
            )}
          </div>
          {sellerTradingOpen ? (
            <ChatButton
              listingId={listing.id}
              sellerId={listing.sellerId}
              available={isAvailable}
              label={paymentsOpen ? "판매자와 채팅하기" : "거래 문의하기"}
              directTradeEnabled={launch.tradeMode === "DIRECT_CHAT"}
              initialOpen={openChat}
            />
          ) : null}
          {listing.catalogSetNumber !== null ? (
            <WishlistButton targetType="catalog_set" targetId={listing.catalogSetNumber} />
          ) : null}
          <div className="mt-1 flex flex-col gap-2 border-t border-neutral-200 pt-4">
            <SellerMiniCard sellerId={listing.sellerId} reviewsOpen={launch.features.reviews} />
            <div className="flex justify-end">
              <ReportButton targetType="LISTING" targetId={listing.id} />
            </div>
          </div>
        </div>
      </div>

      {listing.catalogSetNumber !== null ? (
        <section className="mt-12 flex flex-col gap-4 border-t border-neutral-200 pt-10">
          <Heading level={2}>시세</Heading>
          <SetPriceInsight setNumber={listing.catalogSetNumber} highlight={listing.condition} />
        </section>
      ) : null}

      <section className="mt-12 flex flex-col gap-4 border-t border-neutral-200 pt-10 pb-16">
        <ListingQna listingId={listing.id} sellerTradingOpen={sellerTradingOpen} />
      </section>
    </Container>
  );
}
