"use client";

import { useEffect } from "react";
import { registerDeviceToken, unregisterDeviceToken } from "@entities/notification";
import { loadSession } from "@entities/user";
import { SESSION_CHANGE_EVENT, SESSION_STORAGE_KEY } from "@shared/api";
import { readAppPushToken, subscribeAppPushToken } from "@shared/lib";

/** 마지막으로 서버에 등록한 (계정, 토큰). 같은 조합을 페이지마다 다시 보내지 않으려고 둔다. */
const REGISTERED_KEY = "gole.app-push.registered";
const INVALIDATED_KEY = "gole.app-push.invalidated";

interface Registered {
  readonly accountId: string;
  readonly token: string;
  /** 응답을 잃어도 로그아웃 때 해제할 수 있도록 등록 시도부터 기록한다. */
  readonly confirmed: boolean;
  /** 다른 WebView의 해제 전에 시작한 등록 응답은 완료 캐시로 재사용하지 않는다. */
  readonly invalidation: string | null;
}

// 화면 전환·StrictMode 재마운트에도 서버 변경 순서를 유지한다. 요청 취소만으로는
// 서버에 이미 반영된 등록이 취소되지 않으므로 완료 후 현재 세션을 다시 확인한다.
let syncQueue: Promise<void> = Promise.resolve();
let needsRevalidation = false;
// 다른 WebView의 공유 캐시는 더 새로운 토큰일 수 있다. 교체할 이전 토큰은 이 문서의
// 등록 시도에서만 고른다. 응답 유실 시에도 로그아웃 해제 대상으로 남긴다.
let attemptedToken: string | null = null;

function scheduleSync(): void {
  syncQueue = syncQueue.then(syncRegistration).catch(() => undefined);
}

function revalidateRegistration(): void {
  // StrictMode·같은 틱의 복귀 이벤트는 합치되 문서 밖에서 바뀐 서버 등록은 다시 보장한다.
  needsRevalidation = true;
  scheduleSync();
}

async function syncRegistration(): Promise<void> {
  const push = readAppPushToken();
  if (push === null) return;
  // useSession의 최초 SSR 스냅샷(null)을 로그아웃으로 판단하지 않는다.
  const accountId = loadSession()?.accountId ?? null;
  let registered = readRegistered();

  const removedTokens = new Set<string>();
  if (accountId === null) {
    if (registered !== null) removedTokens.add(registered.token);
    if (attemptedToken !== null) removedTokens.add(attemptedToken);
  } else if (attemptedToken !== null && attemptedToken !== push.token) {
    removedTokens.add(attemptedToken);
  }
  for (const removedToken of removedTokens) {
    invalidateRegistration(removedToken);
    try {
      await unregisterDeviceToken(removedToken);
      clearRegistered(removedToken);
      if (attemptedToken === removedToken) attemptedToken = null;
    } finally {
      // 서버 반영 후 응답만 유실될 수도 있다. 다른 WebView의 늦은 등록 응답도 무효화한다.
      invalidateRegistration(removedToken);
      if (loadSession() !== null && readAppPushToken()?.token === removedToken) scheduleSync();
    }
    registered = readRegistered();
  }
  if (accountId === null) {
    if (loadSession() !== null) scheduleSync();
    return;
  }
  if (loadSession()?.accountId !== accountId) {
    scheduleSync();
    return;
  }
  const revalidate = needsRevalidation;
  needsRevalidation = false;
  if (
    !revalidate &&
    registered?.accountId === accountId &&
    registered.token === push.token &&
    registered.confirmed &&
    registered.invalidation === readInvalidation()
  )
    return;

  const registration = {
    accountId,
    token: push.token,
    confirmed: false,
    invalidation: readInvalidation(),
  };
  attemptedToken = push.token;
  writeRegistered(registration);
  await registerDeviceToken(push.token, push.platform);
  const invalidated = registration.invalidation !== readInvalidation();
  if (!invalidated) writeRegistered({ ...registration, confirmed: true });
  if (
    invalidated ||
    loadSession()?.accountId !== accountId ||
    readAppPushToken()?.token !== push.token
  ) {
    scheduleSync();
  }
}

/**
 * 앱 안에서 로그인 상태에 맞춰 단말 푸시 토큰을 등록·해제한다. 앱 밖 브라우저에서는 아무것도 하지 않는다.
 *
 * - 로그인: 이 계정으로 토큰을 등록한다. 서버 등록은 멱등이라 계정이 바뀌어도 소유자만 갱신된다.
 * - 로그아웃: 마지막으로 등록한 토큰을 지운다. 해제는 토큰만으로 되므로 세션이 없어도 된다.
 */
function useAppPushRegistration(): void {
  useEffect(() => {
    const onStorage = (event: StorageEvent) => {
      if (
        event.key === null ||
        event.key === SESSION_STORAGE_KEY ||
        (event.key === INVALIDATED_KEY && loadSession() !== null)
      ) {
        scheduleSync();
      }
    };
    const unsubscribePush = subscribeAppPushToken(scheduleSync);
    window.addEventListener(SESSION_CHANGE_EVENT, scheduleSync);
    window.addEventListener("storage", onStorage);
    window.addEventListener("focus", revalidateRegistration);
    window.addEventListener("online", revalidateRegistration);
    revalidateRegistration();
    return () => {
      unsubscribePush();
      window.removeEventListener(SESSION_CHANGE_EVENT, scheduleSync);
      window.removeEventListener("storage", onStorage);
      window.removeEventListener("focus", revalidateRegistration);
      window.removeEventListener("online", revalidateRegistration);
    };
  }, []);
}

function readRegistered(): Registered | null {
  try {
    const raw = window.localStorage.getItem(REGISTERED_KEY);
    if (raw === null) return null;
    const value = JSON.parse(raw) as {
      accountId?: unknown;
      token?: unknown;
      confirmed?: unknown;
      invalidation?: unknown;
    };
    return typeof value.accountId === "string" && typeof value.token === "string"
      ? {
          accountId: value.accountId,
          token: value.token,
          confirmed: value.confirmed !== false,
          invalidation: typeof value.invalidation === "string" ? value.invalidation : null,
        }
      : null;
  } catch {
    return null;
  }
}

function writeRegistered(value: Registered): void {
  try {
    window.localStorage.setItem(REGISTERED_KEY, JSON.stringify(value));
  } catch {
    // 저장이 막힌 환경에서는 매 진입마다 다시 등록할 뿐이다(멱등).
  }
}

function readInvalidation(): string | null {
  try {
    return window.localStorage.getItem(INVALIDATED_KEY);
  } catch {
    return null;
  }
}

function invalidateRegistration(token: string): void {
  const current = readRegistered();
  if (current?.token === token) writeRegistered({ ...current, confirmed: false });
  // 인증용 값이 아닌 변경 표식이다. LAN HTTP WebView에는 randomUUID가 없을 수 있다.
  const invalidation =
    globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  try {
    window.localStorage.setItem(INVALIDATED_KEY, invalidation);
  } catch {
    // 저장이 막힌 환경은 완료 캐시도 재사용하지 못하므로 다음 진입 때 다시 등록한다.
  }
}

function clearRegistered(token: string): void {
  try {
    const current = readRegistered();
    if (current?.token !== token) return;
    window.localStorage.removeItem(REGISTERED_KEY);
  } catch {
    // 위와 같다.
  }
}

/**
 * 화면에 그리는 것은 없다. 앱(WebView) 안에서 푸시 토큰 등록만 맡는다.
 *
 * features 슬라이스로 두지 않은 이유: 메인 레이아웃에서만 한 번 쓰는 앱 셸 관심사이고,
 * features 레이어가 steiger 의 슬라이스 수 상한(20)에 이미 닿아 있다.
 */
export function AppPushRegistration(): null {
  useAppPushRegistration();
  return null;
}
