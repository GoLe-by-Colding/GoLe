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
  /**
   * 강조할 상품 상태(매물 상태). 매물 상세에서 넘긴다 — 이 상태의 추정 시세를 맨 위에 따로 보여 주고
   * 상태별 표의 해당 행을 하이라이트한다. 미개봉 최근 체결가를 이 매물의 가격처럼 읽히게 하지 않기 위해서다.
   */
  readonly highlight?: SetCondition;
}

/**
 * 스냅샷의 헤드라인(최근 체결가·차트·관측 목록)은 백엔드가 **미개봉 체결만** 모은 값이다
 * (`PricingService.getSnapshot`). 상태별 추정 시세만 모든 등급 체결을 쓴다. 그래서 화면 문구도
 * "미개봉 최근 체결가"와 "상태별 추정 시세"를 나눠 부른다.
 *
 * 빠른 판매·구매 추정은 추정 시세에 고정 스프레드(×0.96·×1.05, `PriceValuation`)를 곱한 참고값이라
 * 실제 체결가나 지금 받을 수 있는 입찰가가 아니다. 구매 입찰의 실제 "즉시 판매" 기능과 이름이 겹치지 않게 한다.
 */
const QUICK_PRICE_NOTE =
  "빠른 판매·구매 추정은 추정 시세에서 계산한 참고값이에요. 실제 체결가나 지금 받을 수 있는 입찰가가 아니에요.";

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
      <Card padded aria-live="polite" aria-busy="true" className="flex flex-col gap-3">
        <Skeleton className="h-5 w-28" />
        <Skeleton className="h-48 w-full rounded-lg" />
      </Card>
    );
  }

  if (result.failed || result.snapshot === null) {
    return (
      <Card padded aria-live="polite" aria-busy="false">
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
      <Card padded aria-live="polite" aria-busy="false">
        <EmptyState
          variant="inline"
          title="아직 체결 시세가 없어요"
          description="GoLe에서 구매가 확정되면 실제 체결가가 쌓이고 시세가 시작돼요."
        />
      </Card>
    );
  }

  const latest = snapshot.statistics?.latestPrice ?? snapshot.observations[0]?.price ?? null;
  const latestAt = snapshot.observations[0]?.executedAt ?? null;

  if (snapshot.state === "OBSERVATIONS_ONLY") {
    return (
      <Card padded aria-live="polite" aria-busy="false" className="flex flex-col gap-4">
        <div className="flex items-center justify-between gap-3">
          <span className="text-base font-bold text-neutral-900">GoLe 시세</span>
          <span className="flex flex-wrap justify-end gap-2">
            {evidenceWarning === null ? null : <Badge tone="warning">{evidenceWarning}</Badge>}
            <Badge tone="warning">체결 {snapshot.sampleCount}건 · 참고 단계</Badge>
          </span>
        </div>
        <div className="flex flex-col gap-1.5">
          <span className="text-xs font-medium text-neutral-500">
            미개봉 최근 체결가{latestAt === null ? "" : ` · ${formatDate(latestAt)}`}
          </span>
          <span className="text-[28px] leading-none font-bold tracking-[-0.02em] text-neutral-900 tabular-nums">
            {latest === null ? "—" : formatKrw(latest)}
          </span>
        </div>
        <p className="text-sm leading-relaxed text-neutral-600">
          실제 체결가는 확인됐어요. {snapshot.minimumSamples}건이 쌓이기 전까지 등락률과 상태별
          추정가는 표시하지 않아요.
          {highlight !== undefined && highlight !== "new_sealed"
            ? ` 이 상품은 ${CONDITION_LABEL[highlight]} 상태라 미개봉 체결가와 다를 수 있어요.`
            : ""}
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

  const valuation = snapshot.valuation?.hasData ? snapshot.valuation : null;
  const highlighted =
    highlight === undefined
      ? null
      : (valuation?.conditions.find((condition) => condition.condition === highlight) ?? null);

  return (
    <Card padded aria-live="polite" aria-busy="false" className="flex flex-col gap-5">
      <div className="flex items-center justify-between gap-3">
        <span className="text-base font-bold text-neutral-900">GoLe 시세</span>
        <span className="flex items-center gap-2">
          {evidenceWarning === null ? null : <Badge tone="warning">{evidenceWarning}</Badge>}
          <span className="font-mono text-xs text-neutral-400">#{setNumber}</span>
        </span>
      </div>

      {highlighted === null ? null : (
        <div className="flex flex-col gap-1.5 rounded-xl border border-brand-100 bg-brand-50/60 px-4 py-3.5">
          <span className="text-xs font-semibold text-brand-700">
            이 상품 상태 추정 시세 · {CONDITION_LABEL[highlighted.condition]}
          </span>
          <span className="text-[28px] leading-none font-bold tracking-[-0.02em] text-neutral-900 tabular-nums">
            {formatKrw(highlighted.fairPrice)}
          </span>
          <span className="text-xs leading-relaxed text-neutral-500">
            <span className={valuationBasisTone(highlighted.basis)}>
              {valuationBasisLabel(highlighted.basis, highlighted.sampleCount)}
            </span>{" "}
            · 체결가가 아니라 체결 표본으로 계산한 추정값이에요.
          </span>
        </div>
      )}

      <div className="flex flex-col gap-1.5">
        <span className="text-xs font-medium text-neutral-500">
          미개봉 최근 체결가{latestAt === null ? "" : ` · ${formatDate(latestAt)}`}
        </span>
        <div className="flex flex-wrap items-baseline gap-x-2.5 gap-y-1">
          <span
            className={`${highlighted === null ? "text-[28px]" : "text-xl"} leading-none font-bold tracking-[-0.02em] text-neutral-900 tabular-nums`}
          >
            {latest === null ? "—" : formatKrw(latest)}
          </span>
          {ratio === null ? null : (
            <span
              className={`inline-flex items-center gap-1 text-sm font-semibold tabular-nums ${up ? "text-rise" : "text-fall"}`}
            >
              <TrendCaret up={up} />
              {Math.abs(ratio * 100).toFixed(1)}%
              <span className="text-xs font-normal text-neutral-400">첫 체결 대비</span>
            </span>
          )}
          <span className="text-xs text-neutral-400">
            {evidenceWarning === null ? "검증된 체결가" : "참고용 체결가"}
          </span>
        </div>
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

      {valuation === null ? (
        <p className="rounded-lg bg-neutral-50 px-4 py-3 text-sm text-neutral-500">
          미개봉 체결이 {snapshot.minimumSamples}건 쌓이면 상태별 추정 시세도 보여드릴게요.
        </p>
      ) : (
        <div className="flex flex-col gap-2">
          <span className="text-sm font-bold text-neutral-900">상태별 추정 시세</span>
          {/* 휴대폰 폭에서 가로 스크롤 표 대신 행마다 추정 시세와 빠른 판매·구매 추정을 두 줄로 쌓는다. */}
          <ul className="divide-y divide-neutral-100 rounded-lg border border-neutral-200">
            {valuation.conditions.map((condition) => {
              const isHighlight = condition.condition === highlight;
              return (
                <li
                  key={condition.condition}
                  className={`flex items-start justify-between gap-3 px-3 py-2.5 text-sm ${isHighlight ? "bg-brand-50/60" : ""}`}
                >
                  <div className="flex min-w-0 flex-col gap-0.5">
                    <span>
                      <span
                        className={`font-medium ${isHighlight ? "text-brand-700" : "text-neutral-900"}`}
                      >
                        {CONDITION_LABEL[condition.condition]}
                      </span>
                      {isHighlight ? (
                        <span className="ml-1 text-[11px] whitespace-nowrap text-brand-600">
                          · 이 상품
                        </span>
                      ) : null}
                    </span>
                    <span className={`text-[11px] ${valuationBasisTone(condition.basis)}`}>
                      {valuationBasisLabel(condition.basis, condition.sampleCount)}
                    </span>
                  </div>
                  <div className="flex shrink-0 flex-col items-end gap-0.5 text-right">
                    <span className="font-semibold whitespace-nowrap tabular-nums text-neutral-900">
                      {formatKrw(condition.fairPrice)}
                    </span>
                    <span className="text-[11px] whitespace-nowrap tabular-nums text-neutral-500">
                      빠른 판매 {formatKrw(condition.sellPrice)}
                    </span>
                    <span className="text-[11px] whitespace-nowrap tabular-nums text-neutral-500">
                      빠른 구매 {formatKrw(condition.buyPrice)}
                    </span>
                  </div>
                </li>
              );
            })}
          </ul>
          <p className="text-xs leading-relaxed text-neutral-400">{QUICK_PRICE_NOTE}</p>
        </div>
      )}
    </Card>
  );
}
