"use client";

import { useState } from "react";
import { POST_TOPICS, type Post, type PostType } from "@entities/community";
import { Text } from "@shared/ui";
import { PostCard } from "@widgets/post-card";

type Filter = PostType | "all";

export interface CommunityFeedProps {
  readonly posts: readonly Post[];
}

/** 주제 탭으로 피드를 필터링한다(클라이언트). */
export function CommunityFeed({ posts }: CommunityFeedProps) {
  const [filter, setFilter] = useState<Filter>("all");
  const filtered = filter === "all" ? posts : posts.filter((p) => p.type === filter);

  function tabClass(active: boolean): string {
    return `rounded-full px-3.5 py-1.5 text-sm font-semibold transition-colors ${
      active ? "bg-brand-600 text-white" : "bg-neutral-100 text-neutral-600 hover:bg-neutral-200"
    }`;
  }

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-wrap gap-2">
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

      {filtered.length === 0 ? (
        <Text tone="muted">해당 주제의 글이 아직 없어요. 첫 글을 남겨보세요!</Text>
      ) : (
        <div className="grid gap-5 [grid-template-columns:repeat(auto-fill,minmax(240px,1fr))]">
          {filtered.map((post) => (
            <PostCard key={post.id} post={post} />
          ))}
        </div>
      )}
    </div>
  );
}
