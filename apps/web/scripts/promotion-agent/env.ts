import { config } from "dotenv";

export function loadPromotionAgentEnvironment(): void {
  const environmentFile = process.env.PROMOTION_AGENT_ENV_FILE?.trim();
  if (!environmentFile) return;
  const result = config({ path: environmentFile, override: true, quiet: true });
  if (result.error) {
    throw new Error(`PROMOTION_AGENT_ENV_FILE을 읽을 수 없음: ${environmentFile}`, {
      cause: result.error,
    });
  }
}
