"use client";

import { useRef, useState } from "react";
import { ApiError, uploadImages } from "@shared/api";
import { validateListingDraft, type ListingDraft } from "./listing-draft";

export interface ListingDraftForm {
  readonly draft: ListingDraft;
  readonly update: (patch: Partial<ListingDraft>) => void;
  readonly error: string | undefined;
  readonly submitting: boolean;
  readonly uploading: boolean;
  readonly maxPhotos: number;
  readonly addPhotos: (files: readonly File[]) => Promise<void>;
  readonly removePhoto: (key: string) => void;
  /**
   * 검증 → 저장. 성공하면 잠금을 풀지 않는다 — 호출부가 곧바로 다른 화면으로 이동하므로
   * 그 사이 두 번 제출되지 않게 한다. 실패하면 문구를 보여 주고 다시 제출할 수 있게 연다.
   */
  readonly submit: (
    save: (draft: ListingDraft) => Promise<void>,
    describeError: (cause: unknown) => string,
  ) => Promise<void>;
}

/** 등록·수정 폼의 상태와 사진 업로드·제출 잠금. 두 폼이 같은 규칙을 쓰도록 한곳에 둔다. */
export function useListingDraftForm(initial: ListingDraft, maxPhotos: number): ListingDraftForm {
  const [draft, setDraft] = useState<ListingDraft>(initial);
  const [error, setError] = useState<string | undefined>(undefined);
  const [submitting, setSubmitting] = useState(false);
  const [uploading, setUploading] = useState(false);
  const uploadLock = useRef(false);
  const submitLock = useRef(false);

  function update(patch: Partial<ListingDraft>) {
    setDraft((prev) => ({ ...prev, ...patch }));
  }

  async function addPhotos(files: readonly File[]) {
    if (uploadLock.current || submitLock.current || files.length === 0) return;
    setError(undefined);
    const remaining = maxPhotos - draft.photos.length;
    if (remaining <= 0) {
      setError(`이미지는 최대 ${maxPhotos}장까지 올릴 수 있어요.`);
      return;
    }
    uploadLock.current = true;
    setUploading(true);
    try {
      const uploaded = await uploadImages(files.slice(0, remaining));
      setDraft((prev) => ({ ...prev, photos: [...prev.photos, ...uploaded] }));
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "이미지 업로드에 실패했습니다.");
    } finally {
      uploadLock.current = false;
      setUploading(false);
    }
  }

  function removePhoto(key: string) {
    setDraft((prev) => ({ ...prev, photos: prev.photos.filter((photo) => photo.key !== key) }));
  }

  async function submit(
    save: (draft: ListingDraft) => Promise<void>,
    describeError: (cause: unknown) => string,
  ) {
    if (uploadLock.current || submitLock.current) return;
    setError(undefined);
    const invalid = validateListingDraft(draft);
    if (invalid !== undefined) {
      setError(invalid);
      return;
    }
    submitLock.current = true;
    setSubmitting(true);
    try {
      await save(draft);
    } catch (cause) {
      setError(describeError(cause));
      submitLock.current = false;
      setSubmitting(false);
    }
  }

  return {
    draft,
    update,
    error,
    submitting,
    uploading,
    maxPhotos,
    addPhotos,
    removePhoto,
    submit,
  };
}
