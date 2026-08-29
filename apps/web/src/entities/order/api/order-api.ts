import { apiRequest } from "@shared/api";
import type { Order } from "../model/types";

export function placeOrder(
  listingId: string,
  buyerId: string,
  buyerPhone?: string,
): Promise<Order> {
  return apiRequest<Order>("/api/v1/orders", {
    method: "POST",
    body: { listingId, buyerId, ...(buyerPhone ? { buyerPhone } : {}) },
  });
}

export function openDispute(orderId: string, reason: string, detail: string): Promise<Order> {
  return apiRequest<Order>(`/api/v1/orders/${orderId}/dispute`, {
    method: "POST",
    body: { reason, detail },
  });
}

export interface OrderContacts {
  readonly buyerPhone: string | null;
  readonly sellerPhone: string | null;
  readonly notice: string;
}

/** 거래 당사자 전용 — 마스킹 없는 전체 연락처(R8.4). */
export function fetchOrderContacts(orderId: string): Promise<OrderContacts> {
  return apiRequest<OrderContacts>(`/api/v1/orders/${orderId}/contacts`, { cache: "no-store" });
}

/** 판매자에게 보이는 정산 한 건. 지급 확인(paidAt)까지의 전 구간을 담는다. */
export interface SellerSettlement {
  readonly orderId: string;
  readonly grossAmount: number;
  readonly fee: number;
  readonly payout: number;
  readonly feeRate: number;
  readonly status: "PENDING" | "PAYOUT_IN_PROGRESS" | "PAYOUT_FAILED" | "PAYOUT_BLOCKED" | "PAID";
  readonly createdAt: string | null;
  /** 운영 지급 유예가 끝나는 시각. 별도 취소·분쟁 정책을 대신하지 않는다. */
  readonly payableAt: string | null;
  readonly paidAt: string | null;
  readonly payoutNextAttemptAt: string | null;
}

/** 판매자 발송 관리용 판매 내역. */
export function fetchMySales(signal?: AbortSignal): Promise<readonly Order[]> {
  return apiRequest<readonly Order[]>("/api/v1/orders/sales", {
    cache: "no-store",
    ...(signal === undefined ? {} : { signal }),
  });
}

/** 판매자 본인의 정산 원장. 지급 예정액과 지급 가능 시각을 함께 준다. */
export function fetchMySettlements(signal?: AbortSignal): Promise<readonly SellerSettlement[]> {
  return apiRequest<readonly SellerSettlement[]>("/api/v1/orders/settlements", {
    cache: "no-store",
    ...(signal === undefined ? {} : { signal }),
  });
}

export function payOrder(orderId: string): Promise<Order> {
  return apiRequest<Order>(`/api/v1/orders/${orderId}/payment`, { method: "POST" });
}

export function completeOrder(orderId: string): Promise<Order> {
  return apiRequest<Order>(`/api/v1/orders/${orderId}/completion`, { method: "POST" });
}

export function refundOrder(orderId: string): Promise<Order> {
  return apiRequest<Order>(`/api/v1/orders/${orderId}/refund`, { method: "POST" });
}

export function fetchOrder(orderId: string, signal?: AbortSignal): Promise<Order> {
  return apiRequest<Order>(`/api/v1/orders/${orderId}`, {
    cache: "no-store",
    ...(signal === undefined ? {} : { signal }),
  });
}

export function fetchMyOrders(buyerId: string, signal?: AbortSignal): Promise<readonly Order[]> {
  return apiRequest<readonly Order[]>(`/api/v1/orders?buyerId=${buyerId}`, {
    cache: "no-store",
    ...(signal === undefined ? {} : { signal }),
  });
}
