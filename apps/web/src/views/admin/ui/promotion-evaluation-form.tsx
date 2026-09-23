"use client";

import { useEffect, useState } from "react";
import {
  fetchAdminPromotionPostEvaluation,
  saveAdminPromotionPostEvaluation,
  type EvaluationCriterion,
  type EvaluationReasonTag,
  type FirstReviewVerdict,
  type HoldReasonKind,
  type RecordPromotionPostEvaluationInput,
} from "@entities/admin";
import { useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import { Button, Heading, Select, Text, Textarea } from "@shared/ui";
import {
  EVALUATION_CRITERION_LABEL,
  EVALUATION_REASON_TAG_LABEL,
  FIRST_REVIEW_VERDICT_LABEL,
  HOLD_REASON_KIND_LABEL,
} from "../model/labels";

const CRITERIA: readonly EvaluationCriterion[] = [
  "FACT_BASIS",
  "SCREEN_MATCH",
  "READER_VALUE",
  "PERSONA_NATURALNESS",
  "SPECIFICITY_VARIETY",
];

const VERDICTS: readonly FirstReviewVerdict[] = [
  "USE_AS_IS",
  "MINOR_EDIT",
  "MAJOR_REWRITE",
  "UNUSABLE",
  "HOLD",
];

const HOLD_REASONS: readonly HoldReasonKind[] = ["CONFIRMED_DEFECT", "EVIDENCE_GAP"];

const REASON_TAGS: readonly EvaluationReasonTag[] = [
  "FACTUAL_ERROR",
  "EVIDENCE_GAP",
  "SCREEN_MISMATCH",
  "INFO_EXPOSURE",
  "LOW_PROMO_VALUE",
  "TONE",
  "REPETITION",
  "FORMAT",
  "OTHER",
];

const NOTES_MAX_LENGTH = 2000;

export interface PromotionEvaluationFormProps {
  readonly promotionPostId: string;
  /** 모달 제목에 붙는 대상 설명. 예: "Threads · a1b2c3d4" */
  readonly target: string;
  readonly onSaved: () => void;
  readonly onCancel: () => void;
}

/**
 * 홍보 초안 품질 평가 입력 모달(promotion-review/eval.md). 채점 자체는 사람이 하고, 이 폼은
 * 그 결과를 저장할 뿐이다 — 자동 채점기가 아니다. 게시물당 평가는 1건이며 다시 저장하면
 * 기존 평가를 덮어쓴다.
 */
export function PromotionEvaluationForm({
  promotionPostId,
  target,
  onSaved,
  onCancel,
}: PromotionEvaluationFormProps) {
  const { session } = useSession();
  const token = session?.sessionToken ?? null;

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);

  const [criterionScores, setCriterionScores] = useState<
    Partial<Record<EvaluationCriterion, number>>
  >({});
  const [verdict, setVerdict] = useState<FirstReviewVerdict | "">("");
  const [holdReasonKind, setHoldReasonKind] = useState<HoldReasonKind | "">("");
  const [reasonTags, setReasonTags] = useState<readonly EvaluationReasonTag[]>([]);
  const [factualFixNeeded, setFactualFixNeeded] = useState<"" | "true" | "false">("");
  const [reviewSeconds, setReviewSeconds] = useState("");
  const [reviseSeconds, setReviseSeconds] = useState("");
  const [notes, setNotes] = useState("");

  useEffect(() => {
    if (token === null) {
      return;
    }
    let active = true;
    void fetchAdminPromotionPostEvaluation(token, promotionPostId)
      .then((evaluation) => {
        if (!active) return;
        setCriterionScores({ ...evaluation.criterionScores });
        setVerdict(evaluation.verdict);
        setHoldReasonKind(evaluation.holdReasonKind ?? "");
        setReasonTags(evaluation.reasonTags);
        setFactualFixNeeded(
          evaluation.factualFixNeeded === null
            ? ""
            : evaluation.factualFixNeeded
              ? "true"
              : "false",
        );
        setReviewSeconds(evaluation.reviewSeconds === null ? "" : String(evaluation.reviewSeconds));
        setReviseSeconds(evaluation.reviseSeconds === null ? "" : String(evaluation.reviseSeconds));
        setNotes(evaluation.notes ?? "");
      })
      .catch((cause: unknown) => {
        // 404는 "아직 평가하지 않음"이다 — 빈 폼으로 시작한다.
        if (cause instanceof ApiError && cause.status === 404) {
          return;
        }
        if (active) {
          setError(cause instanceof ApiError ? cause.message : "평가를 불러오지 못했습니다.");
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [token, promotionPostId]);

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") onCancel();
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onCancel]);

  function setScore(criterion: EvaluationCriterion, value: string) {
    setCriterionScores((prev) => {
      const next = { ...prev };
      if (value === "") {
        delete next[criterion];
      } else {
        next[criterion] = Number(value);
      }
      return next;
    });
  }

  function toggleReasonTag(tag: EvaluationReasonTag) {
    setReasonTags((prev) => (prev.includes(tag) ? prev.filter((t) => t !== tag) : [...prev, tag]));
  }

  async function handleSave() {
    if (token === null || verdict === "") return;
    setSaving(true);
    setError(undefined);
    try {
      const input: RecordPromotionPostEvaluationInput = {
        criterionScores,
        verdict,
        holdReasonKind: verdict === "HOLD" && holdReasonKind !== "" ? holdReasonKind : null,
        reasonTags,
        factualFixNeeded:
          factualFixNeeded === "" ? null : factualFixNeeded === "true" ? true : false,
        reviewSeconds: reviewSeconds === "" ? null : Number(reviewSeconds),
        reviseSeconds: reviseSeconds === "" ? null : Number(reviseSeconds),
        notes: notes.trim() === "" ? null : notes.trim(),
      };
      await saveAdminPromotionPostEvaluation(token, promotionPostId, input);
      onSaved();
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "평가 저장에 실패했습니다.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="품질 평가 입력"
      className="fixed inset-0 z-50 flex items-center justify-center bg-neutral-950/60 p-4"
    >
      <div className="flex max-h-[90vh] w-full max-w-2xl flex-col gap-4 overflow-y-auto rounded-lg bg-white p-6 shadow-xl">
        <div className="flex flex-col gap-1">
          <Heading level={3}>품질 평가 입력</Heading>
          <Text tone="muted" size="sm">
            {target}
          </Text>
        </div>

        {loading ? (
          <Text tone="muted" size="sm">
            불러오는 중...
          </Text>
        ) : (
          <>
            <div className="flex flex-col gap-3">
              <Text weight="medium" size="sm">
                루브릭 (0~2점, 미평가는 N/A)
              </Text>
              {CRITERIA.map((criterion) => (
                <label
                  key={criterion}
                  className="flex items-center justify-between gap-3 text-sm text-neutral-700"
                >
                  {EVALUATION_CRITERION_LABEL[criterion]}
                  <Select
                    className="w-28"
                    value={criterionScores[criterion] ?? ""}
                    onChange={(e) => setScore(criterion, e.target.value)}
                  >
                    <option value="">N/A</option>
                    <option value="0">0</option>
                    <option value="1">1</option>
                    <option value="2">2</option>
                  </Select>
                </label>
              ))}
            </div>

            <label className="flex flex-col gap-1.5 text-sm font-medium text-neutral-700">
              첫 검토 판정
              <Select
                value={verdict}
                onChange={(e) => {
                  const next = e.target.value as FirstReviewVerdict | "";
                  setVerdict(next);
                  if (next !== "HOLD") setHoldReasonKind("");
                }}
              >
                <option value="">선택</option>
                {VERDICTS.map((v) => (
                  <option key={v} value={v}>
                    {FIRST_REVIEW_VERDICT_LABEL[v]}
                  </option>
                ))}
              </Select>
            </label>

            {verdict === "HOLD" ? (
              <label className="flex flex-col gap-1.5 text-sm font-medium text-neutral-700">
                보류 사유
                <Select
                  value={holdReasonKind}
                  onChange={(e) => setHoldReasonKind(e.target.value as HoldReasonKind | "")}
                >
                  <option value="">미확인</option>
                  {HOLD_REASONS.map((reason) => (
                    <option key={reason} value={reason}>
                      {HOLD_REASON_KIND_LABEL[reason]}
                    </option>
                  ))}
                </Select>
              </label>
            ) : null}

            <div className="flex flex-col gap-2">
              <Text weight="medium" size="sm">
                이유 태그
              </Text>
              <div className="flex flex-wrap gap-3">
                {REASON_TAGS.map((tag) => (
                  <label key={tag} className="flex items-center gap-1.5 text-sm text-neutral-700">
                    <input
                      type="checkbox"
                      checked={reasonTags.includes(tag)}
                      onChange={() => toggleReasonTag(tag)}
                    />
                    {EVALUATION_REASON_TAG_LABEL[tag]}
                  </label>
                ))}
              </div>
            </div>

            <label className="flex flex-col gap-1.5 text-sm font-medium text-neutral-700">
              제품 사실 수정 필요
              <Select
                value={factualFixNeeded}
                onChange={(e) => setFactualFixNeeded(e.target.value as "" | "true" | "false")}
              >
                <option value="">미확인</option>
                <option value="true">예</option>
                <option value="false">아니오</option>
              </Select>
            </label>

            <div className="grid grid-cols-2 gap-3">
              <label className="flex flex-col gap-1.5 text-sm font-medium text-neutral-700">
                검토 소요시간(초)
                <input
                  type="number"
                  min={0}
                  value={reviewSeconds}
                  onChange={(e) => setReviewSeconds(e.target.value)}
                  className="h-11 w-full rounded-md border border-neutral-300 px-3 text-base"
                />
              </label>
              <label className="flex flex-col gap-1.5 text-sm font-medium text-neutral-700">
                수정 소요시간(초)
                <input
                  type="number"
                  min={0}
                  value={reviseSeconds}
                  onChange={(e) => setReviseSeconds(e.target.value)}
                  className="h-11 w-full rounded-md border border-neutral-300 px-3 text-base"
                />
              </label>
            </div>

            <label className="flex flex-col gap-1.5 text-sm font-medium text-neutral-700">
              메모
              <Textarea
                rows={3}
                maxLength={NOTES_MAX_LENGTH}
                value={notes}
                placeholder="문제 문구·이미지, 창작 표현 유지 여부 등"
                onChange={(e) => setNotes(e.target.value)}
              />
            </label>

            {error !== undefined ? <p className="text-sm text-danger">{error}</p> : null}

            <div className="flex justify-end gap-2">
              <Button variant="secondary" onClick={onCancel} disabled={saving}>
                취소
              </Button>
              <Button onClick={() => void handleSave()} disabled={saving || verdict === ""}>
                {saving ? "저장 중..." : "저장"}
              </Button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
