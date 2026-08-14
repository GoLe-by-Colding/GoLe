import Link from "next/link";
import type { TrendingSet } from "@entities/pricing";
import { Badge, MediaImage, Text, TrendingUpIcon } from "@shared/ui";
import { formatKrw, thumbnailUrl } from "@shared/lib";

export interface TrendingSetsProps {
  readonly items: readonly TrendingSet[];
}

export function TrendingSets({ items }: TrendingSetsProps) {
  if (items.length === 0) {
    return (
      <div className="flex flex-col items-center gap-3 rounded-lg border border-dashed border-neutral-300 px-6 py-14 text-center">
        <TrendingUpIcon className="h-10 w-10 text-brand-300" strokeWidth={1.5} />
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
    <ol className="flex flex-col overflow-hidden rounded-lg border border-neutral-200 bg-white">
      {items.map((set, index) => (
        <li key={set.setNumber} className={index > 0 ? "border-t border-neutral-100" : ""}>
          <Link
            href={`/prices?set=${encodeURIComponent(set.setNumber)}`}
            className="flex items-center gap-4 px-5 py-4 hover:bg-neutral-50"
          >
            <span
              className={`grid h-8 w-8 shrink-0 place-items-center rounded-lg text-sm font-bold tabular-nums ${
                index === 0
                  ? "bg-accent-100 text-accent-700"
                  : index < 3
                    ? "bg-brand-50 text-brand-700"
                    : "bg-neutral-100 text-neutral-500"
              }`}
            >
              {index + 1}
            </span>
            <MediaImage
              src={set.imageUrl === null ? null : thumbnailUrl(set.imageUrl, 160)}
              alt={set.name}
              className="h-12 w-12 shrink-0 rounded-md border border-neutral-200 object-cover"
              fallback="SET"
              fallbackClassName="text-[10px] tracking-wide"
            />
            <div className="flex min-w-0 flex-col gap-0.5">
              <Text weight="medium" className="truncate">
                {set.name}
              </Text>
              <Text tone="muted" size="sm">
                #{set.setNumber} · 평균 {formatKrw(set.averagePrice)}
              </Text>
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
