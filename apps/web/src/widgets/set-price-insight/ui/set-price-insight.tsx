"use client";

import { useEffect, useState } from "react";
import {
  CONDITION_LABEL,
  valuationBasisLabel,
  valuationBasisTone,
  fetchPriceChart,
  fetchPriceValuation,
  type PricePoint,
  type PriceValuation,
  type SetCondition,
} from "@entities/pricing";
import { Card, LineChart, Skeleton } from "@shared/ui";
import { formatKrw, formatKrwCompact } from "@shared/lib";

export interface SetPriceInsightProps {
  readonly setNumber: string;
  /** 강조할 상품 상태(매물 상태). 해당 상태 행을 하이라이트한다. */
  readonly highlight?: SetCondition;
}

function formatDate(iso: string): string {
  const d = new Date(iso);
  return `${String(d.getFullYear()).slice(2)}.${String(d.getMonth() + 1).padStart(2, "0")}.${String(d.getDate()).padStart(2, "0")}`;
}

/** 등락 방향 캐럿. 텍스트 색을 그대로 따르므로 상승/하락 색은 부모가 정한다. */
function TrendCaret({ up }: { readonly up: boolean }) {
  return (
    <svg
      width="9"
      height="7"
      viewBox="0 0 10 8"
      aria-hidden="true"
      className={up ? undefined : "rotate-180"}
    >
      <path d="M5 0.6 9.3 7.4H0.7z" fill="currentColor" />
    </svg>
  );
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
        <Skeleton className="h-48 w-full rounded-lg" />
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

      <div className="flex flex-wrap items-baseline gap-x-2.5 gap-y-1">
        <span className="text-[28px] leading-none font-bold tracking-[-0.02em] text-neutral-900 tabular-nums">
          {formatKrw(latest)}
        </span>
        {/* 등락은 ▲/▼ 글리프 대신 선형 아이콘으로 그린다. 글리프는 폰트마다 크기·정렬이
            제각각이고 본문 굵기와 섞이면 값싸 보인다(디자인 시스템: 아이콘 일원화). */}
        <span
          className={`inline-flex items-center gap-1 text-sm font-semibold tabular-nums ${up ? "text-rise" : "text-fall"}`}
        >
          <TrendCaret up={up} />
          {Math.abs(ratio * 100).toFixed(1)}%
        </span>
        <span className="text-xs text-neutral-400">최근 체결가 기준</span>
      </div>

      <LineChart
        points={points.map((p) => ({ value: p.price, label: formatDate(p.executedAt) }))}
        formatValue={formatKrw}
        formatAxisValue={formatKrwCompact}
        emptyText="시세 데이터가 부족해요"
      />

      <div className="overflow-x-auto rounded-lg border border-neutral-200">
        <table className="w-full min-w-[460px] border-collapse text-sm">
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
                    <div className="flex flex-col gap-0.5">
                      <span>
                        <span
                          className={`font-medium ${isHighlight ? "text-brand-700" : "text-neutral-900"}`}
                        >
                          {CONDITION_LABEL[c.condition]}
                        </span>
                        {isHighlight ? (
                          <span className="ml-1 text-[11px] text-brand-600">· 이 상품</span>
                        ) : null}
                      </span>
                      <span className={`text-[11px] ${valuationBasisTone(c.basis)}`}>
                        {valuationBasisLabel(c.basis, c.sampleCount)}
                      </span>
                    </div>
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
