"use client";

import { type FormEvent, useEffect, useState } from "react";
import {
  fetchInterestTags,
  INTEREST_TAG_MAX,
  INTEREST_TAG_MIN,
  type InterestTag,
  setInterestTags,
} from "@entities/user";
import { ApiError } from "@shared/api";
import { Button, Skeleton } from "@shared/ui";

export interface InterestTagsPickerProps {
  /** 이전에 고른 태그 key 목록. 재개 시 선택 상태를 되살린다. */
  readonly initialSelected?: readonly string[];
  /** 저장이 끝나면 호출된다. */
  readonly onCompleted: () => void;
}

/**
 * 온보딩 3단계 — 관심 태그 선택(R6).
 *
 * 목록은 백엔드가 관리하는 curated 상수다(D8) — 프론트에 하드코딩하지 않고 받아 쓴다.
 * 개수 제한은 화면에서 먼저 막지만 최종 판정은 서버가 다시 한다.
 */
export function InterestTagsPicker({ initialSelected = [], onCompleted }: InterestTagsPickerProps) {
  // 목록은 {key, label}이고, 계정에 저장되는 값은 key다.
  const [tags, setTags] = useState<readonly InterestTag[] | null>(null);
  const [selected, setSelected] = useState<readonly string[]>(initialSelected);
  const [error, setError] = useState<string | undefined>(undefined);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    void fetchInterestTags(controller.signal)
      .then(setTags)
      .catch((cause: unknown) => {
        if (controller.signal.aborted) return;
        setTags([]);
        setError(cause instanceof ApiError ? cause.message : "관심 태그를 불러오지 못했습니다.");
      });
    return () => controller.abort();
  }, []);

  function toggle(key: string): void {
    setError(undefined);
    setSelected((prev) => {
      if (prev.includes(key)) {
        return prev.filter((item) => item !== key);
      }
      if (prev.length >= INTEREST_TAG_MAX) {
        setError(`관심 태그는 최대 ${INTEREST_TAG_MAX}개까지 고를 수 있어요.`);
        return prev;
      }
      return [...prev, key];
    });
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (selected.length < INTEREST_TAG_MIN) {
      setError(`관심 태그를 ${INTEREST_TAG_MIN}개 이상 골라 주세요.`);
      return;
    }
    setError(undefined);
    setSubmitting(true);
    try {
      await setInterestTags(selected);
      onCompleted();
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "저장 중 오류가 발생했습니다.");
    } finally {
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

      <p className="text-sm text-neutral-600">
        관심 있는 테마를 {INTEREST_TAG_MIN}~{INTEREST_TAG_MAX}개 골라 주세요. 고른 취향에 맞춰
        매물을 추천해 드려요.
      </p>

      {tags === null ? (
        <div className="flex flex-wrap gap-2" aria-hidden="true">
          {Array.from({ length: 8 }, (_, index) => (
            <Skeleton key={index} className="h-9 w-24 rounded-full" />
          ))}
        </div>
      ) : (
        <div className="flex flex-wrap gap-2" role="group" aria-label="관심 태그">
          {tags.map((tag) => {
            const active = selected.includes(tag.key);
            return (
              <button
                key={tag.key}
                type="button"
                aria-pressed={active}
                onClick={() => toggle(tag.key)}
                className={`rounded-full border px-4 py-2 text-sm font-medium transition-colors ${
                  active
                    ? "border-brand-600 bg-brand-600 text-white"
                    : "border-neutral-300 bg-white text-neutral-700 hover:border-neutral-400 hover:bg-neutral-50"
                }`}
              >
                {tag.label}
              </button>
            );
          })}
        </div>
      )}

      <p className="text-sm text-neutral-500" role="status" aria-live="polite">
        {selected.length}/{INTEREST_TAG_MAX} 선택됨
      </p>

      <Button type="submit" size="lg" fullWidth disabled={submitting || tags === null}>
        {submitting ? "저장 중..." : "다음"}
      </Button>
    </form>
  );
}
