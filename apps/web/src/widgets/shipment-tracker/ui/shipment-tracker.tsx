"use client";

import { DELIVERY_STATUS_LABEL, type DeliveryStatus, type Shipment } from "@entities/shipment";
import { Badge, Button, Text } from "@shared/ui";

export interface ShipmentTrackerProps {
  readonly shipment: Shipment;
  /** 새로고침 요청. 데이터 로드는 조립하는 화면의 몫이다(표현 전용 방침). */
  readonly onRefresh?: () => void;
  readonly refreshing?: boolean;
}

const STEPS: ReadonlyArray<{ readonly key: DeliveryStatus; readonly label: string }> = [
  { key: "pending", label: "접수 대기" },
  { key: "in_transit", label: "배송 중" },
  { key: "delivered", label: "배송 완료" },
];

function stepRank(status: DeliveryStatus): number {
  return status === "delivered" ? 2 : status === "in_transit" ? 1 : 0;
}

function formatDateTime(iso: string): string {
  const d = new Date(iso);
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

/**
 * 배송 상태 타임라인. (shipping-and-fees E4 — 표현 전용, props 주입)
 */
export function ShipmentTracker({ shipment, onRefresh, refreshing = false }: ShipmentTrackerProps) {
  const rank = stepRank(shipment.status);
  const unknown = shipment.status === "unknown";

  return (
    <div className="flex flex-col gap-4" data-testid="shipment-tracker">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <Text weight="medium">
            {shipment.carrierLabel} {shipment.waybillNumber}
          </Text>
          {unknown ? <Badge tone="danger">{DELIVERY_STATUS_LABEL.unknown}</Badge> : null}
        </div>
        {onRefresh ? (
          <Button variant="ghost" size="sm" onClick={onRefresh} disabled={refreshing}>
            {refreshing ? "조회 중…" : "새로고침"}
          </Button>
        ) : null}
      </div>

      <ol className="flex items-center gap-0" aria-label="배송 진행 상태">
        {STEPS.map((step, index) => {
          const reached = !unknown && index <= rank;
          return (
            <li key={step.key} className="flex flex-1 items-center last:flex-none">
              <div className="flex flex-col items-center gap-1.5">
                <span
                  aria-hidden="true"
                  className={`grid h-7 w-7 place-items-center rounded-full text-xs font-bold ${
                    reached ? "bg-brand-600 text-white" : "bg-neutral-100 text-neutral-400"
                  }`}
                >
                  {index + 1}
                </span>
                <span
                  className={`whitespace-nowrap text-xs ${
                    reached ? "font-semibold text-neutral-900" : "text-neutral-400"
                  }`}
                  aria-current={!unknown && index === rank ? "step" : undefined}
                >
                  {step.label}
                </span>
              </div>
              {index < STEPS.length - 1 ? (
                <span
                  aria-hidden="true"
                  className={`mx-2 mb-5 h-0.5 flex-1 ${!unknown && index < rank ? "bg-brand-600" : "bg-neutral-200"}`}
                />
              ) : null}
            </li>
          );
        })}
      </ol>

      <dl className="flex flex-col gap-1 text-sm text-neutral-600">
        {shipment.rawStatus ? (
          <div className="flex gap-2">
            <dt className="text-neutral-500">택배사 상태</dt>
            <dd>{shipment.rawStatus}</dd>
          </div>
        ) : null}
        <div className="flex gap-2">
          <dt className="text-neutral-500">발송 등록</dt>
          <dd>{formatDateTime(shipment.registeredAt)}</dd>
        </div>
        {shipment.deliveredAt ? (
          <div className="flex gap-2">
            <dt className="text-neutral-500">배송 완료</dt>
            <dd>{formatDateTime(shipment.deliveredAt)}</dd>
          </div>
        ) : null}
        {shipment.lastTrackedAt ? (
          <div className="flex gap-2">
            <dt className="text-neutral-500">마지막 조회</dt>
            <dd>{formatDateTime(shipment.lastTrackedAt)}</dd>
          </div>
        ) : null}
      </dl>

      {shipment.status === "delivered" ? (
        <Text size="sm" tone="muted">
          배송 완료 후 7일 동안 분쟁이 없으면 자동으로 구매확정됩니다.
        </Text>
      ) : null}
      {shipment.history.length > 0 ? (
        <details className="text-sm text-neutral-500">
          <summary className="cursor-pointer">운송장 변경 이력 {shipment.history.length}건</summary>
          <ul className="mt-1 flex flex-col gap-0.5">
            {shipment.history.map((h, i) => (
              <li key={i}>
                {h.carrierLabel} {h.waybillNumber} → {formatDateTime(h.replacedAt)} 교체
              </li>
            ))}
          </ul>
        </details>
      ) : null}
    </div>
  );
}
