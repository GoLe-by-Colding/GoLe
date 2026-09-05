/**
 * 배송 도메인 타입. 백엔드 ShipmentResponse와 1:1 대응. (shipping-and-fees E1)
 */
export type DeliveryStatus = "pending" | "in_transit" | "delivered" | "unknown";

export interface WaybillChange {
  readonly carrier: string;
  readonly carrierLabel: string;
  readonly waybillNumber: string;
  readonly replacedAt: string;
}

export interface Shipment {
  readonly orderId: string;
  readonly carrier: string;
  readonly carrierLabel: string;
  readonly waybillNumber: string;
  readonly status: DeliveryStatus;
  /** 택배사 원문 상태 — 정규화 값과 별개로 그대로 보여준다. */
  readonly rawStatus: string | null;
  readonly registeredAt: string;
  readonly deliveredAt: string | null;
  readonly lastTrackedAt: string | null;
  readonly history: readonly WaybillChange[];
}

export const DELIVERY_STATUS_LABEL: Record<DeliveryStatus, string> = {
  pending: "접수 대기",
  in_transit: "배송 중",
  delivered: "배송 완료",
  unknown: "조회 불가",
};

/** 지원 택배사. 백엔드 Carrier enum과 키를 맞춘다. */
export const CARRIERS: ReadonlyArray<{ readonly key: string; readonly label: string }> = [
  { key: "cj_logistics", label: "CJ대한통운" },
  { key: "post_office", label: "우체국택배" },
  { key: "hanjin", label: "한진택배" },
  { key: "lotte", label: "롯데택배" },
  { key: "logen", label: "로젠택배" },
];
