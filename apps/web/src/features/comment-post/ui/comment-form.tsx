"use client";

import { type FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { commentOnPost } from "@entities/community";
import { useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import { loginHrefForCurrentPage } from "@shared/lib";
import { Button, Input } from "@shared/ui";

export interface CommentFormProps {
  readonly postId: string;
  readonly onAdded: () => void;
}

export function CommentForm({ postId, onAdded }: CommentFormProps) {
  const router = useRouter();
  const { session } = useSession();
  const [content, setContent] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) {
      router.push(loginHrefForCurrentPage());
      return;
    }
    if (content.trim().length === 0) {
      return;
    }
    setBusy(true);
    setError(undefined);
    try {
      await commentOnPost(postId, session.accountId, content.trim());
      setContent("");
      onAdded();
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "댓글 작성 중 오류가 발생했습니다.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="flex flex-col gap-2" onSubmit={handleSubmit} noValidate>
      <div className="flex gap-2">
        <Input
          value={content}
          placeholder="댓글을 입력하세요"
          onChange={(e) => setContent(e.target.value)}
        />
        <Button type="submit" disabled={busy}>
          등록
        </Button>
      </div>
      {error ? <span className="text-sm text-danger">{error}</span> : null}
    </form>
  );
}
