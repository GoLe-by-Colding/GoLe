"use client";

import { useEffect, useSyncExternalStore } from "react";
import { fetchPublicProfiles, PUBLIC_PROFILE_BATCH_MAX } from "@gole/core/user";

/**
 * 다른 사람의 공개 표시 이름(public-display-name D3).
 *
 * 카드 열 장이 각자 이 훅을 불러도 같은 틱의 ID를 모아 한 번에 묻는다. 결과는 "닉네임 없음"까지 탭이 살아 있는
 * 동안 캐시해 같은 ID를 다시 묻지 않는다. 이름은 보조 정보라, 조회가 실패하거나 닉네임이 없으면 지금까지처럼
 * 계정 ID 앞 8자를 보여 준다.
 */
const nicknames = new Map<string, string | null>();
const requested = new Set<string>();
const listeners = new Set<() => void>();
let queued: string[] = [];
let flushScheduled = false;
let version = 0;

function notify(): void {
  version += 1;
  listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function flush(): void {
  flushScheduled = false;
  const ids = queued;
  queued = [];
  for (let start = 0; start < ids.length; start += PUBLIC_PROFILE_BATCH_MAX) {
    const batch = ids.slice(start, start + PUBLIC_PROFILE_BATCH_MAX);
    void fetchPublicProfiles(batch)
      .then((profiles) => {
        profiles.forEach((profile) => nicknames.set(profile.accountId, profile.nickname));
        notify();
      })
      .catch(() => {
        // 실패한 ID는 다음 화면에서 다시 묻는다. 그동안 화면은 ID 표시로 동작한다.
        batch.forEach((id) => requested.delete(id));
      });
  }
}

function request(ids: readonly string[]): void {
  for (const id of ids) {
    if (id.length === 0 || requested.has(id)) continue;
    requested.add(id);
    queued.push(id);
  }
  if (queued.length > 0 && !flushScheduled) {
    flushScheduled = true;
    setTimeout(flush, 0);
  }
}

/** 닉네임이 없을 때의 표시. 공개 이름 도입 전과 같은 규칙이라 기존 화면·테스트 기준이 그대로다. */
export function fallbackDisplayName(accountId: string): string {
  return accountId.slice(0, 8);
}

/**
 * 넘긴 계정들의 이름을 묻고, 계정 ID를 표시 이름으로 바꾸는 함수를 돌려준다. 닉네임이 없을 때 쓸 값을 화면마다
 * 다르게 둘 수 있다(채팅 목록은 원래 계정 ID 전체를 보여 줬다).
 */
export function useDisplayNames(
  accountIds: readonly string[],
): (accountId: string, fallback?: string) => string {
  const key = [...new Set(accountIds.filter((id) => id.length > 0))].sort().join("\n");
  useEffect(() => {
    if (key.length > 0) request(key.split("\n"));
  }, [key]);
  useSyncExternalStore(
    subscribe,
    () => version,
    () => 0,
  );
  return (accountId, fallback = fallbackDisplayName(accountId)) =>
    nicknames.get(accountId) ?? fallback;
}
