"use client";

import { useMemo, useState } from "react";
import { POST_TOPICS, type Post, type PostType } from "@entities/community";
import { SearchIcon, Text } from "@shared/ui";
import { PostCard } from "@widgets/post-card";

type Filter = PostType | "all";
type Sort = "latest" | "popular";

export interface CommunityFeedProps {
  readonly posts: readonly Post[];
}

/** 주제 탭으로 피드를 필터링한다(클라이언트). */
export function CommunityFeed({ posts }: CommunityFeedProps) {
  const [filter, setFilter] = useState<Filter>("all");
  const [sort, setSort] = useState<Sort>("latest");
  const [query, setQuery] = useState("");
  const visible = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase("ko-KR");
    return posts
      .filter((post) => filter === "all" || post.type === filter)
      .filter(
        (post) =>
          normalized.length === 0 ||
          post.content.toLocaleLowerCase("ko-KR").includes(normalized) ||
          post.authorId.toLocaleLowerCase("ko-KR").includes(normalized),
      )
      .toSorted((left, right) =>
        sort === "popular"
          ? right.likeCount - left.likeCount || right.createdAt.localeCompare(left.createdAt)
          : right.createdAt.localeCompare(left.createdAt),
      );
  }, [filter, posts, query, sort]);

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
      </div>

      <div className="flex items-center justify-between gap-3">
        <Text tone="muted" size="sm">
          게시글 {visible.length}개
        </Text>
      </div>

      {visible.length === 0 ? (
        <div className="rounded-xl border border-dashed border-neutral-300 px-5 py-12 text-center">
          <Text tone="muted">
            {query.trim().length > 0
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
    </div>
  );
}
