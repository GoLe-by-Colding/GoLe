"use client";

import Link from "next/link";
import { useEffect, useId, useState, useSyncExternalStore } from "react";
import { Button, Heading } from "@shared/ui";
import { parseRecentSets, visitRecentSet, type RecentSet } from "../model/history";
import {
  readRecentSets,
  serverRecentSets,
  subscribeRecentSets,
  writeRecentSets,
} from "../model/store";

export function RecentSets({ current }: { readonly current?: RecentSet }) {
  const headingId = useId();
  const raw = useSyncExternalStore(subscribeRecentSets, readRecentSets, serverRecentSets);
  const [message, setMessage] = useState("");
  const setNumber = current?.setNumber;
  const name = current?.name;

  useEffect(() => {
    if (setNumber === undefined || name === undefined) return;
    // 탐색 기록 저장만 실패했다고 세트 상세를 중단하지 않는다.
    writeRecentSets(visitRecentSet(parseRecentSets(readRecentSets()), { setNumber, name }));
  }, [setNumber, name]);

  const entries = parseRecentSets(raw);
  const visible = entries.filter((entry) => entry.setNumber !== setNumber);

  function remove(number?: string) {
    const latest = parseRecentSets(readRecentSets());
    const next = number === undefined ? [] : latest.filter((entry) => entry.setNumber !== number);
    const saved = writeRecentSets(next);
    setMessage(
      saved
        ? number === undefined
          ? "탐색 기록을 모두 지웠어요."
          : "기록에서 지웠어요."
        : "기록을 지우지 못했어요. 브라우저 저장소 설정을 확인해 주세요.",
    );
  }

  if (visible.length === 0 && message.length === 0) return null;

  return (
    <section aria-labelledby={headingId} className="my-8 border-t border-neutral-200 pt-6">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <Heading level={2} id={headingId}>
          최근 본 세트
        </Heading>
        {entries.length > 0 ? (
          <Button variant="ghost" size="sm" onClick={() => remove()}>
            기록 전체 지우기
          </Button>
        ) : null}
      </div>
      <p className="mt-2 text-sm leading-relaxed text-neutral-500">
        이 탭에서 둘러본 세트 최대 8개예요. 계정에 저장하거나 다른 기기와 공유하지 않아요.
      </p>
      <p role="status" className="mt-2 text-sm text-neutral-600">
        {message}
      </p>
      {visible.length > 0 ? (
        <ul className="mt-3 grid gap-x-6 sm:grid-cols-2">
          {visible.map((entry) => (
            <li
              key={entry.setNumber}
              className="flex min-w-0 items-center gap-2 border-b border-neutral-100 py-2"
            >
              <Link
                href={`/sets/${encodeURIComponent(entry.setNumber)}`}
                className="min-w-0 flex-1 rounded-lg px-2 py-3 hover:bg-brand-50 focus-visible:outline-2 focus-visible:outline-brand-400"
              >
                <span className="block text-xs text-neutral-500">#{entry.setNumber}</span>
                <span className="mt-1 block break-words font-semibold text-neutral-900">
                  {entry.name}
                </span>
              </Link>
              <Button
                variant="ghost"
                size="sm"
                aria-label={`${entry.name} (${entry.setNumber}) 기록 삭제`}
                onClick={() => remove(entry.setNumber)}
              >
                삭제
              </Button>
            </li>
          ))}
        </ul>
      ) : null}
    </section>
  );
}
