"use client";

import type { ReactNode } from "react";
import { Card, Text } from "@shared/ui";

export interface AdminTableProps {
  readonly headers: readonly string[];
  /** 우측 정렬할 컬럼 인덱스. 금액·조치 버튼 열에 사용한다. */
  readonly alignRight?: readonly number[];
  readonly minWidth?: number;
  readonly empty: string;
  readonly rowCount: number;
  readonly children: ReactNode;
}

/**
 * 콘솔 목록 테이블. 섹션마다 같은 마크업을 반복하지 않도록 껍데기만 공통화하고,
 * 행은 각 섹션이 도메인에 맞게 직접 그린다.
 */
export function AdminTable({
  headers,
  alignRight = [],
  minWidth = 640,
  empty,
  rowCount,
  children,
}: AdminTableProps) {
  return (
    <Card padded={false} className="overflow-x-auto">
      <table className="w-full border-collapse text-sm" style={{ minWidth }}>
        <thead>
          <tr className="bg-neutral-50 text-xs text-neutral-500">
            {headers.map((header, index) => (
              <th
                key={header}
                className={`px-3 py-2 font-medium ${alignRight.includes(index) ? "text-right" : "text-left"}`}
              >
                {header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {children}
          {rowCount === 0 ? (
            <tr>
              <td colSpan={headers.length} className="px-3 py-10 text-center">
                <Text tone="muted" size="sm">
                  {empty}
                </Text>
              </td>
            </tr>
          ) : null}
        </tbody>
      </table>
    </Card>
  );
}

/** 목록 로딩/에러 상태를 한 줄로 표시한다. */
export function AdminStatus({
  error,
  loading,
}: {
  readonly error?: string | undefined;
  readonly loading: boolean;
}) {
  if (error !== undefined) {
    return <p className="text-sm text-danger">{error}</p>;
  }
  if (loading) {
    return (
      <Text tone="muted" size="sm">
        불러오는 중...
      </Text>
    );
  }
  return null;
}
