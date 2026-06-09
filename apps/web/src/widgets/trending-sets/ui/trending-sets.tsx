import Link from "next/link";
import type { TrendingSet } from "@entities/pricing";
import { Badge, Text } from "@shared/ui";
import { formatKrw } from "@shared/lib";

export interface TrendingSetsProps {
  readonly items: readonly TrendingSet[];
}

export function TrendingSets({ items }: TrendingSetsProps) {
  if (items.length === 0) {
    return (
      <div className="flex flex-col items-center gap-3 rounded-2xl border border-dashed border-neutral-200 px-6 py-14 text-center">
        <span aria-hidden="true" className="text-4xl">📈</span>
        <Text tone="secondary" weight="medium">아직 거래 데이터가 충분하지 않아요</Text>
        <Text tone="muted" size="sm">거래가 쌓이면 인기 세트가 여기에 표시됩니다.</Text>
      </div>
    );
  }

  return (
    <ol className="flex flex-col overflow-hidden rounded-2xl border border-neutral-200/60 bg-white shadow-soft">
      {items.map((set, index) => (
        <li key={set.setNumber} className={index > 0 ? "border-t border-neutral-100" : ""}>
          <Link
            href={`/prices?set=${encodeURIComponent(set.setNumber)}`}
            className="flex items-center gap-4 px-5 py-4 transition-all duration-200 hover:bg-brand-50/40 hover:pl-6"
          >
            <span
              className={`grid h-8 w-8 shrink-0 place-items-center rounded-full text-sm font-bold tabular-nums ${
                index < 3
                  ? "bg-brand-600 text-white shadow-brand"
                  : "bg-neutral-100 text-neutral-500"
              }`}
            >
              {index + 1}
            </span>
            {set.imageUrl !== null ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                src={set.imageUrl}
                alt={set.name}
                className="h-12 w-12 shrink-0 rounded-xl border border-neutral-200/60 object-cover"
              />
            ) : (
              <span
                aria-hidden="true"
                className="grid h-12 w-12 shrink-0 place-items-center rounded-xl bg-neutral-50 text-xl"
              >
                🧱
              </span>
            )}
            <div className="flex min-w-0 flex-col gap-0.5">
              <Text weight="medium" className="truncate">{set.name}</Text>
              <Text tone="muted" size="sm">#{set.setNumber} · 평균 {formatKrw(set.averagePrice)}</Text>
            </div>
            <Badge tone="brand" className="ml-auto shrink-0">
              {set.tradeCount.toLocaleString("ko-KR")}건
            </Badge>
          </Link>
        </li>
      ))}
    </ol>
  );
}
