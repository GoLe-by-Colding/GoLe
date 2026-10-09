"use client";

import { useState } from "react";
import {
  closePartRequest,
  deletePartRequest,
  PART_REQUEST_ERROR,
  type PartRequest,
} from "@entities/part-request";
import { useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import { Button } from "@shared/ui";

export interface PartRequestOwnerActionsProps {
  readonly request: PartRequest;
  readonly onClosed: (request: PartRequest) => void;
  readonly onDeleted: () => void;
}

function actionErrorMessage(cause: unknown, fallback: string): string {
  if (cause instanceof ApiError) {
    if (cause.code === PART_REQUEST_ERROR.NOT_OPEN) return "이미 마감된 요청이에요.";
    if (cause.code === PART_REQUEST_ERROR.ACCESS_DENIED) return "내가 올린 요청만 바꿀 수 있어요.";
    return cause.message;
  }
  return fallback;
}

/**
 * 작성자 전용 마감·삭제. (wanted-parts W6·W7) 작성자가 아니면 아무것도 그리지 않는다 —
 * 서버도 403으로 막지만, 남의 요청에 버튼을 보여 줄 이유가 없다.
 */
export function PartRequestOwnerActions({
  request,
  onClosed,
  onDeleted,
}: PartRequestOwnerActionsProps) {
  const { session } = useSession();
  const [busy, setBusy] = useState<"close" | "delete" | null>(null);
  const [error, setError] = useState<string | null>(null);

  if (session?.accountId !== request.requesterId) return null;

  async function handleClose() {
    if (busy !== null) return;
    if (!window.confirm("부품을 구했나요? 마감하면 게시판의 '찾는 중' 목록에서 빠져요.")) return;
    setBusy("close");
    setError(null);
    try {
      onClosed(await closePartRequest(request.id));
    } catch (cause) {
      setError(actionErrorMessage(cause, "마감하지 못했어요. 잠시 후 다시 시도해 주세요."));
    } finally {
      setBusy(null);
    }
  }

  async function handleDelete() {
    if (busy !== null) return;
    if (!window.confirm("이 요청을 삭제할까요? 되돌릴 수 없어요.")) return;
    setBusy("delete");
    setError(null);
    try {
      await deletePartRequest(request.id);
      onDeleted();
    } catch (cause) {
      setError(actionErrorMessage(cause, "삭제하지 못했어요. 잠시 후 다시 시도해 주세요."));
      setBusy(null);
    }
  }

  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap gap-2">
        {request.status === "open" ? (
          <Button
            variant="secondary"
            size="sm"
            disabled={busy !== null}
            onClick={() => void handleClose()}
          >
            {busy === "close" ? "마감 중…" : "마감"}
          </Button>
        ) : null}
        <Button
          variant="ghost"
          size="sm"
          className="text-danger hover:bg-danger-soft hover:text-danger"
          disabled={busy !== null}
          onClick={() => void handleDelete()}
        >
          {busy === "delete" ? "삭제 중…" : "삭제"}
        </Button>
      </div>
      {error !== null ? (
        <p role="alert" className="text-sm text-danger">
          {error}
        </p>
      ) : null}
    </div>
  );
}
