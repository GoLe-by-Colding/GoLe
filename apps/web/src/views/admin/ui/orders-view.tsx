"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { fetchAdminOrders, type AdminOrder } from "@entities/admin";
import { useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import { formatKrw } from "@shared/lib";
import { Badge, Heading, Input, Select, Text } from "@shared/ui";
import { ORDER_STATUS_LABEL, formatDateTime, shortId } from "../model/labels";
import { AdminStatus, AdminTable } from "./table";

const STATUSES = [
  "PAYMENT_PENDING",
  "FUNDS_HELD",
  "REFUND_PENDING",
  "COMPLETED",
  "REFUNDED",
  "PAYMENT_FAILED",
];

/** 주문 모니터링 — 읽기 전용. 운영자 개입 환불은 비범위(후속). (요구사항 7.1) */
export function AdminOrdersView({ initialStatus = "" }: { readonly initialStatus?: string }) {
  const { session } = useSession();
  const token = session?.sessionToken ?? null;

  const [status, setStatus] = useState(initialStatus);
  const [query, setQuery] = useState("");
  // null = 아직 불러오지 않음. 로딩 상태를 파생시켜 effect 안에서 setState를 동기 호출하지 않는다.
  const [rows, setRows] = useState<readonly AdminOrder[] | null>(null);
  const [error, setError] = useState<string | undefined>(undefined);

  const load = useCallback(() => {
    if (token === null) {
      return;
    }
    setError(undefined);
    void fetchAdminOrders(token, 100, status === "" ? undefined : status, query)
      .then(setRows)
      .catch((cause: unknown) => {
        setRows([]);
        setError(cause instanceof ApiError ? cause.message : "주문을 불러오지 못했습니다.");
      });
  }, [query, status, token]);

  useEffect(() => {
    const timer = window.setTimeout(load, 250);
    return () => window.clearTimeout(timer);
  }, [load]);

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Heading level={2}>주문 모니터링</Heading>
        <div className="flex flex-wrap items-center gap-2">
          <Input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="주문·회원·세트번호 검색"
            aria-label="주문 검색"
            className="w-56"
          />
          <label className="flex items-center gap-2 text-sm text-neutral-600">
            상태
            <Select value={status} onChange={(e) => setStatus(e.target.value)}>
              <option value="">전체</option>
              {STATUSES.map((s) => (
                <option key={s} value={s}>
                  {ORDER_STATUS_LABEL[s] ?? s}
                </option>
              ))}
            </Select>
          </label>
        </div>
      </div>

      <Text tone="muted" size="sm">
        읽기 전용입니다. 분쟁 환불은 구매자·판매자 흐름에서 처리합니다.
      </Text>

      <AdminStatus error={error} loading={rows === null} />

      <AdminTable
        caption="거래 주문 모니터링 목록"
        headers={["주문", "상태", "금액", "세트", "구매자", "판매자", "생성"]}
        alignRight={[2]}
        minWidth={780}
        empty="주문이 없습니다."
        rowCount={(rows ?? []).length}
      >
        {(rows ?? []).map((o) => (
          <tr key={o.id} className="border-t border-neutral-100">
            <td className="px-3 py-2.5 font-mono text-xs">
              <Link href={`/orders/${o.id}`} className="text-neutral-500 hover:text-brand-600">
                {shortId(o.id)}
              </Link>
            </td>
            <td className="px-3 py-2.5">
              <Badge tone={o.status === "COMPLETED" ? "success" : "neutral"}>
                {ORDER_STATUS_LABEL[o.status] ?? o.status}
              </Badge>
            </td>
            <td className="px-3 py-2.5 text-right font-semibold tabular-nums">
              {formatKrw(o.amount)}
            </td>
            <td className="px-3 py-2.5 text-neutral-600">{o.catalogSetNumber ?? "—"}</td>
            <td className="px-3 py-2.5 text-neutral-600">{shortId(o.buyerId)}</td>
            <td className="px-3 py-2.5 text-neutral-600">{shortId(o.sellerId)}</td>
            <td className="px-3 py-2.5 text-xs text-neutral-500">{formatDateTime(o.createdAt)}</td>
          </tr>
        ))}
      </AdminTable>
    </div>
  );
}
