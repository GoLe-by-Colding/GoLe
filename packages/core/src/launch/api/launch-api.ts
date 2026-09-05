import { apiRequest } from "../../runtime";
import { parseLaunchConfig, SAFE_LAUNCH_CONFIG, type LaunchConfig } from "../model/types";

const LAUNCH_CONFIG_TIMEOUT_MS = 2_500;

/** 공개 출시 단계. 결제 노출을 결정하므로 실패하면 Stage 0으로 닫는다. */
export async function fetchLaunchConfig(signal?: AbortSignal): Promise<LaunchConfig> {
  const controller = new AbortController();
  const abortFromCaller = () => controller.abort(signal?.reason);
  const timeoutId = setTimeout(() => controller.abort(), LAUNCH_CONFIG_TIMEOUT_MS);

  if (signal?.aborted === true) {
    abortFromCaller();
  } else {
    signal?.addEventListener("abort", abortFromCaller, { once: true });
  }

  try {
    const payload = await apiRequest<unknown>("/api/v1/config/launch", {
      cache: "no-store",
      signal: controller.signal,
    });
    return parseLaunchConfig(payload);
  } catch {
    return SAFE_LAUNCH_CONFIG;
  } finally {
    clearTimeout(timeoutId);
    signal?.removeEventListener("abort", abortFromCaller);
  }
}
