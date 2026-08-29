"use client";

import { useEffect, useState } from "react";
import {
  CONDITION_LABEL,
  fetchPriceSnapshot,
  priceEvidenceWarning,
  valuationBasisLabel,
  valuationBasisTone,
  type PricePoint,
  type PriceSnapshot,
  type SetCondition,
} from "@entities/pricing";
import { formatKrw, formatKrwCompact } from "@shared/lib";
import { Badge, Button, Card, EmptyState, LineChart, Skeleton } from "@shared/ui";

export interface SetPriceInsightProps {
  readonly setNumber: string;
  /** 강조할 상품 상태(매물 상태). 해당 상태 행을 하이라이트한다. */
  readonly highlight?: SetCondition;
}

interface PriceInsightResult {
  readonly key: string;
  readonly points: readonly PricePoint[] | null;
  readonly snapshot: PriceSnapshot | null;
  readonly failed: boolean;
}

function formatDate(iso: string): string {
  const date = new Date(iso);
  return `${String(date.getFullYear()).slice(2)}.${String(date.getMonth() + 1).padStart(2, "0")}.${String(date.getDate()).padStart(2, "0")}`;
}

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

export function SetPriceInsight({ setNumber, highlight }: SetPriceInsightProps) {
  const [result, setResult] = useState<PriceInsightResult | null>(null);
  const [retryKey, setRetryKey] = useState(0);
  const requestKey = `${setNumber}:${retryKey}`;

  useEffect(() => {
    const controller = new AbortController();

    void fetchPriceSnapshot(setNumber, controller.signal)
      .then((snapshot) => {
        if (controller.signal.aborted) return;
        setResult({
          key: requestKey,
          points: [...snapshot.observations].reverse(),
          snapshot,
          failed: false,
        });
      })
      .catch(() => {
        if (controller.signal.aborted) return;
        setResult({ key: requestKey, points: null, snapshot: null, failed: true });
      });

    return () => controller.abort();
  }, [requestKey, setNumber]);

  if (result?.key !== requestKey) {
    return (
      <Card padded className="flex flex-col gap-3">
        <Skeleton className="h-5 w-28" />
        <Skeleton className="h-48 w-full rounded-lg" />
      </Card>
    );
  }

  if (result.failed || result.snapshot === null) {
    return (
      <Card padded>
        <EmptyState
          variant="inline"
          title="시세를 불러오지 못했어요"
          description="체결 전 상태가 아니라 일시적인 조회 오류예요."
          action={
            <Button size="sm" variant="secondary" onClick={() => setRetryKey((value) => value + 1)}>
              다시 시도
            </Button>
          }
        />
      </Card>
    );
  }

  const { points, snapshot } = result;
  const evidenceWarning = priceEvidenceWarning(snapshot.provenance);

  if (snapshot.state === "EMPTY") {
    return (
      <Card padded>
        <EmptyState
          variant="inline"
          title="아직 체결 시세가 없어요"
          description="GoLe에서 구매가 확정되면 실제 체결가가 쌓이고 시세가 시작돼요."
        />
      </Card>
    );
  }

  const latest = snapshot.statistics?.latestPrice ?? snapshot.observations[0]?.price ?? null;

  if (snapshot.state === "OBSERVATIONS_ONLY") {
    return (
      <Card padded className="flex flex-col gap-4">
        <div className="flex items-center justify-between gap-3">
          <span className="text-base font-bold text-neutral-900">GoLe 시세</span>
          <span className="flex flex-wrap justify-end gap-2">
            {evidenceWarning === null ? null : <Badge tone="warning">{evidenceWarning}</Badge>}
            <Badge tone="warning">체결 {snapshot.sampleCount}건 · 참고 단계</Badge>
          </span>
        </div>
        <span className="text-[28px] leading-none font-bold tracking-[-0.02em] text-neutral-900 tabular-nums">
          {latest === null ? "—" : formatKrw(latest)}
        </span>
        <p className="text-sm leading-relaxed text-neutral-600">
          실제 체결가는 확인됐어요. {snapshot.minimumSamples}건이 쌓이기 전까지 등락률과 상태별
          추정가는 표시하지 않아요.
        </p>
        <ul className="divide-y divide-neutral-100 rounded-lg border border-neutral-200">
          {snapshot.observations.map((point, index) => (
            <li
              key={`${point.executedAt}-${index}`}
              className="flex items-center justify-between gap-3 px-3 py-2 text-sm"
            >
              <span className="text-neutral-500">
                {formatDate(point.executedAt)} · {CONDITION_LABEL[point.condition]}
              </span>
              <span className="font-semibold tabular-nums text-neutral-900">
                {formatKrw(point.price)}
              </span>
            </li>
          ))}
        </ul>
      </Card>
    );
  }

  const chartReady = points !== null && points.length >= 2;
  const first = chartReady ? points[0]!.price : null;
  const chartLatest = chartReady ? points[points.length - 1]!.price : null;
  const ratio =
    first === null || chartLatest === null || first === 0 ? null : (chartLatest - first) / first;
  const up = ratio === null || ratio >= 0;

  return (
    <Card padded className="flex flex-col gap-5">
      <div className="flex items-center justify-between gap-3">
        <span className="text-base font-bold text-neutral-900">GoLe 시세</span>
        <span className="flex items-center gap-2">
          {evidenceWarning === null ? null : <Badge tone="warning">{evidenceWarning}</Badge>}
          <span className="font-mono text-xs text-neutral-400">#{setNumber}</span>
        </span>
      </div>

      <div className="flex flex-wrap items-baseline gap-x-2.5 gap-y-1">
        <span className="text-[28px] leading-none font-bold tracking-[-0.02em] text-neutral-900 tabular-nums">
          {latest === null ? "—" : formatKrw(latest)}
        </span>
        {ratio === null ? null : (
          <span
            className={`inline-flex items-center gap-1 text-sm font-semibold tabular-nums ${up ? "text-rise" : "text-fall"}`}
          >
            <TrendCaret up={up} />
            {Math.abs(ratio * 100).toFixed(1)}%
          </span>
        )}
        <span className="text-xs text-neutral-400">
          {evidenceWarning === null ? "검증된 체결가 기준" : "참고용 체결가 기준"}
        </span>
      </div>

      <LineChart
        points={(points ?? []).map((point) => ({
          value: point.price,
          label: formatDate(point.executedAt),
        }))}
        formatValue={formatKrw}
        formatAxisValue={formatKrwCompact}
        emptyText={points === null ? "차트를 불러오지 못했어요" : "미개봉 체결이 더 필요해요"}
      />

      {snapshot.valuation?.hasData ? (
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
              {snapshot.valuation.conditions.map((condition) => {
                const isHighlight = condition.condition === highlight;
                return (
                  <tr
                    key={condition.condition}
                    className={`border-t border-neutral-100 ${isHighlight ? "bg-brand-50/60" : ""}`}
                  >
                    <td className="px-3 py-2.5">
                      <div className="flex flex-col gap-0.5">
                        <span>
                          <span
                            className={`font-medium ${isHighlight ? "text-brand-700" : "text-neutral-900"}`}
                          >
                            {CONDITION_LABEL[condition.condition]}
                          </span>
                          {isHighlight ? (
                            <span className="ml-1 text-[11px] text-brand-600">· 이 상품</span>
                          ) : null}
                        </span>
                        <span className={`text-[11px] ${valuationBasisTone(condition.basis)}`}>
                          {valuationBasisLabel(condition.basis, condition.sampleCount)}
                        </span>
                      </div>
                    </td>
                    <td className="px-3 py-2.5 text-right font-semibold tabular-nums text-neutral-900">
                      {formatKrw(condition.fairPrice)}
                    </td>
                    <td className="px-3 py-2.5 text-right tabular-nums text-success">
                      {formatKrw(condition.sellPrice)}
                    </td>
                    <td className="px-3 py-2.5 text-right tabular-nums text-danger">
                      {formatKrw(condition.buyPrice)}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      ) : (
        <p className="rounded-lg bg-neutral-50 px-4 py-3 text-sm text-neutral-500">
          미개봉 체결이 {snapshot.minimumSamples}건 쌓이면 상태별 추정 시세도 보여드릴게요.
        </p>
      )}
    </Card>
  );
}
