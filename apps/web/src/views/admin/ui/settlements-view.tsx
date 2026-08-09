"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import {
  fetchAdminSettlements,
  markAdminSettlementPaid,
  type AdminSettlement,
} from "@entities/admin";
import { useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import { formatKrw } from "@shared/lib";
import { Badge, Button, Heading, Input, Select, Text } from "@shared/ui";
import { formatDateTime, shortId } from "../model/labels";
import { AdminStatus, AdminTable } from "./table";

type StatusFilter = "ALL" | AdminSettlement["status"];

/** 주문 완료 시 자동 생성되는 판매자 정산 원장을 확인하고 외부 송금 증빙을 기록한다. */
export function AdminSettlementsView() {
  const { session } = useSession();
  const token = session?.sessionToken ?? null;
  const [status, setStatus] = useState<StatusFilter>("PENDING");
  const [rows, setRows] = useState<readonly AdminSettlement[] | null>(null);
  const [references, setReferences] = useState<Readonly<Record<string, string>>>({});
  const [busyOrder, setBusyOrder] = useState<string | null>(null);
  const [error, setError] = useState<string | undefined>(undefined);

  const load = useCallback(() => {
    if (token === null) return;
    setError(undefined);
    void fetchAdminSettlements(token, 100, status === "ALL" ? undefined : status)
      .then(setRows)
      .catch((cause: unknown) => {
        setRows([]);
        setError(cause instanceof ApiError ? cause.message : "정산 원장을 불러오지 못했습니다.");
      });
  }, [status, token]);

  useEffect(() => {
    const timer = window.setTimeout(load, 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  async function markPaid(orderId: string) {
    if (token === null) return;
    const paymentReference = references[orderId]?.trim() ?? "";
    if (paymentReference.length === 0) {
      setError("지급 증빙 번호를 입력해 주세요.");
      return;
    }
    setBusyOrder(orderId);
    setError(undefined);
    try {
      const updated = await markAdminSettlementPaid(token, orderId, paymentReference);
      if (status === "PENDING") load();
      else
        setRows((current) =>
          (current ?? []).map((row) => (row.orderId === orderId ? updated : row)),
        );
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "지급 완료 처리에 실패했습니다.");
    } finally {
      setBusyOrder(null);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Heading level={2}>판매자 정산</Heading>
        <label className="flex items-center gap-2 text-sm text-neutral-600">
          상태
          <Select
            value={status}
            onChange={(event) => setStatus(event.target.value as StatusFilter)}
          >
            <option value="PENDING">지급 대기</option>
            <option value="PAID">지급 완료</option>
            <option value="ALL">전체</option>
          </Select>
        </label>
      </div>

      <Text tone="muted" size="sm">
        구매 확정된 주문의 정산액입니다. 실제 송금을 확인한 뒤 은행 거래번호나 지급 배치 ID를
        입력하세요. 처리 내역은 감사 로그에 남습니다.
      </Text>

      <AdminStatus error={error} loading={rows === null} />

      <AdminTable
        caption="판매자 정산 원장"
        headers={["주문", "판매자", "거래액", "수수료", "지급액", "등록", "상태·처리"]}
        alignRight={[2, 3, 4, 6]}
        minWidth={960}
        empty="해당 상태의 정산이 없습니다."
        rowCount={(rows ?? []).length}
      >
        {(rows ?? []).map((row) => (
          <tr key={row.orderId} className="border-t border-neutral-100">
            <td className="px-3 py-2.5 font-mono text-xs">
              <Link
                href={`/orders/${row.orderId}`}
                className="text-neutral-500 hover:text-brand-600"
              >
                {shortId(row.orderId)}
              </Link>
            </td>
            <td className="px-3 py-2.5 text-neutral-600">{shortId(row.sellerId)}</td>
            <td className="px-3 py-2.5 text-right tabular-nums">{formatKrw(row.grossAmount)}</td>
            <td className="px-3 py-2.5 text-right tabular-nums text-neutral-500">
              {formatKrw(row.fee)}
            </td>
            <td className="px-3 py-2.5 text-right font-semibold tabular-nums">
              {formatKrw(row.payout)}
            </td>
            <td className="px-3 py-2.5 text-xs text-neutral-500">
              {formatDateTime(row.createdAt)}
            </td>
            <td className="px-3 py-2.5 text-right">
              {row.status === "PENDING" ? (
                <div className="ml-auto flex max-w-[340px] items-center justify-end gap-2">
                  <Input
                    value={references[row.orderId] ?? ""}
                    onChange={(event) =>
                      setReferences((current) => ({
                        ...current,
                        [row.orderId]: event.target.value,
                      }))
                    }
                    maxLength={120}
                    placeholder="은행 거래번호 / 배치 ID"
                    aria-label={`${shortId(row.orderId)} 지급 증빙 번호`}
                    className="h-9 min-w-48 text-sm"
                  />
                  <Button
                    size="sm"
                    disabled={busyOrder === row.orderId}
                    onClick={() => void markPaid(row.orderId)}
                  >
                    {busyOrder === row.orderId ? "처리 중" : "지급 완료"}
                  </Button>
                </div>
              ) : (
                <div className="flex flex-col items-end gap-1">
                  <Badge tone="success">지급 완료</Badge>
                  <span className="font-mono text-xs text-neutral-500">{row.paymentReference}</span>
                  <span className="text-xs text-neutral-400">{formatDateTime(row.paidAt)}</span>
                </div>
              )}
            </td>
          </tr>
        ))}
      </AdminTable>
    </div>
  );
}
