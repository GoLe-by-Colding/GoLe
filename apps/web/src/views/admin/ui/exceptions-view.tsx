"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import {
  fetchAdminExceptionQueue,
  fetchAdminOrderContacts,
  resolveAdminDispute,
  type AdminExceptionEntry,
} from "@entities/admin";
import { useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import { formatKrw } from "@shared/lib";
import { Badge, Button, Heading, Text } from "@shared/ui";
import { formatDateTime, shortId } from "../model/labels";

/**
 * 예외 큐 — 운영자가 보는 전부. (shipping-and-fees R7.6)
 *
 * 정상 진행 건은 여기 없다. 비어 있으면 할 일이 없는 게 정상이다.
 * 분쟁 카드에는 트래커의 배송 사실을 함께 보여준다(R4.3) — 판정의 객관적 근거.
 */
export function AdminExceptionsView() {
  const { session } = useSession();
  const token = session?.sessionToken ?? null;

  const [rows, setRows] = useState<readonly AdminExceptionEntry[] | null>(null);
  const [error, setError] = useState<string | undefined>(undefined);
  const [busyOrder, setBusyOrder] = useState<string | null>(null);
  const [contacts, setContacts] = useState<Record<string, string>>({});

  const load = useCallback(() => {
    if (token === null) return;
    setError(undefined);
    void fetchAdminExceptionQueue(token)
      .then(setRows)
      .catch((cause: unknown) => {
        setRows([]);
        setError(cause instanceof ApiError ? cause.message : "예외 큐를 불러오지 못했습니다.");
      });
  }, [token]);

  useEffect(load, [load]);

  async function resolve(orderId: string, resolution: "refund" | "complete") {
    if (token === null) return;
    const label = resolution === "refund" ? "전액 환불" : "거래 완료";
    if (!window.confirm(`이 분쟁을 "${label}"로 판정할까요? 되돌릴 수 없습니다.`)) return;
    setBusyOrder(orderId);
    setError(undefined);
    try {
      setRows(await resolveAdminDispute(token, orderId, resolution));
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "판정 처리에 실패했습니다.");
    } finally {
      setBusyOrder(null);
    }
  }

  async function revealContacts(orderId: string) {
    if (token === null) return;
    try {
      const c = await fetchAdminOrderContacts(token, orderId);
      setContacts((prev) => ({
        ...prev,
        [orderId]: `구매자 ${c.buyerPhone ?? "미수집"} · 판매자 ${c.sellerPhone ?? "미수집"}`,
      }));
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "연락처를 불러오지 못했습니다.");
    }
  }

  return (
    <div className="flex flex-col gap-5">
      <div className="flex items-center justify-between">
        <Heading level={2}>예외 큐</Heading>
        <Button size="sm" variant="ghost" onClick={load}>
          새로고침
        </Button>
      </div>
      <Text size="sm" tone="muted">
        분쟁·배송 정체·미접수·추적 불가 건만 올라옵니다. 목록이 비어 있으면 모든 거래가 정상 진행
        중입니다.
      </Text>

      {error ? (
        <Text size="sm" className="text-danger" role="alert">
          {error}
        </Text>
      ) : null}

      {rows === null ? (
        <Text tone="muted">불러오는 중…</Text>
      ) : rows.length === 0 ? (
        <div className="rounded-lg border border-dashed border-neutral-300 px-6 py-14 text-center">
          <Text tone="secondary" weight="medium">
            처리할 예외가 없어요
          </Text>
          <Text tone="muted" size="sm">
            모든 거래가 자동 파이프라인 위에서 정상 진행 중입니다.
          </Text>
        </div>
      ) : (
        <ul className="flex flex-col gap-4">
          {rows.map((row) => {
            const isDispute = row.type === "dispute" || row.type === "dispute_escalated";
            return (
              <li
                key={`${row.type}:${row.orderId}`}
                className="flex flex-col gap-3 rounded-lg border border-neutral-200 bg-white p-4"
              >
                <div className="flex flex-wrap items-center gap-2">
                  <Badge tone={row.type === "dispute_escalated" ? "danger" : "warning"}>
                    {row.typeLabel}
                  </Badge>
                  {row.reason ? <Badge tone="neutral">{row.reason}</Badge> : null}
                  <Link
                    href={`/orders/${row.orderId}`}
                    className="font-mono text-sm text-brand-600 hover:underline"
                  >
                    {shortId(row.orderId)}
                  </Link>
                  <span className="text-sm font-semibold">{formatKrw(row.amount)}</span>
                  <span className="ml-auto text-xs text-neutral-400">
                    {formatDateTime(row.since)}부터
                  </span>
                </div>

                {row.disputeDetail ? (
                  <Text size="sm" tone="secondary">
                    “{row.disputeDetail}”
                  </Text>
                ) : null}

                {/* 배송 사실 — 판정의 객관적 근거 (R4.3) */}
                <dl className="grid gap-x-6 gap-y-1 rounded-md bg-neutral-50 px-3 py-2 text-sm sm:grid-cols-2">
                  {row.shipment === null ? (
                    <div className="text-neutral-500">운송장 미등록 (발송 기록 없음)</div>
                  ) : (
                    <>
                      <div className="flex gap-2">
                        <dt className="text-neutral-500">운송장</dt>
                        <dd>
                          {row.shipment.carrierLabel} {row.shipment.waybillNumber}
                        </dd>
                      </div>
                      <div className="flex gap-2">
                        <dt className="text-neutral-500">배송 상태</dt>
                        <dd>
                          {row.shipment.status}
                          {row.shipment.rawStatus ? ` (${row.shipment.rawStatus})` : ""}
                        </dd>
                      </div>
                      <div className="flex gap-2">
                        <dt className="text-neutral-500">발송 등록</dt>
                        <dd>{formatDateTime(row.shipment.registeredAt)}</dd>
                      </div>
                      <div className="flex gap-2">
                        <dt className="text-neutral-500">배송 완료</dt>
                        <dd>
                          {row.shipment.deliveredAt
                            ? formatDateTime(row.shipment.deliveredAt)
                            : "—"}
                        </dd>
                      </div>
                    </>
                  )}
                </dl>

                <div className="flex flex-wrap items-center gap-2">
                  {isDispute ? (
                    <>
                      <Button
                        size="sm"
                        disabled={busyOrder === row.orderId}
                        onClick={() => void resolve(row.orderId, "refund")}
                      >
                        환불 판정
                      </Button>
                      <Button
                        size="sm"
                        variant="secondary"
                        disabled={busyOrder === row.orderId}
                        onClick={() => void resolve(row.orderId, "complete")}
                      >
                        거래 완료 판정
                      </Button>
                    </>
                  ) : null}
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => void revealContacts(row.orderId)}
                  >
                    연락처 열람
                  </Button>
                  {contacts[row.orderId] ? (
                    <span className="text-xs text-neutral-500">
                      {contacts[row.orderId]} · 열람 기록이 감사 로그에 남습니다
                    </span>
                  ) : null}
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
