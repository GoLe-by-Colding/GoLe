"use client";

import { useEffect, useRef, useState } from "react";
import {
  fetchNotificationPreferences,
  updateNotificationPreferences,
  type NotificationPreferenceCategory,
  type NotificationPreferenceChanges,
  type NotificationPreferenceKey,
} from "@entities/notification";
import { useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import { cn, loginHrefWithReturnTo } from "@shared/lib";
import {
  BackButton,
  Button,
  Card,
  Container,
  EmptyState,
  Heading,
  LinkButton,
  Skeleton,
  Text,
} from "@shared/ui";

const SETTINGS_PATH = "/profile/notifications";

/**
 * 분류마다 어떤 알림이 묶이는지 한 줄로 알려준다. 라벨은 서버가 주지만 설명은 화면 문구라 여기 둔다.
 * 서버가 새 분류를 더해도 설명만 빠질 뿐 스위치는 그대로 그려진다.
 */
const CATEGORY_DESCRIPTION: Partial<Record<NotificationPreferenceKey, string>> = {
  trade: "주문·결제·배송·분쟁 등 거래가 진행되는 소식",
  offer: "가격 제안과 수락·거절, 입찰 체결, 입찰가 이하로 올라온 매물 소식",
  watch: "팔로우한 셀러의 새 매물, 찜한 매물 가격 인하, 관심 세트 새 매물·단종 소식",
  community: "댓글·좋아요·팔로우, 내가 가진 세트의 부품을 찾는 요청",
};

const MANDATORY_REASON = "돈과 물건이 오가는 진행 알림이라 끌 수 없어요.";

type LoadState =
  | {
      readonly accountId: string;
      readonly status: "ready";
      readonly categories: readonly NotificationPreferenceCategory[];
    }
  | { readonly accountId: string; readonly status: "failed"; readonly message: string };

function errorMessage(cause: unknown, fallback: string): string {
  return cause instanceof ApiError ? cause.message : fallback;
}

function withEnabled(
  categories: readonly NotificationPreferenceCategory[],
  key: NotificationPreferenceKey,
  enabled: boolean,
): readonly NotificationPreferenceCategory[] {
  return categories.map((category) => (category.key === key ? { ...category, enabled } : category));
}

/**
 * 알림 수신 설정 — 분류 단위로 켜고 끈다. (notification-preferences P2·P3)
 *
 * 스위치는 낙관적으로 바로 뒤집고, 저장이 실패하면 그 분류만 되돌린다. 여러 분류를 연달아 누르면
 * 응답이 순서대로 오지 않을 수 있어서, 서버 응답을 반영할 때 아직 저장 중인 분류는 화면 값을 지킨다.
 */
export function NotificationSettingsPage() {
  const { session } = useSession();
  const accountId = session?.accountId ?? null;
  const [loaded, setLoaded] = useState<LoadState | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [pendingKeys, setPendingKeys] = useState<ReadonlySet<NotificationPreferenceKey>>(
    () => new Set(),
  );
  const [saveError, setSaveError] = useState<string | null>(null);
  /** 저장 중인 분류와 그 낙관값. 렌더와 무관한 비동기 경합 판정용이라 ref로 둔다. */
  const inFlightRef = useRef(new Map<NotificationPreferenceKey, boolean>());
  const accountIdRef = useRef(accountId);

  useEffect(() => {
    accountIdRef.current = accountId;
  }, [accountId]);

  useEffect(() => {
    if (accountId === null) return;
    const controller = new AbortController();
    fetchNotificationPreferences(accountId, controller.signal)
      .then((categories) => {
        if (!controller.signal.aborted) setLoaded({ accountId, status: "ready", categories });
      })
      .catch((cause: unknown) => {
        if (!controller.signal.aborted) {
          setLoaded({
            accountId,
            status: "failed",
            message: errorMessage(
              cause,
              "알림 설정을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.",
            ),
          });
        }
      });
    return () => controller.abort();
  }, [accountId, reloadKey]);

  function retry() {
    setLoaded(null);
    setReloadKey((current) => current + 1);
  }

  async function toggle(category: NotificationPreferenceCategory) {
    if (accountId === null || category.mandatory) return;
    if (inFlightRef.current.has(category.key)) return;
    const owner = accountId;
    const key = category.key;
    const next = !category.enabled;
    const changes: NotificationPreferenceChanges = {};
    changes[key] = next;

    inFlightRef.current.set(key, next);
    setPendingKeys((current) => new Set(current).add(key));
    setSaveError(null);
    setLoaded((current) =>
      current?.accountId === owner && current.status === "ready"
        ? { ...current, categories: withEnabled(current.categories, key, next) }
        : current,
    );

    try {
      const categories = await updateNotificationPreferences(owner, changes);
      inFlightRef.current.delete(key);
      if (accountIdRef.current !== owner) return;
      setLoaded((current) =>
        current?.accountId === owner
          ? {
              accountId: owner,
              status: "ready",
              categories: categories.map((item) => {
                const optimistic = inFlightRef.current.get(item.key);
                return optimistic === undefined ? item : { ...item, enabled: optimistic };
              }),
            }
          : current,
      );
    } catch (cause) {
      inFlightRef.current.delete(key);
      if (accountIdRef.current !== owner) return;
      setLoaded((current) =>
        current?.accountId === owner && current.status === "ready"
          ? { ...current, categories: withEnabled(current.categories, key, !next) }
          : current,
      );
      setSaveError(
        `${category.label} 설정을 저장하지 못했어요. ${errorMessage(cause, "잠시 후 다시 시도해 주세요.")}`,
      );
    } finally {
      setPendingKeys((current) => {
        const rest = new Set(current);
        rest.delete(key);
        return rest;
      });
    }
  }

  if (accountId === null) {
    return (
      <Container width="sm">
        <div className="flex flex-col items-start gap-4 py-12">
          <Heading level={1}>알림 설정</Heading>
          <Text tone="secondary">받을 알림을 고르려면 로그인이 필요합니다.</Text>
          <LinkButton href={loginHrefWithReturnTo(SETTINGS_PATH)}>로그인하러 가기</LinkButton>
        </div>
      </Container>
    );
  }

  const visible = loaded?.accountId === accountId ? loaded : null;

  return (
    <Container width="sm">
      <div className="flex flex-col gap-6 py-10 pb-16">
        <BackButton fallbackHref="/profile" />
        <div className="flex flex-col gap-2">
          <Heading level={1}>알림 설정</Heading>
          <Text tone="secondary">
            끈 분류의 알림은 알림함에도, 앱 푸시로도 오지 않아요. 언제든 다시 켤 수 있어요.
          </Text>
        </div>

        {saveError !== null ? (
          <p role="alert" className="rounded-md bg-danger-soft p-3 text-sm text-danger">
            {saveError}
          </p>
        ) : null}

        {visible === null ? (
          <Card padded className="flex flex-col gap-5" aria-busy="true">
            {[1, 2, 3, 4].map((i) => (
              <div key={i} className="flex items-center justify-between gap-4">
                <div className="flex flex-1 flex-col gap-1.5">
                  <Skeleton className="h-4 w-28" />
                  <Skeleton className="h-3 w-3/4" />
                </div>
                <Skeleton className="h-6 w-11 rounded-full" />
              </div>
            ))}
          </Card>
        ) : visible.status === "failed" ? (
          <EmptyState
            variant="inline"
            title="알림 설정을 불러오지 못했어요"
            description={visible.message}
            action={
              <Button variant="secondary" onClick={retry}>
                다시 시도
              </Button>
            }
          />
        ) : (
          <Card padded>
            <ul className="flex flex-col divide-y divide-neutral-100">
              {visible.categories.map((category) => (
                <PreferenceRow
                  key={category.key}
                  category={category}
                  pending={pendingKeys.has(category.key)}
                  onToggle={() => void toggle(category)}
                />
              ))}
            </ul>
          </Card>
        )}

        <Text size="sm" tone="muted">
          관심 테마 알림톡도 &lsquo;찜·관심 세트&rsquo; 설정을 따라요.
        </Text>
      </div>
    </Container>
  );
}

function PreferenceRow({
  category,
  pending,
  onToggle,
}: {
  readonly category: NotificationPreferenceCategory;
  readonly pending: boolean;
  readonly onToggle: () => void;
}) {
  const description = CATEGORY_DESCRIPTION[category.key];
  const labelId = `notification-pref-${category.key}-label`;
  const detailId = `notification-pref-${category.key}-detail`;

  return (
    <li
      className="flex items-start justify-between gap-4 py-4 first:pt-0 last:pb-0"
      data-testid={`notification-pref-${category.key}`}
    >
      <div className="flex min-w-0 flex-col gap-1">
        <span id={labelId} className="font-semibold text-neutral-900">
          {category.label}
        </span>
        <span id={detailId} className="flex flex-col gap-0.5 text-sm text-neutral-500">
          {description === undefined ? null : <span>{description}</span>}
          {category.mandatory ? (
            <span className="text-xs text-neutral-400">{MANDATORY_REASON}</span>
          ) : null}
        </span>
      </div>
      <button
        type="button"
        role="switch"
        aria-checked={category.enabled}
        aria-labelledby={labelId}
        aria-describedby={detailId}
        aria-busy={pending || undefined}
        disabled={category.mandatory}
        onClick={pending ? undefined : onToggle}
        className={cn(
          "relative mt-0.5 inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors duration-200 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-400 disabled:cursor-not-allowed",
          category.enabled ? "bg-brand-600" : "bg-neutral-300",
          category.mandatory && "opacity-50",
          pending && "cursor-progress",
        )}
      >
        <span
          aria-hidden="true"
          className={cn(
            "inline-block h-5 w-5 rounded-full bg-white shadow-sm transition-transform duration-200",
            category.enabled ? "translate-x-5.5" : "translate-x-0.5",
          )}
        />
      </button>
    </li>
  );
}
