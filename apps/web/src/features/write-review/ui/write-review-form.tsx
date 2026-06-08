"use client";

import { useState } from "react";
import { writeReview } from "@entities/review";
import { ApiError } from "@shared/api";
import { Button, Field, Select, Textarea } from "@shared/ui";

export interface WriteReviewFormProps {
  readonly orderId: string;
  /** 후기 작성자(=구매자) id. 백엔드에서 주문 구매자와 일치하는지 검증한다. */
  readonly reviewerId: string;
  /** 작성 완료 후 콜백(목록 갱신 등). */
  readonly onSubmitted?: () => void;
}

const RATINGS = [5, 4, 3, 2, 1] as const;

export function WriteReviewForm({ orderId, reviewerId, onSubmitted }: WriteReviewFormProps) {
  const [rating, setRating] = useState(5);
  const [content, setContent] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);
  const [done, setDone] = useState(false);

  async function submit() {
    if (content.trim().length === 0) {
      setError("후기 내용을 입력해 주세요.");
      return;
    }
    setError(undefined);
    setBusy(true);
    try {
      await writeReview({ orderId, reviewerId, rating, content: content.trim() });
      setDone(true);
      setContent("");
      onSubmitted?.();
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "후기 등록 중 오류가 발생했습니다.");
    } finally {
      setBusy(false);
    }
  }

  if (done) {
    return <p className="text-sm text-success">후기가 등록되었습니다. 감사합니다!</p>;
  }

  return (
    <div className="flex flex-col gap-4">
      <Field label="평점">
        {({ inputId }) => (
          <Select
            id={inputId}
            value={rating}
            onChange={(e) => setRating(Number(e.target.value))}
            disabled={busy}
          >
            {RATINGS.map((r) => (
              <option key={r} value={r}>
                {"★".repeat(r)} ({r}점)
              </option>
            ))}
          </Select>
        )}
      </Field>

      <Field label="후기" error={error}>
        {({ inputId, describedBy }) => (
          <Textarea
            id={inputId}
            aria-describedby={describedBy}
            value={content}
            maxLength={1000}
            placeholder="거래 경험을 남겨 주세요 (최대 1000자)"
            invalid={Boolean(error)}
            disabled={busy}
            onChange={(e) => setContent(e.target.value)}
          />
        )}
      </Field>

      <Button size="lg" disabled={busy} onClick={submit}>
        {busy ? "등록 중..." : "후기 등록"}
      </Button>
    </div>
  );
}
