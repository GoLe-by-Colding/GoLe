import { apiRequest } from "@shared/api";
import type { Order } from "../model/types";

export function placeOrder(listingId: string, buyerId: string): Promise<Order> {
  return apiRequest<Order>("/api/v1/orders", {
    method: "POST",
    body: { listingId, buyerId },
  });
}

/**
 * 결제창을 열기 직전에 호출해 PG 결제 식별자를 받는다.
 *
 * <p>주문 id를 그대로 쓰면 안 된다 — PG는 같은 식별자를 두 번 받아주지 않으므로, 결제창을 닫은
 * 뒤 다시 결제하려는 사용자가 영영 결제하지 못한다. 서버가 시도마다 새 식별자를 발급한다.
 */
export function startPayment(orderId: string): Promise<{ readonly paymentId: string }> {
  return apiRequest<{ readonly paymentId: string }>(`/api/v1/orders/${orderId}/payment-attempts`, {
    method: "POST",
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
