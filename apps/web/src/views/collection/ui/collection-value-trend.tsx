"use client";

import { useEffect, useState } from "react";
import { fetchCollectionValueHistory, type CollectionValuePoint } from "@entities/collection";
import { ApiError } from "@shared/api";
import { formatKrw, formatKrwCompact } from "@shared/lib";
import { Button, Card, LineChart, Skeleton, Text } from "@shared/ui";

const PERIODS = [
  { days: 30, label: "30일" },
  { days: 90, label: "90일" },
  { days: 365, label: "1년" },
] as const;

type PeriodDays = (typeof PERIODS)[number]["days"];

type HistoryState =
  | {
      readonly key: string;
      readonly status: "ready";
      readonly points: readonly CollectionValuePoint[];
    }
  | { readonly key: string; readonly status: "failed"; readonly message: string };

export interface CollectionValueTrendProps {
  readonly accountId: string;
  /** 보유 목록이 바뀌면 올린다. 서버가 조회 때 오늘 점을 다시 계산하므로 다시 받아야 맞는 값이 보인다. */
  readonly refreshKey: number;
}

/** `YYYY-MM-DD`(서울 기준 날짜)를 시간대 변환 없이 `YY.MM.DD`로 바꾼다. */
function formatPointDate(date: string): string {
  const [year, month, day] = date.split("-");
  return year !== undefined && month !== undefined && day !== undefined
    ? `${year.slice(2)}.${month}.${day}`
    : date;
}

function ValueDelta({ first, last }: { readonly first: number; readonly last: number }) {
  const diff = last - first;
  if (diff === 0) {
    return <span className="text-sm font-bold tabular-nums text-neutral-500">변동 없음</span>;
  }
  const up = diff > 0;
  const ratio = first === 0 ? null : diff / first;
  return (
    <span className={`text-sm font-bold tabular-nums ${up ? "text-rise" : "text-fall"}`}>
      {up ? "▲" : "▼"} {formatKrw(Math.abs(diff))}
      {ratio === null ? "" : ` (${Math.abs(ratio * 100).toFixed(1)}%)`}
    </span>
  );
}

/**
 * "내 레고 자산" 추이 카드. (collection-value-history H3 프론트)
 *
 * 컬렉션 목록·추정가와 따로 불러온다 — 추이 조회가 실패해도 목록과 추가·삭제는 그대로 써야 한다.
 */
export function CollectionValueTrend({ accountId, refreshKey }: CollectionValueTrendProps) {
  const [days, setDays] = useState<PeriodDays>(90);
  const [history, setHistory] = useState<HistoryState | null>(null);
  const [retryNonce, setRetryNonce] = useState(0);
  const requestKey = `${accountId}:${days}:${refreshKey}:${retryNonce}`;

  useEffect(() => {
    const controller = new AbortController();
    fetchCollectionValueHistory(accountId, days, controller.signal)
      .then((points) => {
        if (!controller.signal.aborted) setHistory({ key: requestKey, status: "ready", points });
      })
      .catch((cause: unknown) => {
        if (!controller.signal.aborted) {
          setHistory({
            key: requestKey,
            status: "failed",
            message: cause instanceof ApiError ? cause.message : "잠시 후 다시 시도해 주세요.",
          });
        }
      });
    return () => controller.abort();
  }, [accountId, days, requestKey]);

  // 기간을 바꾸는 동안에는 직전 차트를 남겨 둔다 — 빈 칸으로 깜빡이는 것보다 덜 거슬린다.
  const current = history?.key === requestKey ? history : null;
  const shown = current ?? (history?.status === "ready" ? history : null);
  const loading = current === null;
  const points = shown?.status === "ready" ? shown.points : [];
  const first = points[0];
  const last = points[points.length - 1];
  const periodLabel = PERIODS.find((period) => period.days === days)?.label ?? `${days}일`;

  return (
    <Card padded className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-base font-bold text-neutral-900">내 레고 자산 추이</h2>
        <div
          className="flex rounded-md border border-neutral-200 p-0.5"
          role="group"
          aria-label="기간"
        >
          {PERIODS.map((period) => (
            <button
              key={period.days}
              type="button"
              aria-pressed={days === period.days}
              onClick={() => setDays(period.days)}
              className={`rounded px-3 py-1 text-sm font-semibold transition-colors ${
                days === period.days
                  ? "bg-brand-600 text-white"
                  : "text-neutral-500 hover:bg-neutral-100 hover:text-neutral-900"
              }`}
            >
              {period.label}
            </button>
          ))}
        </div>
      </div>

      {shown === null && loading ? (
        <div className="flex flex-col gap-3" aria-busy="true">
          <Skeleton className="h-7 w-40" />
          <Skeleton className="h-[200px] w-full rounded-lg" />
        </div>
      ) : current?.status === "failed" ? (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg bg-neutral-50 px-4 py-3">
          <Text size="sm" tone="secondary">
            추이를 불러오지 못했어요. {current.message}
          </Text>
          <Button variant="ghost" size="sm" onClick={() => setRetryNonce((value) => value + 1)}>
            다시 시도
          </Button>
        </div>
      ) : (
        <div className="flex flex-col gap-3" aria-busy={loading || undefined}>
          {/* 지금 값은 바로 위 "보유 추정가"가 보여준다. 여기서는 기간 첫 점 대비 증감만 적는다. */}
          {first !== undefined && last !== undefined && points.length >= 2 ? (
            <div className="flex flex-wrap items-baseline gap-x-2 gap-y-1">
              <ValueDelta first={first.ownedValue} last={last.ownedValue} />
              <span className="text-xs text-neutral-400">
                {formatPointDate(first.date)} 대비 · 최근 {periodLabel}
              </span>
            </div>
          ) : null}
          <LineChart
            height={200}
            points={points.map((point) => ({
              value: point.ownedValue,
              label: formatPointDate(point.date),
            }))}
            formatValue={formatKrw}
            formatAxisValue={formatKrwCompact}
            emptyText="내일부터 추이가 쌓여요"
          />
          {last !== undefined && last.pricedCount < last.ownedCount ? (
            <Text size="sm" tone="muted">
              시세가 잡힌 세트 {last.pricedCount}/{last.ownedCount}개 기준이에요. 시세가 없는 세트는
              0원으로 계산돼요.
            </Text>
          ) : null}
        </div>
      )}
    </Card>
  );
}
