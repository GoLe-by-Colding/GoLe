"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import {
  fetchAdminAudit,
  fetchAdminOverview,
  fetchAdminReports,
  type AdminAuditEntry,
  type AdminOverview,
} from "@entities/admin";
import { useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import { formatKrw } from "@shared/lib";
import { Badge, Card, Heading, Text } from "@shared/ui";
import {
  AUDIT_TYPE_LABEL,
  AUDIT_TYPE_TONE,
  COUNT_LABEL,
  ORDER_STATUS_LABEL,
  formatDateTime,
} from "../model/labels";
import { AdminStatus } from "./table";

/** 대시보드 — 오늘 처리할 일과 플랫폼 지표. (요구사항 2.2, 2.3, 2.4) */
export function AdminDashboardView() {
  const { session } = useSession();
  const token = session?.sessionToken ?? null;

  const [overview, setOverview] = useState<AdminOverview | null>(null);
  const [pendingReports, setPendingReports] = useState(0);
  const [audit, setAudit] = useState<readonly AdminAuditEntry[]>([]);
  const [error, setError] = useState<string | undefined>(undefined);

  useEffect(() => {
    if (token === null) {
      return;
    }
    let active = true;
    void (async () => {
      try {
        const [data, reports, actions] = await Promise.all([
          fetchAdminOverview(token),
          fetchAdminReports(token, 100, "PENDING"),
          fetchAdminAudit(token, 8),
        ]);
        if (active) {
          setOverview(data);
          setPendingReports(reports.length);
          setAudit(actions);
        }
      } catch (cause) {
        if (active) {
          setError(cause instanceof ApiError ? cause.message : "대시보드를 불러오지 못했습니다.");
        }
      }
    })();
    return () => {
      active = false;
    };
  }, [token]);

  return (
    <div className="flex flex-col gap-8">
      <AdminStatus error={error} loading={overview === null && error === undefined} />

      {pendingReports > 0 ? (
        <Link href="/admin/reports">
          <Card padded className="flex items-center gap-3 border-warning/40 bg-warning-soft">
            <Badge tone="warning">처리 필요</Badge>
            <Text weight="medium">미처리 신고 {pendingReports}건이 대기 중입니다 →</Text>
          </Card>
        </Link>
      ) : null}

      {overview !== null ? (
        <>
          <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Card padded className="flex flex-col gap-1">
              <Text tone="secondary" size="sm">
                거래액(GMV · 완료)
              </Text>
              <span className="text-2xl font-extrabold tracking-tight text-brand-600">
                {formatKrw(overview.gmv)}
              </span>
            </Card>
            {/* GMV는 플랫폼을 통과한 돈이고, 플랫폼의 매출은 수수료다. 둘을 나란히 두어 혼동을 막는다. */}
            <Card padded className="flex flex-col gap-1">
              <Text tone="secondary" size="sm">
                플랫폼 수익(수수료)
              </Text>
              <span className="text-2xl font-extrabold tracking-tight">
                {formatKrw(overview.platformRevenue)}
              </span>
              <Text tone="muted" size="sm">
                완료 주문 기준 · 지급 실행 미연동
              </Text>
            </Card>
            <Card padded className="flex flex-col gap-1">
              <Text tone="secondary" size="sm">
                활성 매물
              </Text>
              <span className="text-2xl font-extrabold tracking-tight">
                {overview.activeListings.toLocaleString("ko-KR")}
              </span>
            </Card>
            <Card padded className="flex flex-col gap-1">
              <Text tone="secondary" size="sm">
                주문 상태
              </Text>
              <div className="mt-1 flex flex-wrap gap-1.5">
                {Object.entries(overview.ordersByStatus).length === 0 ? (
                  <Text tone="muted" size="sm">
                    주문 없음
                  </Text>
                ) : (
                  Object.entries(overview.ordersByStatus).map(([status, n]) => (
                    <Badge key={status} tone="neutral">
                      {ORDER_STATUS_LABEL[status] ?? status} {n}
                    </Badge>
                  ))
                )}
              </div>
            </Card>
          </section>

          <section className="flex flex-col gap-3">
            <Heading level={3}>데이터 현황</Heading>
            <div className="grid gap-4 [grid-template-columns:repeat(auto-fill,minmax(150px,1fr))]">
              {Object.entries(overview.counts).map(([key, value]) => (
                <Card key={key} padded className="flex flex-col gap-1">
                  <Text tone="secondary" size="sm">
                    {COUNT_LABEL[key] ?? key}
                  </Text>
                  <span className="text-xl font-bold tracking-tight">
                    {value.toLocaleString("ko-KR")}
                  </span>
                </Card>
              ))}
            </div>
          </section>
        </>
      ) : null}

      <section className="flex flex-col gap-3">
        <div className="flex items-center justify-between">
          <Heading level={3}>최근 관리자 조치</Heading>
          <Link href="/admin/audit" className="text-sm text-brand-600 hover:underline">
            전체 보기 →
          </Link>
        </div>
        <Card padded className="flex flex-col divide-y divide-neutral-100">
          {audit.map((entry) => (
            <div key={entry.id} className="flex items-center gap-3 py-2.5 text-sm">
              <Badge tone={AUDIT_TYPE_TONE[entry.type] ?? "neutral"}>
                {AUDIT_TYPE_LABEL[entry.type] ?? entry.type}
              </Badge>
              <span className="min-w-0 flex-1 truncate text-neutral-600">
                {entry.actorEmail}
                {entry.reason !== null ? ` · ${entry.reason}` : ""}
              </span>
              <span className="shrink-0 text-xs text-neutral-400">
                {formatDateTime(entry.occurredAt)}
              </span>
            </div>
          ))}
          {audit.length === 0 ? (
            <Text tone="muted" size="sm">
              기록된 조치가 없습니다.
            </Text>
          ) : null}
        </Card>
      </section>
    </div>
  );
}
