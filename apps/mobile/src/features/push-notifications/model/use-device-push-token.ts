import { useEffect, useState } from "react";
import { getDevicePushToken, type DevicePushToken } from "../lib/device-push-token";

let pending: Promise<DevicePushToken | null> | null = null;

/**
 * 앱 실행당 한 번만 알림 권한을 묻고 토큰을 얻는다. 탭마다 WebView 가 따로 떠도 권한 창은 한 번이다.
 * 거절·실패면 이번 실행 동안 null 이다 — 다음 실행에서 다시 시도한다.
 */
export function loadDevicePushToken(): Promise<DevicePushToken | null> {
  pending ??= getDevicePushToken();
  return pending;
}

/** 토큰을 아직 못 얻었거나 받을 수 없으면 null 이다. */
export function useDevicePushToken(): DevicePushToken | null {
  const [token, setToken] = useState<DevicePushToken | null>(null);

  useEffect(() => {
    let active = true;
    void loadDevicePushToken().then((value) => {
      if (active) setToken(value);
    });
    return () => {
      active = false;
    };
  }, []);

  return token;
}
