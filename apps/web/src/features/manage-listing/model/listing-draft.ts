import {
  listingPhotos,
  type Completeness,
  type ItemCondition,
  type Listing,
  type ListingInterestTag,
  type UpdateListingInput,
} from "@entities/listing";
import type { UploadedImage } from "@shared/api";
import { thumbnailUrl } from "@shared/lib";

/** 등록 화면 기본 사진 한도. 수정 화면은 이미 올린 장수가 더 많으면 그만큼 허용한다. */
export const MAX_LISTING_PHOTOS = 5;

/**
 * 등록·수정 폼이 함께 편집하는 값.
 * 카테고리·세트 번호는 등록에서만 고르므로 여기에 넣지 않는다(수정 불가 — 서버도 무시한다).
 */
export interface ListingDraft {
  readonly title: string;
  readonly description: string;
  /** 입력 중인 원문. 제출할 때 정수로 바꾼다. */
  readonly price: string;
  readonly condition: ItemCondition;
  readonly completeness: Completeness;
  readonly hasBox: boolean;
  readonly hasManual: boolean;
  readonly hasMissingParts: boolean;
  readonly missingPartsNote: string;
  readonly defectsNote: string;
  readonly interestTag: ListingInterestTag | "";
  readonly photos: readonly UploadedImage[];
}

export const EMPTY_LISTING_DRAFT: ListingDraft = {
  title: "",
  description: "",
  price: "",
  condition: "new_sealed",
  completeness: "full_box",
  hasBox: true,
  hasManual: true,
  hasMissingParts: false,
  missingPartsNote: "",
  defectsNote: "",
  interestTag: "",
  photos: [],
};

/** 수정 화면 초기값. 사진은 서버가 준 저장 키를 그대로 다시 제출한다. */
export function draftFromListing(listing: Listing): ListingDraft {
  return {
    title: listing.title,
    description: listing.description,
    price: String(listing.price),
    condition: listing.condition,
    completeness: listing.completeness,
    hasBox: listing.hasBox,
    hasManual: listing.hasManual,
    hasMissingParts: listing.hasMissingParts,
    missingPartsNote: listing.missingPartsNote,
    defectsNote: listing.defectsNote,
    interestTag: listing.interestTag ?? "",
    // 서버가 주는 공개 경로는 API 원점 기준 상대 경로라 미리보기용으로만 풀어 쓴다.
    photos: listingPhotos(listing).map((photo) => ({
      key: photo.key,
      url: thumbnailUrl(photo.url, 240),
    })),
  };
}

/** 입력한 가격 원문을 원 단위 정수로 읽는다. 비었거나 정수가 아니면 `null`. */
export function parseDraftPrice(raw: string): number | null {
  const trimmed = raw.trim();
  if (trimmed.length === 0) {
    return null;
  }
  const amount = Number(trimmed);
  return Number.isSafeInteger(amount) && amount >= 0 ? amount : null;
}

/** 서버로 보내기 전에 화면에서 바로 알려줄 수 있는 오류. 없으면 `undefined`. */
export function validateListingDraft(draft: ListingDraft): string | undefined {
  if (parseDraftPrice(draft.price) === null) {
    return "가격을 0원 이상의 숫자로 입력해 주세요.";
  }
  if (draft.photos.length === 0) {
    return "상품 이미지를 한 장 이상 업로드해 주세요.";
  }
  if (draft.hasMissingParts && draft.missingPartsNote.trim().length === 0) {
    return "누락 부품이 있으면 누락 내용을 입력해 주세요.";
  }
  return undefined;
}

/** 등록·수정이 공통으로 보내는 본문. 검증을 통과한 초안에만 부른다. */
export function draftToInput(draft: ListingDraft): UpdateListingInput {
  return {
    title: draft.title,
    description: draft.description,
    price: parseDraftPrice(draft.price) ?? 0,
    condition: draft.condition,
    completeness: draft.completeness,
    hasBox: draft.hasBox,
    hasManual: draft.hasManual,
    hasMissingParts: draft.hasMissingParts,
    missingPartsNote: draft.missingPartsNote.trim(),
    defectsNote: draft.defectsNote.trim(),
    photoKeys: draft.photos.map((photo) => photo.key),
    interestTag: draft.interestTag === "" ? null : draft.interestTag,
  };
}
