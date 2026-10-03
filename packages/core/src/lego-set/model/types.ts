/**
 * LEGO 카탈로그 세트 엔티티 (요구사항 4: LEGO Set Catalog).
 * 백엔드 Catalog_Service 응답과 1:1 대응하는 도메인 타입.
 */
/** `retiring_soon` = 단종 임박. 관리자가 수동으로 전환하며 관심 사용자에게 알림이 간다. */
export type RetirementStatus = "active" | "retiring_soon" | "retired";

export interface LegoSet {
  readonly setNumber: string;
  readonly name: string;
  readonly theme: string;
  readonly pieceCount: number;
  readonly releaseYear: number;
  readonly retirementStatus: RetirementStatus;
  readonly imageUrl: string | null;
}

export function isRetired(set: LegoSet): boolean {
  return set.retirementStatus === "retired";
}

export function isRetiringSoon(set: LegoSet): boolean {
  return set.retirementStatus === "retiring_soon";
}
