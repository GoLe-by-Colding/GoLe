"use client";

import { type FormEvent, useState } from "react";
import { createListing, type ListingCategory, LISTING_CATEGORIES } from "@entities/listing";
import { ApiError } from "@shared/api";
import { Button, Field, Input, Select } from "@shared/ui";
import { draftToInput, EMPTY_LISTING_DRAFT, MAX_LISTING_PHOTOS } from "../model/listing-draft";
import { useListingDraftForm } from "../model/use-listing-draft-form";
import { ListingDraftFields } from "./listing-draft-fields";

export interface CreateListingFormProps {
  readonly sellerId: string;
  readonly paymentsOpen: boolean;
  readonly onCreated: (listingId: string) => void;
}

export function CreateListingForm({ sellerId, paymentsOpen, onCreated }: CreateListingFormProps) {
  const form = useListingDraftForm(EMPTY_LISTING_DRAFT, MAX_LISTING_PHOTOS);
  const [category, setCategory] = useState<ListingCategory>("set");
  const [catalogSetNumber, setCatalogSetNumber] = useState("");

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void form.submit(
      async (draft) => {
        const listing = await createListing({
          ...draftToInput(draft),
          sellerId,
          catalogSetNumber: catalogSetNumber.trim().length > 0 ? catalogSetNumber.trim() : null,
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
      <ListingDraftFields form={form} paymentsOpen={paymentsOpen} />
      <Field
        label="브릭 세트 번호 (선택)"
        hint="해당하는 공식 세트 번호가 있으면 입력하세요. 세트명·번호는 식별용 텍스트로만 표시됩니다."
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
      <Button type="submit" size="lg" fullWidth disabled={form.submitting || form.uploading}>
        {form.submitting ? "등록 중..." : "상품 등록"}
      </Button>
    </form>
  );
}
