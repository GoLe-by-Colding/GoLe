"use client";

import { useMemo, useState } from "react";
import {
  CONDITION_LABEL,
  filterPricePointsByPeriod,
  priceEvidenceWarning,
  valuationBasisLabel,
  valuationBasisTone,
  type PricePoint,
  type PriceSnapshot,
} from "@entities/pricing";
import { formatKrw, formatKrwCompact } from "@shared/lib";
import { Badge, Button, Card, EmptyState, LineChart, MediaImage } from "@shared/ui";
import { SetPriceActions } from "./set-price-actions";

export interface PriceBoardItem {
  readonly setNumber: string;
  readonly name: string;
  readonly theme: string;
  readonly imageUrl: string | null;
  /** 미개봉 체결 시계열(오름차순). null은 조회 실패, []는 실제 빈 데이터다. */
  readonly points: readonly PricePoint[] | null;
  /** null은 조회 실패다. EMPTY 상태와 섞지 않는다. */
  readonly snapshot: PriceSnapshot | null;
}

export interface PriceExplorerProps {
  readonly items: readonly PriceBoardItem[];
  readonly initialSetNumber?: string | undefined;
}

type Period = "1M" | "6M" | "1Y" | "ALL";
type SortKey = "popular" | "recent" | "price_desc" | "price_asc";

const SORTS: ReadonlyArray<{ readonly value: SortKey; readonly label: string }> = [
  { value: "popular", label: "인기순" },
  { value: "recent", label: "최신 거래순" },
  { value: "price_desc", label: "가격 높은순" },
  { value: "price_asc", label: "가격 낮은순" },
];

const PERIODS: ReadonlyArray<{
  readonly value: Period;
  readonly label: string;
  readonly days: number;
}> = [
  { value: "1M", label: "1개월", days: 31 },
  { value: "6M", label: "6개월", days: 183 },
  { value: "1Y", label: "1년", days: 366 },
  { value: "ALL", label: "전체", days: Number.MAX_SAFE_INTEGER },
];

function latestObservation(item: PriceBoardItem): PricePoint | null {
  return item.snapshot?.observations[0] ?? null;
}

function lastPrice(item: PriceBoardItem): number {
  return item.snapshot?.statistics?.latestPrice ?? latestObservation(item)?.price ?? 0;
}

function lastTime(item: PriceBoardItem): number {
  const latest = latestObservation(item);
  return latest === null ? 0 : new Date(latest.executedAt).getTime();
}

function changeRatio(points: readonly PricePoint[]): number | null {
  if (points.length < 2) return null;
  const first = points[0]!.price;
  const last = points[points.length - 1]!.price;
  return first === 0 ? null : (last - first) / first;
}

function formatDate(iso: string): string {
  const date = new Date(iso);
  return `${String(date.getFullYear()).slice(2)}.${String(date.getMonth() + 1).padStart(2, "0")}.${String(date.getDate()).padStart(2, "0")}`;
}

function ChangeBadge({ ratio, diff }: { readonly ratio: number; readonly diff: number }) {
  const up = ratio >= 0;
  return (
    <span className={`text-sm font-bold tabular-nums ${up ? "text-rise" : "text-fall"}`}>
      {up ? "▲" : "▼"} {Math.abs(diff).toLocaleString("ko-KR")} ({Math.abs(ratio * 100).toFixed(1)}
      %)
    </span>
  );
}

function EvidenceBadge({ warning }: { readonly warning: string | null }) {
  return warning === null ? null : <Badge tone="warning">{warning}</Badge>;
}

function ObservationList({ points }: { readonly points: readonly PricePoint[] }) {
  if (points.length === 0) return null;
  return (
    <div className="flex flex-col gap-2">
      <span className="text-sm font-bold text-neutral-900">최근 체결 내역</span>
      <ul className="divide-y divide-neutral-100 overflow-hidden rounded-lg border border-neutral-200">
        {points.slice(0, 8).map((point, index) => (
          <li
            key={`${point.executedAt}-${point.price}-${index}`}
            className="flex items-center justify-between gap-4 px-3 py-2 text-sm"
          >
            <span className="flex min-w-0 items-center gap-2 text-neutral-500">
              <span>{formatDate(point.executedAt)}</span>
              <span className="truncate text-xs text-neutral-400">
                {CONDITION_LABEL[point.condition]}
              </span>
            </span>
            <span className="shrink-0 font-semibold tabular-nums text-neutral-900">
              {formatKrw(point.price)}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

export function PriceExplorer({ items, initialSetNumber }: PriceExplorerProps) {
  const [sort, setSort] = useState<SortKey>("popular");
  const sorted = useMemo(() => {
    const copy = [...items];
    switch (sort) {
      case "recent":
        return copy.sort((a, b) => lastTime(b) - lastTime(a));
      case "price_desc":
        return copy.sort((a, b) => lastPrice(b) - lastPrice(a));
      case "price_asc":
        return copy.sort((a, b) => lastPrice(a) - lastPrice(b));
      case "popular":
      default:
        return copy.sort(
          (a, b) => (b.snapshot?.sampleCount ?? -1) - (a.snapshot?.sampleCount ?? -1),
        );
    }
  }, [items, sort]);
  const firstWithData =
    sorted.find((item) => item.snapshot?.state === "ESTABLISHED") ??
    sorted.find((item) => (item.snapshot?.sampleCount ?? 0) > 0) ??
    sorted[0];
  const requested = sorted.find((item) => item.setNumber === initialSetNumber);
  const [selected, setSelected] = useState<string>(
    requested?.setNumber ?? firstWithData?.setNumber ?? "",
  );
  const [period, setPeriod] = useState<Period>("6M");

  // 서버가 같은 /prices 라우트의 다른 set 쿼리를 렌더할 때 부모가 initialSetNumber를
  // key로 사용해 이 컴포넌트를 다시 마운트한다. 화면 내부 선택은 history만 바꾸므로
  // 여기서는 로컬 selected를 그대로 따른다.
  const current = sorted.find((item) => item.setNumber === selected) ?? firstWithData;
  const periodDays = PERIODS.find((item) => item.value === period)!.days;
  const series = current?.points
    ? filterPricePointsByPeriod(current.points, periodDays)
    : ([] as readonly PricePoint[]);
  const ratio = changeRatio(series);
  const seriesPrices = series.map((point) => point.price);
  const high = seriesPrices.length > 0 ? Math.max(...seriesPrices) : null;
  const low = seriesPrices.length > 0 ? Math.min(...seriesPrices) : null;

  if (!current) return null;

  function handleSelect(setNumber: string) {
    setSelected(setNumber);
    const url = new URL(window.location.href);
    url.searchParams.set("set", setNumber);
    window.history.replaceState(window.history.state, "", url);
  }

  const snapshot = current.snapshot;
  const evidenceWarning = snapshot === null ? null : priceEvidenceWarning(snapshot.provenance);
  const marketLatest =
    snapshot?.statistics?.latestPrice ?? snapshot?.observations[0]?.price ?? null;
  const periodLabel = PERIODS.find((item) => item.value === period)!.label;

  return (
    <div className="grid gap-6 lg:grid-cols-[300px_1fr]">
      <div className="flex flex-col gap-2 lg:sticky lg:top-20 lg:self-start">
        <div className="flex items-center justify-between px-1">
          <span className="text-sm font-bold text-neutral-900">세트 시세</span>
          <select
            value={sort}
            onChange={(event) => setSort(event.target.value as SortKey)}
            aria-label="정렬"
            className="rounded-md border border-neutral-200 bg-white px-2 py-1 text-xs font-medium text-neutral-700 focus-visible:outline-2 focus-visible:outline-brand-400"
          >
            {SORTS.map((item) => (
              <option key={item.value} value={item.value}>
                {item.label}
              </option>
            ))}
          </select>
        </div>
        <ol className="flex max-h-[360px] flex-col gap-1 overflow-y-auto rounded-lg border border-neutral-200 bg-white p-2 lg:max-h-[560px]">
          {sorted.map((item) => {
            const active = item.setNumber === current.setNumber;
            const itemLatest = lastPrice(item);
            const stateLabel =
              item.snapshot === null
                ? "불러오기 실패"
                : item.snapshot.state === "EMPTY"
                  ? "체결 전"
                  : item.snapshot.state === "OBSERVATIONS_ONLY"
                    ? `${formatKrw(itemLatest)} · 참고 ${item.snapshot.sampleCount}건`
                    : formatKrw(itemLatest);
            return (
              <li key={item.setNumber}>
                <button
                  type="button"
                  onClick={() => handleSelect(item.setNumber)}
                  aria-pressed={active}
                  className={`flex w-full items-center gap-3 rounded-md border px-3 py-2.5 text-left transition-colors ${
                    active
                      ? "border-brand-200 bg-brand-50"
                      : "border-transparent hover:bg-neutral-50"
                  }`}
                >
                  <MediaImage
                    src={item.imageUrl}
                    alt=""
                    className="h-10 w-10 shrink-0 rounded-md border border-neutral-200 object-cover"
                    fallback="SET"
                    fallbackClassName="text-[9px] tracking-wide"
                  />
                  <span className="flex min-w-0 flex-col">
                    <span
                      className={`truncate text-sm font-semibold ${active ? "text-brand-700" : "text-neutral-900"}`}
                    >
                      {item.name}
                    </span>
                    <span className="text-xs tabular-nums text-neutral-500">{stateLabel}</span>
                  </span>
                </button>
              </li>
            );
          })}
        </ol>
      </div>

      <Card padded className="flex flex-col gap-5">
        <div className="flex items-center gap-3">
          <MediaImage
            src={current.imageUrl}
            alt=""
            className="h-14 w-14 shrink-0 rounded-md border border-neutral-200 object-cover"
            fallback="SET"
            fallbackClassName="text-[10px] tracking-wide"
          />
          <div className="flex min-w-0 flex-1 flex-col">
            <span className="truncate text-lg font-bold text-neutral-900">{current.name}</span>
            <span className="font-mono text-xs text-neutral-500">
              #{current.setNumber} · {current.theme}
            </span>
          </div>
          {snapshot === null ? null : <EvidenceBadge warning={evidenceWarning} />}
        </div>

        <SetPriceActions setNumber={current.setNumber} setName={current.name} />

        {snapshot === null ? (
          <EmptyState
            variant="inline"
            title="시세를 불러오지 못했어요"
            description="실제 체결 데이터가 없는 상태와 구분되는 일시적인 조회 오류예요."
            action={
              <Button size="sm" variant="secondary" onClick={() => window.location.reload()}>
                다시 시도
              </Button>
            }
          />
        ) : snapshot.state === "EMPTY" ? (
          <EmptyState
            variant="inline"
            title="아직 체결 시세가 없어요"
            description="플랫폼 결제가 열리고 구매확정된 거래가 생기면 검증된 체결가가 여기에 쌓여요."
          />
        ) : snapshot.state === "OBSERVATIONS_ONLY" ? (
          <div className="flex flex-col gap-5">
            <div className="flex flex-wrap items-center gap-3">
              <span className="text-3xl font-extrabold tracking-tight text-neutral-900">
                {marketLatest === null ? "—" : formatKrw(marketLatest)}
              </span>
              <Badge tone="warning">체결 {snapshot.sampleCount}건 · 참고 단계</Badge>
            </div>
            <p className="rounded-lg bg-warning-soft px-4 py-3 text-sm leading-relaxed text-neutral-700">
              실제 체결가는 확인됐지만 아직 {snapshot.minimumSamples}건 미만이라
              등락률·고저가·상태별 추정 시세는 계산하지 않아요.
            </p>
            <ObservationList points={snapshot.observations} />
          </div>
        ) : (
          <>
            <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
              <span className="text-3xl font-extrabold tracking-tight text-neutral-900">
                {marketLatest === null ? "—" : formatKrw(marketLatest)}
              </span>
              {ratio !== null && series.length >= 2 ? (
                <ChangeBadge
                  ratio={ratio}
                  diff={series[series.length - 1]!.price - series[0]!.price}
                />
              ) : null}
              <span className="text-xs text-neutral-400">
                {series.length >= 2 ? `${periodLabel} 기준` : "최근 전체 체결가"}
              </span>
            </div>

            <div className="flex border-b border-neutral-200">
              {PERIODS.map((item) => (
                <button
                  key={item.value}
                  type="button"
                  onClick={() => setPeriod(item.value)}
                  aria-pressed={period === item.value}
                  className={`flex-1 border-b-2 px-3 py-2 text-sm font-semibold transition-colors ${
                    period === item.value
                      ? "border-brand-600 text-brand-700"
                      : "border-transparent text-neutral-500 hover:border-neutral-300 hover:text-neutral-800"
                  }`}
                >
                  {item.label}
                </button>
              ))}
            </div>

            <div className="flex flex-col gap-2">
              <div className="flex items-center justify-between gap-3">
                <span className="text-sm font-semibold text-neutral-900">체결가 추이</span>
                <span className="text-xs text-neutral-400">
                  {current.points === null
                    ? "차트 조회 실패"
                    : series.length === 0
                      ? `${periodLabel} 체결 없음`
                      : series.length === 1
                        ? `${periodLabel} 체결 1건`
                        : "차트를 가리켜 날짜별 가격 확인"}
                </span>
              </div>
              <LineChart
                points={series.map((point) => ({
                  value: point.price,
                  label: formatDate(point.executedAt),
                }))}
                formatValue={formatKrw}
                formatAxisValue={formatKrwCompact}
                emptyText={
                  current.points === null
                    ? "차트를 불러오지 못했어요"
                    : series.length === 0
                      ? "이 기간에는 체결이 없어요"
                      : "이 기간에는 체결이 1건뿐이에요"
                }
              />
            </div>

            {series.length >= 2 ? (
              <dl className="grid grid-cols-3 divide-x divide-neutral-200 border-y border-neutral-200">
                {[
                  { label: "거래량", value: `${series.length}건` },
                  { label: "기간 고가", value: high === null ? "—" : formatKrw(high) },
                  { label: "기간 저가", value: low === null ? "—" : formatKrw(low) },
                ].map((item) => (
                  <div key={item.label} className="flex flex-col gap-0.5 px-3 py-3">
                    <dt className="text-xs text-neutral-400">{item.label}</dt>
                    <dd className="text-sm font-bold tabular-nums text-neutral-900">
                      {item.value}
                    </dd>
                  </div>
                ))}
              </dl>
            ) : null}

            {snapshot.valuation?.hasData ? (
              <div className="flex flex-col gap-2">
                <div className="flex items-center justify-between">
                  <span className="text-sm font-bold text-neutral-900">상태별 시세</span>
                  <span className="text-xs text-neutral-400">
                    {evidenceWarning === null
                      ? "검증된 체결가 기준 추정"
                      : "참고용 체결가 기준 추정"}
                  </span>
                </div>
                <div className="overflow-x-auto rounded-lg border border-neutral-200">
                  <table className="w-full min-w-[520px] border-collapse text-sm">
                    <thead>
                      <tr className="bg-neutral-50 text-xs text-neutral-500">
                        <th className="px-3 py-2 text-left font-medium">상태</th>
                        <th className="px-3 py-2 text-right font-medium">감가</th>
                        <th className="px-3 py-2 text-right font-medium">시세</th>
                        <th className="px-3 py-2 text-right font-medium">즉시판매</th>
                        <th className="px-3 py-2 text-right font-medium">즉시구매</th>
                      </tr>
                    </thead>
                    <tbody>
                      {snapshot.valuation.conditions.map((condition) => (
                        <tr
                          key={condition.condition}
                          className="border-t border-neutral-100 hover:bg-neutral-50"
                        >
                          <td className="px-3 py-2.5">
                            <div className="flex flex-col gap-0.5">
                              <span className="font-medium text-neutral-900">
                                {CONDITION_LABEL[condition.condition]}
                              </span>
                              <span
                                className={`text-[11px] ${valuationBasisTone(condition.basis)}`}
                              >
                                {valuationBasisLabel(condition.basis, condition.sampleCount)}
                              </span>
                            </div>
                          </td>
                          <td className="px-3 py-2.5 text-right tabular-nums text-neutral-500">
                            {condition.depreciationPct === 0
                              ? "—"
                              : `-${condition.depreciationPct}%`}
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
                      ))}
                    </tbody>
                  </table>
                </div>
                <p className="text-xs leading-relaxed text-neutral-400">
                  즉시판매는 판매자가 빠르게 거래할 때, 즉시구매는 구매자가 빠르게 구할 때의 참고
                  가격이에요. 실제 거래 조건에 따라 달라질 수 있어요.
                </p>
              </div>
            ) : (
              <p className="rounded-lg bg-neutral-50 px-4 py-3 text-sm text-neutral-500">
                미개봉 기준 체결이 {snapshot.minimumSamples}건 쌓이면 상태별 추정 시세도
                보여드릴게요.
              </p>
            )}

            <ObservationList points={snapshot.observations} />
          </>
        )}
      </Card>
    </div>
  );
}
