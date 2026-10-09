import type { WantedPart } from "@gole/core/parts";
import { cn } from "@shared/lib";

export interface PartItemsTableProps {
  readonly items: readonly WantedPart[];
  /** 게시판 카드처럼 좁은 자리에서 앞의 몇 줄만 보여줄 때. 나머지는 "외 n종"으로 접는다. */
  readonly limit?: number;
  readonly className?: string;
}

/** 찾는 부품 목록 표. 부품 번호는 고정폭으로 보여 번호를 옮겨 적기 쉽게 한다. */
export function PartItemsTable({ items, limit, className }: PartItemsTableProps) {
  const visible = limit === undefined ? items : items.slice(0, limit);
  const hidden = items.length - visible.length;

  return (
    <div className={cn("overflow-hidden rounded-lg border border-neutral-200", className)}>
      <table className="w-full border-collapse text-sm">
        <thead>
          <tr className="bg-neutral-50 text-xs text-neutral-500">
            <th scope="col" className="px-3 py-2 text-left font-medium">
              부품 번호
            </th>
            <th scope="col" className="px-3 py-2 text-left font-medium">
              색상
            </th>
            <th scope="col" className="px-3 py-2 text-right font-medium">
              수량
            </th>
          </tr>
        </thead>
        <tbody>
          {visible.map((item, index) => (
            <tr
              key={`${item.partNumber}-${item.colorName}-${index}`}
              className="border-t border-neutral-100"
            >
              <td className="px-3 py-2 font-mono text-neutral-900">{item.partNumber}</td>
              <td className="px-3 py-2 text-neutral-700">{item.colorName}</td>
              <td className="px-3 py-2 text-right tabular-nums text-neutral-900">
                {item.quantity}개
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {hidden > 0 ? (
        <p className="border-t border-neutral-100 px-3 py-2 text-xs text-neutral-500">
          외 {hidden}종 더
        </p>
      ) : null}
    </div>
  );
}
