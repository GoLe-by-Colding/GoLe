/**
 * 주문 도메인 타입. 백엔드 OrderResponse와 1:1 대응.
 */
import type { PaymentMethod } from "@shared/lib";
export type OrderStatus =
  | "payment_pending"
  | "payment_review"
  | "payment_failed"
  | "funds_held"
  | "completed"
  | "refund_pending"
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
  /** 결제 승인 시 PG가 알려준 결제수단. 결제 전 주문은 null. */
  readonly paymentMethod: PaymentMethod | null;
  readonly createdAt: string;
  readonly history: readonly OrderStatusChange[];
}

const STATUS_LABEL: Record<OrderStatus, string> = {
  payment_pending: "결제 대기",
  payment_review: "결제 확인 필요",
  payment_failed: "결제 실패",
  funds_held: "결제 완료(정산 대기)",
  completed: "거래 완료",
  refund_pending: "환불 처리 중",
  refunded: "환불됨",
};

export function orderStatusLabel(status: OrderStatus): string {
  return STATUS_LABEL[status];
}
