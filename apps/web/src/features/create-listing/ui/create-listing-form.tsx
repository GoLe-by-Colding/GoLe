"use client";

import { type FormEvent, useState } from "react";
import {
  createListing,
  type ItemCondition,
  conditionLabel,
} from "@entities/listing";
import { ApiError } from "@shared/api";
import { Button, Field, Input, Select, Textarea } from "@shared/ui";

const CONDITIONS: readonly ItemCondition[] = [
  "new_sealed",
  "used_complete",
  "used_incomplete",
];

export interface CreateListingFormProps {
  readonly sellerId: string;
  readonly onCreated: (listingId: string) => void;
}

export function CreateListingForm({ sellerId, onCreated }: CreateListingFormProps) {
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [price, setPrice] = useState("");
  const [condition, setCondition] = useState<ItemCondition>("new_sealed");
  const [photoUrl, setPhotoUrl] = useState("");
  const [catalogSetNumber, setCatalogSetNumber] = useState("");
  const [error, setError] = useState<string | undefined>(undefined);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(undefined);
    setSubmitting(true);
    try {
      const listing = await createListing({
        sellerId,
        title,
        description,
        price: Number(price),
        condition,
        photoUrls: photoUrl.trim().length > 0 ? [photoUrl.trim()] : [],
        catalogSetNumber: catalogSetNumber.trim().length > 0 ? catalogSetNumber.trim() : null,
      });
      onCreated(listing.id);
    } catch (cause) {
      setError(
        cause instanceof ApiError ? cause.message : "등록 중 오류가 발생했습니다.",
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
      {error ? (
        <p className="p-3 rounded-md bg-danger-soft text-danger text-sm" role="alert">
          {error}
        </p>
      ) : null}
      <Field label="제목">
        {({ inputId, describedBy }) => (
          <Input
            id={inputId}
            value={title}
            placeholder="예: 에펠탑 10307 미개봉"
            aria-describedby={describedBy}
            onChange={(e) => setTitle(e.target.value)}
            required
          />
        )}
      </Field>
      <Field label="설명">
        {({ inputId, describedBy }) => (
          <Textarea
            id={inputId}
            value={description}
            placeholder="상품 상태, 구성품 등을 적어주세요."
            aria-describedby={describedBy}
            onChange={(e) => setDescription(e.target.value)}
            required
          />
        )}
      </Field>
      <Field label="가격 (원)">
        {({ inputId, describedBy }) => (
          <Input
            id={inputId}
            type="number"
            min={0}
            inputMode="numeric"
            value={price}
            placeholder="280000"
            aria-describedby={describedBy}
            onChange={(e) => setPrice(e.target.value)}
            required
          />
        )}
      </Field>
      <Field label="상품 상태">
        {({ inputId }) => (
          <Select
            id={inputId}
            value={condition}
            onChange={(e) => setCondition(e.target.value as ItemCondition)}
          >
            {CONDITIONS.map((c) => (
              <option key={c} value={c}>
                {conditionLabel(c)}
              </option>
            ))}
          </Select>
        )}
      </Field>
      <Field label="대표 이미지 URL">
        {({ inputId, describedBy }) => (
          <Input
            id={inputId}
            type="url"
            value={photoUrl}
            placeholder="https://..."
            aria-describedby={describedBy}
            onChange={(e) => setPhotoUrl(e.target.value)}
            required
          />
        )}
      </Field>
      <Field label="레고 세트 번호 (선택)" hint="해당하는 공식 세트 번호가 있으면 입력하세요.">
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
      <Button type="submit" size="lg" fullWidth disabled={submitting}>
        {submitting ? "등록 중..." : "상품 등록"}
      </Button>
    </form>
  );
}
