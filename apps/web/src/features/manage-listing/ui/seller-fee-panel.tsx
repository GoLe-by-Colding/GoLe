"use client";

import { useEffect, useState } from "react";
import { calculateSellerPayout, fetchSellerFeePolicy, type SellerFeePolicy } from "@entities/order";
import { formatKrw } from "@shared/lib";
import { parseDraftPrice } from "../model/listing-draft";

const PERCENT_FORMATTER = new Intl.NumberFormat("ko-KR", {
  style: "percent",
  maximumFractionDigits: 2,
});

type FeePolicyState =
  | { readonly status: "loading" }
  | { readonly status: "ready"; readonly policy: SellerFeePolicy }
  | { readonly status: "unavailable" };

export interface SellerFeePanelProps {
  readonly paymentsOpen: boolean;
  /** 입력 중인 가격 원문. */
  readonly price: string;
}

/** 판매 수수료와 예상 정산액. 결제가 닫혀 있으면 직접 거래 안내만 보여 준다. */
export function SellerFeePanel({ paymentsOpen, price }: SellerFeePanelProps) {
  const [feePolicyState, setFeePolicyState] = useState<FeePolicyState>({ status: "loading" });
  const [feePolicyRequestKey, setFeePolicyRequestKey] = useState(0);

  const priceAmount = parseDraftPrice(price);
  const payoutEstimate =
    feePolicyState.status === "ready" && priceAmount !== null && priceAmount > 0
      ? calculateSellerPayout(priceAmount, feePolicyState.policy)
      : null;

  useEffect(() => {
    if (!paymentsOpen) {
      return;
    }
    const controller = new AbortController();

    fetchSellerFeePolicy(controller.signal)
      .then((policy) => setFeePolicyState({ status: "ready", policy }))
      .catch(() => {
        if (!controller.signal.aborted) {
          setFeePolicyState({ status: "unavailable" });
        }
      });

    return () => controller.abort();
  }, [feePolicyRequestKey, paymentsOpen]);

  function retryFeePolicy() {
    setFeePolicyState({ status: "loading" });
    setFeePolicyRequestKey((current) => current + 1);
  }

  return (
    <section
      aria-labelledby="seller-fee-heading"
      aria-live="polite"
      className="rounded-xl border border-brand-100 bg-brand-50/60 p-4"
    >
      <h2 id="seller-fee-heading" className="text-sm font-bold text-brand-900">
        {paymentsOpen ? "판매 수수료와 예상 정산액" : "현재 거래 방식"}
      </h2>
      {!paymentsOpen ? (
        <p className="mt-2 text-sm leading-relaxed text-neutral-600">
          지금은 플랫폼 결제와 정산 수수료 없이 판매자와 구매자가 채팅으로 조건을 합의하는 직접
          거래만 지원합니다.
        </p>
      ) : feePolicyState.status === "loading" ? (
        <p className="mt-2 text-sm text-neutral-600">현재 수수료 정책을 확인하고 있어요.</p>
      ) : feePolicyState.status === "unavailable" ? (
        <div className="mt-2 flex flex-col items-start gap-2">
          <p className="text-sm leading-relaxed text-neutral-600">
            수수료와 예상 정산액을 불러오지 못했습니다. 상품 등록은 계속할 수 있어요. 거래 전
            수수료를 다시 확인해 주세요.
          </p>
          <button
            type="button"
            className="text-sm font-semibold text-brand-700 underline-offset-4 hover:underline"
            onClick={retryFeePolicy}
          >
            다시 불러오기
          </button>
        </div>
      ) : (
        <div className="mt-2 flex flex-col gap-2 text-sm">
          <p className="text-neutral-600">
            플랫폼 결제 거래 기준 판매 금액의{" "}
            <strong className="text-neutral-900">
              {PERCENT_FORMATTER.format(feePolicyState.policy.rate)}
            </strong>
            {feePolicyState.policy.minFee > 0
              ? ` · 최소 ${formatKrw(feePolicyState.policy.minFee)}`
              : ""}
            {feePolicyState.policy.maxFee > 0
              ? ` · 최대 ${formatKrw(feePolicyState.policy.maxFee)}`
              : " · 상한 없음"}
          </p>
          {payoutEstimate === null ? (
            <p className="font-medium text-brand-800">
              가격을 입력하면 예상 정산액을 바로 확인할 수 있어요.
            </p>
          ) : (
            <dl className="grid grid-cols-2 gap-x-4 gap-y-1 border-t border-brand-100 pt-2 tabular-nums">
              <dt className="text-neutral-600">예상 수수료</dt>
              <dd className="text-right font-semibold text-neutral-900">
                {formatKrw(payoutEstimate.fee)}
              </dd>
              <dt className="text-neutral-600">예상 정산액</dt>
              <dd className="text-right font-bold text-brand-800">
                {formatKrw(payoutEstimate.payout)}
              </dd>
            </dl>
          )}
        </div>
      )}
    </section>
  );
}
