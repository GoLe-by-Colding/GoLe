"use client";

import { type FormEvent, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { commentOnPost, type Comment } from "@entities/community";
import { useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import { loginHrefForCurrentPage } from "@shared/lib";
import { Button, Input } from "@shared/ui";

export interface CommentFormProps {
  readonly postId: string;
  readonly onAdded: (comment: Comment) => void;
}

export function CommentForm({ postId, onAdded }: CommentFormProps) {
  const router = useRouter();
  const { session } = useSession();
  const [content, setContent] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);
  const submitLock = useRef(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitLock.current) return;
    if (!session) {
      router.push(loginHrefForCurrentPage());
      return;
    }
    if (content.trim().length === 0) {
      return;
    }
    submitLock.current = true;
    setBusy(true);
    setError(undefined);
    try {
      const comment = await commentOnPost(postId, session.accountId, content.trim());
      setContent("");
      onAdded(comment);
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "댓글 작성 중 오류가 발생했습니다.");
    } finally {
      submitLock.current = false;
      setBusy(false);
    }
  }

  return (
    <form className="flex flex-col gap-2" onSubmit={handleSubmit} noValidate>
      <div className="flex gap-2">
        <Input
          aria-label="댓글"
          value={content}
          placeholder="댓글을 입력하세요"
          onChange={(e) => setContent(e.target.value)}
          maxLength={1000}
          disabled={busy}
        />
        <Button type="submit" disabled={busy}>
          {busy ? "등록 중…" : "등록"}
        </Button>
      </div>
      {error ? (
        <span role="alert" className="text-sm text-danger">
          {error}
        </span>
      ) : null}
    </form>
  );
}
