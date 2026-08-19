"use client";

import { useState } from "react";
import { DISPUTE_REASON_LABEL, openDispute, type DisputeReason, type Order } from "@entities/order";
import { ApiError } from "@shared/api";
import { Button, Text, Textarea } from "@shared/ui";

export interface OpenDisputeButtonProps {
  readonly orderId: string;
  readonly onDisputed: (order: Order) => void;
}

const REASONS = Object.keys(DISPUTE_REASON_LABEL) as readonly DisputeReason[];

/**
 * 구매자 분쟁 제기. (shipping-and-fees E3)
 * 접수 즉시 자동 구매확정 타이머가 정지하고 운영자 판정으로 넘어간다(R4.2).
 */
export function OpenDisputeButton({ orderId, onDisputed }: OpenDisputeButtonProps) {
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState<DisputeReason>("not_arrived");
  const [detail, setDetail] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit() {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      const order = await openDispute(orderId, reason, detail.trim());
      setOpen(false);
      onDisputed(order);
    } catch (cause) {
      setError(
        cause instanceof ApiError ? cause.message : "분쟁 접수에 실패했어요. 다시 시도해 주세요",
      );
    } finally {
      setBusy(false);
    }
  }

  if (!open) {
    return (
      <Button variant="ghost" onClick={() => setOpen(true)}>
        문제가 있어요 (분쟁 접수)
      </Button>
    );
  }

  return (
    <div className="flex flex-col gap-3 rounded-lg border border-neutral-200 p-4">
      <Text weight="medium">어떤 문제가 있나요?</Text>
      <div className="flex flex-wrap gap-2" role="radiogroup" aria-label="분쟁 사유">
        {REASONS.map((key) => (
          <button
            key={key}
            type="button"
            role="radio"
            aria-checked={reason === key}
            onClick={() => setReason(key)}
            className={`rounded-full border px-3 py-1.5 text-sm transition-colors ${
              reason === key
                ? "border-brand-600 bg-brand-50 font-semibold text-brand-700"
                : "border-neutral-200 text-neutral-600 hover:border-neutral-400"
            }`}
          >
            {DISPUTE_REASON_LABEL[key]}
          </button>
        ))}
      </div>
      <Textarea
        value={detail}
        onChange={(e) => setDetail(e.target.value)}
        placeholder="상황을 자세히 알려주시면 판정이 빨라져요 (선택)"
        rows={3}
        maxLength={1000}
      />
      <Text size="sm" tone="muted">
        접수하면 자동 구매확정이 멈추고, 운영자가 배송 기록을 근거로 환불 또는 거래 완료를
        판정합니다.
      </Text>
      {error ? (
        <Text size="sm" className="text-danger" role="alert">
          {error}
        </Text>
      ) : null}
      <div className="flex gap-2">
        <Button onClick={handleSubmit} disabled={busy}>
          {busy ? "접수 중…" : "분쟁 접수"}
        </Button>
        <Button variant="ghost" onClick={() => setOpen(false)} disabled={busy}>
          취소
        </Button>
      </div>
    </div>
  );
}
