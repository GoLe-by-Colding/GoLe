import { apiRequest } from "@shared/api";
import type { ReportReason, ReportTargetType } from "../model/types";

export interface SubmitReportInput {
  readonly reporterId: string;
  readonly targetType: ReportTargetType;
  readonly targetId: string;
  readonly reason: ReportReason;
  readonly detail: string;
}

/** 신고 접수 — 가품·IP 도용·사기 매물/게시글 notice & takedown 입구. */
export async function submitReport(input: SubmitReportInput): Promise<{ id: string }> {
  return apiRequest<{ id: string }>("/api/v1/reports", { method: "POST", body: input });
}
