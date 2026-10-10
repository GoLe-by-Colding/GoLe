"use client";

import type { ReactNode } from "react";
import { cn } from "@shared/lib";
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
 *
 * 콘솔 본문은 데스크톱에서도 약 930px라 `minWidth`가 그보다 넓은 표는 가로로 스크롤된다.
 * 그때 잘리는 쪽이 조치 버튼이 되지 않게 md 이상에서는 마지막 열을 오른쪽에 고정한다.
 * 빈 목록은 표 너비를 강제하지 않고 안내를 스크롤 영역 밖에 둔다 — 넓은 표 가운데에 두면
 * 좁은 화면에서 안내가 보이지 않는 빈 상자가 된다.
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
  const hasRows = rowCount > 0;
  return (
    <Card padded={false} className="min-w-0 max-w-full overflow-hidden">
      <div className="overflow-x-auto">
        <table
          className={cn("w-full border-collapse text-sm", hasRows && STICKY_LAST_COLUMN)}
          style={hasRows ? { minWidth } : undefined}
        >
          <caption className="sr-only">
            {caption} · {rowCount.toLocaleString("ko-KR")}개 결과
          </caption>
          <thead>
            <tr className="bg-neutral-50 text-xs text-neutral-500">
              {headers.map((header, index) => (
                <th
                  key={header}
                  className={`whitespace-nowrap px-3 py-2 font-medium ${alignRight.includes(index) ? "text-right" : "text-left"}`}
                >
                  {header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>{children}</tbody>
        </table>
      </div>
      {hasRows ? null : (
        <div className="border-t border-neutral-100 px-3 py-10 text-center">
          <Text tone="muted" size="sm">
            {empty}
          </Text>
        </div>
      )}
    </Card>
  );
}

/**
 * md 이상에서 마지막 열(조치·상태)을 스크롤 영역 오른쪽에 붙인다. 행 배경을 칠해야 스크롤되는 열이
 * 비쳐 보이지 않고, 왼쪽 안쪽 그림자로 고정된 열의 경계를 보인다.
 */
const STICKY_LAST_COLUMN = cn(
  "md:[&_tr>*:last-child]:sticky md:[&_tr>*:last-child]:right-0",
  "md:[&_tr>*:last-child]:shadow-[inset_1px_0_0_var(--color-neutral-100)]",
  "md:[&_tbody_tr>*:last-child]:bg-white md:[&_thead_tr>*:last-child]:bg-neutral-50",
);

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
