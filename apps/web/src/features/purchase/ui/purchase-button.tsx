"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { placeOrder } from "@entities/order";
import { useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import { loginHrefForCurrentPage } from "@shared/lib";
import { Button, Field, Input, Text } from "@shared/ui";

export interface PurchaseButtonProps {
  readonly listingId: string;
  /** 매물 판매자 id. 자기 매물이면 구매 동선을 노출하지 않는다. */
  readonly sellerId: string;
  readonly available: boolean;
}

/** 마지막으로 쓴 CS 연락처를 기억해 다음 구매에서 미리 채운다. */
const PHONE_STORAGE_KEY = "gole.buyer-phone";

function readStoredPhone(): string {
  if (typeof window === "undefined") return "";
  try {
    return window.localStorage.getItem(PHONE_STORAGE_KEY) ?? "";
  } catch {
    return "";
  }
}

export function PurchaseButton({ listingId, sellerId, available }: PurchaseButtonProps) {
  const router = useRouter();
  const { session } = useSession();
  const [error, setError] = useState<string | undefined>(undefined);
  const [submitting, setSubmitting] = useState(false);
  // 구매하기 → 연락처 확인 → 주문 생성. 배송 문제가 생기면 연락할 번호가 필요하다(R8.1).
  const [phoneStep, setPhoneStep] = useState(false);
  const [buyerPhone, setBuyerPhone] = useState("");
  const isOwnListing = session?.accountId === sellerId;

  function handleClick() {
    if (!session) {
      router.push(loginHrefForCurrentPage());
      return;
    }
    setBuyerPhone(readStoredPhone());
    setPhoneStep(true);
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (!session || submitting) return;
    setError(undefined);
    setSubmitting(true);
    try {
      const phone = buyerPhone.trim();
      const order = await placeOrder(listingId, session.accountId, phone || undefined);
      try {
        if (phone) window.localStorage.setItem(PHONE_STORAGE_KEY, phone);
      } catch {
        // 저장 실패는 무시 — 다음 구매에서 다시 입력하면 된다.
      }
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

  if (phoneStep) {
    return (
      <form onSubmit={handleSubmit} className="flex flex-col gap-3">
        <Field
          label="CS 연락처"
          hint="배송 문제가 생겼을 때 연락받을 번호예요. 판매자에게는 마스킹되어 보입니다."
        >
          {({ inputId, describedBy }) => (
            <Input
              id={inputId}
              aria-describedby={describedBy}
              value={buyerPhone}
              onChange={(e) => setBuyerPhone(e.target.value)}
              placeholder="010-1234-5678"
              inputMode="tel"
              type="tel"
              required
              autoFocus
            />
          )}
        </Field>
        {error ? (
          <Text size="sm" className="text-danger" role="alert">
            {error}
          </Text>
        ) : null}
        <div className="flex gap-2">
          <Button size="lg" fullWidth type="submit" disabled={submitting}>
            {submitting ? "처리 중..." : "주문하기"}
          </Button>
          <Button size="lg" variant="ghost" type="button" onClick={() => setPhoneStep(false)}>
            취소
          </Button>
        </div>
      </form>
    );
  }

  return (
    <div className="flex flex-col gap-2">
      <Button size="lg" disabled={!available || submitting} onClick={handleClick}>
        {available ? "구매하기" : "거래완료"}
      </Button>
      {error ? <span className="text-sm text-danger">{error}</span> : null}
    </div>
  );
}
