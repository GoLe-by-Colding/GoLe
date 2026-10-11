"use client";

import { type ItemCondition, type ListingCategory, LISTING_CATEGORIES } from "@entities/listing";
import {
  CONDITION_LABEL,
  priceGapBasisCaption,
  priceGapLabel,
  sameGradeEstimate,
  type PriceSnapshot,
} from "@entities/pricing";
import { formatKrw } from "@shared/lib";
import { parseDraftPrice } from "../model/listing-draft";
import { useSellPriceSnapshot } from "../model/use-sell-price-snapshot";

export interface SellPriceGuideProps {
  /** 입력한 세트 번호. 번호가 없으면 `null` — 아무것도 보이지 않는다. */
  readonly setNumber: string | null;
  /** 매물 카테고리. 세트 한 벌(`set`)만 세트 시세와 견준다. */
  readonly category: ListingCategory;
  readonly condition: ItemCondition;
  /** 입력 중인 판매가(입력란 문자열 그대로). */
  readonly price: string;
}

/**
 * 판매가 입력란 아래의 같은 등급 추정 시세. 판매자가 가격을 정하기 전에 시세를 보고, 넣은 가격이 시세와 얼마나
 * 다른지 바로 알게 한다. 가격을 대신 채우지 않는다 — 박스·설명서·부품 상태는 판매자가 가장 잘 안다.
 * 비교 규칙은 매물 상세·목록과 같다(`sameGradeEstimate`: 확정 시세 + 실제 체결 표본 근거일 때만).
 *
 * 미니피규어·부품·MOC는 출처 세트 번호를 달아도 세트 한 벌 시세와 견주지 않는다(`priceComparableSetNumber`).
 * 이때 안내가 말없이 사라지면 판매자는 시세가 없는지·고장인지 알 수 없으므로 견주지 않는 이유를 한 줄 둔다.
 */
export function SellPriceGuide({ setNumber, category, condition, price }: SellPriceGuideProps) {
  const wholeSet = category === "set";
  const state = useSellPriceSnapshot(wholeSet ? setNumber : null);
  if (setNumber !== null && !wholeSet) {
    return (
      <p
        aria-live="polite"
        data-testid="sell-price-guide"
        className="text-xs leading-relaxed break-keep text-neutral-500"
      >
        {categoryName(category)} 매물은 세트 한 벌의 시세와 견주지 않아요. 세트 시세는 세트 한 벌이
        통째로 거래된 가격이에요.
      </p>
    );
  }
  if (state.status === "idle") return null;
  return (
    <div aria-live="polite" data-testid="sell-price-guide">
      {state.status === "loading" ? (
        <p className="text-xs text-neutral-500">이 세트의 시세를 확인하고 있어요…</p>
      ) : state.status === "failed" ? (
        <p className="text-xs break-keep text-neutral-500">
          지금은 시세를 불러오지 못했어요. 가격은 그대로 정해도 돼요.
        </p>
      ) : (
        <SnapshotGuide snapshot={state.snapshot} condition={condition} price={price} />
      )}
    </div>
  );
}

function SnapshotGuide({
  snapshot,
  condition,
  price,
}: {
  readonly snapshot: PriceSnapshot;
  readonly condition: ItemCondition;
  readonly price: string;
}) {
  const estimate = sameGradeEstimate(condition, snapshot);
  if (estimate === null) {
    return (
      <p className="text-xs leading-relaxed break-keep text-neutral-500">
        {noEstimateReason(snapshot, condition)}
      </p>
    );
  }
  const amount = parseDraftPrice(price);
  const gap =
    amount !== null && amount > 0 ? (amount - estimate.fairPrice) / estimate.fairPrice : null;
  return (
    <div className="flex flex-col gap-1 rounded-lg border border-neutral-200 bg-neutral-50 px-3 py-2.5">
      <span className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1">
        <span className="flex flex-wrap items-baseline gap-x-1.5 text-sm text-neutral-600">
          <span>{CONDITION_LABEL[estimate.condition]} 추정 시세</span>
          <span className="font-semibold tabular-nums text-neutral-900">
            {formatKrw(estimate.fairPrice)}
          </span>
        </span>
        {gap === null ? null : (
          <span className="whitespace-nowrap rounded-full border border-neutral-200 bg-white px-2 py-0.5 text-xs font-semibold text-neutral-800">
            {priceGapLabel(gap, "추정 시세보다")}
          </span>
        )}
      </span>
      <span className="text-xs leading-relaxed break-keep text-neutral-500">
        {priceGapBasisCaption(estimate)} · 박스·설명서·부품 상태를 보고 직접 정해 주세요
      </span>
    </div>
  );
}

/** 추정 시세를 보이지 않는 이유. 판매자가 "왜 시세가 안 나오지"를 묻지 않게 상태별로 짧게 말한다. */
function noEstimateReason(snapshot: PriceSnapshot, condition: ItemCondition): string {
  switch (snapshot.state) {
    case "EMPTY":
      return "아직 이 세트의 GoLe 체결 기록이 없어 추정 시세를 보여 드리지 못해요.";
    case "OBSERVATIONS_ONLY":
      return `이 세트의 체결이 아직 ${snapshot.sampleCount}건뿐이라 추정 시세를 내지 않아요.`;
    case "ESTABLISHED":
      return `${CONDITION_LABEL[condition]} 상태는 체결 표본이 부족해 추정 시세를 보여 드리지 못해요.`;
  }
}

function categoryName(category: ListingCategory): string {
  return LISTING_CATEGORIES.find((item) => item.key === category)?.label ?? "세트가 아닌";
}
