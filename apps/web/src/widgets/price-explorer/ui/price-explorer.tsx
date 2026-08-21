"use client";

import { useMemo, useState } from "react";
import {
  CONDITION_LABEL,
  valuationBasisLabel,
  valuationBasisTone,
  type PricePoint,
  type PriceValuation,
} from "@entities/pricing";
import { Badge, Card, LineChart, MediaImage } from "@shared/ui";
import { formatKrw } from "@shared/lib";

export interface PriceBoardItem {
  readonly setNumber: string;
  readonly name: string;
  readonly theme: string;
  readonly imageUrl: string | null;
  /** 체결 시계열(오름차순). */
  readonly points: readonly PricePoint[];
  readonly valuation: PriceValuation | null;
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

function lastPrice(item: PriceBoardItem): number {
  return item.points.length > 0 ? item.points[item.points.length - 1]!.price : 0;
}

function lastTime(item: PriceBoardItem): number {
  return item.points.length > 0
    ? new Date(item.points[item.points.length - 1]!.executedAt).getTime()
    : 0;
}

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

function filterByPeriod(points: readonly PricePoint[], days: number): readonly PricePoint[] {
  if (days === Number.MAX_SAFE_INTEGER) {
    return points;
  }
  const cutoff = Date.now() - days * 24 * 60 * 60 * 1000;
  const filtered = points.filter((p) => new Date(p.executedAt).getTime() >= cutoff);
  return filtered.length >= 2 ? filtered : points;
}

function changeRatio(points: readonly PricePoint[]): number {
  if (points.length < 2) return 0;
  const first = points[0]!.price;
  const last = points[points.length - 1]!.price;
  return first === 0 ? 0 : (last - first) / first;
}

function formatDate(iso: string): string {
  const d = new Date(iso);
  return `${String(d.getFullYear()).slice(2)}.${String(d.getMonth() + 1).padStart(2, "0")}.${String(d.getDate()).padStart(2, "0")}`;
}

/** 기간 변동 표기 — 크림처럼 금액과 %를 함께, 색은 한국 관례(상승 빨강/하락 파랑). */
function ChangeBadge({ ratio, diff }: { readonly ratio: number; readonly diff?: number }) {
  const up = ratio >= 0;
  const cls = up ? "text-rise" : "text-fall";
  const sign = up ? "▲" : "▼";
  return (
    <span className={`text-sm font-bold tabular-nums ${cls}`}>
      {sign} {diff !== undefined ? `${Math.abs(diff).toLocaleString("ko-KR")} ` : ""}(
      {Math.abs(ratio * 100).toFixed(1)}%)
    </span>
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
        return copy.sort((a, b) => b.points.length - a.points.length);
    }
  }, [items, sort]);
  const firstWithData = sorted.find((i) => i.points.length >= 2) ?? sorted[0];
  const requested = sorted.find((i) => i.setNumber === initialSetNumber);
  const [selected, setSelected] = useState<string>(
    requested?.setNumber ?? firstWithData?.setNumber ?? "",
  );
  const [period, setPeriod] = useState<Period>("6M");

  const current = sorted.find((i) => i.setNumber === selected) ?? firstWithData;
  const periodDays = PERIODS.find((p) => p.value === period)!.days;
  const series = current ? filterByPeriod(current.points, periodDays) : [];
  const latest = series.length > 0 ? series[series.length - 1]!.price : null;
  const ratio = changeRatio(series);
  const seriesPrices = series.map((p) => p.price);
  const high = seriesPrices.length > 0 ? Math.max(...seriesPrices) : null;
  const low = seriesPrices.length > 0 ? Math.min(...seriesPrices) : null;
  const recent = [...series].slice(-8).reverse();

  if (!current) {
    return null;
  }

  function handleSelect(setNumber: string) {
    setSelected(setNumber);
    const url = new URL(window.location.href);
    url.searchParams.set("set", setNumber);
    window.history.replaceState(window.history.state, "", url);
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[300px_1fr]">
      {/* 세트 목록 + 정렬 */}
      <div className="flex flex-col gap-2 lg:sticky lg:top-20 lg:self-start">
        <div className="flex items-center justify-between px-1">
          <span className="text-sm font-bold text-neutral-900">세트 시세</span>
          <select
            value={sort}
            onChange={(e) => setSort(e.target.value as SortKey)}
            aria-label="정렬"
            className="rounded-md border border-neutral-200 bg-white px-2 py-1 text-xs font-medium text-neutral-700 focus-visible:outline-2 focus-visible:outline-brand-400"
          >
            {SORTS.map((s) => (
              <option key={s.value} value={s.value}>
                {s.label}
              </option>
            ))}
          </select>
        </div>
        <ol className="flex max-h-[360px] flex-col gap-1 overflow-y-auto rounded-lg border border-neutral-200 bg-white p-2 lg:max-h-[560px]">
          {sorted.map((item) => {
            const active = item.setNumber === current.setNumber;
            const itemLatest =
              item.points.length > 0 ? item.points[item.points.length - 1]!.price : null;
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
                    <span className="text-xs tabular-nums text-neutral-500">
                      {itemLatest !== null ? formatKrw(itemLatest) : "데이터 없음"}
                    </span>
                  </span>
                </button>
              </li>
            );
          })}
        </ol>
      </div>

      {/* 상세 */}
      <Card padded className="flex flex-col gap-5">
        <div className="flex items-center gap-3">
          <MediaImage
            src={current.imageUrl}
            alt=""
            className="h-14 w-14 shrink-0 rounded-md border border-neutral-200 object-cover"
            fallback="SET"
            fallbackClassName="text-[10px] tracking-wide"
          />
          <div className="flex flex-col">
            <span className="text-lg font-bold text-neutral-900">{current.name}</span>
            <span className="font-mono text-xs text-neutral-500">
              #{current.setNumber} · {current.theme}
            </span>
          </div>
        </div>

        {latest !== null ? (
          <>
            <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
              <span className="text-3xl font-extrabold tracking-tight text-neutral-900">
                {formatKrw(latest)}
              </span>
              <span>
                <ChangeBadge
                  ratio={ratio}
                  {...(series.length >= 2 ? { diff: latest - series[0]!.price } : {})}
                />
              </span>
              <span className="text-xs text-neutral-400">
                {PERIODS.find((p) => p.value === period)!.label} 기준
              </span>
            </div>

            {/* 기간 탭 */}
            <div className="flex border-b border-neutral-200">
              {PERIODS.map((p) => (
                <button
                  key={p.value}
                  type="button"
                  onClick={() => setPeriod(p.value)}
                  aria-pressed={period === p.value}
                  className={`flex-1 border-b-2 px-3 py-2 text-sm font-semibold transition-colors ${
                    period === p.value
                      ? "border-brand-600 text-brand-700"
                      : "border-transparent text-neutral-500 hover:border-neutral-300 hover:text-neutral-800"
                  }`}
                >
                  {p.label}
                </button>
              ))}
            </div>

            <div className="flex flex-col gap-2">
              <div className="flex items-center justify-between gap-3">
                <span className="text-sm font-semibold text-neutral-900">체결가 추이</span>
                <span className="text-xs text-neutral-400">차트를 가리켜 날짜별 가격 확인</span>
              </div>
              <LineChart
                points={series.map((p) => ({ value: p.price, label: formatDate(p.executedAt) }))}
                formatValue={formatKrw}
                emptyText="시세 데이터가 부족해요"
              />
            </div>

            {/* 기간 통계 스트립 */}
            <dl className="grid grid-cols-3 divide-x divide-neutral-200 border-y border-neutral-200">
              {[
                { label: "거래량", value: `${series.length}건` },
                { label: "기간 고가", value: high !== null ? formatKrw(high) : "—" },
                { label: "기간 저가", value: low !== null ? formatKrw(low) : "—" },
              ].map((s) => (
                <div key={s.label} className="flex flex-col gap-0.5 px-3 py-3">
                  <dt className="text-xs text-neutral-400">{s.label}</dt>
                  <dd className="text-sm font-bold tabular-nums text-neutral-900">{s.value}</dd>
                </div>
              ))}
            </dl>

            {/* 상태별 감가 · 매수/매도 */}
            {current.valuation?.hasData ? (
              <div className="flex flex-col gap-2">
                <div className="flex items-center justify-between">
                  <span className="text-sm font-bold text-neutral-900">상태별 시세</span>
                  <span className="text-xs text-neutral-400">최근 체결가 기준 추정</span>
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
                      {current.valuation.conditions.map((c) => (
                        <tr
                          key={c.condition}
                          className="border-t border-neutral-100 hover:bg-neutral-50"
                        >
                          <td className="px-3 py-2.5">
                            <div className="flex flex-col gap-0.5">
                              <span className="font-medium text-neutral-900">
                                {CONDITION_LABEL[c.condition]}
                              </span>
                              <span className={`text-[11px] ${valuationBasisTone(c.basis)}`}>
                                {valuationBasisLabel(c.basis, c.sampleCount)}
                              </span>
                            </div>
                          </td>
                          <td className="px-3 py-2.5 text-right tabular-nums text-neutral-500">
                            {c.depreciationPct === 0 ? "—" : `-${c.depreciationPct}%`}
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
                      ))}
                    </tbody>
                  </table>
                </div>
                <p className="text-xs leading-relaxed text-neutral-400">
                  즉시판매(매도)는 판매자가 바로 받는 가격, 즉시구매(매수)는 구매자가 바로 내는
                  가격이에요. 상태가 낮을수록 감가가 적용됩니다.
                </p>
              </div>
            ) : null}

            {/* 최근 체결 내역 */}
            {recent.length > 0 ? (
              <div className="flex flex-col gap-2">
                <span className="text-sm font-bold text-neutral-900">최근 체결 내역</span>
                <ul className="divide-y divide-neutral-100 overflow-hidden rounded-lg border border-neutral-200">
                  {recent.map((p, i) => (
                    <li
                      key={`${p.executedAt}-${i}`}
                      className="flex items-center justify-between px-3 py-2 text-sm"
                    >
                      <span className="text-neutral-500">{formatDate(p.executedAt)}</span>
                      <span className="font-semibold tabular-nums text-neutral-900">
                        {formatKrw(p.price)}
                      </span>
                    </li>
                  ))}
                </ul>
              </div>
            ) : null}
          </>
        ) : (
          <div className="flex h-40 items-center justify-center">
            <Badge tone="neutral">체결 데이터 없음</Badge>
          </div>
        )}
      </Card>
    </div>
  );
}
