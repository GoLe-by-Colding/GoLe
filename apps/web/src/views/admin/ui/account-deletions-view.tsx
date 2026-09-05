"use client";

import { useCallback, useEffect, useState } from "react";
import {
  completeAdminAccountDeletion,
  fetchAdminAccountDeletionRequests,
  holdAdminAccountDeletion,
  releaseAdminAccountDeletionHold,
  reviewAdminAccountDeletion,
  type AdminAccountDeletionHoldReason,
  type AdminAccountDeletionRequest,
  type AdminAccountDeletionStatus,
} from "@entities/admin";
import { useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import { Badge, Button, Heading, Text } from "@shared/ui";
import { AdminStatus, AdminTable } from "./table";

const STATUS_LABEL: Record<AdminAccountDeletionStatus, string> = {
  BLOCKED: "보존/수명주기 검토 필요",
  READY: "파기 가능",
  COMPLETED: "파기 완료",
};

const BLOCKER_LABEL: Readonly<Record<string, string>> = {
  ACTIVE_ORDER: "진행 주문/분쟁",
  UNSETTLED_PAYOUT: "미완료 정산",
  PENDING_REPORT: "처리 중 신고",
  SUPPORT_RECORDS_REQUIRE_PURGE: "문의 기록 연계 파기 필요",
  PUBLIC_CONTENT_REQUIRES_LIFECYCLE_REVIEW: "공개 콘텐츠 정리 필요",
  MEDIA_REQUIRES_LIFECYCLE_REVIEW: "미디어 정리 필요",
  OWNED_GROUP_REQUIRES_TRANSFER: "그룹 소유권 이전 필요",
  EXPLICIT_RETENTION_HOLD: "명시적 보존 중지",
};

const HOLD_REASONS: readonly AdminAccountDeletionHoldReason[] = [
  "LEGAL_OBLIGATION",
  "DISPUTE_OR_CLAIM",
  "FRAUD_OR_SECURITY_INVESTIGATION",
];

export function AdminAccountDeletionsView() {
  const { session } = useSession();
  const token = session?.sessionToken ?? null;
  const [status, setStatus] = useState<AdminAccountDeletionStatus | "">("");
  const [rows, setRows] = useState<readonly AdminAccountDeletionRequest[] | null>(null);
  const [error, setError] = useState<string | undefined>();
  const [busyId, setBusyId] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (token === null) return;
    try {
      const nextRows = await fetchAdminAccountDeletionRequests(token, status || undefined);
      setRows(nextRows);
      setError(undefined);
    } catch (cause) {
      setRows([]);
      setError(cause instanceof ApiError ? cause.message : "탈퇴 요청을 불러오지 못했습니다.");
    }
  }, [status, token]);

  useEffect(() => {
    if (token === null) return;
    let cancelled = false;
    void fetchAdminAccountDeletionRequests(token, status || undefined)
      .then((nextRows) => {
        if (cancelled) return;
        setRows(nextRows);
        setError(undefined);
      })
      .catch((cause: unknown) => {
        if (cancelled) return;
        setRows([]);
        setError(cause instanceof ApiError ? cause.message : "탈퇴 요청을 불러오지 못했습니다.");
      });
    return () => {
      cancelled = true;
    };
  }, [status, token]);

  async function run(
    requestId: string,
    operation: () => Promise<AdminAccountDeletionRequest>,
  ): Promise<void> {
    setBusyId(requestId);
    setError(undefined);
    try {
      await operation();
      await load();
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "요청을 처리하지 못했습니다.");
    } finally {
      setBusyId(null);
    }
  }

  function confirmRequestId(requestId: string, action: string): boolean {
    return (
      window.prompt(`${action}하려면 요청 ID를 그대로 입력하세요.\n${requestId}`) === requestId
    );
  }

  function placeHold(row: AdminAccountDeletionRequest): void {
    if (token === null || !confirmRequestId(row.requestId, "보존 중지")) return;
    const selected = window.prompt(
      `보존 사유 코드를 입력하세요.\n${HOLD_REASONS.join("\n")}`,
      "DISPUTE_OR_CLAIM",
    );
    if (!HOLD_REASONS.includes(selected as AdminAccountDeletionHoldReason)) {
      setError("허용된 보존 사유 코드를 선택해 주세요.");
      return;
    }
    void run(row.requestId, () =>
      holdAdminAccountDeletion(token, row.requestId, selected as AdminAccountDeletionHoldReason),
    );
  }

  function complete(row: AdminAccountDeletionRequest): void {
    if (token === null || !confirmRequestId(row.requestId, "연계 파기")) return;
    const reviewed = window.confirm(
      "진행 거래·정산·분쟁·신고·문의·법정 보존과 공개 콘텐츠 수명주기를 모두 확인했습니까? 서버가 마지막으로 다시 검사합니다.",
    );
    if (!reviewed) return;
    void run(row.requestId, () =>
      completeAdminAccountDeletion(token, row.requestId, crypto.randomUUID()),
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <Heading level={2}>회원 탈퇴 검토</Heading>
      <Text tone="muted" size="sm">
        요청 즉시 계정과 세션은 차단됩니다. 이 화면은 대상 이메일·accountId를 표시하지 않으며, 보존
        조건이 하나라도 남으면 서버가 파기를 거부합니다.
      </Text>

      <label className="flex items-center gap-2 text-sm text-neutral-700">
        상태
        <select
          value={status}
          onChange={(event) => setStatus(event.target.value as AdminAccountDeletionStatus | "")}
          className="h-10 rounded-md border border-neutral-300 bg-white px-3"
        >
          <option value="">전체</option>
          <option value="BLOCKED">검토 필요</option>
          <option value="READY">파기 가능</option>
          <option value="COMPLETED">완료</option>
        </select>
      </label>

      <AdminStatus error={error} loading={rows === null} />
      <AdminTable
        caption="비식별 회원 탈퇴 처리 목록"
        headers={["요청 ID", "상태", "차단 조건", "보존 중지", "요청/갱신", "관리"]}
        alignRight={[5]}
        minWidth={980}
        empty="탈퇴 요청이 없습니다."
        rowCount={(rows ?? []).length}
      >
        {(rows ?? []).map((row) => {
          const busy = busyId === row.requestId;
          return (
            <tr key={row.requestId} className="border-t border-neutral-100 align-top">
              <td className="max-w-[180px] break-all px-3 py-2.5 font-mono text-xs text-neutral-700">
                {row.requestId}
              </td>
              <td className="px-3 py-2.5">
                <Badge
                  tone={
                    row.status === "READY"
                      ? "success"
                      : row.status === "BLOCKED"
                        ? "warning"
                        : "neutral"
                  }
                >
                  {STATUS_LABEL[row.status]}
                </Badge>
              </td>
              <td className="max-w-[260px] px-3 py-2.5 text-xs leading-relaxed text-neutral-600">
                {row.blockers.length === 0
                  ? "없음"
                  : row.blockers.map((blocker) => BLOCKER_LABEL[blocker] ?? blocker).join(" · ")}
              </td>
              <td className="px-3 py-2.5 text-xs text-neutral-600">{row.holdReason ?? "—"}</td>
              <td className="whitespace-nowrap px-3 py-2.5 text-xs text-neutral-500">
                {new Date(row.requestedAt).toLocaleString("ko-KR")}
                <br />
                {new Date(row.updatedAt).toLocaleString("ko-KR")}
              </td>
              <td className="px-3 py-2.5 text-right">
                {row.status === "COMPLETED" ? (
                  <span className="text-xs text-neutral-500">
                    {Object.values(row.deletionCounts).reduce((sum, count) => sum + count, 0)}건
                    처리
                  </span>
                ) : (
                  <span className="inline-flex flex-wrap justify-end gap-1">
                    <Button
                      size="sm"
                      variant="secondary"
                      disabled={busy || token === null}
                      onClick={() =>
                        void run(row.requestId, () =>
                          reviewAdminAccountDeletion(token ?? "", row.requestId),
                        )
                      }
                    >
                      재검사
                    </Button>
                    {row.holdReason === null ? (
                      <Button
                        size="sm"
                        variant="ghost"
                        disabled={busy}
                        onClick={() => placeHold(row)}
                      >
                        보존 중지
                      </Button>
                    ) : (
                      <Button
                        size="sm"
                        variant="ghost"
                        disabled={busy || token === null}
                        onClick={() => {
                          if (!confirmRequestId(row.requestId, "보존 중지 해제")) return;
                          void run(row.requestId, () =>
                            releaseAdminAccountDeletionHold(token ?? "", row.requestId),
                          );
                        }}
                      >
                        중지 해제
                      </Button>
                    )}
                    <Button
                      size="sm"
                      variant="danger"
                      disabled={busy || row.status !== "READY"}
                      onClick={() => complete(row)}
                    >
                      연계 파기
                    </Button>
                  </span>
                )}
              </td>
            </tr>
          );
        })}
      </AdminTable>
    </div>
  );
}
