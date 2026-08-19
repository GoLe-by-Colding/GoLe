import { apiRequest } from "@shared/api";
import type { Shipment } from "../model/types";

export interface RegisterWaybillInput {
  readonly carrier: string;
  readonly waybillNumber: string;
  readonly sellerPhone?: string;
}

/** 운송장 등록/교체(판매자 전용). */
export function registerWaybill(orderId: string, input: RegisterWaybillInput): Promise<Shipment> {
  return apiRequest<Shipment>(`/api/v1/orders/${orderId}/shipment`, {
    method: "PUT",
    body: input,
  });
}

export function fetchShipment(orderId: string, signal?: AbortSignal): Promise<Shipment> {
  return apiRequest<Shipment>(`/api/v1/orders/${orderId}/shipment`, {
    cache: "no-store",
    ...(signal === undefined ? {} : { signal }),
  });
}

/** 트래커 즉시 재조회(짧은 TTL 캐시를 거친다). */
export function refreshShipment(orderId: string): Promise<Shipment> {
  return apiRequest<Shipment>(`/api/v1/orders/${orderId}/shipment/tracking`, { method: "POST" });
}
