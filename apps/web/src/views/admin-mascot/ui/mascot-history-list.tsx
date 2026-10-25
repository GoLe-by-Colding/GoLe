import { useState } from "react";
import { fetchMascotHistory, type MascotSelection } from "@gole/core/mascot";
import { Button } from "@shared/ui";
import { formatTime } from "../lib/mascot-format";

export function MascotHistoryList({
  history,
  hasMore,
  knownIds,
  busy,
  onPick,
  onMore,
  onError,
}: {
  readonly history: readonly MascotSelection[];
  readonly hasMore: boolean;
  readonly knownIds: ReadonlySet<string>;
  readonly busy: boolean;
  readonly onPick: (row: MascotSelection) => void;
  readonly onMore: (rows: readonly MascotSelection[]) => void;
  readonly onError: () => void;
}) {
  const [loadingMore, setLoadingMore] = useState(false);
  return (
    <section className="min-w-0 space-y-3">
      <div className="space-y-1">
        <h3 className="font-semibold">적용 이력 · 감사 기록</h3>
        <p className="text-sm text-neutral-600">
          되돌리기는 그 마스코트를 미리보기에 불러올 뿐입니다. 확인하고 적용하면 새 리비전으로
          남습니다.
        </p>
      </div>
      {history.length === 0 ? (
        <p className="text-sm text-neutral-500">
          아직 적용 이력이 없습니다. 기본 고래가 쓰이고 있습니다.
        </p>
      ) : (
        <ul className="space-y-2">
          {history.map((row) => {
            const available = knownIds.has(row.assetId);
            return (
              <li
                key={row.revision}
                className="flex min-w-0 flex-wrap items-center justify-between gap-3 rounded-lg border border-neutral-200 bg-white p-4"
              >
                <div className="min-w-0 flex-1 text-sm">
                  <p className="font-semibold break-keep [overflow-wrap:anywhere]">
                    revision {row.revision} · {row.assetName}
                  </p>
                  <p className="break-all text-neutral-700">{row.reason}</p>
                  <p className="text-xs break-all text-neutral-500">
                    {formatTime(row.publishedAt)} · 관리자 {row.actorId.slice(0, 8)}
                  </p>
                </div>
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={busy || !available}
                  onClick={() => onPick(row)}
                >
                  {available ? "다시 고르기" : "지운 그림"}
                </Button>
              </li>
            );
          })}
        </ul>
      )}
      {hasMore ? (
        <Button
          variant="secondary"
          size="sm"
          disabled={busy || loadingMore}
          onClick={() => {
            setLoadingMore(true);
            void fetchMascotHistory(history.at(-1)?.revision)
              .then(onMore)
              .catch(onError)
              .finally(() => setLoadingMore(false));
          }}
        >
          이전 이력 더 보기
        </Button>
      ) : null}
    </section>
  );
}
