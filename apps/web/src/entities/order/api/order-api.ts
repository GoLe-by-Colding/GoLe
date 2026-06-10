import { apiRequest } from "@shared/api";
import type { Order } from "../model/types";

export function placeOrder(listingId: string, buyerId: string): Promise<Order> {
  return apiRequest<Order>("/api/v1/orders", {
    method: "POST",
    body: { listingId, buyerId },
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
