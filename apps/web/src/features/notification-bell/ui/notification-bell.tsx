"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { fetchUnreadCount } from "@entities/notification";
import { useSession } from "@entities/user";

const POLL_MS = 30_000;

/**
 * 헤더 알림 벨. 로그인 시 안읽음 수를 주기 폴링해 배지로 표시하고 /notifications로 링크한다.
 * (알림 스펙 N7)
 */
export function NotificationBell() {
  const { session } = useSession();
  const accountId = session?.accountId ?? null;
  const [count, setCount] = useState(0);

  useEffect(() => {
    if (accountId === null) {
      return; // 비로그인 시 컴포넌트가 null을 렌더하므로 카운트 갱신 불필요
    }
    let active = true;
    const load = async (): Promise<void> => {
      try {
        const n = await fetchUnreadCount(accountId);
        if (active) {
          setCount(n);
        }
      } catch {
        /* 폴링 실패는 무시 */
      }
    };
    void load();
    const timer = setInterval(() => void load(), POLL_MS);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, [accountId]);

  if (accountId === null) {
    return null;
  }

  return (
    <Link
      href="/notifications"
      aria-label={count > 0 ? `알림 ${count}건` : "알림"}
      className="relative grid h-8 w-8 place-items-center rounded-full text-neutral-600 transition-colors hover:bg-neutral-100 hover:text-neutral-900"
    >
      <span aria-hidden="true" className="text-lg">
        🔔
      </span>
      {count > 0 ? (
        <span className="absolute -right-0.5 -top-0.5 grid min-w-[16px] place-items-center rounded-full bg-danger px-1 text-[10px] font-bold leading-4 text-white">
          {count > 99 ? "99+" : count}
        </span>
      ) : null}
    </Link>
  );
}
