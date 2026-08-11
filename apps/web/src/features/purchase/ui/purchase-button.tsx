"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { placeOrder } from "@entities/order";
import { useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import { Button } from "@shared/ui";

export interface PurchaseButtonProps {
  readonly listingId: string;
  /** 매물 판매자 id. 자기 매물이면 구매 동선을 노출하지 않는다. */
  readonly sellerId: string;
  readonly available: boolean;
}

export function PurchaseButton({ listingId, sellerId, available }: PurchaseButtonProps) {
  const router = useRouter();
  const { session } = useSession();
  const [error, setError] = useState<string | undefined>(undefined);
  const [submitting, setSubmitting] = useState(false);
  const isOwnListing = session?.accountId === sellerId;

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

  // 자기 매물은 구매할 수 없다(서버도 SELF_PURCHASE_NOT_ALLOWED로 거부한다).
  // 버튼을 비활성으로 남기면 왜 막혔는지 알 수 없어 이유를 함께 보여준다.
  if (isOwnListing) {
    return (
      <div className="flex flex-col gap-2">
        <Button size="lg" disabled>
          내 매물
        </Button>
        <span className="text-sm text-neutral-500">
          내가 등록한 매물은 구매할 수 없어요. 시세를 지키기 위한 정책이에요.
        </span>
      </div>
    );
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
