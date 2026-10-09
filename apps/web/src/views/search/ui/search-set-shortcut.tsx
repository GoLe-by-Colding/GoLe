import Link from "next/link";
import type { LegoSet } from "@entities/lego-set";

export interface SearchSetShortcutProps {
  /** 검색어에 맞는 카탈로그 세트(최대 3개). 비어 있으면 아무것도 보이지 않는다. */
  readonly sets: readonly LegoSet[];
  readonly listingCount: number;
}

/**
 * 검색 결과 위의 세트 바로가기. 세트 페이지에는 시세·구매 입찰·그 세트의 모든 매물이 모여 있다.
 * 세트 하나면 다음 행동까지 적은 카드로, 여럿이면 결과를 밀어내지 않게 짧은 링크 줄로 보인다.
 * 매물이 없을 때 특히 쓸모 있다 — 구매자가 빈 결과에서 멈추지 않고 입찰을 걸거나 시세를 볼 수 있다.
 */
export function SearchSetShortcut({ sets, listingCount }: SearchSetShortcutProps) {
  const [only] = sets;
  if (only === undefined) return null;
  if (sets.length === 1) {
    return (
      <Link
        href={setHref(only)}
        data-testid="search-set-shortcut"
        className="group flex items-center justify-between gap-3 rounded-lg border border-brand-100 bg-brand-50 px-4 py-3 transition-colors hover:border-brand-200 hover:bg-brand-100/60"
      >
        <span className="flex min-w-0 flex-col gap-0.5">
          <span className="text-xs font-semibold tabular-nums text-brand-700">
            세트 #{only.setNumber}
          </span>
          <span className="truncate text-sm font-semibold text-neutral-900">{only.name}</span>
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
  return (
    <div className="flex flex-wrap items-center gap-x-2 gap-y-1.5">
      <span className="text-xs font-semibold text-neutral-600">세트로 보기</span>
      <ul aria-label="세트 바로가기" className="flex flex-wrap gap-1.5">
        {sets.map((set) => (
          <li key={set.setNumber}>
            <Link
              href={setHref(set)}
              data-testid="search-set-shortcut"
              className="inline-flex max-w-[16rem] items-center gap-1 rounded-full border border-brand-100 bg-brand-50 px-3 py-1.5 text-xs font-semibold text-brand-800 transition-colors hover:bg-brand-100"
            >
              <span className="tabular-nums text-brand-600">#{set.setNumber}</span>
              <span className="truncate">{set.name}</span>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}

function setHref(set: LegoSet): string {
  return `/sets/${encodeURIComponent(set.setNumber)}`;
}
