"use client";

import { type FormEvent, useState } from "react";
import { createListing, type ListingCategory, LISTING_CATEGORIES } from "@entities/listing";
import { ApiError } from "@shared/api";
import { Button, Field, Input, Select } from "@shared/ui";
import { draftToInput, EMPTY_LISTING_DRAFT, MAX_LISTING_PHOTOS } from "../model/listing-draft";
import { useListingDraftForm } from "../model/use-listing-draft-form";
import { ListingDraftFields } from "./listing-draft-fields";
import { SellPriceGuide } from "./sell-price-guide";

export interface CreateListingFormProps {
  readonly sellerId: string;
  readonly paymentsOpen: boolean;
  /** 처음 채워 둘 세트 번호(세트 페이지의 "이 세트 팔기"). 판매자가 지우거나 바꿀 수 있다. */
  readonly initialSetNumber?: string | null;
  readonly onCreated: (listingId: string) => void;
}

export function CreateListingForm({
  sellerId,
  paymentsOpen,
  initialSetNumber = null,
  onCreated,
}: CreateListingFormProps) {
  const form = useListingDraftForm(EMPTY_LISTING_DRAFT, MAX_LISTING_PHOTOS);
  const [category, setCategory] = useState<ListingCategory>("set");
  const [catalogSetNumber, setCatalogSetNumber] = useState(initialSetNumber ?? "");
  const setNumber = catalogSetNumber.trim().length > 0 ? catalogSetNumber.trim() : null;

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void form.submit(
      async (draft) => {
        const listing = await createListing({
          ...draftToInput(draft),
          sellerId,
          catalogSetNumber: setNumber,
          category,
        });
        onCreated(listing.id);
      },
      (cause) => (cause instanceof ApiError ? cause.message : "등록 중 오류가 발생했습니다."),
    );
  }

  return (
    <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
      {form.error ? (
        <p className="p-3 rounded-md bg-danger-soft text-danger text-sm" role="alert">
          {form.error}
        </p>
      ) : null}
      <Field label="카테고리">
        {({ inputId }) => (
          <Select
            id={inputId}
            value={category}
            onChange={(e) => setCategory(e.target.value as ListingCategory)}
          >
            {LISTING_CATEGORIES.map((c) => (
              <option key={c.key} value={c.key}>
                {c.label}
              </option>
            ))}
          </Select>
        )}
      </Field>
      {/* 세트 번호는 시세·관심 세트 알림·입찰의 기준이라 가격보다 먼저 받는다. 세트로 팔면 가격 칸 아래에
          같은 상태의 추정 시세가 뜬다. */}
      <Field
        label="브릭 세트 번호 (선택)"
        hint="해당하는 공식 세트 번호가 있으면 입력하세요. 세트명·번호는 식별용 텍스트로만 표시됩니다. 세트로 팔면 가격 칸 아래에 같은 상태의 추정 시세를 보여 드려요."
      >
        {({ inputId, describedBy }) => (
          <Input
            id={inputId}
            value={catalogSetNumber}
            placeholder="10307"
            aria-describedby={describedBy}
            onChange={(e) => setCatalogSetNumber(e.target.value)}
          />
        )}
      </Field>
      <ListingDraftFields
        form={form}
        paymentsOpen={paymentsOpen}
        priceGuide={
          <SellPriceGuide
            setNumber={category === "set" ? setNumber : null}
            condition={form.draft.condition}
            price={form.draft.price}
          />
        }
      />
      <Button type="submit" size="lg" fullWidth disabled={form.submitting || form.uploading}>
        {form.submitting ? "등록 중..." : "상품 등록"}
      </Button>
    </form>
  );
}
