"use client";

import type { ReactNode } from "react";
import { AlertCircleIcon, Card, LoaderIcon, Text } from "@shared/ui";

export interface AdminTableProps {
  /** 표의 목적을 설명하는 접근 가능한 제목. 화면에는 노출하지 않는다. */
  readonly caption: string;
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
  caption,
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
        <caption className="sr-only">
          {caption} · {rowCount.toLocaleString("ko-KR")}개 결과
        </caption>
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

/** 목록 로딩/에러 상태를 운영 맥락이 유지되는 상태 패널로 표시한다. */
export function AdminStatus({
  error,
  loading,
}: {
  readonly error?: string | undefined;
  readonly loading: boolean;
}) {
  if (error !== undefined) {
    return (
      <Card
        padded
        className="flex items-start gap-3 border-danger/25 bg-danger-soft/65"
        role="alert"
        aria-atomic="true"
      >
        <AlertCircleIcon className="mt-0.5 size-5 shrink-0 text-danger" />
        <div className="flex flex-col gap-1">
          <Text weight="medium">운영 데이터 연결을 확인해 주세요</Text>
          <Text tone="secondary" size="sm">
            {error} 화면 구조와 메뉴는 계속 사용할 수 있으며, API가 복구되면 최신 상태를 다시
            불러옵니다.
          </Text>
        </div>
      </Card>
    );
  }
  if (loading) {
    return (
      <Card
        padded
        className="flex items-center gap-3 border-brand-100 bg-brand-50/55"
        role="status"
        aria-live="polite"
        aria-atomic="true"
      >
        <LoaderIcon className="size-5 animate-spin text-brand-600 motion-reduce:animate-none" />
        <Text tone="secondary" size="sm">
          운영 현황을 불러오는 중입니다.
        </Text>
      </Card>
    );
  }
  return null;
}
