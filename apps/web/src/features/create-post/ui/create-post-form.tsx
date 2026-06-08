"use client";

import { type FormEvent, type ChangeEvent, useState } from "react";
import { publishPost } from "@entities/community";
import { ApiError, uploadImage } from "@shared/api";
import { Button, Field, Textarea } from "@shared/ui";

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
  const [uploading, setUploading] = useState(false);

  async function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (file === undefined) {
      return;
    }
    setError(undefined);
    setUploading(true);
    try {
      const uploaded = await uploadImage(file);
      setImageUrl(uploaded.url);
    } catch (cause) {
      setError(
        cause instanceof ApiError ? cause.message : "이미지 업로드에 실패했습니다.",
      );
    } finally {
      setUploading(false);
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(undefined);
    if (imageUrl.trim().length === 0) {
      setError("이미지를 업로드해 주세요.");
      return;
    }
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
      <Field label="이미지">
        {({ inputId, describedBy }) => (
          <div className="flex flex-col gap-2">
            <input
              id={inputId}
              type="file"
              accept="image/*"
              aria-describedby={describedBy}
              onChange={handleFileChange}
              disabled={uploading || submitting}
              className="text-sm text-neutral-700 file:mr-3 file:rounded-md file:border file:border-neutral-200 file:bg-neutral-50 file:px-3 file:py-1.5 file:text-sm"
            />
            {uploading ? (
              <p className="text-sm text-neutral-500">업로드 중...</p>
            ) : null}
            {imageUrl.length > 0 && !uploading ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                src={imageUrl}
                alt="업로드한 이미지 미리보기"
                className="h-32 w-32 rounded-lg border border-neutral-200/70 object-cover"
              />
            ) : null}
          </div>
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
      <Button type="submit" size="lg" fullWidth disabled={submitting || uploading}>
        {submitting ? "게시 중..." : "게시하기"}
      </Button>
    </form>
  );
}
