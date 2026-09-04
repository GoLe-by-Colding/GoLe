"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "@shared/api";
import { acceptThirdPartyProvisionConsent, fetchCurrentSignupPolicy } from "../api/user-api";
import type { CurrentSignupPolicy, ThirdPartyProvisionPath } from "./types";

export const THIRD_PARTY_PROVISION_CONSENT_REQUIRED_CODE = "THIRD_PARTY_PROVISION_CONSENT_REQUIRED";
export const THIRD_PARTY_PROVISION_VERSION_STALE_CODE = "THIRD_PARTY_PROVISION_VERSION_STALE";

export function isThirdPartyProvisionConsentRequiredError(cause: unknown): cause is ApiError {
  return (
    cause instanceof ApiError &&
    cause.status === 403 &&
    cause.code === THIRD_PARTY_PROVISION_CONSENT_REQUIRED_CODE
  );
}

export class ThirdPartyProvisionConsentCancelledError extends Error {
  constructor() {
    super("제3자 제공 동의가 취소되었습니다.");
    this.name = "ThirdPartyProvisionConsentCancelledError";
  }
}

export function isThirdPartyProvisionConsentCancelledError(
  cause: unknown,
): cause is ThirdPartyProvisionConsentCancelledError {
  return cause instanceof ThirdPartyProvisionConsentCancelledError;
}

interface PendingAction {
  readonly path: ThirdPartyProvisionPath;
  readonly requestId: string;
  readonly retry: () => Promise<unknown>;
  readonly resolve: (result: unknown) => void;
  readonly reject: (cause: unknown) => void;
}

export interface ThirdPartyProvisionConsentDialogController {
  readonly open: boolean;
  readonly policy: CurrentSignupPolicy | undefined;
  readonly path: ThirdPartyProvisionPath | null;
  readonly loading: boolean;
  readonly submitting: boolean;
  readonly error: string | undefined;
  readonly accept: () => Promise<void>;
  readonly cancel: () => void;
  readonly retryPolicyLoad: () => void;
}

export interface UseThirdPartyProvisionConsentResult {
  readonly runWithConsent: <T>(
    action: () => Promise<T>,
    path: ThirdPartyProvisionPath,
  ) => Promise<T>;
  readonly requestConsentThenRetry: <T>(
    action: () => Promise<T>,
    path: ThirdPartyProvisionPath,
  ) => Promise<T>;
  readonly dialog: ThirdPartyProvisionConsentDialogController;
}

/**
 * 보호 API가 동의 필요 오류를 돌려준 경우에만 동의창을 열고, 동의 기록 성공 후 원 요청을
 * 정확히 한 번 다시 실행한다. 최초 요청 외의 일반 오류는 그대로 호출부에 전달한다.
 */
export function useThirdPartyProvisionConsent(): UseThirdPartyProvisionConsentResult {
  const [open, setOpen] = useState(false);
  const [policy, setPolicy] = useState<CurrentSignupPolicy | undefined>();
  const [path, setPath] = useState<ThirdPartyProvisionPath | null>(null);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | undefined>();
  const pendingRef = useRef<PendingAction | null>(null);
  const mountedRef = useRef(true);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      pendingRef.current?.reject(new ThirdPartyProvisionConsentCancelledError());
      pendingRef.current = null;
    };
  }, []);

  const loadPolicy = useCallback(async (): Promise<CurrentSignupPolicy | undefined> => {
    setLoading(true);
    setError(undefined);
    try {
      const current = await fetchCurrentSignupPolicy();
      if (mountedRef.current) setPolicy(current);
      return current;
    } catch {
      if (mountedRef.current) {
        setPolicy(undefined);
        setError("최신 제3자 제공 안내를 불러오지 못했습니다. 다시 시도해 주세요.");
      }
      return undefined;
    } finally {
      if (mountedRef.current) setLoading(false);
    }
  }, []);

  const requestConsentThenRetry = useCallback(
    <T>(action: () => Promise<T>, actionPath: ThirdPartyProvisionPath): Promise<T> => {
      if (pendingRef.current !== null) {
        return Promise.reject(new Error("다른 제3자 제공 동의 요청을 처리하고 있습니다."));
      }

      setPath(actionPath);
      setPolicy(undefined);
      setError(undefined);
      setOpen(true);
      void loadPolicy();

      return new Promise<T>((resolve, reject) => {
        pendingRef.current = {
          path: actionPath,
          requestId: createConsentRequestId(),
          retry: action,
          resolve: (result) => resolve(result as T),
          reject,
        };
      });
    },
    [loadPolicy],
  );

  const runWithConsent = useCallback(
    async <T>(action: () => Promise<T>, actionPath: ThirdPartyProvisionPath): Promise<T> => {
      try {
        return await action();
      } catch (cause) {
        if (!isThirdPartyProvisionConsentRequiredError(cause)) throw cause;
        return requestConsentThenRetry(action, actionPath);
      }
    },
    [requestConsentThenRetry],
  );

  const cancel = useCallback(() => {
    if (submitting) return;
    const pending = pendingRef.current;
    pendingRef.current = null;
    setOpen(false);
    setPath(null);
    pending?.reject(new ThirdPartyProvisionConsentCancelledError());
  }, [submitting]);

  const accept = useCallback(async () => {
    const pending = pendingRef.current;
    const noticeVersion = policy?.thirdPartyProvisionVersion;
    if (pending === null || noticeVersion === undefined || submitting) return;

    setSubmitting(true);
    setError(undefined);
    try {
      await acceptThirdPartyProvisionConsent(noticeVersion, pending.path, pending.requestId);
      if (!mountedRef.current || pendingRef.current !== pending) return;

      // 재시도 전에 pending을 비워 중복 클릭·중첩 오류가 두 번째 요청을 만들지 못하게 한다.
      pendingRef.current = null;
      setOpen(false);
      setPath(null);
      try {
        pending.resolve(await pending.retry());
      } catch (cause) {
        pending.reject(cause);
      }
    } catch (cause) {
      if (mountedRef.current) {
        if (cause instanceof ApiError && cause.code === THIRD_PARTY_PROVISION_VERSION_STALE_CODE) {
          setPolicy(undefined);
          const current = await loadPolicy();
          if (mountedRef.current && current !== undefined) {
            setError("동의 안내가 변경되었습니다. 최신 내용을 확인하고 다시 동의해 주세요.");
          }
        } else {
          setError(cause instanceof ApiError ? cause.message : "동의를 기록하지 못했습니다.");
        }
      }
    } finally {
      if (mountedRef.current) setSubmitting(false);
    }
  }, [loadPolicy, policy?.thirdPartyProvisionVersion, submitting]);

  return {
    runWithConsent,
    requestConsentThenRetry,
    dialog: {
      open,
      policy,
      path,
      loading,
      submitting,
      error,
      accept,
      cancel,
      retryPolicyLoad: () => void loadPolicy(),
    },
  };
}

function createConsentRequestId(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return `consent-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}
