"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import {
  addCollectionItem,
  fetchCollection,
  removeCollectionItem,
  type CollectionItem,
} from "@entities/collection";
import { useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import { Button, HeartIcon, LinkButton } from "@shared/ui";

export interface SetPriceActionsProps {
  readonly setNumber: string;
  readonly setName: string;
}

interface CollectionState {
  readonly accountId: string | null;
  readonly status: "idle" | "loading" | "ready" | "error";
  readonly items: readonly CollectionItem[];
}

interface Feedback {
  readonly setNumber: string;
  readonly tone: "status" | "error";
  readonly message: string;
}

function actionErrorMessage(cause: unknown): string {
  return cause instanceof ApiError
    ? cause.message
    : "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.";
}

function priceHref(setNumber: string): string {
  return `/prices?set=${encodeURIComponent(setNumber)}`;
}

/**
 * 공개 시세를 본 다음 위시 저장, 같은 세트의 매물 확인, 공유까지 이어 주는 후속 행동 묶음.
 * 컬렉션 조회는 계정마다 한 번만 하고 선택 중인 세트는 로컬 목록에서 판정한다.
 */
export function SetPriceActions({ setNumber, setName }: SetPriceActionsProps) {
  const router = useRouter();
  const { session } = useSession();
  const accountId = session?.accountId ?? null;
  const [collection, setCollection] = useState<CollectionState>({
    accountId: null,
    status: "idle",
    items: [],
  });
  const [reloadGeneration, setReloadGeneration] = useState(0);
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<Feedback | null>(null);

  useEffect(() => {
    if (accountId === null) {
      return;
    }
    const controller = new AbortController();
    void fetchCollection(accountId, controller.signal)
      .then((items) => {
        if (!controller.signal.aborted) {
          setCollection({ accountId, status: "ready", items });
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setCollection({ accountId, status: "error", items: [] });
        }
      });
    return () => controller.abort();
  }, [accountId, reloadGeneration]);

  const matchingItems = useMemo(
    () =>
      collection.accountId === accountId
        ? collection.items.filter((item) => item.setNumber === setNumber)
        : [],
    [accountId, collection, setNumber],
  );
  const wantedItem = matchingItems.find((item) => item.status === "wanted") ?? null;
  const otherItem = matchingItems.find((item) => item.status !== "wanted") ?? null;
  const collectionMatchesAccount = collection.accountId === accountId;
  const checking =
    accountId !== null && (!collectionMatchesAccount || collection.status === "loading");
  const loadFailed =
    accountId !== null && collectionMatchesAccount && collection.status === "error";
  const visibleFeedback = feedback?.setNumber === setNumber ? feedback : null;

  async function handleCollectionAction() {
    const targetHref = priceHref(setNumber);
    if (accountId === null) {
      router.push(`/login?returnTo=${encodeURIComponent(targetHref)}`);
      return;
    }
    if (checking || busy) {
      return;
    }
    if (loadFailed) {
      setCollection({ accountId, status: "loading", items: [] });
      setReloadGeneration((generation) => generation + 1);
      return;
    }
    if (otherItem !== null && wantedItem === null) {
      return;
    }

    setBusy(true);
    setFeedback(null);
    try {
      if (wantedItem !== null) {
        await removeCollectionItem(wantedItem.id, accountId);
        setCollection((current) =>
          current.accountId === accountId
            ? { ...current, items: current.items.filter((item) => item.id !== wantedItem.id) }
            : current,
        );
        setFeedback({ setNumber, tone: "status", message: "위시에서 뺐어요." });
      } else {
        const added = await addCollectionItem(accountId, setNumber, "wanted");
        setCollection((current) =>
          current.accountId === accountId
            ? { ...current, status: "ready", items: [...current.items, added] }
            : current,
        );
        setFeedback({ setNumber, tone: "status", message: "위시에 저장했어요." });
      }
    } catch (cause) {
      setFeedback({ setNumber, tone: "error", message: actionErrorMessage(cause) });
    } finally {
      setBusy(false);
    }
  }

  async function handleShare() {
    const url = new URL(priceHref(setNumber), window.location.origin).toString();
    setFeedback(null);
    try {
      if (typeof navigator.share === "function") {
        await navigator.share({
          title: `${setName} 시세 · GoLe`,
          text: `${setName}의 GoLe 시세를 확인해 보세요.`,
          url,
        });
        setFeedback({ setNumber, tone: "status", message: "공유 창을 열었어요." });
        return;
      }
      if (navigator.clipboard === undefined) {
        throw new Error("Clipboard API unavailable");
      }
      await navigator.clipboard.writeText(url);
      setFeedback({ setNumber, tone: "status", message: "시세 링크를 복사했어요." });
    } catch (cause) {
      if (cause instanceof DOMException && cause.name === "AbortError") {
        return;
      }
      setFeedback({
        setNumber,
        tone: "error",
        message: "공유하지 못했어요. 잠시 후 다시 시도해 주세요.",
      });
    }
  }

  const collectionLabel = checking
    ? "컬렉션 확인 중…"
    : loadFailed
      ? "컬렉션 다시 확인"
      : wantedItem !== null
        ? "위시에 저장됨"
        : otherItem?.status === "owned"
          ? "보유 컬렉션에 있음"
          : otherItem?.status === "sold"
            ? "판매 기록에 있음"
            : accountId === null
              ? "로그인하고 갖고 싶어요"
              : "갖고 싶어요";
  const saved = wantedItem !== null || otherItem !== null;

  return (
    <section
      aria-label={`${setName} 후속 작업`}
      className="flex flex-col gap-2 rounded-lg border border-neutral-200 bg-neutral-50 p-3"
    >
      <div className="flex flex-wrap gap-2">
        <Button
          size="sm"
          variant={wantedItem === null ? "primary" : "secondary"}
          disabled={checking || busy || (otherItem !== null && wantedItem === null)}
          aria-pressed={saved}
          onClick={() => void handleCollectionAction()}
        >
          <HeartIcon className="h-4 w-4" filled={saved} />
          {collectionLabel}
        </Button>
        <LinkButton
          href={`/sets/${encodeURIComponent(setNumber)}#set-listings-heading`}
          size="sm"
          variant="secondary"
        >
          세트·매물 보기
        </LinkButton>
        <Button size="sm" variant="ghost" onClick={() => void handleShare()}>
          공유
        </Button>
        {saved ? (
          <LinkButton href="/collection" size="sm" variant="ghost">
            내 컬렉션 보기
          </LinkButton>
        ) : null}
      </div>
      {loadFailed ? (
        <p className="text-xs text-danger" role="alert">
          컬렉션 상태를 확인하지 못했어요. 다시 확인해 주세요.
        </p>
      ) : visibleFeedback?.tone === "error" ? (
        <p className="text-xs text-danger" role="alert">
          {visibleFeedback.message}
        </p>
      ) : visibleFeedback === null ? null : (
        <p className="text-xs text-neutral-600" role="status">
          {visibleFeedback.message}
        </p>
      )}
    </section>
  );
}
