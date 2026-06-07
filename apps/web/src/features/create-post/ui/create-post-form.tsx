"use client";

import { type FormEvent, useState } from "react";
import { publishPost } from "@entities/community";
import { ApiError } from "@shared/api";
import { Button, Field, Input, Textarea } from "@shared/ui";

export interface CreatePostFormProps {
  readonly authorId: string;
  readonly onCreated: (postId: string) => void;
}

export function CreatePostForm({ authorId, onCreated }: CreatePostFormProps) {
  const [content, setContent] = useState("");
  const [imageUrl, setImageUrl] = useState("");
  const [moc, setMoc] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(undefined);
    setSubmitting(true);
    try {
      const post = await publishPost({
        authorId,
        content,
        imageUrls: imageUrl.trim().length > 0 ? [imageUrl.trim()] : [],
        moc,
      });
      onCreated(post.id);
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "게시 중 오류가 발생했습니다.");
      setSubmitting(false);
    }
  }

  return (
    <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
      {error ? (
        <p className="p-3 rounded-md bg-danger-soft text-danger text-sm" role="alert">
          {error}
        </p>
      ) : null}
      <Field label="내용">
        {({ inputId, describedBy }) => (
          <Textarea
            id={inputId}
            value={content}
            placeholder="레고 자랑, 후기, MOC를 공유하세요."
            aria-describedby={describedBy}
            onChange={(e) => setContent(e.target.value)}
            required
          />
        )}
      </Field>
      <Field label="이미지 URL">
        {({ inputId, describedBy }) => (
          <Input
            id={inputId}
            type="url"
            value={imageUrl}
            placeholder="https://..."
            aria-describedby={describedBy}
            onChange={(e) => setImageUrl(e.target.value)}
            required
          />
        )}
      </Field>
      <label className="inline-flex items-center gap-2 text-sm text-neutral-700">
        <input
          type="checkbox"
          checked={moc}
          onChange={(e) => setMoc(e.target.checked)}
        />
        MOC(직접 창작물)로 게시
      </label>
      <Button type="submit" size="lg" fullWidth disabled={submitting}>
        {submitting ? "게시 중..." : "게시하기"}
      </Button>
    </form>
  );
}
