"use client";

import { useEffect, useMemo, useState } from "react";
import {
  fetchFeedPage,
  fetchFollowingFeed,
  POST_TOPICS,
  type Post,
  type PostFeedPage,
  type PostType,
} from "@entities/community";
import { useSession } from "@entities/user";
import { Button, SearchIcon, Text } from "@shared/ui";
import { PostCard } from "@widgets/post-card";

type Filter = PostType | "all" | "following";
type Sort = "latest" | "popular";
type FeedStatus = "idle" | "loading" | "error";

interface FeedResult {
  readonly requestKey: string;
  readonly page: PostFeedPage;
}

interface FeedRequestState {
  readonly requestKey: string;
  readonly status: FeedStatus;
}

export interface CommunityFeedProps {
  readonly initialPage: PostFeedPage;
  readonly pageSize: number;
}

/** 주제 탭으로 피드를 필터링하고 키셋 커서로 다음 글을 이어 붙인다. */
export function CommunityFeed({ initialPage, pageSize }: CommunityFeedProps) {
  const { session } = useSession();
  const [feedResult, setFeedResult] = useState<FeedResult>({
    requestKey: "all:",
    page: initialPage,
  });
  const [feedRequestState, setFeedRequestState] = useState<FeedRequestState>({
    requestKey: "all:",
    status: "idle",
  });
  const [feedAttempt, setFeedAttempt] = useState(0);
  const [moreStatus, setMoreStatus] = useState<"idle" | "loading" | "error">("idle");
  const [filter, setFilter] = useState<Filter>("all");
  const [sort, setSort] = useState<Sort>("latest");
  const [query, setQuery] = useState("");
  const [followingState, setFollowingState] = useState<{
    readonly requestKey: string;
    readonly posts: readonly Post[];
    readonly error: boolean;
  } | null>(null);
  const [followingAttempt, setFollowingAttempt] = useState(0);
  const followingRequestKey = session === null ? null : `${session.accountId}:${followingAttempt}`;

  useEffect(() => {
    if (session === null || followingRequestKey === null || filter !== "following") return;
    const controller = new AbortController();
    void fetchFollowingFeed(controller.signal)
      .then((followingPosts) =>
        setFollowingState({ requestKey: followingRequestKey, posts: followingPosts, error: false }),
      )
      .catch(() => {
        if (!controller.signal.aborted) {
          setFollowingState({ requestKey: followingRequestKey, posts: [], error: true });
        }
      });
    return () => controller.abort();
  }, [filter, followingRequestKey, session]);

  const currentFollowingState =
    followingState?.requestKey === followingRequestKey ? followingState : null;
  const followingPosts =
    currentFollowingState?.error === false ? currentFollowingState.posts : null;
  const followingError = currentFollowingState?.error === true;
  const effectiveFilter: Filter = filter === "following" && session === null ? "all" : filter;
  const normalizedQuery = query.trim();
  const feedTopic =
    effectiveFilter === "all" || effectiveFilter === "following" ? undefined : effectiveFilter;
  const feedRequestKey = `${feedTopic ?? "all"}:${normalizedQuery.toLocaleLowerCase("ko-KR")}`;
  const currentFeedPage = feedResult.requestKey === feedRequestKey ? feedResult.page : null;
  const currentFeedStatus =
    feedRequestState.requestKey === feedRequestKey ? feedRequestState.status : "loading";

  useEffect(() => {
    if (effectiveFilter === "following" || feedResult.requestKey === feedRequestKey) return;

    const controller = new AbortController();
    const timeout = window.setTimeout(() => {
      setFeedRequestState({ requestKey: feedRequestKey, status: "loading" });
      setMoreStatus("idle");
      void fetchFeedPage({
        signal: controller.signal,
        limit: pageSize,
        ...(feedTopic === undefined ? {} : { topic: feedTopic }),
        ...(normalizedQuery.length === 0 ? {} : { query: normalizedQuery }),
      })
        .then((page) => {
          setFeedResult({ requestKey: feedRequestKey, page });
          setFeedRequestState({ requestKey: feedRequestKey, status: "idle" });
        })
        .catch(() => {
          if (!controller.signal.aborted) {
            setFeedRequestState({ requestKey: feedRequestKey, status: "error" });
          }
        });
    }, 250);

    return () => {
      window.clearTimeout(timeout);
      controller.abort();
    };
  }, [
    effectiveFilter,
    feedAttempt,
    feedRequestKey,
    feedResult.requestKey,
    feedTopic,
    normalizedQuery,
    pageSize,
  ]);

  const visible = useMemo(() => {
    const normalized = normalizedQuery.toLocaleLowerCase("ko-KR");
    const source =
      effectiveFilter === "following" ? (followingPosts ?? []) : (currentFeedPage?.items ?? []);
    return source
      .filter(
        (post) =>
          effectiveFilter === "following" ||
          effectiveFilter === "all" ||
          post.type === effectiveFilter,
      )
      .filter(
        (post) =>
          effectiveFilter !== "following" ||
          normalized.length === 0 ||
          post.content.toLocaleLowerCase("ko-KR").includes(normalized) ||
          post.authorId.toLocaleLowerCase("ko-KR").includes(normalized),
      )
      .toSorted((left, right) =>
        sort === "popular"
          ? right.likeCount - left.likeCount || right.createdAt.localeCompare(left.createdAt)
          : right.createdAt.localeCompare(left.createdAt),
      );
  }, [currentFeedPage?.items, effectiveFilter, followingPosts, normalizedQuery, sort]);

  async function loadMore(): Promise<void> {
    const cursor = currentFeedPage?.nextCursor ?? null;
    if (cursor === null || moreStatus === "loading") return;
    setMoreStatus("loading");
    try {
      const next = await fetchFeedPage({
        limit: pageSize,
        cursor,
        ...(feedTopic === undefined ? {} : { topic: feedTopic }),
        ...(normalizedQuery.length === 0 ? {} : { query: normalizedQuery }),
      });
      setFeedResult((current) => {
        if (current.requestKey !== feedRequestKey) return current;
        const currentPage = current.page;
        if (
          currentPage.nextCursor?.beforeCreatedAt !== cursor.beforeCreatedAt ||
          currentPage.nextCursor.beforeId !== cursor.beforeId
        ) {
          return current;
        }
        const known = new Set(currentPage.items.map((post) => post.id));
        return {
          requestKey: current.requestKey,
          page: {
            items: [...currentPage.items, ...next.items.filter((post) => !known.has(post.id))],
            nextCursor: next.nextCursor,
          },
        };
      });
      setMoreStatus("idle");
    } catch {
      setMoreStatus("error");
    }
  }

  function tabClass(active: boolean): string {
    return `rounded-md border px-3.5 py-1.5 text-sm font-semibold transition-colors ${
      active
        ? "border-brand-600 bg-brand-600 text-white"
        : "border-neutral-200 bg-white text-neutral-600 hover:border-neutral-300 hover:bg-neutral-50"
    }`;
  }

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-col gap-3 rounded-xl border border-neutral-200 bg-white p-3 sm:flex-row sm:items-center sm:justify-between">
        <label className="relative min-w-0 flex-1 sm:max-w-sm">
          <SearchIcon
            aria-hidden="true"
            className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-neutral-400"
          />
          <span className="sr-only">게시글 검색</span>
          <input
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="내용이나 작성자로 검색"
            maxLength={100}
            className="h-10 w-full rounded-md border border-neutral-200 bg-neutral-50 pr-3 pl-9 text-sm outline-none transition-colors placeholder:text-neutral-400 focus:border-brand-500 focus:bg-white focus:ring-2 focus:ring-brand-50"
          />
        </label>
        <div className="flex rounded-md bg-neutral-100 p-1" aria-label="게시글 정렬">
          {(["latest", "popular"] as const).map((value) => (
            <button
              key={value}
              type="button"
              onClick={() => setSort(value)}
              className={`rounded px-3 py-1.5 text-sm font-semibold transition-colors ${
                sort === value ? "bg-white text-neutral-900 shadow-sm" : "text-neutral-500"
              }`}
            >
              {value === "latest" ? "최신순" : "인기순"}
            </button>
          ))}
        </div>
      </div>

      <div className="flex flex-wrap gap-2" aria-label="게시글 주제">
        <button
          type="button"
          onClick={() => setFilter("all")}
          className={tabClass(filter === "all")}
        >
          전체
        </button>
        {POST_TOPICS.map((t) => (
          <button
            key={t.key}
            type="button"
            onClick={() => setFilter(t.key)}
            className={tabClass(filter === t.key)}
          >
            {t.label}
          </button>
        ))}
        {session !== null ? (
          <button
            type="button"
            onClick={() => setFilter("following")}
            className={tabClass(filter === "following")}
          >
            팔로잉
          </button>
        ) : null}
      </div>

      <div className="flex items-center justify-between gap-3">
        <Text tone="muted" size="sm">
          {currentFeedStatus === "loading" && currentFeedPage === null
            ? "전체 게시글에서 찾는 중…"
            : normalizedQuery.length > 0 ||
                (effectiveFilter !== "all" && effectiveFilter !== "following")
              ? `검색 결과 ${visible.length}개${currentFeedPage?.nextCursor === null ? "" : " 이상"}`
              : `불러온 게시글 ${visible.length}개`}
        </Text>
        {sort === "popular" ? (
          <Text tone="muted" size="sm">
            현재 결과 내 좋아요 기준
          </Text>
        ) : null}
      </div>

      {effectiveFilter !== "following" &&
      currentFeedPage === null &&
      currentFeedStatus === "loading" ? (
        <div
          role="status"
          className="rounded-xl border border-neutral-200 bg-neutral-50 px-5 py-12 text-center"
        >
          <Text tone="muted">전체 게시글에서 검색하고 있어요…</Text>
        </div>
      ) : effectiveFilter !== "following" &&
        currentFeedPage === null &&
        currentFeedStatus === "error" ? (
        <div className="flex flex-col items-center gap-3 rounded-xl border border-danger/20 bg-danger/5 px-5 py-12 text-center">
          <Text tone="secondary">검색 결과를 불러오지 못했어요.</Text>
          <Button
            size="sm"
            variant="secondary"
            onClick={() => setFeedAttempt((value) => value + 1)}
          >
            다시 시도
          </Button>
        </div>
      ) : effectiveFilter === "following" && followingPosts === null && !followingError ? (
        <div
          role="status"
          className="rounded-xl border border-neutral-200 bg-neutral-50 px-5 py-12 text-center"
        >
          <Text tone="muted">팔로잉 피드를 불러오는 중이에요…</Text>
        </div>
      ) : effectiveFilter === "following" && followingError ? (
        <div className="flex flex-col items-center gap-3 rounded-xl border border-danger/20 bg-danger/5 px-5 py-12 text-center">
          <Text tone="secondary">팔로잉 피드를 불러오지 못했어요.</Text>
          <Button size="sm" variant="secondary" onClick={() => setFollowingAttempt((v) => v + 1)}>
            다시 시도
          </Button>
        </div>
      ) : visible.length === 0 ? (
        <div className="rounded-xl border border-dashed border-neutral-300 px-5 py-12 text-center">
          <Text tone="muted">
            {effectiveFilter === "following"
              ? "팔로우한 빌더·판매자의 새 글이 여기에 모여요."
              : query.trim().length > 0
                ? "검색 결과가 없어요. 다른 표현으로 찾아보세요."
                : "해당 주제의 글이 아직 없어요. 첫 글을 남겨보세요!"}
          </Text>
        </div>
      ) : (
        <div className="grid gap-5 [grid-template-columns:repeat(auto-fill,minmax(240px,1fr))]">
          {visible.map((post) => (
            <PostCard key={post.id} post={post} />
          ))}
        </div>
      )}

      {effectiveFilter !== "following" &&
      currentFeedPage !== null &&
      currentFeedPage.nextCursor !== null ? (
        <div className="flex flex-col items-center gap-2 border-t border-neutral-100 pt-2">
          <Button
            variant="secondary"
            onClick={() => void loadMore()}
            disabled={moreStatus === "loading"}
            aria-describedby={moreStatus === "error" ? "community-load-more-error" : undefined}
          >
            {moreStatus === "loading" ? "새 글을 불러오는 중…" : "게시글 더 보기"}
          </Button>
          {moreStatus === "error" ? (
            <Text id="community-load-more-error" tone="secondary" size="sm" role="alert">
              더 불러오지 못했어요. 잠시 후 다시 시도해 주세요.
            </Text>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
