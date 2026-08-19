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

/** 판매자 발송 관리용 판매 내역. */
export function fetchMySales(signal?: AbortSignal): Promise<readonly Order[]> {
  return apiRequest<readonly Order[]>("/api/v1/orders/sales", {
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
