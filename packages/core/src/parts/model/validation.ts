import type { CreatePartRequestInput } from "./types";

/**
 * 부품 요청 입력 규칙. 서버 도메인(`WantedPart`·`PartRequest`)과 같은 값이어야 한다. (wanted-parts W1)
 *
 * 서버가 최종 판정한다 — 여기서는 사용자가 제출 전에 어느 칸이 틀렸는지 알게 하려는 것뿐이다.
 */
export const PART_REQUEST_RULES = {
  minItems: 1,
  maxItems: 20,
  maxNoteLength: 500,
  maxColorNameLength: 30,
  minQuantity: 1,
  maxQuantity: 999,
  maxSetNumberLength: 30,
  partNumberPattern: /^[A-Za-z0-9][A-Za-z0-9.-]{0,19}$/,
} as const;

/** 부품 한 줄의 칸별 오류. 문제가 없는 칸은 빠진다. */
export interface WantedPartFieldErrors {
  readonly partNumber?: string;
  readonly colorName?: string;
  readonly quantity?: string;
}

export interface PartRequestValidation {
  readonly valid: boolean;
  /** 입력 순서와 같은 길이. 문제가 없는 줄은 빈 객체다. */
  readonly items: readonly WantedPartFieldErrors[];
  /** 줄 수 자체가 규칙을 벗어날 때. */
  readonly itemsError?: string;
  readonly note?: string;
  readonly setNumber?: string;
}

export function validatePartNumber(raw: string): string | undefined {
  const value = raw.trim();
  if (value.length === 0) return "부품 번호를 입력해 주세요.";
  if (!PART_REQUEST_RULES.partNumberPattern.test(value)) {
    return "영문·숫자로 시작하고 영문·숫자·점(.)·하이픈(-)만 20자 이내로 입력해 주세요.";
  }
  return undefined;
}

export function validateColorName(raw: string): string | undefined {
  const value = raw.trim();
  if (value.length === 0) return "색상을 입력해 주세요.";
  if (value.length > PART_REQUEST_RULES.maxColorNameLength) {
    return `색상은 ${PART_REQUEST_RULES.maxColorNameLength}자 이내로 입력해 주세요.`;
  }
  return undefined;
}

export function validateQuantity(quantity: number): string | undefined {
  if (
    !Number.isInteger(quantity) ||
    quantity < PART_REQUEST_RULES.minQuantity ||
    quantity > PART_REQUEST_RULES.maxQuantity
  ) {
    return `수량은 ${PART_REQUEST_RULES.minQuantity}~${PART_REQUEST_RULES.maxQuantity} 사이 정수여야 해요.`;
  }
  return undefined;
}

/** 서버 W1 규칙을 그대로 검사한다. 앞뒤 공백은 서버처럼 제거한 값으로 본다. */
export function validatePartRequest(input: CreatePartRequestInput): PartRequestValidation {
  const items = input.items.map((item) => {
    const partNumber = validatePartNumber(item.partNumber);
    const colorName = validateColorName(item.colorName);
    const quantity = validateQuantity(item.quantity);
    return {
      ...(partNumber === undefined ? {} : { partNumber }),
      ...(colorName === undefined ? {} : { colorName }),
      ...(quantity === undefined ? {} : { quantity }),
    };
  });
  const itemsError =
    input.items.length < PART_REQUEST_RULES.minItems
      ? "찾는 부품을 하나 이상 적어 주세요."
      : input.items.length > PART_REQUEST_RULES.maxItems
        ? `부품은 한 요청에 ${PART_REQUEST_RULES.maxItems}개까지 적을 수 있어요.`
        : undefined;
  const noteLength = (input.note ?? "").trim().length;
  const note =
    noteLength > PART_REQUEST_RULES.maxNoteLength
      ? `메모는 ${PART_REQUEST_RULES.maxNoteLength}자 이내로 입력해 주세요.`
      : undefined;
  const setNumberLength = (input.setNumber ?? "").trim().length;
  const setNumber =
    setNumberLength > PART_REQUEST_RULES.maxSetNumberLength
      ? `세트 번호는 ${PART_REQUEST_RULES.maxSetNumberLength}자 이내로 입력해 주세요.`
      : undefined;

  const valid =
    itemsError === undefined &&
    note === undefined &&
    setNumber === undefined &&
    items.every((errors) => Object.keys(errors).length === 0);

  return {
    valid,
    items,
    ...(itemsError === undefined ? {} : { itemsError }),
    ...(note === undefined ? {} : { note }),
    ...(setNumber === undefined ? {} : { setNumber }),
  };
}
