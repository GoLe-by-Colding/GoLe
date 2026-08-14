import type { PaymentMethod } from "@shared/lib";

/**
 * 주문 도메인 타입. 백엔드 OrderResponse와 1:1 대응.
 */
export type OrderStatus =
  | "payment_pending"
  | "payment_failed"
  | "funds_held"
  | "completed"
  | "refunded";

export interface OrderStatusChange {
  readonly status: OrderStatus;
  readonly occurredAt: string;
}

export interface Order {
  readonly id: string;
  readonly listingId: string;
  readonly buyerId: string;
  readonly sellerId: string;
  readonly catalogSetNumber: string | null;
  readonly amount: number;
  readonly status: OrderStatus;
  /** 결제 승인 시점에 PG가 알려준 값. 결제 전이면 null. */
  readonly paymentMethod: PaymentMethod | null;
  readonly createdAt: string;
  readonly history: readonly OrderStatusChange[];
}

const STATUS_LABEL: Record<OrderStatus, string> = {
  payment_pending: "결제 대기",
  payment_failed: "결제 실패",
  funds_held: "결제 완료(에스크로 보관)",
  completed: "거래 완료",
  refunded: "환불됨",
};

export function orderStatusLabel(status: OrderStatus): string {
  return STATUS_LABEL[status];
}
