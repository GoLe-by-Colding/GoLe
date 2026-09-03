import { apiRequest } from "@shared/api";
import type { ReportReason, ReportTargetType } from "../model/types";

export interface SubmitReportInput {
  readonly reporterId: string;
  readonly targetType: ReportTargetType;
  readonly targetId: string;
  /** 댓글 신고 시 서버가 댓글의 부모 관계를 검증하는 게시글 ID. */
  readonly parentId?: string;
  readonly reason: ReportReason;
  readonly detail: string;
}

/** 신고 접수 — 가품·IP 도용·사기 매물/게시글 notice & takedown 입구. */
export async function submitReport(input: SubmitReportInput): Promise<{ id: string }> {
  if (input.targetType === "COMMENT") {
    if (input.parentId === undefined) {
      throw new Error("댓글 신고에는 게시글 ID가 필요합니다");
    }
    return apiRequest<{ id: string }>(
      `/api/v1/community/posts/${encodeURIComponent(input.parentId)}/comments/${encodeURIComponent(input.targetId)}/report`,
      {
        method: "POST",
        body: { reason: input.reason, detail: input.detail },
      },
    );
  }
  return apiRequest<{ id: string }>("/api/v1/reports", { method: "POST", body: input });
}
