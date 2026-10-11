/**
 * 부족 부품 요청 도메인 타입. 백엔드 `PartRequestDtos`와 대응. (wanted-parts W1·W9)
 */
export type PartRequestStatus = "open" | "closed";

/** 게시판 조회 필터. 서버 기본값은 `open`이다. */
export type PartRequestStatusFilter = PartRequestStatus | "all";

/** 찾는 부품 한 줄. `partNumber`는 BrickLink·LEGO 부품 번호(예: 3062b), `colorName`은 자유 입력. */
export interface WantedPart {
  readonly partNumber: string;
  readonly colorName: string;
  readonly quantity: number;
}

export interface PartRequest {
  readonly id: string;
  readonly requesterId: string;
  /** 세트를 지정하지 않은 요청이면 `null`. */
  readonly setNumber: string | null;
  readonly items: readonly WantedPart[];
  /** 메모가 없으면 빈 문자열. */
  readonly note: string;
  readonly status: PartRequestStatus;
  readonly createdAt: string;
  readonly closedAt: string | null;
}

export interface CreatePartRequestInput {
  readonly setNumber?: string | null;
  readonly items: readonly WantedPart[];
  readonly note?: string;
}

export interface PartRequestQuery {
  readonly setNumber?: string;
  readonly status?: PartRequestStatusFilter;
  readonly limit?: number;
}

/** 서버가 내려주는 오류 코드. 화면이 사유별 문구를 고를 때 쓴다. */
export const PART_REQUEST_ERROR = {
  INVALID: "PART_REQUEST_INVALID",
  SET_NOT_FOUND: "PART_REQUEST_SET_NOT_FOUND",
  LIMIT_EXCEEDED: "PART_REQUEST_LIMIT_EXCEEDED",
  NOT_FOUND: "PART_REQUEST_NOT_FOUND",
  ACCESS_DENIED: "PART_REQUEST_ACCESS_DENIED",
  NOT_OPEN: "PART_REQUEST_NOT_OPEN",
} as const;
