import Link from "next/link";
import type { TrendingSet } from "@entities/pricing";
import { Badge, Text } from "@shared/ui";
import { formatKrw } from "@shared/lib";

export interface TrendingSetsProps {
  readonly items: readonly TrendingSet[];
}

/**
 * 체결 거래량 기준 인기 세트 랭킹. (백로그 13.4)
 * 데이터 로딩은 상위(view)에서 수행하고, 본 위젯은 표현만 담당한다.
 */
export function TrendingSets({ items }: TrendingSetsProps) {
  if (items.length === 0) {
    return (
      <div className="flex flex-col items-center gap-2 rounded-2xl border border-dashed border-neutral-200 px-6 py-12 text-center">
        <span aria-hidden="true" className="text-3xl">
          📈
        </span>
        <Text tone="secondary" weight="medium">
          아직 거래 데이터가 충분하지 않아요
        </Text>
        <Text tone="muted" size="sm">
          거래가 쌓이면 인기 세트가 여기에 표시됩니다.
        </Text>
      </div>
    );
  }

  return (
    <ol className="flex flex-col divide-y divide-neutral-100 overflow-hidden rounded-2xl border border-neutral-200/70 bg-white">
      {items.map((set, index) => (
        <li key={set.setNumber}>
          <Link
            href={`/prices?set=${encodeURIComponent(set.setNumber)}`}
            className="flex items-center gap-4 px-5 py-4 transition-colors hover:bg-neutral-50"
          >
            <span className="w-6 shrink-0 text-center text-lg font-bold tabular-nums text-brand-500">
              {index + 1}
            </span>
            {set.imageUrl !== null ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                src={set.imageUrl}
                alt={set.name}
                className="h-12 w-12 shrink-0 rounded-lg border border-neutral-200/70 object-cover"
              />
            ) : (
              <span
                aria-hidden="true"
                className="flex h-12 w-12 shrink-0 items-center justify-center rounded-lg bg-neutral-100 text-xl"
              >
                🧱
              </span>
            )}
            <div className="flex min-w-0 flex-col gap-0.5">
              <Text weight="medium" className="truncate">
                {set.name}
              </Text>
              <Text tone="muted" size="sm">
                #{set.setNumber} · 평균 {formatKrw(set.averagePrice)}
              </Text>
            </div>
            <Badge tone="brand" className="ml-auto shrink-0">
              거래 {set.tradeCount.toLocaleString("ko-KR")}건
            </Badge>
          </Link>
        </li>
      ))}
    </ol>
  );
}
