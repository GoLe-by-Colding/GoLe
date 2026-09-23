"use client";

import { type ChangeEvent, useCallback, useEffect, useRef, useState } from "react";
import {
  approveAdminPromotionPost,
  createAdminPromotionPost,
  fetchAdminPromotionPosts,
  publishAdminPromotionPost,
  rejectAdminPromotionPost,
  submitAdminPromotionPost,
  type AdminPromotionPost,
  type PromotionPostStatus,
} from "@entities/admin";
import { useSession } from "@entities/user";
import { ReasonPrompt, useModerationAction } from "@features/admin-moderation";
import { ApiError, uploadImages, type UploadedImage } from "@shared/api";
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
import {
  PROMOTION_CHANNEL_LABEL,
  PROMOTION_POST_STATUS_LABEL,
  PROMOTION_POST_STATUS_TONE,
  formatDateTime,
  shortCommitSha,
  shortId,
} from "../model/labels";
import { AdminStatus, AdminTable } from "./table";
import { PromotionEvaluationForm } from "./promotion-evaluation-form";

type StatusFilter = "ALL" | PromotionPostStatus;

const CAPTION_MAX_LENGTH = 500;
/** 백엔드 `PromotionPost.MAX_MEDIA_COUNT`, `MediaController.MAX_BATCH_SIZE`와 맞춘다. (T3) */
const MAX_MEDIA_COUNT = 10;

/**
 * 홍보 게시 검토 — Threads 등 외부 채널 업로드 전 다른 관리자의 승인을 강제한다.
 * (promotion-review)
 *
 * 지금 "발행"은 스텁 어댑터가 처리해 실제로 외부에 올라가지 않는다(promotion-review D5).
 * Threads 자격증명이 연동되기 전까지는 승인·발행을 몇 번 눌러도 실제 계정에 나가지 않는다.
 */
export function AdminPromotionPostsView() {
  const { session } = useSession();
  const token = session?.sessionToken ?? null;
  const accountId = session?.accountId ?? null;

  return <PromotionWorkspace key={`${accountId}:${token}`} token={token} accountId={accountId} />;
}

function PromotionWorkspace({
  token,
  accountId,
}: {
  readonly token: string | null;
  readonly accountId: string | null;
}) {
  const [status, setStatus] = useState<StatusFilter>("PENDING_REVIEW");
  const [rows, setRows] = useState<readonly AdminPromotionPost[] | null>(null);
  const [error, setError] = useState<string | undefined>(undefined);
  const [listError, setListError] = useState<string | undefined>(undefined);
  const [notice, setNotice] = useState("");
  const [recoveryId, setRecoveryId] = useState<string | null>(null);
  const [actionId, setActionId] = useState<string | null>(null);
  const requestGeneration = useRef(0);
  const mutationInFlight = useRef(false);
  const [evaluatingId, setEvaluatingId] = useState<string | null>(null);

  const [caption, setCaption] = useState("");
  const [images, setImages] = useState<readonly UploadedImage[]>([]);
  const [uploading, setUploading] = useState(false);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | undefined>(undefined);

  const load = useCallback(() => {
    if (token === null) {
      return;
    }
    const generation = ++requestGeneration.current;
    let active = true;
    void fetchAdminPromotionPosts(token, 50, status === "ALL" ? undefined : status)
      .then((next) => {
        if (active && generation === requestGeneration.current) {
          setRows(next);
          setListError(undefined);
        }
      })
      .catch((cause: unknown) => {
        if (!active || generation !== requestGeneration.current) return;
        setRows([]);
        setListError(
          cause instanceof ApiError ? cause.message : "홍보 게시 목록을 불러오지 못했습니다.",
        );
      });
    return () => {
      active = false;
    };
  }, [token, status]);

  useEffect(load, [load]);
  const reload = useCallback(() => {
    setRows(null);
    setListError(undefined);
    load();
  }, [load]);
  const reviewAction = useModerationAction(reload);
  const busy = creating || uploading || actionId !== null || reviewAction.pending !== null;

  async function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    const input = event.currentTarget;
    const selected = Array.from(event.target.files ?? []);
    input.value = "";
    if (selected.length === 0 || mutationInFlight.current) {
      return;
    }
    setCreateError(undefined);
    const remaining = MAX_MEDIA_COUNT - images.length;
    if (remaining <= 0) {
      setCreateError(`이미지는 최대 ${MAX_MEDIA_COUNT}장까지 첨부할 수 있어요.`);
      return;
    }
    mutationInFlight.current = true;
    setUploading(true);
    try {
      const uploaded = await uploadImages(selected.slice(0, remaining));
      setImages((prev) => [...prev, ...uploaded]);
    } catch (cause) {
      setCreateError(cause instanceof ApiError ? cause.message : "이미지 업로드에 실패했습니다.");
    } finally {
      setUploading(false);
      mutationInFlight.current = false;
    }
  }

  function removeImage(key: string) {
    setImages((prev) => prev.filter((image) => image.key !== key));
  }

  async function handleCreate(submitNow: boolean) {
    if (token === null || caption.trim().length === 0 || mutationInFlight.current) {
      return;
    }
    mutationInFlight.current = true;
    setCreating(true);
    setCreateError(undefined);
    setNotice("");
    let savedId: string | null = null;
    try {
      const { id } = await createAdminPromotionPost(token, {
        channel: "THREADS",
        caption: caption.trim(),
        mediaKeys: images.map((image) => image.key),
      });
      savedId = id;
      // 생성 성공은 제출 성공과 별개다. 제출 오류 후 같은 내용을 다시 생성하지 않는다.
      setCaption("");
      setImages([]);
      setRecoveryId(submitNow ? id : null);
      if (submitNow) {
        await submitAdminPromotionPost(token, id);
        setRecoveryId(null);
      }
      setNotice(submitNow ? "초안을 저장하고 검토를 요청했습니다." : "초안을 저장했습니다.");
      const nextStatus = submitNow ? "PENDING_REVIEW" : "DRAFT";
      if (status === nextStatus) reload();
      else setStatus(nextStatus);
    } catch (cause) {
      if (savedId !== null) {
        setCreateError(
          "초안은 저장됐지만 검토 요청 응답을 확인하지 못했습니다. 저장한 초안으로 다시 요청해 주세요.",
        );
        if (status === "DRAFT") reload();
        else setStatus("DRAFT");
      } else {
        setCreateError(
          cause instanceof ApiError ? cause.message : "홍보 게시 등록에 실패했습니다.",
        );
      }
    } finally {
      setCreating(false);
      mutationInFlight.current = false;
    }
  }

  async function act(
    id: string,
    operation: (token: string, id: string) => Promise<unknown>,
    message: string,
  ) {
    if (token === null || mutationInFlight.current) return false;
    mutationInFlight.current = true;
    setActionId(id);
    setError(undefined);
    setNotice("");
    try {
      await operation(token, id);
      setNotice(message);
      reload();
      return true;
    } catch (cause) {
      setError(
        cause instanceof ApiError ? cause.message : "처리하지 못했습니다. 다시 시도해 주세요.",
      );
      return false;
    } finally {
      mutationInFlight.current = false;
      setActionId(null);
    }
  }

  async function handleSubmit(id: string) {
    if (await act(id, submitAdminPromotionPost, "검토를 요청했습니다.")) {
      if (recoveryId === id) {
        setRecoveryId(null);
        setCreateError(undefined);
      }
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-3">
        <Heading level={2}>홍보 게시 검토</Heading>
        <label className="flex items-center gap-2 text-sm text-neutral-600">
          상태
          <Select
            value={status}
            disabled={busy}
            onChange={(e) => {
              ++requestGeneration.current;
              setRows(null);
              setStatus(e.target.value as StatusFilter);
            }}
          >
            <option value="PENDING_REVIEW">검토대기</option>
            <option value="DRAFT">초안</option>
            <option value="APPROVED">승인됨</option>
            <option value="PUBLISHED">발행완료</option>
            <option value="ALL">전체</option>
          </Select>
        </label>
      </div>

      <Text tone="muted" size="sm">
        Threads에 올릴 초안을 작성해 검토를 요청하면, 작성자 본인이 아닌 다른 관리자가 승인해야
        발행할 수 있습니다. 발행은 지금 모의(스텁) 처리되어 실제 Threads 계정에는 올라가지 않습니다.
      </Text>

      <Card padded className="flex flex-col gap-3">
        <Heading level={3}>새 홍보 게시 작성</Heading>
        <label className="flex flex-col gap-1.5 text-sm font-medium text-neutral-700">
          캡션 ({PROMOTION_CHANNEL_LABEL.THREADS})
          <Textarea
            rows={3}
            maxLength={CAPTION_MAX_LENGTH}
            value={caption}
            disabled={busy}
            placeholder="예: GoLe에 OO 기능이 추가됐어요! ..."
            onChange={(e) => setCaption(e.target.value)}
          />
          <span className="text-xs font-normal text-neutral-500">
            {caption.length}/{CAPTION_MAX_LENGTH}자
          </span>
        </label>
        <Field
          label="이미지 (선택)"
          hint={`최대 ${MAX_MEDIA_COUNT}장 · JPEG/PNG 정지 이미지만 가능하며 위치정보 등 메타데이터는 제거됩니다.`}
        >
          {({ inputId, describedBy }) => (
            <div className="flex flex-col gap-3">
              <input
                id={inputId}
                type="file"
                accept="image/jpeg,image/png"
                multiple
                aria-describedby={describedBy}
                onChange={(e) => void handleFileChange(e)}
                disabled={busy || images.length >= MAX_MEDIA_COUNT}
                className="text-sm text-neutral-700 file:mr-3 file:rounded-md file:border file:border-neutral-200 file:bg-neutral-50 file:px-3 file:py-1.5 file:text-sm"
              />
              {uploading ? <p className="text-sm text-neutral-500">업로드 중...</p> : null}
              {images.length > 0 ? (
                <ul className="flex flex-wrap gap-3">
                  {images.map((image, index) => (
                    <li key={image.key} className="relative">
                      <MediaImage
                        src={image.url}
                        alt={`첨부 이미지 ${index + 1}`}
                        className="h-24 w-24 rounded-lg border border-neutral-200/70 object-cover"
                      />
                      <button
                        type="button"
                        onClick={() => removeImage(image.key)}
                        disabled={busy}
                        aria-label={`첨부 이미지 ${index + 1} 삭제`}
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
        {createError !== undefined ? (
          <p role="alert" className="text-sm text-danger">
            {createError}
          </p>
        ) : null}
        {recoveryId !== null ? (
          <div className="flex items-center justify-between gap-3 rounded-lg border border-neutral-200 p-3">
            <Text size="sm">저장한 초안 · {shortId(recoveryId)}</Text>
            <Button
              variant="secondary"
              disabled={busy}
              onClick={() => void handleSubmit(recoveryId)}
            >
              저장한 초안 검토 요청
            </Button>
          </div>
        ) : null}
        <div className="flex justify-end gap-2">
          <Button
            variant="secondary"
            disabled={busy || caption.trim().length === 0}
            onClick={() => void handleCreate(false)}
          >
            초안 저장
          </Button>
          <Button
            disabled={busy || caption.trim().length === 0}
            onClick={() => void handleCreate(true)}
          >
            저장 후 검토 요청
          </Button>
        </div>
      </Card>

      {notice ? (
        <p role="status" className="text-sm text-brand-700">
          {notice}
        </p>
      ) : null}
      <div className="flex items-center justify-between gap-3">
        <AdminStatus error={listError ?? error} loading={rows === null} />
        <Button size="sm" variant="secondary" disabled={busy || rows === null} onClick={reload}>
          목록 새로고침
        </Button>
      </div>
      {actionId !== null ? (
        <p role="status" className="text-sm text-neutral-600">
          처리 중입니다.
        </p>
      ) : null}

      <AdminTable
        caption="홍보 게시 검토 목록"
        headers={["채널", "캡션", "작성자", "원본 릴리스", "상태", "제출/발행", "처리"]}
        alignRight={[6]}
        minWidth={960}
        empty="해당 상태의 홍보 게시물이 없습니다."
        rowCount={(rows ?? []).length}
      >
        {(rows ?? []).map((p) => {
          const isAuthor = accountId !== null && accountId === p.authorId;
          return (
            <tr key={p.id} className="border-t border-neutral-100">
              <td className="px-3 py-2.5 font-medium text-neutral-900">
                {PROMOTION_CHANNEL_LABEL[p.channel] ?? p.channel}
              </td>
              <td className="max-w-[320px] px-3 py-2.5 text-neutral-600">
                <p className="line-clamp-2 whitespace-pre-wrap break-words">{p.caption}</p>
                {p.mediaUrls.length > 0 ? (
                  <ul className="mt-1.5 flex flex-wrap gap-1.5">
                    {p.mediaUrls.map((url, index) => (
                      <li key={url}>
                        <MediaImage
                          src={url}
                          alt={`${shortId(p.id)} 첨부 이미지 ${index + 1}`}
                          className="h-12 w-12 rounded-md border border-neutral-200/70 object-cover"
                        />
                      </li>
                    ))}
                  </ul>
                ) : null}
                {p.rejectionReason !== null ? (
                  <p className="mt-1 text-xs text-danger">반려 사유: {p.rejectionReason}</p>
                ) : null}
              </td>
              <td className="px-3 py-2.5 text-neutral-600">
                {shortId(p.authorId)}
                {isAuthor ? (
                  <Badge className="ml-1" tone="neutral">
                    본인
                  </Badge>
                ) : null}
              </td>
              {/* 승인 게이트의 안전 근거는 "누가·무엇을 보고 썼는지"다. 값이 있으면 에이전트가
                  그 릴리스를 보고 자동 생성한 초안이고, 없으면 사람이 직접 쓴 것이다(D9·D11). */}
              <td className="px-3 py-2.5 text-neutral-600">
                {p.sourceCommitSha === null ? (
                  <span className="text-xs text-neutral-400">직접 작성</span>
                ) : (
                  <span className="font-mono text-xs" title={p.sourceCommitSha}>
                    {shortCommitSha(p.sourceCommitSha)}
                  </span>
                )}
              </td>
              <td className="px-3 py-2.5">
                <Badge tone={PROMOTION_POST_STATUS_TONE[p.status] ?? "neutral"}>
                  {PROMOTION_POST_STATUS_LABEL[p.status] ?? p.status}
                </Badge>
              </td>
              <td className="px-3 py-2.5 text-xs text-neutral-500">
                {p.status === "PUBLISHED"
                  ? `${formatDateTime(p.publishedAt)} · ${p.externalPostId ?? "—"}`
                  : p.status === "PENDING_REVIEW"
                    ? formatDateTime(p.submittedAt)
                    : formatDateTime(p.createdAt)}
              </td>
              <td className="px-3 py-2.5 text-right">
                {p.status === "DRAFT" ? (
                  <Button
                    size="sm"
                    variant="secondary"
                    disabled={busy}
                    onClick={() => void handleSubmit(p.id)}
                  >
                    검토 요청
                  </Button>
                ) : null}
                {p.status === "PENDING_REVIEW" ? (
                  <span className="inline-flex gap-1">
                    <Button
                      size="sm"
                      disabled={isAuthor || busy}
                      title={isAuthor ? "작성자 본인은 승인할 수 없습니다" : undefined}
                      onClick={() =>
                        void act(p.id, approveAdminPromotionPost, "홍보 초안을 승인했습니다.")
                      }
                    >
                      승인
                    </Button>
                    <Button
                      size="sm"
                      variant="danger"
                      disabled={isAuthor || busy}
                      title={isAuthor ? "작성자 본인은 반려할 수 없습니다" : undefined}
                      onClick={() =>
                        reviewAction.ask({
                          title: "홍보 게시 반려",
                          target: `${PROMOTION_CHANNEL_LABEL[p.channel] ?? p.channel} · ${shortId(p.id)}`,
                          confirmLabel: "반려하기",
                          run: async (reason) => {
                            await rejectAdminPromotionPost(token ?? "", p.id, reason);
                          },
                        })
                      }
                    >
                      반려
                    </Button>
                  </span>
                ) : null}
                {p.status === "APPROVED" ? (
                  <Button
                    size="sm"
                    disabled={busy}
                    onClick={() =>
                      void act(
                        p.id,
                        publishAdminPromotionPost,
                        "모의 발행을 완료했습니다. 실제 Threads에는 게시되지 않습니다.",
                      )
                    }
                  >
                    모의 발행
                  </Button>
                ) : null}
                {p.status === "PUBLISHED" ? (
                  <span className="text-xs text-neutral-400">완료됨</span>
                ) : null}
                <Button
                  className="ml-1"
                  size="sm"
                  variant="secondary"
                  disabled={busy}
                  onClick={() => setEvaluatingId(p.id)}
                >
                  평가
                </Button>
              </td>
            </tr>
          );
        })}
      </AdminTable>

      {reviewAction.pending !== null ? (
        <ReasonPrompt
          title={reviewAction.pending.title}
          target={reviewAction.pending.target}
          confirmLabel={reviewAction.pending.confirmLabel}
          busy={reviewAction.busy}
          error={reviewAction.error}
          onConfirm={reviewAction.confirm}
          onCancel={reviewAction.cancel}
        />
      ) : null}

      {evaluatingId !== null ? (
        <PromotionEvaluationForm
          promotionPostId={evaluatingId}
          target={(() => {
            const target = (rows ?? []).find((row) => row.id === evaluatingId);
            return target
              ? `${PROMOTION_CHANNEL_LABEL[target.channel] ?? target.channel} · ${shortId(target.id)}`
              : shortId(evaluatingId);
          })()}
          onSaved={() => setEvaluatingId(null)}
          onCancel={() => setEvaluatingId(null)}
        />
      ) : null}
    </div>
  );
}
