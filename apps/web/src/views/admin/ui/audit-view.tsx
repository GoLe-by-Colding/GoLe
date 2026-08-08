"use client";

import { useEffect, useState } from "react";
import { fetchAdminAudit, type AdminAuditEntry } from "@entities/admin";
import { useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import { Badge, Heading, Text } from "@shared/ui";
import { AUDIT_TYPE_LABEL, AUDIT_TYPE_TONE, formatDateTime, shortId } from "../model/labels";
import { AdminStatus, AdminTable } from "./table";

/** 감사 로그 — append-only. 조회만 가능하며 수정·삭제 수단이 없다. (요구사항 8.3, 8.4) */
export function AdminAuditView() {
  const { session } = useSession();
  const token = session?.sessionToken ?? null;

  // null = 아직 불러오지 않음. 로딩 상태를 파생시켜 effect 안에서 setState를 동기 호출하지 않는다.
  const [rows, setRows] = useState<readonly AdminAuditEntry[] | null>(null);
  const [error, setError] = useState<string | undefined>(undefined);

  useEffect(() => {
    if (token === null) {
      return;
    }
    let active = true;
    void fetchAdminAudit(token, 100)
      .then((data) => {
        if (active) {
          setRows(data);
        }
      })
      .catch((cause: unknown) => {
        if (active) {
          setRows([]);
          setError(cause instanceof ApiError ? cause.message : "감사 로그를 불러오지 못했습니다.");
        }
      });
    return () => {
      active = false;
    };
  }, [token]);

  return (
    <div className="flex flex-col gap-4">
      <Heading level={2}>감사 로그</Heading>
      <Text tone="muted" size="sm">
        성공한 관리자 조치만 기록되며 수정·삭제할 수 없습니다. 조치자 이메일은 조치 시점의
        스냅샷입니다.
      </Text>

      <AdminStatus error={error} loading={rows === null} />

      <AdminTable
        headers={["시각", "조치", "조치자", "대상", "사유"]}
        minWidth={780}
        empty="기록된 조치가 없습니다."
        rowCount={(rows ?? []).length}
      >
        {(rows ?? []).map((entry) => (
          <tr key={entry.id} className="border-t border-neutral-100">
            <td className="whitespace-nowrap px-3 py-2.5 text-xs text-neutral-500">
              {formatDateTime(entry.occurredAt)}
            </td>
            <td className="px-3 py-2.5">
              <Badge tone={AUDIT_TYPE_TONE[entry.type] ?? "neutral"}>
                {AUDIT_TYPE_LABEL[entry.type] ?? entry.type}
              </Badge>
            </td>
            <td className="px-3 py-2.5 text-neutral-700">{entry.actorEmail}</td>
            <td className="px-3 py-2.5 font-mono text-xs text-neutral-500">
              {entry.targetType} {shortId(entry.targetId)}
            </td>
            <td className="max-w-[280px] truncate px-3 py-2.5 text-neutral-600">
              {entry.reason ?? "—"}
            </td>
          </tr>
        ))}
      </AdminTable>
    </div>
  );
}
