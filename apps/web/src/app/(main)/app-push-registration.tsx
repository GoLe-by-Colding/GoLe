"use client";

import { useEffect, useState } from "react";
import { registerDeviceToken, unregisterDeviceToken } from "@entities/notification";
import { useSession } from "@entities/user";
import { readAppPushToken, subscribeAppPushToken, type AppPushToken } from "@shared/lib";

/** 마지막으로 서버에 등록한 (계정, 토큰). 같은 조합을 페이지마다 다시 보내지 않으려고 둔다. */
const REGISTERED_KEY = "gole.app-push.registered";

interface Registered {
  readonly accountId: string;
  readonly token: string;
}

/**
 * 앱 안에서 로그인 상태에 맞춰 단말 푸시 토큰을 등록·해제한다. 앱 밖 브라우저에서는 아무것도 하지 않는다.
 *
 * - 로그인: 이 계정으로 토큰을 등록한다. 서버 등록은 멱등이라 계정이 바뀌어도 소유자만 갱신된다.
 * - 로그아웃: 마지막으로 등록한 토큰을 지운다. 해제는 토큰만으로 되므로 세션이 없어도 된다.
 */
function useAppPushRegistration(): void {
  const { session } = useSession();
  const accountId = session?.accountId ?? null;
  const [push, setPush] = useState<AppPushToken | null>(readAppPushToken);

  useEffect(() => subscribeAppPushToken(setPush), []);

  useEffect(() => {
    if (push === null) return;
    const registered = readRegistered();

    if (accountId === null) {
      if (registered === null) return;
      // 이 단말로 이전 계정의 알림이 계속 오지 않게 한다. 실패해도 다음 로그인 등록이 소유자를 덮는다.
      void unregisterDeviceToken(registered.token)
        .catch(() => undefined)
        .finally(clearRegistered);
      return;
    }

    if (registered?.accountId === accountId && registered.token === push.token) return;
    const controller = new AbortController();
    void registerDeviceToken(push.token, push.platform, controller.signal)
      .then(() => writeRegistered({ accountId, token: push.token }))
      // 다음 페이지 진입에서 다시 시도한다. 등록 실패를 화면에 드러내지 않는다 — 인앱 알림은 그대로 온다.
      .catch(() => undefined);
    return () => controller.abort();
  }, [accountId, push]);
}

function readRegistered(): Registered | null {
  try {
    const raw = window.localStorage.getItem(REGISTERED_KEY);
    if (raw === null) return null;
    const value = JSON.parse(raw) as { accountId?: unknown; token?: unknown };
    return typeof value.accountId === "string" && typeof value.token === "string"
      ? { accountId: value.accountId, token: value.token }
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

function clearRegistered(): void {
  try {
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
