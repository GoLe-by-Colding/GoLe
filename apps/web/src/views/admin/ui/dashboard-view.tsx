"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import {
  fetchAdminAudit,
  fetchAdminOverview,
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
  const [failedPayments, setFailedPayments] = useState(0);
  const [pendingPayments, setPendingPayments] = useState(0);
  const [pendingSettlements, setPendingSettlements] = useState(0);
  const [audit, setAudit] = useState<readonly AdminAuditEntry[]>([]);
  const [error, setError] = useState<string | undefined>(undefined);

  useEffect(() => {
    if (token === null) {
      return;
    }
    let active = true;
    void (async () => {
      try {
        const [data, actions] = await Promise.all([
          fetchAdminOverview(token),
          fetchAdminAudit(token, 8),
        ]);
        if (active) {
          setOverview(data);
          // 롤링 배포 중 구버전 API가 이 필드를 아직 주지 않아도 숫자가 비어 보이지 않게 한다.
          setPendingReports(data.pendingReports ?? 0);
          setFailedPayments(data.ordersByStatus.PAYMENT_FAILED ?? 0);
          setPendingPayments(data.ordersByStatus.PAYMENT_PENDING ?? 0);
          setPendingSettlements(data.pendingSettlements ?? 0);
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

      <section className="flex flex-col gap-3">
        <div className="flex items-end justify-between gap-3">
          <div>
            <Heading level={3}>운영 작업함</Heading>
            <Text tone="muted" size="sm">
              지금 확인하거나 조치해야 할 항목입니다.
            </Text>
          </div>
        </div>
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <Link href="/admin/reports">
            <Card
              padded
              className={
                pendingReports > 0
                  ? "h-full border-warning/40 bg-warning-soft"
                  : "h-full transition-colors hover:border-neutral-300"
              }
            >
              <div className="flex items-center justify-between gap-3">
                <Text weight="medium">미처리 신고</Text>
                <Badge tone={pendingReports > 0 ? "warning" : "success"}>{pendingReports}건</Badge>
              </div>
              <Text tone="muted" size="sm" className="mt-2">
                대상 확인 및 내림·삭제 →
              </Text>
            </Card>
          </Link>
          <Link href="/admin/orders?status=PAYMENT_FAILED">
            <Card
              padded
              className={
                failedPayments > 0
                  ? "h-full border-danger/30 bg-danger-soft"
                  : "h-full transition-colors hover:border-neutral-300"
              }
            >
              <div className="flex items-center justify-between gap-3">
                <Text weight="medium">결제 실패 주문</Text>
                <Badge tone={failedPayments > 0 ? "danger" : "success"}>{failedPayments}건</Badge>
              </div>
              <Text tone="muted" size="sm" className="mt-2">
                오류 주문 추적 →
              </Text>
            </Card>
          </Link>
          <Link href="/admin/orders?status=PAYMENT_PENDING">
            <Card padded className="h-full transition-colors hover:border-neutral-300">
              <div className="flex items-center justify-between gap-3">
                <Text weight="medium">결제 대기 주문</Text>
                <Badge tone={pendingPayments > 0 ? "warning" : "success"}>
                  {pendingPayments}건
                </Badge>
              </div>
              <Text tone="muted" size="sm" className="mt-2">
                장기 대기 여부 확인 →
              </Text>
            </Card>
          </Link>
          <Link href="/admin/settlements?status=PENDING">
            <Card
              padded
              className={
                pendingSettlements > 0
                  ? "h-full border-warning/40 bg-warning-soft"
                  : "h-full transition-colors hover:border-neutral-300"
              }
            >
              <div className="flex items-center justify-between gap-3">
                <Text weight="medium">지급 대기 정산</Text>
                <Badge tone={pendingSettlements > 0 ? "warning" : "success"}>
                  {pendingSettlements}건
                </Badge>
              </div>
              <Text tone="muted" size="sm" className="mt-2">
                송금 및 증빙 기록 →
              </Text>
            </Card>
          </Link>
        </div>
      </section>

      {overview !== null ? (
        <>
          <section className="grid gap-4 sm:grid-cols-3">
            <Card padded className="flex flex-col gap-1">
              <Text tone="secondary" size="sm">
                거래액(GMV · 완료)
              </Text>
              <span className="text-2xl font-extrabold tracking-tight text-brand-600">
                {formatKrw(overview.gmv)}
              </span>
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
