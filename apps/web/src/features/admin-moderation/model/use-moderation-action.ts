"use client";

import { useCallback, useState } from "react";
import { ApiError } from "@shared/api";

/** 사유 입력 모달이 열려 있을 때의 대상 정보. */
export interface PendingAction {
  /** 모달 제목. */
  readonly title: string;
  /** 대상 설명(사람이 읽는 이름). */
  readonly target: string;
  readonly confirmLabel?: string | undefined;
  /** 사유를 받아 실제 조치를 수행한다. */
  readonly run: (reason: string) => Promise<void>;
}

export interface UseModerationActionResult {
  readonly pending: PendingAction | null;
  readonly busy: boolean;
  readonly error: string | undefined;
  /** 사유 입력 모달을 연다. */
  readonly ask: (action: PendingAction) => void;
  /** 모달에서 확인했을 때 호출. 성공하면 모달을 닫고 onDone을 실행한다. */
  readonly confirm: (reason: string) => void;
  readonly cancel: () => void;
  readonly clearError: () => void;
}

/**
 * "사유를 받아 조치하고, 실패하면 모달에 오류를 보여준다"는 흐름을 공통화한 훅.
 *
 * 조치가 성공해야만 목록을 갱신해야 하므로(감사 로그는 성공한 조치만 남는다),
 * 성공 콜백은 run이 resolve된 뒤에만 실행한다.
 */
export function useModerationAction(onDone: () => void): UseModerationActionResult {
  const [pending, setPending] = useState<PendingAction | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);

  const ask = useCallback((action: PendingAction) => {
    setError(undefined);
    setPending(action);
  }, []);

  const cancel = useCallback(() => {
    setPending(null);
    setError(undefined);
  }, []);

  const confirm = useCallback(
    (reason: string) => {
      if (pending === null) {
        return;
      }
      setBusy(true);
      setError(undefined);
      void pending
        .run(reason)
        .then(() => {
          setPending(null);
          onDone();
        })
        .catch((cause: unknown) => {
          setError(cause instanceof ApiError ? cause.message : "조치에 실패했습니다.");
        })
        .finally(() => setBusy(false));
    },
    [pending, onDone],
  );

  const clearError = useCallback(() => setError(undefined), []);

  return { pending, busy, error, ask, confirm, cancel, clearError };
}
