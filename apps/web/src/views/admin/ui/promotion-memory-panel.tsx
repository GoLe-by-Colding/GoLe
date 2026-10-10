"use client";

import { useEffect, useRef, useState } from "react";
import {
  activateAdminPromotionGuideline,
  dismissAdminPromotionGuideline,
  fetchAdminPromotionFeedback,
  fetchAdminPromotionFeedbackById,
  fetchAdminPromotionGuidelines,
  fetchAdminPromotionRuns,
  retireAdminPromotionGuideline,
  updateAdminPromotionGuideline,
  type AdminPromotionFeedback,
  type AdminPromotionGuideline,
  type AdminPromotionRun,
  type PromotionCategory,
  type PromotionGuidelineStatus,
  type PromotionMemoryTarget,
  type UpdatePromotionGuidelineInput,
} from "@entities/admin";
import { ApiError } from "@shared/api";
import {
  Badge,
  Button,
  Card,
  Field,
  Heading,
  MediaImage,
  Select,
  Text,
  Textarea,
} from "@shared/ui";
import { formatDateTime, shortId } from "../model/labels";
import { AdminStatus } from "./table";

const TARGET_LABEL: Record<PromotionMemoryTarget, string> = {
  CAPTION: "글 작성",
  SCREEN_SELECTION: "화면 선택",
  IMAGE_EDIT: "이미지 다듬기",
};
const CATEGORY_LABEL: Record<PromotionCategory, string> = {
  FEATURE: "기능 홍보",
  SERVICE: "서비스 홍보",
};
const STATUS_LABEL: Record<PromotionGuidelineStatus, string> = {
  PROPOSED: "검토할 제안",
  ACTIVE: "활성",
  DISMISSED: "기각",
  RETIRED: "해제",
};

export function PromotionMemoryPanel({
  token,
  accountId,
  refreshKey,
}: {
  readonly token: string | null;
  readonly accountId: string | null;
  readonly refreshKey: number;
}) {
  const [status, setStatus] = useState<PromotionGuidelineStatus | "ALL">("ALL");
  const [data, setData] = useState<{
    guidelines: readonly AdminPromotionGuideline[];
    feedback: readonly AdminPromotionFeedback[];
    runs: readonly AdminPromotionRun[];
  } | null>(null);
  const [revision, setRevision] = useState(0);
  const [error, setError] = useState<string>();
  const [sourceErrors, setSourceErrors] = useState<readonly string[]>([]);
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const inFlight = useRef(false);

  useEffect(() => {
    if (token === null) return;
    let active = true;
    void Promise.all([
      fetchAdminPromotionGuidelines(token, status === "ALL" ? undefined : status),
      fetchAdminPromotionFeedback(token),
      fetchAdminPromotionRuns(token),
    ])
      .then(async ([guidelines, feedback, runs]) => {
        if (!active) return;
        setData({ guidelines, feedback, runs });
        setError(undefined);
        setSourceErrors([]);
        const missing = [
          ...new Set(guidelines.flatMap((guideline) => guideline.sourceFeedbackIds)),
        ].filter((id) => !feedback.some((entry) => entry.id === id));
        const results = await Promise.allSettled(
          missing.map((id) => fetchAdminPromotionFeedbackById(token, id)),
        );
        if (!active) return;
        setData({
          guidelines,
          feedback: [
            ...feedback,
            ...results.flatMap((result) => (result.status === "fulfilled" ? [result.value] : [])),
          ],
          runs,
        });
        setSourceErrors(missing.filter((_, index) => results[index]?.status === "rejected"));
      })
      .catch((cause: unknown) => {
        if (active) {
          setError(
            cause instanceof ApiError ? cause.message : "홍보 메모리를 불러오지 못했습니다.",
          );
        }
      });
    return () => {
      active = false;
    };
  }, [token, status, revision, refreshKey]);

  async function act(
    guideline: AdminPromotionGuideline,
    action: "activate" | "dismiss" | "retire",
    input?: UpdatePromotionGuidelineInput,
  ) {
    if (token === null || inFlight.current) return;
    inFlight.current = true;
    setBusy(true);
    setError(undefined);
    setNotice("");
    try {
      if (input !== undefined) await updateAdminPromotionGuideline(token, guideline.id, input);
      const operation = {
        activate: activateAdminPromotionGuideline,
        dismiss: dismissAdminPromotionGuideline,
        retire: retireAdminPromotionGuideline,
      }[action];
      await operation(token, guideline.id);
      setNotice(
        {
          activate: "지침을 확정했습니다. 다음 실행부터 적용됩니다.",
          dismiss: "지침 제안을 기각했습니다.",
          retire: "지침을 해제했습니다. 다음 실행부터 적용되지 않습니다.",
        }[action],
      );
      setRevision((value) => value + 1);
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "홍보 지침을 처리하지 못했습니다.");
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  }

  return (
    <section aria-label="홍보 피드백 메모리" className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Heading level={3}>홍보 지침</Heading>
        <div className="flex items-center gap-2">
          <label className="flex items-center gap-2 text-sm">
            지침 상태
            <Select
              value={status}
              disabled={busy}
              onChange={(event) => {
                setData(null);
                setStatus(event.target.value as PromotionGuidelineStatus | "ALL");
              }}
            >
              <option value="ALL">전체</option>
              {Object.entries(STATUS_LABEL).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>
          </label>
          <Button
            size="sm"
            variant="secondary"
            disabled={busy}
            onClick={() => setRevision((value) => value + 1)}
          >
            메모리 새로고침
          </Button>
        </div>
      </div>
      <Text size="sm" tone="muted">
        반려 사례에서 나온 제안은 다른 관리자가 확정해야 적용됩니다. 활성 지침은 해제할 수 있으며,
        이전 실행에 사용한 내용은 남습니다.
      </Text>
      <AdminStatus error={error} loading={data === null && error === undefined} />
      {sourceErrors.length > 0 ? (
        <p role="alert" className="break-words text-sm text-danger">
          반려 근거를 불러오지 못했습니다: {sourceErrors.join(", ")}. 메모리 새로고침으로 다시
          시도해 주세요.
        </p>
      ) : null}
      {notice ? (
        <p role="status" className="text-sm text-brand-700">
          {notice}
        </p>
      ) : null}
      {data !== null ? (
        <>
          {data.guidelines.length === 0 ? (
            <Text size="sm" tone="muted">
              해당 상태의 홍보 지침이 없습니다.
            </Text>
          ) : null}
          {data.guidelines.map((guideline) => (
            <Card
              key={`${guideline.id}:${guideline.updatedAt}`}
              padded
              role="group"
              aria-label={`홍보 지침 ${guideline.id}`}
            >
              <GuidelineReview
                guideline={guideline}
                accountId={accountId}
                busy={busy}
                onAction={act}
              />
              <div className="mt-3 flex flex-wrap gap-2 text-xs text-neutral-500">
                <span>근거 반려 기록:</span>
                {guideline.sourceFeedbackIds.map((id) =>
                  data.feedback.some((feedback) => feedback.id === id) ? (
                    <a
                      key={id}
                      href={`#promotion-feedback-${id}`}
                      className="text-brand-700 underline"
                    >
                      {shortId(id)}
                    </a>
                  ) : (
                    <span key={id}>
                      {id} ({sourceErrors.includes(id) ? "근거 조회 실패" : "근거 불러오는 중"})
                    </span>
                  ),
                )}
                <span>제안 실행: {guideline.reflectionRunKey}</span>
              </div>
            </Card>
          ))}
          <details className="rounded-xl border border-neutral-200 p-4">
            <summary className="cursor-pointer font-medium">
              반려 당시 자료 · 최근 및 지침 근거 {data.feedback.length}건
            </summary>
            <div className="mt-3 flex flex-col gap-3">
              {data.feedback.length === 0 ? (
                <Text size="sm" tone="muted">
                  저장된 반려 이력이 없습니다.
                </Text>
              ) : null}
              {data.feedback.map((feedback) => (
                <FeedbackRecord key={feedback.id} feedback={feedback} />
              ))}
            </div>
          </details>
          <details className="rounded-xl border border-neutral-200 p-4">
            <summary className="cursor-pointer font-medium">
              실행에 사용한 기억 · 최근 {data.runs.length}건
            </summary>
            {data.runs.length === 0 ? (
              <Text size="sm" tone="muted">
                저장된 실행 기록이 없습니다.
              </Text>
            ) : null}
            {data.runs.map((run) => (
              <div key={run.id} className="mt-3 border-t border-neutral-100 pt-3 text-sm">
                <p className="break-words font-medium">
                  {run.runKey} · {run.outcome} · {run.reasonCode}
                </p>
                <p className="text-xs text-neutral-500">{formatDateTime(run.recordedAt)}</p>
                <p className="break-words">
                  반려 기록: {run.memoryContext?.feedbackIds.join(", ") || "없음"}
                </p>
                {(run.memoryContext?.guidelines ?? []).map((guideline) => (
                  <div key={guideline.id} className="mt-1">
                    <p className="whitespace-pre-wrap break-words">
                      [{guideline.targets.map((target) => TARGET_LABEL[target]).join(" · ")}]{" "}
                      {guideline.content}
                    </p>
                    <p className="break-words text-xs text-neutral-500">
                      지침 {guideline.id} · {guideline.kind === "KNOWLEDGE" ? "지식" : "절차"} ·{" "}
                      {guideline.categories.map((category) => CATEGORY_LABEL[category]).join(" · ")}
                    </p>
                  </div>
                ))}
                {!run.memoryContext?.guidelines.length ? (
                  <p className="text-neutral-500">사용한 확정 지침 없음</p>
                ) : null}
              </div>
            ))}
          </details>
        </>
      ) : null}
    </section>
  );
}

function GuidelineReview({
  guideline,
  accountId,
  busy,
  onAction,
}: {
  readonly guideline: AdminPromotionGuideline;
  readonly accountId: string | null;
  readonly busy: boolean;
  readonly onAction: (
    guideline: AdminPromotionGuideline,
    action: "activate" | "dismiss" | "retire",
    input?: UpdatePromotionGuidelineInput,
  ) => Promise<void>;
}) {
  const [content, setContent] = useState(guideline.content);
  const [targets, setTargets] = useState(guideline.targets);
  const [categories, setCategories] = useState(guideline.categories);
  const proposed = guideline.status === "PROPOSED";
  const isAuthor = guideline.proposedBy === accountId;

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-2">
        <Badge tone={guideline.status === "ACTIVE" ? "success" : "neutral"}>
          {STATUS_LABEL[guideline.status]}
        </Badge>
        <Text size="sm">
          {guideline.kind === "KNOWLEDGE" ? "지식" : "절차"} · {shortId(guideline.id)}
        </Text>
        <Text size="sm" tone="muted">
          제안자 {shortId(guideline.proposedBy)}
        </Text>
      </div>
      {proposed ? (
        <>
          <Field label="지침 내용">
            {({ inputId }) => (
              <Textarea
                id={inputId}
                rows={3}
                maxLength={1000}
                value={content}
                disabled={busy}
                onChange={(event) => setContent(event.target.value)}
              />
            )}
          </Field>
          <fieldset disabled={busy} className="flex flex-wrap gap-3 text-sm">
            <legend className="mb-1">적용 단계</legend>
            {(Object.keys(TARGET_LABEL) as PromotionMemoryTarget[]).map((target) => (
              <label key={target} className="flex items-center gap-1.5">
                <input
                  type="checkbox"
                  checked={targets.includes(target)}
                  onChange={(event) =>
                    setTargets(
                      event.target.checked
                        ? [...targets, target]
                        : targets.filter((value) => value !== target),
                    )
                  }
                />
                {TARGET_LABEL[target]}
              </label>
            ))}
          </fieldset>
          <fieldset disabled={busy} className="flex flex-wrap gap-3 text-sm">
            <legend className="mb-1">적용 홍보 종류</legend>
            {(Object.keys(CATEGORY_LABEL) as PromotionCategory[]).map((category) => (
              <label key={category} className="flex items-center gap-1.5">
                <input
                  type="checkbox"
                  checked={categories.includes(category)}
                  onChange={(event) =>
                    setCategories(
                      event.target.checked
                        ? [...categories, category]
                        : categories.filter((value) => value !== category),
                    )
                  }
                />
                {CATEGORY_LABEL[category]}
              </label>
            ))}
          </fieldset>
          {isAuthor ? (
            <Text size="sm" tone="muted">
              원 제안자는 직접 확정할 수 없습니다. 다른 관리자의 검토가 필요합니다.
            </Text>
          ) : null}
          <div className="flex justify-end gap-2">
            <Button
              size="sm"
              variant="danger"
              disabled={busy}
              onClick={() => void onAction(guideline, "dismiss")}
            >
              기각
            </Button>
            <Button
              size="sm"
              disabled={
                busy ||
                isAuthor ||
                content.trim().length === 0 ||
                targets.length === 0 ||
                categories.length === 0
              }
              onClick={() =>
                void onAction(guideline, "activate", {
                  content: content.trim(),
                  targets,
                  categories,
                })
              }
            >
              수정 후 확정
            </Button>
          </div>
        </>
      ) : (
        <>
          <p className="whitespace-pre-wrap break-words text-sm">{guideline.content}</p>
          <Text size="sm" tone="muted">
            {guideline.targets.map((target) => TARGET_LABEL[target]).join(" · ")} /{" "}
            {guideline.categories.map((category) => CATEGORY_LABEL[category]).join(" · ")}
          </Text>
          {guideline.confirmedBy !== null ? (
            <Text size="sm" tone="muted">
              확정 {shortId(guideline.confirmedBy)} · {formatDateTime(guideline.confirmedAt)}
            </Text>
          ) : null}
          {guideline.status === "ACTIVE" ? (
            <Button
              size="sm"
              variant="secondary"
              disabled={busy}
              onClick={() => void onAction(guideline, "retire")}
            >
              활성 해제
            </Button>
          ) : null}
        </>
      )}
    </div>
  );
}

function FeedbackRecord({ feedback }: { readonly feedback: AdminPromotionFeedback }) {
  return (
    <article
      id={`promotion-feedback-${feedback.id}`}
      className="scroll-mt-4 rounded-lg border border-neutral-200 p-3 text-sm"
    >
      <p className="font-medium">
        반려 기록 {shortId(feedback.id)} · 게시물 {shortId(feedback.postId)}
      </p>
      <p className="text-xs text-neutral-500">
        검토자 {shortId(feedback.reviewerId)} · {formatDateTime(feedback.reviewedAt)}
      </p>
      <p className="text-xs text-neutral-500">
        {CATEGORY_LABEL[feedback.category]} ·{" "}
        {feedback.targets.map((target) => TARGET_LABEL[target]).join(" · ")}
        {feedback.reasonTags.length > 0 ? ` · ${feedback.reasonTags.join(" · ")}` : ""}
      </p>
      <p className="mt-2 whitespace-pre-wrap break-words text-danger">
        반려 사유: {feedback.reason}
      </p>
      <p className="mt-2 whitespace-pre-wrap break-words">당시 캡션: {feedback.snapshot.caption}</p>
      <p className="text-xs text-neutral-500">
        {feedback.reflectedAt === null
          ? "지침 제안 대기"
          : `제안 검토 완료 · ${feedback.reflectedRunKey}`}
      </p>
      {feedback.snapshot.sourceCommitSha ? (
        <p className="break-words text-xs">릴리스: {feedback.snapshot.sourceCommitSha}</p>
      ) : null}
      {feedback.snapshot.provenance?.rationale ? (
        <p>당시 선정 이유: {feedback.snapshot.provenance.rationale}</p>
      ) : null}
      <div className="mt-2 grid gap-3 sm:grid-cols-2">
        {feedback.snapshot.mediaUrls.map((url, index) => {
          const capture = feedback.snapshot.captures[index];
          return (
            <figure key={`${index}:${url}`} className="min-w-0">
              <MediaImage
                src={url}
                alt={`반려 당시 게시 이미지 ${index + 1}`}
                className="aspect-[4/3] max-h-64 w-full rounded-md object-contain"
              />
              {capture?.originalUrl ? (
                <MediaImage
                  src={capture.originalUrl}
                  alt={`반려 당시 원본 이미지 ${index + 1}`}
                  className="mt-2 aspect-[4/3] max-h-64 w-full rounded-md object-contain"
                />
              ) : null}
              <figcaption className="mt-1 whitespace-pre-wrap break-words text-xs text-neutral-500">
                {capture
                  ? `${capture.label} · ${capture.route} · ${capture.actions} · ${capture.dataSource} · ${formatDateTime(capture.capturedAt)}${capture.edit ? ` · 지시문: ${capture.edit}` : ""}`
                  : "화면 설명 없음"}
              </figcaption>
            </figure>
          );
        })}
      </div>
    </article>
  );
}
