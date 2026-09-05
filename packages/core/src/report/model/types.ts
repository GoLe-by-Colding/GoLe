/** 신고 대상 유형 (백엔드 ReportTargetType 미러) */
export type ReportTargetType = "LISTING" | "POST" | "COMMENT" | "REVIEW";

/** 신고 사유 (백엔드 ReportReason 미러) */
export type ReportReason = "COUNTERFEIT" | "IP_INFRINGEMENT" | "FRAUD" | "INAPPROPRIATE" | "OTHER";

export const REPORT_REASON_LABEL: Record<ReportReason, string> = {
  COUNTERFEIT: "가품·위조품 의심",
  IP_INFRINGEMENT: "이미지·저작권 도용",
  FRAUD: "사기·허위 매물",
  INAPPROPRIATE: "욕설·스팸 등 부적절",
  OTHER: "기타",
};

export const REPORT_REASONS: readonly ReportReason[] = [
  "COUNTERFEIT",
  "IP_INFRINGEMENT",
  "FRAUD",
  "INAPPROPRIATE",
  "OTHER",
];
