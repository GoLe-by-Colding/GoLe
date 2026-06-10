"use client";

import { useEffect, useState } from "react";
import {
  CONDITION_LABEL,
  fetchPriceChart,
  fetchPriceValuation,
  type PricePoint,
  type PriceValuation,
  type SetCondition,
} from "@entities/pricing";
import { Card, LineChart, Skeleton } from "@shared/ui";
import { formatKrw } from "@shared/lib";

export interface SetPriceInsightProps {
  readonly setNumber: string;
  /** 강조할 상품 상태(매물 상태). 해당 상태 행을 하이라이트한다. */
  readonly highlight?: SetCondition;
}

function formatDate(iso: string): string {
  const d = new Date(iso);
  return `${String(d.getFullYear()).slice(2)}.${String(d.getMonth() + 1).padStart(2, "0")}.${String(d.getDate()).padStart(2, "0")}`;
}

/**
 * 카탈로그 세트 시세 인사이트 패널. setNumber로 체결 시계열·상태별 밸류에이션을 조회해
 * KREAM처럼 상품 상세에서 차트 + 상태별 매수/매도 시세를 함께 보여준다.
 */
export function SetPriceInsight({ setNumber, highlight }: SetPriceInsightProps) {
  const [points, setPoints] = useState<readonly PricePoint[] | null>(null);
  const [valuation, setValuation] = useState<PriceValuation | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    Promise.all([
      fetchPriceChart(setNumber, controller.signal),
      fetchPriceValuation(setNumber, controller.signal),
    ])
      .then(([p, v]) => {
        setPoints(p);
        setValuation(v);
      })
      .catch(() => setFailed(true));
    return () => controller.abort();
  }, [setNumber]);

  if (failed) {
    return null;
  }
  if (points === null) {
    return (
      <Card padded className="flex flex-col gap-3">
        <Skeleton className="h-5 w-28" />
        <Skeleton className="h-48 w-full rounded-xl" />
      </Card>
    );
  }
  if (points.length < 2 || !valuation?.hasData) {
    return null; // 데이터 없으면 패널 자체를 숨긴다.
  }

  const latest = points[points.length - 1]!.price;
  const first = points[0]!.price;
  const ratio = first === 0 ? 0 : (latest - first) / first;
  const up = ratio >= 0;

  return (
    <Card padded className="flex flex-col gap-5">
      <div className="flex items-center justify-between">
        <span className="text-base font-bold text-neutral-900">GoLe 시세</span>
        <span className="font-mono text-xs text-neutral-400">#{setNumber}</span>
      </div>

      <div className="flex items-end gap-3">
        <span className="text-2xl font-extrabold tracking-tight text-neutral-900">
          {formatKrw(latest)}
        </span>
        <span className={`mb-1 text-sm font-bold tabular-nums ${up ? "text-success" : "text-danger"}`}>
          {up ? "▲" : "▼"} {Math.abs(ratio * 100).toFixed(1)}%
        </span>
        <span className="mb-1 text-xs text-neutral-400">최근 체결가 기준</span>
      </div>

      <LineChart
        points={points.map((p) => ({ value: p.price, label: formatDate(p.executedAt) }))}
        formatValue={formatKrw}
        emptyText="시세 데이터가 부족해요"
      />

      <div className="overflow-hidden rounded-xl border border-neutral-200/60">
        <table className="w-full border-collapse text-sm">
          <thead>
            <tr className="bg-neutral-50 text-xs text-neutral-500">
              <th className="px-3 py-2 text-left font-medium">상태</th>
              <th className="px-3 py-2 text-right font-medium">시세</th>
              <th className="px-3 py-2 text-right font-medium">즉시판매</th>
              <th className="px-3 py-2 text-right font-medium">즉시구매</th>
            </tr>
          </thead>
          <tbody>
            {valuation.conditions.map((c) => {
              const isHighlight = c.condition === highlight;
              return (
                <tr
                  key={c.condition}
                  className={`border-t border-neutral-100 ${isHighlight ? "bg-brand-50/60" : ""}`}
                >
                  <td className="px-3 py-2.5">
                    <span className={`font-medium ${isHighlight ? "text-brand-700" : "text-neutral-900"}`}>
                      {CONDITION_LABEL[c.condition]}
                    </span>
                    {isHighlight ? (
                      <span className="ml-1 text-[11px] text-brand-600">· 이 상품</span>
                    ) : null}
                  </td>
                  <td className="px-3 py-2.5 text-right font-semibold tabular-nums text-neutral-900">
                    {formatKrw(c.fairPrice)}
                  </td>
                  <td className="px-3 py-2.5 text-right tabular-nums text-success">
                    {formatKrw(c.sellPrice)}
                  </td>
                  <td className="px-3 py-2.5 text-right tabular-nums text-danger">
                    {formatKrw(c.buyPrice)}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </Card>
  );
}
