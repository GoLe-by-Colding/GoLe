import { apiRequest } from "@shared/api";
import { parseLaunchConfig, SAFE_LAUNCH_CONFIG, type LaunchConfig } from "../model/types";

/** 공개 출시 단계. 결제 노출을 결정하므로 실패하면 Stage 0으로 닫는다. */
export async function fetchLaunchConfig(signal?: AbortSignal): Promise<LaunchConfig> {
  try {
    const payload = await apiRequest<unknown>("/api/v1/config/launch", {
      cache: "no-store",
      ...(signal === undefined ? {} : { signal }),
    });
    return parseLaunchConfig(payload);
  } catch {
    return SAFE_LAUNCH_CONFIG;
  }
}
