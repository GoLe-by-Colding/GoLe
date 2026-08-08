"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { fetchAdminListings, takedownListing, type AdminListing } from "@entities/admin";
import { useSession } from "@entities/user";
import { ReasonPrompt, useModerationAction } from "@features/admin-moderation";
import { ApiError } from "@shared/api";
import { formatKrw } from "@shared/lib";
import { Badge, Button, Heading, Input, Select, Text } from "@shared/ui";
import {
  LISTING_STATUS_LABEL,
  LISTING_STATUS_TONE,
  formatDateTime,
  shortId,
} from "../model/labels";
import { AdminStatus, AdminTable } from "./table";

/** 매물 모더레이션 — 전체 상태 목록 + 사유를 받는 강제 내림. (요구사항 4) */
export function AdminListingsView() {
  const { session } = useSession();
  const token = session?.sessionToken ?? null;

  const [status, setStatus] = useState("");
  const [query, setQuery] = useState("");
  // null = 아직 불러오지 않음. 로딩 상태를 파생시켜 effect 안에서 setState를 동기 호출하지 않는다.
  const [rows, setRows] = useState<readonly AdminListing[] | null>(null);
  const [error, setError] = useState<string | undefined>(undefined);

  const load = useCallback(() => {
    if (token === null) {
      return;
    }
    setError(undefined);
    void fetchAdminListings(token, 100, status === "" ? undefined : status, query)
      .then(setRows)
      .catch((cause: unknown) => {
        setRows([]);
        setError(cause instanceof ApiError ? cause.message : "매물을 불러오지 못했습니다.");
      });
  }, [query, status, token]);

  useEffect(() => {
    const timer = window.setTimeout(load, 250);
    return () => window.clearTimeout(timer);
  }, [load]);
  const action = useModerationAction(load);

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Heading level={2}>매물 모더레이션</Heading>
        <div className="flex flex-wrap items-center gap-2">
          <Input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="제목·판매자·카테고리 검색"
            aria-label="매물 검색"
            className="w-60"
          />
          <Select value={status} onChange={(e) => setStatus(e.target.value)} aria-label="매물 상태">
            <option value="">전체 상태</option>
            <option value="ACTIVE">판매중</option>
            <option value="RESERVED">예약중</option>
            <option value="SOLD">거래완료</option>
            <option value="DELETED">내려짐</option>
          </Select>
        </div>
      </div>
      <Text tone="muted" size="sm">
        일반 검색과 달리 내려간 매물까지 모두 보입니다. 진행 중 주문이 있어도 운영자는 내릴 수
        있습니다.
      </Text>

      <AdminStatus error={error} loading={rows === null} />

      <AdminTable
        caption="중고 매물 관리 목록"
        headers={["제목", "판매자", "가격", "상태", "등록", "관리"]}
        alignRight={[2, 5]}
        minWidth={720}
        empty="매물이 없습니다."
        rowCount={(rows ?? []).length}
      >
        {(rows ?? []).map((l) => (
          <tr key={l.id} className="border-t border-neutral-100">
            <td className="max-w-[260px] truncate px-3 py-2.5 font-medium">
              <Link href={`/listings/${l.id}`} className="text-neutral-900 hover:text-brand-600">
                {l.title}
              </Link>
            </td>
            <td className="px-3 py-2.5 text-neutral-600">{shortId(l.sellerId)}</td>
            <td className="px-3 py-2.5 text-right tabular-nums">{formatKrw(l.price)}</td>
            <td className="px-3 py-2.5">
              <Badge tone={LISTING_STATUS_TONE[l.status] ?? "neutral"}>
                {LISTING_STATUS_LABEL[l.status] ?? l.status}
              </Badge>
            </td>
            <td className="px-3 py-2.5 text-xs text-neutral-500">{formatDateTime(l.createdAt)}</td>
            <td className="px-3 py-2.5 text-right">
              {l.status === "DELETED" ? (
                <span className="text-xs text-neutral-400">내려짐</span>
              ) : (
                <Button
                  size="sm"
                  variant="danger"
                  onClick={() =>
                    action.ask({
                      title: "매물 내리기",
                      target: l.title,
                      confirmLabel: "내리기",
                      run: (reason) => takedownListing(token ?? "", l.id, reason),
                    })
                  }
                >
                  내리기
                </Button>
              )}
            </td>
          </tr>
        ))}
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
