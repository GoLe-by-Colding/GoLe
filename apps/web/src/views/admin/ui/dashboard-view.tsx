"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import {
  fetchAdminAudit,
  fetchAdminOverview,
  type AdminAuditEntry,
  type AdminOverview,
  type AdminPaymentReadiness,
} from "@entities/admin";
import { useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import { formatKrw, paymentMethodLabel } from "@shared/lib";
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
  const [reviewPayments, setReviewPayments] = useState(0);
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
          setReviewPayments(data.ordersByStatus.PAYMENT_REVIEW ?? 0);
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
    <div className="gole-rise-in flex flex-col gap-8">
      <AdminStatus error={error} loading={overview === null && error === undefined} />

      {overview !== null ? <PaymentReadinessPanel readiness={overview.paymentReadiness} /> : null}

      <section className="flex flex-col gap-3">
        <div className="flex items-end justify-between gap-3">
          <div>
            <Heading level={3}>운영 작업함</Heading>
            <Text tone="muted" size="sm">
              지금 확인하거나 조치해야 할 항목입니다.
            </Text>
          </div>
        </div>
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          <Link href="/admin/reports">
            <Card
              padded
              className={
                pendingReports > 0
                  ? "h-full border-warning/40 bg-warning-soft"
                  : "h-full transition-[border-color,transform,box-shadow] duration-200 motion-safe:hover:-translate-y-0.5 hover:border-brand-200 hover:shadow-soft"
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
                  : "h-full transition-[border-color,transform,box-shadow] duration-200 motion-safe:hover:-translate-y-0.5 hover:border-brand-200 hover:shadow-soft"
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
            <Card
              padded
              className="h-full transition-[border-color,transform,box-shadow] duration-200 motion-safe:hover:-translate-y-0.5 hover:border-brand-200 hover:shadow-soft"
            >
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
          <Link href="/admin/orders?status=PAYMENT_REVIEW">
            <Card
              padded
              className={
                reviewPayments > 0
                  ? "h-full border-danger/30 bg-danger-soft"
                  : "h-full transition-[border-color,transform,box-shadow] duration-200 motion-safe:hover:-translate-y-0.5 hover:border-brand-200 hover:shadow-soft"
              }
            >
              <div className="flex items-center justify-between gap-3">
                <Text weight="medium">결제 확인 필요</Text>
                <Badge tone={reviewPayments > 0 ? "danger" : "success"}>{reviewPayments}건</Badge>
              </div>
              <Text tone="muted" size="sm" className="mt-2">
                금액·PG 상태 수동 확인 →
              </Text>
            </Card>
          </Link>
          <Link href="/admin/settlements?status=PENDING">
            <Card
              padded
              className={
                pendingSettlements > 0
                  ? "h-full border-warning/40 bg-warning-soft"
                  : "h-full transition-[border-color,transform,box-shadow] duration-200 motion-safe:hover:-translate-y-0.5 hover:border-brand-200 hover:shadow-soft"
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

/**
 * 결제수단 식별자를 사람이 읽는 말로 옮긴다. 값은 분류 이름(CARD)일 수도, 간편결제 사업자
 * 이름(KAKAOPAY)일 수도 있어서 분류로 먼저 읽어 보고, 표기 테이블에 없으면 사업자로 읽는다.
 *
 * 표기 테이블은 shared의 한 벌을 그대로 쓴다 — 관리자 화면만 다른 이름을 쓰면 안 된다.
 */
function readinessMethodLabel(id: string): string {
  const asType = paymentMethodLabel({ type: id, provider: null });
  return asType === id ? paymentMethodLabel({ type: "UNKNOWN", provider: id }) : asType;
}

function PaymentReadinessPanel({
  readiness,
}: {
  readonly readiness: AdminPaymentReadiness | undefined;
}) {
  if (readiness === undefined) {
    return (
      <section className="flex flex-col gap-3">
        <Heading level={3}>결제 연동</Heading>
        <Card padded className="border-warning/40 bg-warning-soft">
          <div className="flex items-center justify-between gap-3">
            <Text weight="medium">PortOne · 카카오페이</Text>
            <Badge tone="warning">상태 확인 불가</Badge>
          </div>
          <Text tone="secondary" size="sm" className="mt-2">
            현재 API 버전이 결제 준비 상태를 제공하지 않습니다. 백엔드 배포 버전을 확인하세요.
          </Text>
        </Card>
      </section>
    );
  }

  // 구버전 API는 methods를 내보내지 않는다. 그 시절에는 카카오페이만 열려 있었다.
  const methodLabels = (readiness.methods ?? ["KAKAOPAY"]).map(readinessMethodLabel).join(" · ");
  const isTest = readiness.ready && readiness.channelType === "TEST";
  const tone = readiness.ready ? (isTest ? "warning" : "success") : "danger";
  const label = readiness.ready
    ? isTest
      ? "테스트 설정 준비"
      : "실결제 설정 준비"
    : readiness.state === "DISABLED"
      ? "비활성"
      : "설정 확인 필요";
  const description = readiness.ready
    ? isTest
      ? `${methodLabels} TEST 결제에 필요한 서버 설정이 모두 존재합니다. 실제 승인·웹훅은 테스트 결제로 확인하세요.`
      : `${methodLabels} LIVE 결제에 필요한 서버 설정이 모두 존재합니다. 공개 전 최소 금액 승인·웹훅·환불을 확인하세요.`
    : readiness.state === "DISABLED"
      ? "PortOne 서버 검증이 비활성화되어 있습니다. 이 상태에서는 운영 결제를 시작하지 마세요."
      : "필수 설정이 누락되었거나 허용되지 않은 값입니다. 아래 환경변수를 확인하세요.";

  return (
    <section className="flex flex-col gap-3">
      <div>
        <Heading level={3}>결제 연동</Heading>
        <Text tone="muted" size="sm">
          비밀값 원문은 표시하지 않고 서버 설정의 존재 여부만 진단합니다. 키 유효성은 실제 테스트로
          확인해야 합니다.
        </Text>
      </div>
      <Card
        padded
        className={
          readiness.ready
            ? isTest
              ? "border-warning/40 bg-warning-soft"
              : "border-success/30 bg-success-soft"
            : "border-danger/30 bg-danger-soft"
        }
      >
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <Text weight="medium">PortOne · {methodLabels}</Text>
            <Text tone="secondary" size="sm">
              {readiness.currency} · {readiness.channelType} 채널
            </Text>
          </div>
          <Badge tone={tone}>{label}</Badge>
        </div>
        <Text tone="secondary" size="sm" className="mt-3">
          {description}
        </Text>
        {readiness.issues.length > 0 ? (
          <div className="mt-3 flex flex-wrap gap-1.5" aria-label="결제 설정 문제">
            {readiness.issues.map((issue) => (
              <Badge key={issue.setting} tone="danger">
                {issue.setting} · {issue.problem === "MISSING" ? "누락" : "값 오류"}
              </Badge>
            ))}
          </div>
        ) : null}
      </Card>
    </section>
  );
}
