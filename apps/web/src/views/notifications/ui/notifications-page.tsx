"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import {
  fetchNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  type Notification,
} from "@entities/notification";
import { useSession } from "@entities/user";
import { Button, Card, Container, Heading, LinkButton, Skeleton, Text } from "@shared/ui";

/**
 * 알림 목록. 항목 클릭 시 읽음 처리 후 링크 이동, 전체 읽음 버튼 제공. (알림 스펙 N7)
 */
export function NotificationsPage() {
  const { session } = useSession();
  const accountId = session?.accountId ?? null;
  const [items, setItems] = useState<readonly Notification[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (accountId === null) {
      return; // 비로그인은 아래에서 로그인 안내를 렌더한다
    }
    let active = true;
    const run = async (): Promise<void> => {
      try {
        const list = await fetchNotifications(accountId);
        if (active) {
          setItems(list);
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    };
    void run();
    return () => {
      active = false;
    };
  }, [accountId]);

  async function handleReadAll() {
    if (accountId === null) {
      return;
    }
    await markAllNotificationsRead(accountId);
    setItems((prev) => prev.map((n) => ({ ...n, read: true })));
  }

  function handleClickItem(n: Notification) {
    if (accountId !== null && !n.read) {
      void markNotificationRead(accountId, n.id).catch(() => undefined);
    }
  }

  if (accountId === null) {
    return (
      <Container width="sm">
        <div className="flex flex-col items-start gap-4 pt-10 pb-16">
          <Heading level={1}>알림</Heading>
          <Text tone="secondary">로그인이 필요합니다.</Text>
          <LinkButton href="/login">로그인하러 가기</LinkButton>
        </div>
      </Container>
    );
  }

  return (
    <Container width="sm">
      <div className="flex flex-col gap-5 pt-10 pb-16">
        <div className="flex items-center justify-between">
          <Heading level={1}>알림</Heading>
          {items.some((n) => !n.read) ? (
            <Button variant="ghost" size="sm" onClick={handleReadAll}>
              전체 읽음
            </Button>
          ) : null}
        </div>

        {loading ? (
          <div className="flex flex-col gap-3">
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="flex items-start gap-3 px-1 py-2">
                <Skeleton circle className="mt-1.5 h-2 w-2 shrink-0" />
                <div className="flex flex-1 flex-col gap-1.5">
                  <Skeleton className="h-4 w-3/4" />
                  <Skeleton className="h-3 w-1/3" />
                </div>
              </div>
            ))}
          </div>
        ) : items.length === 0 ? (
          <Card>
            <div className="flex flex-col items-center gap-2 p-10 text-center">
              <Text tone="secondary">아직 알림이 없어요.</Text>
            </div>
          </Card>
        ) : (
          <ul className="flex flex-col divide-y divide-neutral-100 overflow-hidden rounded-lg border border-neutral-200 bg-white">
            {items.map((n) => {
              const body = (
                <div className="flex items-start gap-3 px-5 py-4">
                  {!n.read ? (
                    <span
                      aria-hidden="true"
                      className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-brand-500"
                    />
                  ) : (
                    <span aria-hidden="true" className="mt-1.5 h-2 w-2 shrink-0" />
                  )}
                  <div className="flex min-w-0 flex-col gap-0.5">
                    <Text className={n.read ? "text-neutral-500" : ""}>{n.message}</Text>
                    <Text tone="muted" size="sm">
                      {new Date(n.createdAt).toLocaleString("ko-KR")}
                    </Text>
                  </div>
                </div>
              );
              return (
                <li key={n.id}>
                  {n.link !== null ? (
                    <Link
                      href={n.link}
                      onClick={() => handleClickItem(n)}
                      className="block hover:bg-neutral-50"
                    >
                      {body}
                    </Link>
                  ) : (
                    <button
                      type="button"
                      onClick={() => handleClickItem(n)}
                      className="block w-full text-left hover:bg-neutral-50"
                    >
                      {body}
                    </button>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </Container>
  );
}
