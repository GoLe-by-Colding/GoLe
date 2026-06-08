"use client";

import { type FormEvent, type ChangeEvent, useState } from "react";
import {
  createListing,
  type ItemCondition,
  type Completeness,
  conditionLabel,
  completenessLabel,
} from "@entities/listing";
import { ApiError, uploadImage } from "@shared/api";
import { Button, Field, Input, Select, Textarea } from "@shared/ui";

const CONDITIONS: readonly ItemCondition[] = [
  "new_sealed",
  "used_complete",
  "used_incomplete",
];

const COMPLETENESS: readonly Completeness[] = ["full_box", "no_box", "bulk"];

export interface CreateListingFormProps {
  readonly sellerId: string;
  readonly onCreated: (listingId: string) => void;
}

export function CreateListingForm({ sellerId, onCreated }: CreateListingFormProps) {
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [price, setPrice] = useState("");
  const [condition, setCondition] = useState<ItemCondition>("new_sealed");
  const [completeness, setCompleteness] = useState<Completeness>("full_box");
  const [hasBox, setHasBox] = useState(true);
  const [hasManual, setHasManual] = useState(true);
  const [hasMissingParts, setHasMissingParts] = useState(false);
  const [missingPartsNote, setMissingPartsNote] = useState("");
  const [defectsNote, setDefectsNote] = useState("");
  const [photoUrl, setPhotoUrl] = useState("");
  const [catalogSetNumber, setCatalogSetNumber] = useState("");
  const [error, setError] = useState<string | undefined>(undefined);
  const [submitting, setSubmitting] = useState(false);
  const [uploading, setUploading] = useState(false);

  async function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (file === undefined) {
      return;
    }
    setError(undefined);
    setUploading(true);
    try {
      const uploaded = await uploadImage(file);
      setPhotoUrl(uploaded.url);
    } catch (cause) {
      setError(
        cause instanceof ApiError ? cause.message : "이미지 업로드에 실패했습니다.",
      );
    } finally {
      setUploading(false);
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(undefined);
    if (photoUrl.trim().length === 0) {
      setError("대표 이미지를 업로드해 주세요.");
      return;
    }
    if (hasMissingParts && missingPartsNote.trim().length === 0) {
      setError("누락 부품이 있으면 누락 내용을 입력해 주세요.");
      return;
    }
    setSubmitting(true);
    try {
      const listing = await createListing({
        sellerId,
        title,
        description,
        price: Number(price),
        condition,
        completeness,
        hasBox,
        hasManual,
        hasMissingParts,
        missingPartsNote: missingPartsNote.trim(),
        defectsNote: defectsNote.trim(),
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
      <Field label="구성">
        {({ inputId }) => (
          <Select
            id={inputId}
            value={completeness}
            onChange={(e) => setCompleteness(e.target.value as Completeness)}
          >
            {COMPLETENESS.map((c) => (
              <option key={c} value={c}>
                {completenessLabel(c)}
              </option>
            ))}
          </Select>
        )}
      </Field>
      <div className="flex flex-wrap gap-4">
        <label className="flex items-center gap-2 text-sm text-neutral-700">
          <input type="checkbox" checked={hasBox} onChange={(e) => setHasBox(e.target.checked)} />
          박스 포함
        </label>
        <label className="flex items-center gap-2 text-sm text-neutral-700">
          <input
            type="checkbox"
            checked={hasManual}
            onChange={(e) => setHasManual(e.target.checked)}
          />
          설명서 포함
        </label>
        <label className="flex items-center gap-2 text-sm text-neutral-700">
          <input
            type="checkbox"
            checked={hasMissingParts}
            onChange={(e) => setHasMissingParts(e.target.checked)}
          />
          누락 부품 있음
        </label>
      </div>
      {hasMissingParts ? (
        <Field label="누락 부품 상세" hint="어떤 부품이 빠졌는지 구체적으로 적어주세요.">
          {({ inputId, describedBy }) => (
            <Textarea
              id={inputId}
              value={missingPartsNote}
              placeholder="예: 미니피겨 1개, 1x1 타일 약 5개 누락"
              aria-describedby={describedBy}
              onChange={(e) => setMissingPartsNote(e.target.value)}
              required
            />
          )}
        </Field>
      ) : null}
      <Field label="하자/손상 고지 (선택)" hint="뭉개짐·변색·파손 등이 있으면 솔직히 적어주세요.">
        {({ inputId, describedBy }) => (
          <Textarea
            id={inputId}
            value={defectsNote}
            placeholder="예: 일부 피스 변색, 박스 모서리 눌림"
            aria-describedby={describedBy}
            onChange={(e) => setDefectsNote(e.target.value)}
          />
        )}
      </Field>
      <Field
        label="대표 이미지"
        hint="직접 촬영한 실물 사진을 올려주세요. 레고 공식 제품 이미지 도용은 금지됩니다."
      >
        {({ inputId, describedBy }) => (
          <div className="flex flex-col gap-2">
            <input
              id={inputId}
              type="file"
              accept="image/*"
              aria-describedby={describedBy}
              onChange={handleFileChange}
              disabled={uploading || submitting}
              className="text-sm text-neutral-700 file:mr-3 file:rounded-md file:border file:border-neutral-200 file:bg-neutral-50 file:px-3 file:py-1.5 file:text-sm"
            />
            {uploading ? (
              <p className="text-sm text-neutral-500">업로드 중...</p>
            ) : null}
            {photoUrl.length > 0 && !uploading ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                src={photoUrl}
                alt="업로드한 대표 이미지 미리보기"
                className="h-32 w-32 rounded-lg border border-neutral-200/70 object-cover"
              />
            ) : null}
          </div>
        )}
      </Field>
      <Field label="레고 세트 번호 (선택)" hint="해당하는 공식 세트 번호가 있으면 입력하세요. 세트명·번호는 식별용 텍스트로만 표시됩니다.">
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
      <Button type="submit" size="lg" fullWidth disabled={submitting || uploading}>
        {submitting ? "등록 중..." : "상품 등록"}
      </Button>
    </form>
  );
}
