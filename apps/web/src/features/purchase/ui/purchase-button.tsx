"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { placeOrder } from "@entities/order";
import { useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import { Button } from "@shared/ui";

export interface PurchaseButtonProps {
  readonly listingId: string;
  readonly available: boolean;
}

export function PurchaseButton({ listingId, available }: PurchaseButtonProps) {
  const router = useRouter();
  const { session } = useSession();
  const [error, setError] = useState<string | undefined>(undefined);
  const [submitting, setSubmitting] = useState(false);

  async function handleClick() {
    if (!session) {
      router.push("/login");
      return;
    }
    setError(undefined);
    setSubmitting(true);
    try {
      const order = await placeOrder(listingId, session.accountId);
      router.push(`/orders/${order.id}`);
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "주문 생성 중 오류가 발생했습니다.");
      setSubmitting(false);
    }
  }

  return (
    <div className="flex flex-col gap-2">
      <Button size="lg" disabled={!available || submitting} onClick={handleClick}>
        {available ? (submitting ? "처리 중..." : "구매하기") : "거래완료"}
      </Button>
      {error ? <span className="text-sm text-danger">{error}</span> : null}
    </div>
  );
}
