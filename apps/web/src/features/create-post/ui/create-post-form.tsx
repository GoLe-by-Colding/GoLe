"use client";

import { type FormEvent, type ChangeEvent, useState } from "react";
import { POST_TOPICS, publishPost, type PostType } from "@entities/community";
import { ApiError, uploadImages } from "@shared/api";
import { Button, Field, Select, Textarea } from "@shared/ui";

export interface CreatePostFormProps {
  readonly authorId: string;
  readonly onCreated: (postId: string) => void;
}

const MAX_IMAGES = 5;

export function CreatePostForm({ authorId, onCreated }: CreatePostFormProps) {
  const [content, setContent] = useState("");
  const [imageUrls, setImageUrls] = useState<readonly string[]>([]);
  const [topic, setTopic] = useState<PostType>("general");
  const [error, setError] = useState<string | undefined>(undefined);
  const [submitting, setSubmitting] = useState(false);
  const [uploading, setUploading] = useState(false);

  async function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    const selected = Array.from(event.target.files ?? []);
    if (selected.length === 0) {
      return;
    }
    setError(undefined);
    const remaining = MAX_IMAGES - imageUrls.length;
    if (remaining <= 0) {
      setError(`이미지는 최대 ${MAX_IMAGES}장까지 올릴 수 있어요.`);
      return;
    }
    setUploading(true);
    try {
      const uploaded = await uploadImages(selected.slice(0, remaining));
      setImageUrls((prev) => [...prev, ...uploaded.map((u) => u.url)]);
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "이미지 업로드에 실패했습니다.");
    } finally {
      setUploading(false);
      event.target.value = "";
    }
  }

  function removeImage(url: string) {
    setImageUrls((prev) => prev.filter((u) => u !== url));
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(undefined);
    setSubmitting(true);
    try {
      const post = await publishPost({
        authorId,
        content,
        imageUrls: [...imageUrls],
        topic,
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
      <Field label="주제">
        {({ inputId }) => (
          <Select id={inputId} value={topic} onChange={(e) => setTopic(e.target.value as PostType)}>
            {POST_TOPICS.map((t) => (
              <option key={t.key} value={t.key}>
                {t.label}
              </option>
            ))}
          </Select>
        )}
      </Field>
      <Field label="내용">
        {({ inputId, describedBy }) => (
          <Textarea
            id={inputId}
            value={content}
            placeholder="자랑·리뷰·질문·팁·창작(MOC)·이스터에그 등 무엇이든 공유해보세요."
            aria-describedby={describedBy}
            onChange={(e) => setContent(e.target.value)}
            required
          />
        )}
      </Field>
      <Field
        label="이미지 (선택)"
        hint={`최대 ${MAX_IMAGES}장 · 직접 촬영하거나 제작한 이미지만 올려주세요. LEGO 공식 제품·카탈로그 이미지 도용은 금지됩니다.`}
      >
        {({ inputId, describedBy }) => (
          <div className="flex flex-col gap-3">
            <input
              id={inputId}
              type="file"
              accept="image/jpeg,image/png,image/gif,image/webp"
              multiple
              aria-describedby={describedBy}
              onChange={handleFileChange}
              disabled={uploading || submitting || imageUrls.length >= MAX_IMAGES}
              className="text-sm text-neutral-700 file:mr-3 file:rounded-md file:border file:border-neutral-200 file:bg-neutral-50 file:px-3 file:py-1.5 file:text-sm"
            />
            {uploading ? <p className="text-sm text-neutral-500">업로드 중...</p> : null}
            {imageUrls.length > 0 ? (
              <ul className="flex flex-wrap gap-3">
                {imageUrls.map((url, index) => (
                  <li key={url} className="relative">
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    <img
                      src={url}
                      alt={`이미지 ${index + 1}`}
                      className="h-24 w-24 rounded-lg border border-neutral-200/70 object-cover"
                    />
                    <button
                      type="button"
                      onClick={() => removeImage(url)}
                      aria-label={`이미지 ${index + 1} 삭제`}
                      className="absolute -right-2 -top-2 flex h-6 w-6 items-center justify-center rounded-full bg-neutral-900/80 text-sm text-white"
                    >
                      ×
                    </button>
                  </li>
                ))}
              </ul>
            ) : null}
          </div>
        )}
      </Field>
      <Button type="submit" size="lg" fullWidth disabled={submitting || uploading}>
        {submitting ? "게시 중..." : "게시하기"}
      </Button>
    </form>
  );
}
