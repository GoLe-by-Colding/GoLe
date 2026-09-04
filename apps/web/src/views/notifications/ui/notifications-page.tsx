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
import { ApiError } from "@shared/api";
import { loginHrefWithReturnTo } from "@shared/lib";
import {
  BellIcon,
  Button,
  Container,
  EmptyState,
  Heading,
  LinkButton,
  Skeleton,
  Text,
} from "@shared/ui";

function notificationErrorMessage(cause: unknown): string {
  return cause instanceof ApiError
    ? cause.message
    : "알림을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.";
}

const NOTIFICATION_ROUTE_PATTERNS: readonly RegExp[] = [
  /^\/(?:chat|collection|community|feed|notifications|prices|profile|search)(?:\/)?$/,
  /^\/(?:community|listings|orders|sets|shops)\/[^/]+\/?$/,
];

/** 서버 데이터가 깨져도 알림을 외부 주소나 존재하지 않는 앱 경로의 링크로 만들지 않는다. */
function safeNotificationHref(link: string | null): string | null {
  if (link === null || !link.startsWith("/") || link.startsWith("//")) return null;
  try {
    const base = "https://gole.invalid";
    const url = new URL(link, base);
    if (
      url.origin !== base ||
      !NOTIFICATION_ROUTE_PATTERNS.some((pattern) => pattern.test(url.pathname))
    ) {
      return null;
    }
    return `${url.pathname}${url.search}${url.hash}`;
  } catch {
    return null;
  }
}

/**
 * 알림 목록. 항목 클릭 시 읽음 처리 후 링크 이동, 전체 읽음 버튼 제공. (알림 스펙 N7)
 */
export function NotificationsPage() {
  const { session } = useSession();
  const accountId = session?.accountId ?? null;
  const [items, setItems] = useState<readonly Notification[]>([]);
  const [loadedAccountId, setLoadedAccountId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | undefined>(undefined);
  const [reloadKey, setReloadKey] = useState(0);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (accountId === null) {
      return; // 비로그인은 아래에서 로그인 안내를 렌더한다
    }
    const controller = new AbortController();
    const run = async (): Promise<void> => {
      setLoading(true);
      setError(undefined);
      try {
        const list = await fetchNotifications(accountId, controller.signal);
        if (!controller.signal.aborted) {
          setItems(list);
          setLoadedAccountId(accountId);
        }
      } catch (cause) {
        if (!controller.signal.aborted) {
          setItems([]);
          setLoadedAccountId(accountId);
          setError(notificationErrorMessage(cause));
        }
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    };
    const timer = window.setTimeout(() => void run(), 0);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [accountId, reloadKey]);

  async function handleReadAll() {
    if (accountId === null) {
      return;
    }
    setBusy(true);
    setError(undefined);
    try {
      await markAllNotificationsRead(accountId);
      setItems((prev) => prev.map((n) => ({ ...n, read: true })));
    } catch (cause) {
      setError(notificationErrorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  function handleClickItem(n: Notification) {
    if (accountId !== null && !n.read) {
      setItems((current) =>
        current.map((item) => (item.id === n.id ? { ...item, read: true } : item)),
      );
      void markNotificationRead(accountId, n.id).catch((cause: unknown) => {
        setItems((current) =>
          current.map((item) => (item.id === n.id ? { ...item, read: false } : item)),
        );
        setError(notificationErrorMessage(cause));
      });
    }
  }

  if (accountId === null) {
    return (
      <Container width="sm">
        <div className="flex flex-col items-start gap-4 pt-10 pb-16">
          <Heading level={1}>알림</Heading>
          <Text tone="secondary">로그인이 필요합니다.</Text>
          <LinkButton href={loginHrefWithReturnTo("/notifications")}>로그인하러 가기</LinkButton>
        </div>
      </Container>
    );
  }

  const visibleItems = loadedAccountId === accountId ? items : [];
  const visibleLoading = loadedAccountId !== accountId || loading;
  const visibleError = loadedAccountId === accountId ? error : undefined;

  return (
    <Container width="sm">
      <div className="flex flex-col gap-5 pt-10 pb-16">
        <div className="flex items-center justify-between">
          <Heading level={1}>알림</Heading>
          {visibleItems.some((n) => !n.read) ? (
            <Button variant="ghost" size="sm" disabled={busy} onClick={handleReadAll}>
              {busy ? "처리 중" : "전체 읽음"}
            </Button>
          ) : null}
        </div>

        {visibleLoading ? (
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
        ) : visibleError ? (
          <EmptyState
            variant="inline"
            title="알림을 불러오지 못했어요"
            description={visibleError}
            action={
              <Button variant="secondary" onClick={() => setReloadKey((current) => current + 1)}>
                다시 시도
              </Button>
            }
          />
        ) : visibleItems.length === 0 ? (
          <EmptyState
            variant="inline"
            icon={<BellIcon className="h-8 w-8 text-neutral-400" strokeWidth={1.5} />}
            title="아직 알림이 없어요"
            description="새 댓글과 팔로우 소식이 생기면 이곳에서 바로 이어볼 수 있어요."
            action={
              <LinkButton href="/community" size="sm" variant="secondary">
                커뮤니티 둘러보기
              </LinkButton>
            }
          />
        ) : (
          <ul className="flex flex-col divide-y divide-neutral-100 overflow-hidden rounded-lg border border-neutral-200 bg-white">
            {visibleItems.map((n) => {
              const href = safeNotificationHref(n.link);
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
                  {href !== null ? (
                    <Link
                      href={href}
                      onClick={() => handleClickItem(n)}
                      className="block hover:bg-neutral-50"
                    >
                      {body}
                    </Link>
                  ) : !n.read ? (
                    <button
                      type="button"
                      onClick={() => handleClickItem(n)}
                      className="block w-full text-left hover:bg-neutral-50"
                    >
                      {body}
                    </button>
                  ) : (
                    <div>{body}</div>
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
