"use client";

import { useCallback, useEffect, useState } from "react";
import {
  changeAdminAccountRole,
  fetchAdminAccounts,
  reinstateAdminAccount,
  suspendAdminAccount,
  type AdminAccount,
} from "@entities/admin";
import { useSession } from "@entities/user";
import { ReasonPrompt, useModerationAction } from "@features/admin-moderation";
import { ApiError } from "@shared/api";
import { Badge, Button, Heading, Input, Text } from "@shared/ui";
import { ACCOUNT_STATUS_LABEL, ACCOUNT_STATUS_TONE } from "../model/labels";
import { AdminStatus, AdminTable } from "./table";

/**
 * 회원 관리 — 정지/해제/권한. (요구사항 6)
 *
 * 자기 자신은 조치 대상에서 UI로도 막아, 서버가 400을 주기 전에 실수를 줄인다.
 */
export function AdminAccountsView() {
  const { session } = useSession();
  const token = session?.sessionToken ?? null;
  const myId = session?.accountId ?? null;

  const [query, setQuery] = useState("");
  // null = 아직 불러오지 않음. 로딩 상태를 파생시켜 effect 안에서 setState를 동기 호출하지 않는다.
  const [rows, setRows] = useState<readonly AdminAccount[] | null>(null);
  const [error, setError] = useState<string | undefined>(undefined);

  const load = useCallback(() => {
    if (token === null) {
      return;
    }
    void fetchAdminAccounts(token, 50, query.trim() === "" ? undefined : query.trim())
      .then(setRows)
      .catch((cause: unknown) => {
        setRows([]);
        setError(cause instanceof ApiError ? cause.message : "회원을 불러오지 못했습니다.");
      });
  }, [token, query]);

  useEffect(() => {
    const timer = setTimeout(load, 250);
    return () => clearTimeout(timer);
  }, [load]);

  const action = useModerationAction(load);

  async function directAction(run: () => Promise<unknown>) {
    setError(undefined);
    try {
      await run();
      load();
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "요청에 실패했습니다.");
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <Heading level={2}>회원 관리</Heading>
      <Text tone="muted" size="sm">
        정지하면 로그인이 차단되고 이미 발급된 세션도 즉시 폐기됩니다. 자기 자신과 마지막 관리자는
        정지·강등할 수 없습니다.
      </Text>

      <Input
        value={query}
        placeholder="이메일로 검색"
        onChange={(e) => setQuery(e.target.value)}
        className="max-w-xs"
      />

      <AdminStatus error={error} loading={rows === null} />

      <AdminTable
        headers={["이메일", "권한", "상태", "정지 사유", "관리"]}
        alignRight={[4]}
        minWidth={780}
        empty="회원이 없습니다."
        rowCount={(rows ?? []).length}
      >
        {(rows ?? []).map((a) => {
          const isSelf = a.id === myId;
          const suspended = a.status === "SUSPENDED";
          return (
            <tr key={a.id} className="border-t border-neutral-100">
              <td className="px-3 py-2.5 text-neutral-800">
                {a.email}
                {isSelf ? <span className="ml-1.5 text-xs text-neutral-400">(나)</span> : null}
              </td>
              <td className="px-3 py-2.5">
                <Badge tone={a.role === "ADMIN" ? "brand" : "neutral"}>{a.role}</Badge>
              </td>
              <td className="px-3 py-2.5">
                <Badge tone={ACCOUNT_STATUS_TONE[a.status] ?? "neutral"}>
                  {ACCOUNT_STATUS_LABEL[a.status] ?? a.status}
                </Badge>
              </td>
              <td className="max-w-[200px] truncate px-3 py-2.5 text-neutral-600">
                {a.suspendedReason ?? "—"}
              </td>
              <td className="px-3 py-2.5 text-right">
                {isSelf ? (
                  <span className="text-xs text-neutral-400">본인 계정</span>
                ) : (
                  <span className="inline-flex gap-1">
                    {suspended ? (
                      <Button
                        size="sm"
                        variant="secondary"
                        onClick={() =>
                          directAction(() => reinstateAdminAccount(token ?? "", a.id))
                        }
                      >
                        정지 해제
                      </Button>
                    ) : (
                      <Button
                        size="sm"
                        variant="danger"
                        onClick={() =>
                          action.ask({
                            title: "회원 정지",
                            target: a.email,
                            confirmLabel: "정지",
                            run: (reason) =>
                              suspendAdminAccount(token ?? "", a.id, reason).then(() => undefined),
                          })
                        }
                      >
                        정지
                      </Button>
                    )}
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() =>
                        directAction(() =>
                          changeAdminAccountRole(
                            token ?? "",
                            a.id,
                            a.role === "ADMIN" ? "USER" : "ADMIN",
                          ),
                        )
                      }
                    >
                      {a.role === "ADMIN" ? "관리자 해제" : "관리자 지정"}
                    </Button>
                  </span>
                )}
              </td>
            </tr>
          );
        })}
      </AdminTable>

      {action.pending !== null ? (
        <ReasonPrompt
          title={action.pending.title}
          target={action.pending.target}
          confirmLabel={action.pending.confirmLabel}
          busy={action.busy}
          error={action.error}
          onConfirm={action.confirm}
          onCancel={action.cancel}
        />
      ) : null}
    </div>
  );
}
