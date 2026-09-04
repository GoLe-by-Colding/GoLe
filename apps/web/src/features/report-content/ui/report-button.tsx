"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import {
  REPORT_REASON_LABEL,
  REPORT_REASONS,
  submitReport,
  type ReportReason,
  type ReportTargetType,
} from "@entities/report";
import { useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import { loginHrefForCurrentPage } from "@shared/lib";
import { Button, FlagIcon, Textarea } from "@shared/ui";

export interface ReportButtonProps {
  readonly targetType: ReportTargetType;
  readonly targetId: string;
  readonly parentId?: string;
  readonly compact?: boolean;
}

type SubmitState = "idle" | "busy" | "done" | "duplicate";

function hasFinalConsonant(value: string): boolean {
  const last = value.charCodeAt(value.length - 1);
  return last >= 0xac00 && last <= 0xd7a3 && (last - 0xac00) % 28 !== 0;
}

function withObjectParticle(value: string): string {
  return `${value}${hasFinalConsonant(value) ? "을" : "를"}`;
}

function withConditionalCopula(value: string): string {
  return `${value}${hasFinalConsonant(value) ? "이라면" : "라면"}`;
}

/**
 * 신고하기 — 가품·이미지 도용·사기 콘텐츠를 운영자에게 접수한다(notice & takedown).
 * 매물 상세·커뮤니티 글·거래 후기에서 공용.
 */
export function ReportButton({
  targetType,
  targetId,
  parentId,
  compact = false,
}: ReportButtonProps) {
  const router = useRouter();
  const { session } = useSession();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState<ReportReason>(
    targetType === "LISTING" ? "COUNTERFEIT" : "INAPPROPRIATE",
  );
  const [detail, setDetail] = useState("");
  const [state, setState] = useState<SubmitState>("idle");
  const [error, setError] = useState<string | null>(null);
  const isReview = targetType === "REVIEW";
  const isComment = targetType === "COMMENT";
  const isTextContent = isReview || isComment;
  const targetLabel = isReview ? "후기" : isComment ? "댓글" : "콘텐츠";
  const availableReasons: readonly ReportReason[] = isTextContent
    ? ["INAPPROPRIATE", "OTHER"]
    : REPORT_REASONS;

  function handleOpen() {
    if (!session) {
      router.push(loginHrefForCurrentPage());
      return;
    }
    setOpen(true);
  }

  async function handleSubmit() {
    if (!session || state === "busy") {
      return;
    }
    setState("busy");
    setError(null);
    try {
      await submitReport({
        reporterId: session.accountId,
        targetType,
        targetId,
        ...(parentId === undefined ? {} : { parentId }),
        reason,
        detail: detail.trim(),
      });
      setState("done");
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 409) {
        setState("duplicate");
        return;
      }
      setState("idle");
      setError("신고 접수에 실패했어요. 잠시 후 다시 시도해 주세요.");
    }
  }

  function handleClose() {
    setOpen(false);
    setState("idle");
    setDetail("");
    setError(null);
  }

  return (
    <>
      <button
        type="button"
        onClick={handleOpen}
        className={`inline-flex w-fit items-center gap-1.5 text-xs font-medium text-neutral-400 transition-colors hover:text-danger ${
          compact ? "min-h-8" : "min-h-11"
        }`}
      >
        <FlagIcon className="h-4 w-4" />
        신고하기
      </button>

      {open ? (
        <div
          className="fixed inset-0 z-50 grid place-items-center bg-neutral-900/40 p-4"
          role="dialog"
          aria-modal="true"
          aria-label={`${targetLabel} 신고`}
        >
          <div className="flex w-full max-w-md flex-col gap-4 rounded-lg bg-white p-6 shadow-lift">
            {state === "done" || state === "duplicate" ? (
              <>
                <span className="text-lg font-bold text-neutral-900">
                  {state === "done" ? "신고가 접수되었어요" : "이미 신고하신 콘텐츠예요"}
                </span>
                <p className="text-sm leading-relaxed text-neutral-500">
                  {state === "done"
                    ? isTextContent
                      ? `운영팀이 문맥을 확인하고 필요하면 ${withObjectParticle(targetLabel)} 블라인드합니다.`
                      : "운영팀이 확인 후 필요한 조치(매물 내림·게시글 삭제 등)를 진행합니다."
                    : "접수된 신고를 운영팀이 확인하고 있어요. 조금만 기다려 주세요."}
                </p>
                <Button onClick={handleClose} fullWidth>
                  확인
                </Button>
              </>
            ) : (
              <>
                <div className="flex flex-col gap-1">
                  <span className="text-lg font-bold text-neutral-900">신고하기</span>
                  <p className="text-sm text-neutral-500">
                    {isTextContent
                      ? `욕설·스팸·주제와 무관한 ${withConditionalCopula(targetLabel)} 알려주세요. 운영팀이 확인합니다.`
                      : "가품·이미지 도용·사기가 의심되면 알려주세요. 운영팀이 확인 후 조치합니다."}
                  </p>
                </div>
                <fieldset className="flex flex-col gap-2">
                  <legend className="sr-only">신고 사유</legend>
                  {availableReasons.map((value) => (
                    <label
                      key={value}
                      className={`flex cursor-pointer items-center gap-2.5 rounded-md border px-3.5 py-2.5 text-sm font-medium transition-colors ${
                        reason === value
                          ? "border-brand-400 bg-brand-50 text-brand-700"
                          : "border-neutral-200 text-neutral-600 hover:border-neutral-300"
                      }`}
                    >
                      <input
                        type="radio"
                        name="report-reason"
                        value={value}
                        checked={reason === value}
                        onChange={() => setReason(value)}
                        className="accent-brand-600"
                      />
                      {REPORT_REASON_LABEL[value]}
                    </label>
                  ))}
                </fieldset>
                <Textarea
                  value={detail}
                  onChange={(e) => setDetail(e.target.value)}
                  placeholder="상세 내용 (선택, 최대 1000자)"
                  maxLength={1000}
                  rows={3}
                />
                {error !== null ? <p className="text-sm text-danger">{error}</p> : null}
                <div className="flex gap-2">
                  <Button variant="secondary" onClick={handleClose} fullWidth>
                    취소
                  </Button>
                  <Button onClick={handleSubmit} disabled={state === "busy"} fullWidth>
                    {state === "busy" ? "접수 중…" : "신고 접수"}
                  </Button>
                </div>
              </>
            )}
          </div>
        </div>
      ) : null}
    </>
  );
}
