import type {
  AdminSupportNoticeFilter,
  AdminSupportNoticeKind,
  AdminSupportNoticeSummary,
  AdminSupportTicket,
} from "@entities/admin";
import { Badge, Button } from "@shared/ui";
import { formatDateTime } from "../model/labels";

export function SupportDeadlineBadge({
  ticket,
  nowMs,
  kind,
}: {
  readonly ticket: AdminSupportTicket;
  readonly nowMs: number | null;
  readonly kind: AdminSupportNoticeKind;
}) {
  const due = kind === "PROGRESS" ? ticket.progressDueAt : ticket.responseDueAt;
  if (!due) return null;
  const dueMs = Date.parse(due);
  if (!Number.isFinite(dueMs)) return null;
  const label = kind === "PROGRESS" ? "진행 안내" : "결과·방안";
  const sent = kind === "PROGRESS" ? ticket.progressNotifiedAt : ticket.resultNotifiedAt;
  if (sent && Number.isFinite(Date.parse(sent))) {
    const late = Date.parse(sent) > dueMs;
    return (
      <Badge
        tone={late ? "warning" : "success"}
        title={`실제 안내 저장 ${formatDateTime(sent)} · 내부 목표 ${formatDateTime(due)}`}
      >
        {label} {late ? "목표 후 안내" : "목표 내 안내"}
      </Badge>
    );
  }
  if (ticket.status === "RESOLVED") return <Badge tone="neutral">{label} 기록 확인</Badge>;
  if (nowMs === null) return <Badge tone="neutral">{label} 기록 없음</Badge>;
  if (nowMs > dueMs) {
    const days = Math.floor((nowMs - dueMs) / 86_400_000);
    return (
      <Badge tone="danger" title={`안내 기록 없음 · 내부 목표 ${formatDateTime(due)}`}>
        {label} {days === 0 ? "1일 미만" : `${days}일`} 초과 · 미기록
      </Badge>
    );
  }
  const days = Math.ceil((dueMs - nowMs) / 86_400_000);
  return (
    <Badge
      tone={days <= (kind === "PROGRESS" ? 1 : 2) ? "warning" : "brand"}
      title={`안내 기록 없음 · 내부 목표 ${formatDateTime(due)}`}
    >
      {label} {days}일 남음
    </Badge>
  );
}

export function SupportNoticeTargets({ ticket }: { readonly ticket: AdminSupportTicket }) {
  const dates = [
    ["접수", ticket.createdAt],
    ["진행 안내 목표", ticket.progressDueAt],
    ["최초 진행 안내", ticket.progressNotifiedAt],
    ["결과·방안 목표", ticket.responseDueAt],
    ["최초 결과 안내", ticket.resultNotifiedAt],
  ] as const;
  return (
    <section aria-label="접수와 안내 목표">
      <h4 className="text-sm font-bold text-neutral-800">접수와 안내 목표</h4>
      <dl className="mt-3 space-y-2 text-xs tabular-nums">
        {dates.map(([label, date]) => (
          <div key={label} className="flex flex-wrap justify-between gap-x-3 gap-y-1">
            <dt className="text-neutral-600">{label}</dt>
            <dd className="text-neutral-800">
              {date ? <time dateTime={date}>{formatDateTime(date)}</time> : "기록 없음"}
            </dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

export function SupportNoticePanel({
  summary,
  activeFilter,
  error,
  onRetry,
  onSelect,
}: {
  readonly summary: AdminSupportNoticeSummary | null;
  readonly activeFilter: "ALL" | AdminSupportNoticeFilter;
  readonly error: string | undefined;
  readonly onRetry: () => void;
  readonly onSelect: (filter: AdminSupportNoticeFilter) => void;
}) {
  return (
    <section
      aria-label="안내 이행 현황"
      className="min-w-0 rounded-xl border border-neutral-200 bg-white tabular-nums"
    >
      <div className="border-b border-neutral-100 px-4 py-3">
        <h3 className="font-display font-bold text-neutral-900">지금 안내가 필요한 문의</h3>
        <p className="mt-1 text-sm leading-relaxed text-neutral-500">
          미해결 문의의 임박·초과 건부터 확인합니다. 해결한 문의의 미기록은 따로 살펴봅니다.
        </p>
      </div>
      {error ? (
        <div role="alert" className="flex flex-wrap items-center justify-between gap-3 p-4">
          <p className="text-sm text-danger">{error}</p>
          <Button size="sm" variant="secondary" onClick={onRetry}>
            안내 현황 다시 확인
          </Button>
        </div>
      ) : summary === null ? (
        <p role="status" className="p-4 text-sm text-neutral-500">
          안내 현황을 확인하고 있어요.
        </p>
      ) : (
        <>
          <div className="divide-y divide-neutral-100">
            {(["PROGRESS", "RESULT"] as const).map((kind) => {
              const counts = kind === "PROGRESS" ? summary.progress : summary.result;
              const active = kind === "PROGRESS" ? summary.activeProgress : summary.activeResult;
              const label = kind === "PROGRESS" ? "진행 안내" : "결과·처리방안";
              return (
                <div
                  key={kind}
                  className="grid min-w-0 gap-3 px-4 py-4 sm:grid-cols-[minmax(0,1fr)_auto]"
                  aria-label={`${label} 현황`}
                >
                  <div>
                    <p className="text-sm font-semibold text-neutral-900">{label}</p>
                    <p className="mt-1 text-xs leading-relaxed text-neutral-500">
                      전체 · 목표 내 안내 {counts.onTime}건 · 목표 후 안내 {counts.late}건 · 아직
                      여유 {counts.pending}건
                    </p>
                  </div>
                  <div className="flex flex-wrap items-center gap-2">
                    <Button
                      size="sm"
                      variant="secondary"
                      aria-label={`${label} 미해결 임박 ${active.dueSoon}건`}
                      aria-pressed={activeFilter === `${kind}_DUE_SOON`}
                      onClick={() => onSelect(`${kind}_DUE_SOON`)}
                    >
                      임박 {active.dueSoon}건
                    </Button>
                    <Button
                      size="sm"
                      variant="secondary"
                      aria-label={`${label} 미해결 초과 ${active.overdue}건`}
                      aria-pressed={activeFilter === `${kind}_OVERDUE`}
                      onClick={() => onSelect(`${kind}_OVERDUE`)}
                    >
                      <span className={active.overdue > 0 ? "text-danger" : undefined}>
                        초과 {active.overdue}건
                      </span>
                    </Button>
                  </div>
                </div>
              );
            })}
          </div>
          <div className="border-t border-neutral-100 px-4 py-3">
            <p className="text-sm font-semibold text-neutral-800">해결한 문의 · 안내 기록 확인</p>
            <p className="mt-1 text-xs leading-relaxed text-neutral-500">
              미기록을 실제 미안내로 단정하지 않습니다. 기존 대화부터 확인해 주세요.
            </p>
            <div className="mt-2 flex flex-wrap gap-2">
              <Button
                size="sm"
                variant="ghost"
                aria-pressed={activeFilter === "PROGRESS_MISSING"}
                onClick={() => onSelect("PROGRESS_MISSING")}
              >
                진행 안내 미기록 {summary.resolvedMissingProgress}건
              </Button>
              <Button
                size="sm"
                variant="ghost"
                aria-pressed={activeFilter === "RESULT_MISSING"}
                onClick={() => onSelect("RESULT_MISSING")}
              >
                결과 안내 미기록 {summary.resolvedMissingResult}건
              </Button>
            </div>
          </div>
          <p className="border-t border-neutral-100 px-4 py-3 text-xs leading-relaxed text-neutral-500">
            보존 문의 전체 {summary.total}건 · {formatDateTime(summary.generatedAt)} 기준. 현재 목록
            필터와 별개이며 고객 읽음·법정 기한 준수의 증명은 아닙니다.
          </p>
        </>
      )}
    </section>
  );
}
