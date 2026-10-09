"use client";

import type { FormEvent } from "react";
import {
  formatWon,
  LISTING_CATEGORY_LABEL,
  listingMutationErrorMessage,
  updateListing,
  type Listing,
} from "@entities/listing";
import { Button, LinkButton } from "@shared/ui";
import {
  draftFromListing,
  draftToInput,
  MAX_LISTING_PHOTOS,
  parseDraftPrice,
} from "../model/listing-draft";
import { useListingDraftForm } from "../model/use-listing-draft-form";
import { ListingDraftFields } from "./listing-draft-fields";
import { SellPriceGuide } from "./sell-price-guide";

export interface EditListingFormProps {
  /** 수정할 매물. 판매 중(`active`)이고 본인 매물인지는 화면(view)이 먼저 거른다. */
  readonly listing: Listing;
  readonly paymentsOpen: boolean;
  readonly onSaved: (listing: Listing) => void;
}

/**
 * 매물 수정 폼. 등록 폼과 같은 입력란을 쓰고, 카테고리·세트 번호만 읽기 전용으로 보여 준다.
 * 서버가 본문을 통째로 교체하므로 손대지 않은 값도 그대로 다시 보낸다.
 */
export function EditListingForm({ listing, paymentsOpen, onSaved }: EditListingFormProps) {
  const initial = draftFromListing(listing);
  const form = useListingDraftForm(initial, Math.max(MAX_LISTING_PHOTOS, initial.photos.length));
  const missingPhotoCount = listing.photoUrls.length - initial.photos.length;
  const nextPrice = parseDraftPrice(form.draft.price);
  const lowering = nextPrice !== null && nextPrice < listing.price;

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void form.submit(
      async (draft) => {
        const updated = await updateListing(listing.id, draftToInput(draft));
        onSaved(updated);
      },
      (cause) => listingMutationErrorMessage(cause, "edit"),
    );
  }

  return (
    <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
      {form.error ? (
        <p className="p-3 rounded-md bg-danger-soft text-danger text-sm" role="alert">
          {form.error}
        </p>
      ) : null}
      <section
        aria-labelledby="listing-fixed-heading"
        className="flex flex-col gap-2 rounded-lg border border-neutral-200 bg-neutral-50 p-4 text-sm"
      >
        <h2 id="listing-fixed-heading" className="font-semibold text-neutral-800">
          바꿀 수 없는 정보
        </h2>
        <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1">
          <dt className="text-neutral-500">카테고리</dt>
          <dd className="text-neutral-900">{LISTING_CATEGORY_LABEL[listing.category]}</dd>
          <dt className="text-neutral-500">세트 번호</dt>
          <dd className="text-neutral-900">
            {listing.catalogSetNumber === null ? "없음" : `#${listing.catalogSetNumber}`}
          </dd>
        </dl>
        <p className="text-xs leading-relaxed text-neutral-500">
          세트가 바뀌면 관심 세트 알림과 시세가 어긋나서 수정할 수 없어요. 다른 상품이라면 판매를
          중지하고 새로 등록해 주세요.
        </p>
      </section>
      {missingPhotoCount > 0 ? (
        <p className="rounded-md bg-warning-soft p-3 text-sm text-warning">
          기존 사진 {missingPhotoCount}장을 불러오지 못했어요. 필요하면 다시 올려 주세요.
        </p>
      ) : null}
      <ListingDraftFields
        form={form}
        paymentsOpen={paymentsOpen}
        priceGuide={
          <SellPriceGuide
            setNumber={listing.category === "set" ? listing.catalogSetNumber : null}
            condition={form.draft.condition}
            price={form.draft.price}
          />
        }
      />
      {lowering ? (
        <p className="rounded-md bg-brand-50 p-3 text-sm text-brand-800">
          {formatWon(listing.price - nextPrice)} 내려요. 이 매물을 찜한 분들께 가격 인하 알림이
          가요.
        </p>
      ) : null}
      <div className="flex flex-col gap-2 sm:flex-row-reverse">
        <Button type="submit" size="lg" fullWidth disabled={form.submitting || form.uploading}>
          {form.submitting ? "저장 중..." : "수정 완료"}
        </Button>
        <LinkButton href={`/listings/${listing.id}`} size="lg" variant="ghost">
          취소
        </LinkButton>
      </div>
    </form>
  );
}
