"use client";

import { type FormEvent, useCallback, useEffect, useState } from "react";
import {
  fetchListingComments,
  postListingComment,
  type ListingCommentItem,
} from "@entities/listing";
import { useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import { Button, Heading, Skeleton } from "@shared/ui";

export interface ListingQnaProps {
  readonly listingId: string;
}

/**
 * 매물 Q&A 댓글. 비로그인은 읽기만 가능, 로그인 시 작성 가능.
 */
export function ListingQna({ listingId }: ListingQnaProps) {
  const { session } = useSession();
  const [comments, setComments] = useState<readonly ListingCommentItem[] | null>(null);
  const [content, setContent] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);

  const load = useCallback(async () => {
    try {
      const data = await fetchListingComments(listingId);
      setComments(data);
    } catch {
      setComments([]);
    }
  }, [listingId]);

  useEffect(() => {
    let active = true;
    fetchListingComments(listingId)
      .then((data) => {
        if (active) {
          setComments(data);
        }
      })
      .catch(() => {
        if (active) {
          setComments([]);
        }
      });
    return () => {
      active = false;
    };
  }, [listingId]);

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!session || !content.trim()) {
      return;
    }
    setSubmitting(true);
    setError(undefined);
    try {
      await postListingComment(listingId, session.accountId, content.trim());
      setContent("");
      await load();
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "댓글 작성에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <Heading level={2}>문의 Q&amp;A</Heading>

      {/* 댓글 목록 */}
      {comments === null ? (
        <div className="flex flex-col gap-2">
          <Skeleton className="h-10 w-full rounded-lg" />
          <Skeleton className="h-10 w-4/5 rounded-lg" />
        </div>
      ) : comments.length === 0 ? (
        <p className="text-sm text-neutral-400">아직 문의가 없어요. 첫 질문을 남겨보세요!</p>
      ) : (
        <ul className="flex flex-col divide-y divide-neutral-100 overflow-hidden rounded-lg border border-neutral-200">
          {comments.map((c) => (
            <li key={c.id} className="px-4 py-3">
              <div className="flex items-center gap-2">
                <span className="grid h-6 w-6 shrink-0 place-items-center rounded-full bg-brand-50 text-xs font-bold text-brand-700">
                  {c.authorId.slice(0, 1).toUpperCase()}
                </span>
                <span className="text-xs text-neutral-500">{c.authorId.slice(0, 8)}</span>
                <span className="ml-auto text-xs text-neutral-300">
                  {new Date(c.createdAt).toLocaleDateString("ko-KR")}
                </span>
              </div>
              <p className="mt-1 whitespace-pre-wrap text-sm text-neutral-800">{c.content}</p>
            </li>
          ))}
        </ul>
      )}

      {/* 작성 폼 */}
      {session ? (
        <form onSubmit={handleSubmit} className="flex flex-col gap-2">
          {error ? (
            <p className="text-sm text-danger" role="alert">
              {error}
            </p>
          ) : null}
          <textarea
            value={content}
            onChange={(e) => setContent(e.target.value)}
            placeholder="궁금한 점을 남겨주세요."
            rows={3}
            required
            className="w-full rounded-md border border-neutral-200 bg-white px-4 py-2.5 text-sm text-neutral-900 outline-none transition-colors focus-visible:border-brand-400 focus-visible:ring-2 focus-visible:ring-brand-100"
          />
          <div className="flex justify-end">
            <Button type="submit" size="sm" disabled={submitting || !content.trim()}>
              {submitting ? "등록 중..." : "문의 남기기"}
            </Button>
          </div>
        </form>
      ) : (
        <p className="text-sm text-neutral-500">로그인 후 문의를 남길 수 있어요.</p>
      )}
    </div>
  );
}
