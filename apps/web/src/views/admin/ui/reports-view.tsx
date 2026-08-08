"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import {
  dismissAdminReport,
  fetchAdminReports,
  resolveAdminReport,
  type AdminReport,
} from "@entities/admin";
import { useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import { Badge, Button, Heading, Select, Text } from "@shared/ui";
import {
  REPORT_REASON_LABEL,
  REPORT_STATUS_LABEL,
  REPORT_STATUS_TONE,
  formatDateTime,
  shortId,
} from "../model/labels";
import { AdminStatus, AdminTable } from "./table";

type StatusFilter = "ALL" | AdminReport["status"];

/** 신고 큐 — 접수된 신고를 확인하고 조치/기각한다. (요구사항 3) */
export function AdminReportsView() {
  const { session } = useSession();
  const token = session?.sessionToken ?? null;

  const [status, setStatus] = useState<StatusFilter>("PENDING");
  // null = 아직 불러오지 않음. 로딩 상태를 파생시켜 effect 안에서 setState를 동기 호출하지 않는다.
  const [rows, setRows] = useState<readonly AdminReport[] | null>(null);
  const [error, setError] = useState<string | undefined>(undefined);

  const load = useCallback(() => {
    if (token === null) {
      return;
    }
    void fetchAdminReports(token, 50, status === "ALL" ? undefined : status)
      .then(setRows)
      .catch((cause: unknown) => {
        setRows([]);
        setError(cause instanceof ApiError ? cause.message : "신고를 불러오지 못했습니다.");
      });
  }, [token, status]);

  useEffect(load, [load]);

  async function handle(reportId: string, action: "resolve" | "dismiss") {
    if (token === null) {
      return;
    }
    setError(undefined);
    try {
      const updated =
        action === "resolve"
          ? await resolveAdminReport(token, reportId)
          : await dismissAdminReport(token, reportId);
      // 조치 후 필터가 PENDING이면 목록에서 빠져야 하므로 다시 불러온다.
      if (status === "PENDING") {
        load();
      } else {
        setRows((prev) => (prev ?? []).map((r) => (r.id === reportId ? updated : r)));
      }
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "신고 처리에 실패했습니다.");
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-3">
        <Heading level={2}>신고 큐</Heading>
        <label className="flex items-center gap-2 text-sm text-neutral-600">
          상태
          <Select value={status} onChange={(e) => setStatus(e.target.value as StatusFilter)}>
            <option value="PENDING">접수</option>
            <option value="RESOLVED">조치완료</option>
            <option value="DISMISSED">기각</option>
            <option value="ALL">전체</option>
          </Select>
        </label>
      </div>

      <Text tone="muted" size="sm">
        신고 대상을 열어 확인한 뒤 조치하세요. 실제 내림·삭제는 대상 화면이나 매물·커뮤니티 탭에서
        사유와 함께 수행합니다.
      </Text>

      <AdminStatus error={error} loading={rows === null} />

      <AdminTable
        headers={["대상", "사유", "상세", "신고자", "접수", "상태", "처리"]}
        alignRight={[6]}
        minWidth={820}
        empty="해당 상태의 신고가 없습니다."
        rowCount={(rows ?? []).length}
      >
        {(rows ?? []).map((r) => (
          <tr key={r.id} className="border-t border-neutral-100">
            <td className="px-3 py-2.5 font-medium">
              <Link
                href={
                  r.targetType === "LISTING" ? `/listings/${r.targetId}` : `/community/${r.targetId}`
                }
                className="text-neutral-900 hover:text-brand-600"
              >
                {r.targetType === "LISTING" ? "매물" : "게시글"} {shortId(r.targetId)} →
              </Link>
            </td>
            <td className="px-3 py-2.5">
              <Badge tone={r.reason === "COUNTERFEIT" ? "danger" : "neutral"}>
                {REPORT_REASON_LABEL[r.reason] ?? r.reason}
              </Badge>
            </td>
            <td className="max-w-[220px] truncate px-3 py-2.5 text-neutral-600">
              {r.detail.length > 0 ? r.detail : "—"}
            </td>
            <td className="px-3 py-2.5 text-neutral-600">{shortId(r.reporterId)}</td>
            <td className="px-3 py-2.5 text-xs text-neutral-500">{formatDateTime(r.createdAt)}</td>
            <td className="px-3 py-2.5">
              <Badge tone={REPORT_STATUS_TONE[r.status] ?? "neutral"}>
                {REPORT_STATUS_LABEL[r.status] ?? r.status}
              </Badge>
            </td>
            <td className="px-3 py-2.5 text-right">
              {r.status === "PENDING" ? (
                <span className="inline-flex gap-1">
                  <Button size="sm" variant="secondary" onClick={() => handle(r.id, "resolve")}>
                    조치완료
                  </Button>
                  <Button size="sm" variant="ghost" onClick={() => handle(r.id, "dismiss")}>
                    기각
                  </Button>
                </span>
              ) : (
                <span className="text-xs text-neutral-400">
                  {formatDateTime(r.handledAt)} 처리됨
                </span>
              )}
            </td>
          </tr>
        ))}
      </AdminTable>
    </div>
  );
}
