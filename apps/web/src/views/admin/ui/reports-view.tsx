"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import {
  dismissAdminReport,
  fetchAdminChatReportSnapshot,
  fetchAdminReports,
  resolveAdminReport,
  resolveAdminReportTarget,
  type AdminChatReportSnapshot,
  type AdminReport,
} from "@entities/admin";
import { useSession } from "@entities/user";
import { ReasonPrompt, useModerationAction } from "@features/admin-moderation";
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
  const [snapshot, setSnapshot] = useState<AdminChatReportSnapshot | null>(null);
  const [snapshotLoading, setSnapshotLoading] = useState(false);

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
  const targetAction = useModerationAction(load);

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

  async function openSnapshot(reportId: string) {
    if (token === null) return;
    setSnapshotLoading(true);
    setSnapshot(null);
    setError(undefined);
    try {
      setSnapshot(await fetchAdminChatReportSnapshot(token, reportId));
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "채팅 신고 문맥을 불러오지 못했습니다.");
    } finally {
      setSnapshotLoading(false);
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
        대상을 확인한 뒤 바로 내림·삭제와 신고 완료를 한 번에 처리할 수 있습니다. 모든 조치와 사유는
        감사 로그에 남습니다.
      </Text>

      <AdminStatus error={error} loading={rows === null} />

      <AdminTable
        caption="신고 처리 목록"
        headers={["대상", "사유", "상세", "신고자", "접수", "상태", "처리"]}
        alignRight={[6]}
        minWidth={820}
        empty="해당 상태의 신고가 없습니다."
        rowCount={(rows ?? []).length}
      >
        {(rows ?? []).map((r) => (
          <tr key={r.id} className="border-t border-neutral-100">
            <td className="px-3 py-2.5 font-medium">
              {r.targetType === "CHAT_MESSAGE" ? (
                <button
                  type="button"
                  className="text-left text-neutral-900 hover:text-brand-600"
                  onClick={() => void openSnapshot(r.id)}
                >
                  채팅 {shortId(r.targetId)} · 고정 문맥 보기 →
                </button>
              ) : (
                <Link
                  href={
                    r.targetType === "LISTING"
                      ? `/listings/${r.targetId}`
                      : `/community/${r.targetId}`
                  }
                  className="text-neutral-900 hover:text-brand-600"
                >
                  {r.targetType === "LISTING" ? "매물" : "게시글"} {shortId(r.targetId)} →
                </Link>
              )}
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
                  {r.targetType === "CHAT_MESSAGE" ? (
                    <Button
                      size="sm"
                      variant="secondary"
                      onClick={() => void handle(r.id, "resolve")}
                    >
                      검토 완료
                    </Button>
                  ) : (
                    <Button
                      size="sm"
                      variant="danger"
                      onClick={() =>
                        targetAction.ask({
                          title:
                            r.targetType === "LISTING" ? "신고 매물 내리기" : "신고 게시글 삭제",
                          target: `${r.targetType === "LISTING" ? "매물" : "게시글"} ${shortId(r.targetId)}`,
                          confirmLabel:
                            r.targetType === "LISTING" ? "내리고 완료" : "삭제하고 완료",
                          run: async (reason) => {
                            await resolveAdminReportTarget(token ?? "", r.id, reason);
                          },
                        })
                      }
                    >
                      {r.targetType === "LISTING" ? "내리고 완료" : "삭제하고 완료"}
                    </Button>
                  )}
                  {r.targetType !== "CHAT_MESSAGE" ? (
                    <Button
                      size="sm"
                      variant="secondary"
                      onClick={() => void handle(r.id, "resolve")}
                    >
                      이미 조치됨
                    </Button>
                  ) : null}
                  <Button size="sm" variant="ghost" onClick={() => void handle(r.id, "dismiss")}>
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

      {snapshotLoading ? (
        <div className="rounded-lg border border-brand-100 bg-brand-50 px-5 py-8 text-center text-sm text-brand-700">
          신고 당시 대화 문맥을 확인하는 중…
        </div>
      ) : null}

      {snapshot !== null ? (
        <section
          className="rounded-xl border border-neutral-200 bg-neutral-50 p-5"
          aria-label="채팅 신고 스냅샷"
        >
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <div className="flex items-center gap-2">
                <Heading level={3}>신고 당시 대화 문맥</Heading>
                <Badge tone="warning">읽기 전용</Badge>
              </div>
              <Text className="mt-1" size="sm" tone="muted">
                신고 시 서버가 고정한 앞뒤 메시지만 표시됩니다. 현재 채팅방의 추가 대화에는 접근하지
                않습니다.
              </Text>
            </div>
            <Button size="sm" variant="ghost" onClick={() => setSnapshot(null)}>
              닫기
            </Button>
          </div>
          <ol className="mt-4 flex max-h-96 flex-col gap-2 overflow-y-auto rounded-lg border border-neutral-200 bg-white p-4">
            {snapshot.messages.map((message) => {
              const reported = message.messageId === snapshot.reportedMessageId;
              return (
                <li
                  key={message.messageId}
                  className={
                    reported
                      ? "rounded-lg border border-danger/30 bg-danger-soft p-3"
                      : "rounded-lg border border-neutral-100 bg-neutral-50 p-3"
                  }
                >
                  <div className="flex items-center justify-between gap-3 text-xs text-neutral-500">
                    <span>작성자 {shortId(message.senderId)}</span>
                    <time>{formatDateTime(message.sentAt)}</time>
                  </div>
                  <p className="mt-1 whitespace-pre-wrap break-words text-sm text-neutral-800">
                    {message.content}
                  </p>
                  {reported ? (
                    <Badge className="mt-2" tone="danger">
                      신고된 메시지
                    </Badge>
                  ) : null}
                </li>
              );
            })}
          </ol>
          <p className="mt-3 text-xs text-neutral-500">
            캡처 {formatDateTime(snapshot.capturedAt)} · 스냅샷 열람 기록이 감사 로그에 남았습니다.
          </p>
        </section>
      ) : null}

      {targetAction.pending !== null ? (
        <ReasonPrompt
          title={targetAction.pending.title}
          target={targetAction.pending.target}
          confirmLabel={targetAction.pending.confirmLabel}
          busy={targetAction.busy}
          error={targetAction.error}
          onConfirm={targetAction.confirm}
          onCancel={targetAction.cancel}
        />
      ) : null}
    </div>
  );
}
