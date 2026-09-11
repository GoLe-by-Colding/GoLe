export interface PromotionCandidate {
  readonly sha: string;
  readonly changedFiles: readonly string[];
  readonly route: string;
  readonly summary: string;
}

export interface CaptureResult {
  readonly paths: readonly string[];
  readonly route: string;
  readonly sourceSpec: string | null;
  readonly fallback: boolean;
}
