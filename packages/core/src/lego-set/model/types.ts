/**
 * LEGO 카탈로그 세트 엔티티 (요구사항 4: LEGO Set Catalog).
 * 백엔드 Catalog_Service 응답과 1:1 대응하는 도메인 타입.
 */
export type RetirementStatus = "active" | "retired";

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
