import Link from "next/link";
import type { LegoSet } from "@entities/lego-set";

export interface SearchSetShortcutProps {
  readonly set: LegoSet;
  readonly listingCount: number;
}

/**
 * 세트 번호로 찾았을 때 결과 위에 두는 세트 바로가기. 세트 페이지에는 시세·구매 입찰·그 세트의 모든 매물이 모여 있다.
 * 매물이 없을 때 특히 쓸모 있다 — 구매자가 빈 결과에서 멈추지 않고 입찰을 걸거나 시세를 볼 수 있다.
 */
export function SearchSetShortcut({ set, listingCount }: SearchSetShortcutProps) {
  return (
    <Link
      href={`/sets/${encodeURIComponent(set.setNumber)}`}
      data-testid="search-set-shortcut"
      className="group flex items-center justify-between gap-3 rounded-lg border border-brand-100 bg-brand-50 px-4 py-3 transition-colors hover:border-brand-200 hover:bg-brand-100/60"
    >
      <span className="flex min-w-0 flex-col gap-0.5">
        <span className="text-xs font-semibold tabular-nums text-brand-700">
          세트 #{set.setNumber}
        </span>
        <span className="truncate text-sm font-semibold text-neutral-900">{set.name}</span>
        <span className="text-xs leading-relaxed break-keep text-neutral-600">
          {listingCount > 0
            ? "시세·구매 입찰·이 세트의 매물을 한곳에서 봐요"
            : "아직 매물이 없어요. 세트 페이지에서 시세를 보고 구매 입찰을 걸어 둘 수 있어요"}
        </span>
      </span>
      <span
        aria-hidden="true"
        className="text-brand-700 transition-transform group-hover:translate-x-0.5"
      >
        →
      </span>
    </Link>
  );
}
