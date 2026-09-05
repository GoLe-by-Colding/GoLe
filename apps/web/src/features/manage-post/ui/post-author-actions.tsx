"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { deletePost, editPost, type Post } from "@entities/community";
import { useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import { Button, Textarea } from "@shared/ui";

export interface PostAuthorActionsProps {
  readonly post: Post;
  /** 수정 저장 후 갱신된 게시글 전달 */
  readonly onUpdated: (post: Post) => void;
}

/**
 * 작성자 전용 게시글 관리 — 본인 글에만 수정/삭제 노출.
 * 수정은 인라인 textarea로 본문을 편집하고, 삭제는 확인 후 커뮤니티로 이동한다.
 */
export function PostAuthorActions({ post, onUpdated }: PostAuthorActionsProps) {
  const router = useRouter();
  const { session } = useSession();
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(post.content);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!session || session.accountId !== post.authorId) {
    return null;
  }

  async function handleSave() {
    if (!session || busy) return;
    const content = draft.trim();
    if (!content) {
      setError("내용을 입력해 주세요.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const updated = await editPost(post.id, {
        requesterId: session.accountId,
        content,
        imageKeys:
          post.imageKeys ??
          post.imageUrls.flatMap((url) => {
            const marker = "/api/v1/media/";
            const offset = url.indexOf(marker);
            return offset < 0 ? [] : [url.slice(offset + marker.length)];
          }),
      });
      onUpdated(updated);
      setEditing(false);
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "수정에 실패했어요.");
    } finally {
      setBusy(false);
    }
  }

  async function handleDelete() {
    if (!session || busy) return;
    if (!window.confirm("이 게시글을 삭제할까요? 되돌릴 수 없습니다.")) return;
    setBusy(true);
    setError(null);
    try {
      await deletePost(post.id, session.accountId);
      router.push("/community");
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "삭제에 실패했어요.");
      setBusy(false);
    }
  }

  if (editing) {
    return (
      <div className="flex flex-col gap-2 rounded-lg border border-neutral-200 bg-neutral-50 p-3">
        <Textarea
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          rows={5}
          maxLength={2000}
        />
        {error !== null ? <p className="text-sm text-danger">{error}</p> : null}
        <div className="flex justify-end gap-2">
          <Button
            variant="secondary"
            size="sm"
            onClick={() => {
              setEditing(false);
              setDraft(post.content);
              setError(null);
            }}
          >
            취소
          </Button>
          <Button size="sm" onClick={handleSave} disabled={busy}>
            {busy ? "저장 중…" : "저장"}
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex items-center gap-3">
      <button
        type="button"
        onClick={() => setEditing(true)}
        className="text-sm font-medium text-neutral-500 transition-colors hover:text-brand-600"
      >
        수정
      </button>
      <span aria-hidden="true" className="text-neutral-200">
        |
      </span>
      <button
        type="button"
        onClick={handleDelete}
        disabled={busy}
        className="text-sm font-medium text-neutral-500 transition-colors hover:text-danger disabled:opacity-50"
      >
        삭제
      </button>
      {error !== null ? <span className="text-sm text-danger">{error}</span> : null}
    </div>
  );
}
