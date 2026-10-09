"use client";

import type { ChangeEvent } from "react";
import {
  type Completeness,
  type ItemCondition,
  type ListingInterestTag,
  conditionLabel,
  completenessLabel,
  ITEM_CONDITIONS,
  LISTING_INTEREST_TAGS,
} from "@entities/listing";
import { Field, Input, Select, Textarea } from "@shared/ui";
import type { ListingDraftForm } from "../model/use-listing-draft-form";
import { SellerFeePanel } from "./seller-fee-panel";

const COMPLETENESS: readonly Completeness[] = ["full_box", "no_box", "bulk"];

export interface ListingDraftFieldsProps {
  readonly form: ListingDraftForm;
  readonly paymentsOpen: boolean;
}

/**
 * 등록·수정 폼이 공유하는 입력란 — 관심 테마부터 사진까지.
 * 카테고리·세트 번호는 등록에서만 고르므로 각 폼이 이 앞뒤에 따로 둔다.
 */
export function ListingDraftFields({ form, paymentsOpen }: ListingDraftFieldsProps) {
  const { draft, update, uploading, submitting, maxPhotos } = form;

  async function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    const input = event.target;
    const selected = Array.from(input.files ?? []);
    try {
      await form.addPhotos(selected);
    } finally {
      input.value = ""; // 같은 파일 재선택 허용
    }
  }

  return (
    <>
      <Field
        label="관심 테마"
        hint="이 테마를 관심 태그로 고른 이용자에게 알림톡이 갈 수 있어요 (선택)"
      >
        {({ inputId, describedBy }) => (
          <Select
            id={inputId}
            value={draft.interestTag}
            aria-describedby={describedBy}
            onChange={(e) => update({ interestTag: e.target.value as ListingInterestTag | "" })}
          >
            <option value="">선택 안 함</option>
            {LISTING_INTEREST_TAGS.map((tag) => (
              <option key={tag.key} value={tag.key}>
                {tag.label}
              </option>
            ))}
          </Select>
        )}
      </Field>
      <Field label="제목">
        {({ inputId, describedBy }) => (
          <Input
            id={inputId}
            value={draft.title}
            placeholder="예: 에펠탑 10307 미개봉"
            aria-describedby={describedBy}
            onChange={(e) => update({ title: e.target.value })}
            required
          />
        )}
      </Field>
      <Field label="설명">
        {({ inputId, describedBy }) => (
          <Textarea
            id={inputId}
            value={draft.description}
            placeholder="상품 상태, 구성품 등을 적어주세요."
            aria-describedby={describedBy}
            onChange={(e) => update({ description: e.target.value })}
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
            value={draft.price}
            placeholder="280000"
            aria-describedby={describedBy}
            onChange={(e) => update({ price: e.target.value })}
            required
          />
        )}
      </Field>
      <SellerFeePanel paymentsOpen={paymentsOpen} price={draft.price} />
      <Field label="상품 상태">
        {({ inputId }) => (
          <Select
            id={inputId}
            value={draft.condition}
            onChange={(e) => update({ condition: e.target.value as ItemCondition })}
          >
            {ITEM_CONDITIONS.map((c) => (
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
            value={draft.completeness}
            onChange={(e) => update({ completeness: e.target.value as Completeness })}
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
          <input
            type="checkbox"
            checked={draft.hasBox}
            onChange={(e) => update({ hasBox: e.target.checked })}
          />
          박스 포함
        </label>
        <label className="flex items-center gap-2 text-sm text-neutral-700">
          <input
            type="checkbox"
            checked={draft.hasManual}
            onChange={(e) => update({ hasManual: e.target.checked })}
          />
          설명서 포함
        </label>
        <label className="flex items-center gap-2 text-sm text-neutral-700">
          <input
            type="checkbox"
            checked={draft.hasMissingParts}
            onChange={(e) => update({ hasMissingParts: e.target.checked })}
          />
          누락 부품 있음
        </label>
      </div>
      {draft.hasMissingParts ? (
        <Field
          label="누락 부품 상세"
          hint="어떤 부품이 몇 개 빠졌는지 구체적으로 적어주세요. 누락 부위 사진도 함께 올리면 좋아요."
        >
          {({ inputId, describedBy }) => (
            <Textarea
              id={inputId}
              value={draft.missingPartsNote}
              placeholder="예: 미니피겨 1개, 1x1 타일 약 5개 누락"
              aria-describedby={describedBy}
              onChange={(e) => update({ missingPartsNote: e.target.value })}
              required
            />
          )}
        </Field>
      ) : null}
      <Field label="하자/손상 고지 (선택)" hint="뭉개짐·변색·파손 등이 있으면 솔직히 적어주세요.">
        {({ inputId, describedBy }) => (
          <Textarea
            id={inputId}
            value={draft.defectsNote}
            placeholder="예: 일부 피스 변색, 박스 모서리 눌림"
            aria-describedby={describedBy}
            onChange={(e) => update({ defectsNote: e.target.value })}
          />
        )}
      </Field>
      <Field
        label="상품 이미지"
        hint={`직접 촬영한 JPEG/PNG/HEIC/HEIF 정지 사진을 올려주세요(최대 ${maxPhotos}장). HEIC/HEIF는 자동 변환하고 위치정보 등 메타데이터는 제거하며 제조사 공식 제품 이미지 도용은 금지됩니다.`}
      >
        {({ inputId, describedBy }) => (
          <div className="flex flex-col gap-3">
            <input
              id={inputId}
              type="file"
              accept="image/jpeg,image/png,image/heic,image/heif,.heic,.heif"
              multiple
              aria-describedby={describedBy}
              onChange={handleFileChange}
              disabled={uploading || submitting || draft.photos.length >= maxPhotos}
              className="text-sm text-neutral-700 file:mr-3 file:rounded-md file:border file:border-neutral-200 file:bg-neutral-50 file:px-3 file:py-1.5 file:text-sm"
            />
            {uploading ? (
              <p role="status" className="text-sm text-neutral-500">
                업로드 중...
              </p>
            ) : null}
            {draft.photos.length > 0 ? (
              <ul className="flex flex-wrap gap-3">
                {draft.photos.map((photo, index) => (
                  <li key={photo.key} className="relative">
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    <img
                      src={photo.url}
                      alt={`상품 이미지 ${index + 1}`}
                      className="h-24 w-24 rounded-lg border border-neutral-200/70 object-cover"
                    />
                    <button
                      type="button"
                      onClick={() => form.removePhoto(photo.key)}
                      aria-label={`이미지 ${index + 1} 삭제`}
                      className="absolute -right-2 -top-2 flex h-6 w-6 items-center justify-center rounded-full bg-neutral-900/80 text-sm text-white"
                    >
                      ×
                    </button>
                  </li>
                ))}
              </ul>
            ) : null}
          </div>
        )}
      </Field>
    </>
  );
}
