"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import {
  claimAdminSettlement,
  fetchAdminSettlements,
  fetchAdminLaunchConfig,
  markAdminSettlementPaid,
  reconcileAdminSettlement,
  recoverAdminSettlement,
  type AdminLaunchConfig,
  type AdminSettlement,
} from "@entities/admin";
import { useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import { formatKrw } from "@shared/lib";
import { Badge, Button, Heading, Input, Select, Text } from "@shared/ui";
import { formatDateTime, shortId } from "../model/labels";
import { AdminStatus, AdminTable } from "./table";

type StatusFilter = "ALL" | AdminSettlement["status"];

/**
 * 운영 지급 유예 중이라 완료 처리하면 안 되는 건인지 판정한다. 별도 취소·분쟁 정책을 대신하지
 * 않으며, 서버가 같은 기준으로 거부하므로 화면에서는 헛수고를 막는 용도다(권위는 서버에 있다).
 */
function payoutAvailability(payableAt: string | null): "ready" | "held" | "invalid" {
  if (payableAt === null) return "invalid";
  const at = Date.parse(payableAt);
  if (!Number.isFinite(at)) return "invalid";
  return Date.now() < at ? "held" : "ready";
}

/** 주문 완료 시 자동 생성되는 판매자 정산 원장을 확인하고 외부 송금 증빙을 기록한다. */
export function AdminSettlementsView() {
  const { session } = useSession();
  const token = session?.sessionToken ?? null;
  const [status, setStatus] = useState<StatusFilter>("PENDING");
  const [rows, setRows] = useState<readonly AdminSettlement[] | null>(null);
  const [launch, setLaunch] = useState<AdminLaunchConfig | null>(null);
  const [references, setReferences] = useState<Readonly<Record<string, string>>>({});
  const [reconcileReasons, setReconcileReasons] = useState<Readonly<Record<string, string>>>({});
  const [busyOrder, setBusyOrder] = useState<string | null>(null);
  const [error, setError] = useState<string | undefined>(undefined);

  const load = useCallback(() => {
    if (token === null) return;
    setError(undefined);
    void Promise.all([
      fetchAdminSettlements(token, 100, status === "ALL" ? undefined : status),
      fetchAdminLaunchConfig(token).catch(() => null),
    ])
      .then(([nextRows, nextLaunch]) => {
        setRows(nextRows);
        setLaunch(nextLaunch);
      })
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
    if (
      !window.confirm("외부 이체가 실제 완료됐나요? 입력한 증빙으로 지급 완료 상태를 확정합니다.")
    ) {
      return;
    }
    setBusyOrder(orderId);
    setError(undefined);
    try {
      const updated = await markAdminSettlementPaid(token, orderId, paymentReference);
      replaceRow(updated);
      if (status !== "ALL") setStatus("PAID");
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "지급 완료 처리에 실패했습니다.");
    } finally {
      setBusyOrder(null);
    }
  }

  function replaceRow(updated: AdminSettlement) {
    setRows((current) =>
      (current ?? []).map((row) => (row.orderId === updated.orderId ? updated : row)),
    );
  }

  async function claimPayout(orderId: string) {
    if (token === null) return;
    setBusyOrder(orderId);
    setError(undefined);
    try {
      replaceRow(await claimAdminSettlement(token, orderId));
      if (status !== "ALL") setStatus("PAYOUT_IN_PROGRESS");
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "정산 작업을 배정받지 못했습니다.");
      load();
    } finally {
      setBusyOrder(null);
    }
  }

  async function reconcilePayout(orderId: string) {
    if (token === null) return;
    const reason = reconcileReasons[orderId]?.trim() ?? "";
    if (reason.length === 0) {
      setError("외부 지급 결과를 확인한 내용과 재조정 사유를 입력해 주세요.");
      return;
    }
    setBusyOrder(orderId);
    setError(undefined);
    try {
      replaceRow(await reconcileAdminSettlement(token, orderId, reason));
      setReconcileReasons((current) => ({ ...current, [orderId]: "" }));
      if (status !== "ALL") setStatus("ALL");
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "정산 재조정에 실패했습니다.");
      load();
    } finally {
      setBusyOrder(null);
    }
  }

  async function recoverPayout(orderId: string, alreadyPaid: boolean) {
    if (token === null) return;
    const reason = reconcileReasons[orderId]?.trim() ?? "";
    const paymentReference = references[orderId]?.trim() ?? "";
    if (reason.length === 0) {
      setError("지급사 또는 은행에서 확인한 외부 지급 결과와 근거를 입력해 주세요.");
      return;
    }
    if (alreadyPaid && paymentReference.length === 0) {
      setError("이미 지급된 건은 외부 지급 증빙 번호를 입력해야 합니다.");
      return;
    }
    const confirmed = window.confirm(
      alreadyPaid
        ? "외부 지급사·은행에서 실제 지급 완료를 확인했나요? 입력한 증빙으로 정산을 완료 처리합니다."
        : launch?.settlementMode === "PROVIDER"
          ? "외부 지급사에서 지급되지 않았음을 확인했나요? 자동 지급 재시도 예산을 새로 부여합니다."
          : "외부 지급사·은행에서 지급되지 않았음을 확인했나요? 이 정산을 내 작업으로 다시 배정합니다.",
    );
    if (!confirmed) return;
    setBusyOrder(orderId);
    setError(undefined);
    try {
      const updated = await recoverAdminSettlement(token, orderId, {
        alreadyPaid,
        ...(alreadyPaid ? { paymentReference } : {}),
        reason,
      });
      replaceRow(updated);
      setReconcileReasons((current) => ({ ...current, [orderId]: "" }));
      if (status !== "ALL") setStatus(updated.status);
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "차단된 정산을 복구하지 못했습니다.");
      load();
    } finally {
      setBusyOrder(null);
    }
  }

  function renderAction(row: AdminSettlement) {
    const busy = busyOrder === row.orderId;
    const manualReady =
      launch?.settlementMode === "MANUAL" && launch.payoutContractVerified === true;
    const providerReady =
      launch?.settlementMode === "PROVIDER" && launch.payoutContractVerified === true;
    if (row.status === "PAID") {
      return (
        <div className="flex flex-col items-end gap-1">
          <Badge tone="success">지급 완료</Badge>
          <span className="font-mono text-xs text-neutral-500">{row.paymentReference}</span>
          <span className="text-xs text-neutral-400">{formatDateTime(row.paidAt)}</span>
        </div>
      );
    }

    if (!manualReady && row.status !== "PAYOUT_BLOCKED") {
      return (
        <div className="ml-auto flex max-w-[360px] flex-col items-end gap-1">
          <Badge
            tone={
              row.status === "PAYOUT_FAILED"
                ? "danger"
                : row.status === "PAYOUT_IN_PROGRESS"
                  ? "brand"
                  : "neutral"
            }
          >
            {row.status === "PAYOUT_IN_PROGRESS"
              ? providerReady
                ? "자동 지급 중"
                : "지급 결과 확인 필요"
              : row.status === "PAYOUT_FAILED"
                ? providerReady
                  ? "재시도 대기"
                  : "지급 잠김"
                : providerReady
                  ? "지급 대기"
                  : "지급 잠김"}
          </Badge>
          {row.payoutError ? <span className="text-xs text-danger">{row.payoutError}</span> : null}
          {row.payoutAttempts > 0 ? (
            <span className="text-xs text-neutral-500">
              자동 시도 {row.payoutAttempts}회
              {row.payoutNextAttemptAt
                ? ` · ${formatDateTime(row.payoutNextAttemptAt)} 재시도`
                : ""}
            </span>
          ) : null}
          {!providerReady ? (
            <span className="text-xs leading-relaxed text-neutral-500">
              {launch === null
                ? "정산 설정을 확인하고 있습니다."
                : !launch.payoutContractVerified
                  ? "지급 계약 확인 전이라 실행이 잠겨 있습니다."
                  : "현재 정산 모드에서는 자동 지급하지 않습니다."}
            </span>
          ) : null}
          {row.status === "PAYOUT_IN_PROGRESS" ? (
            <div className="mt-2 grid w-full gap-2 sm:grid-cols-[minmax(0,1fr)_auto]">
              <Input
                value={reconcileReasons[row.orderId] ?? ""}
                onChange={(event) =>
                  setReconcileReasons((current) => ({
                    ...current,
                    [row.orderId]: event.target.value,
                  }))
                }
                maxLength={500}
                placeholder="지급사 조회 결과와 재조정 사유"
                aria-label={`${shortId(row.orderId)} 정산 재조정 사유`}
                className="h-9 min-w-0 text-sm"
              />
              <Button
                size="sm"
                variant="secondary"
                disabled={busy}
                onClick={() => void reconcilePayout(row.orderId)}
              >
                장기 정체 차단
              </Button>
              <span className="text-left text-[11px] leading-relaxed text-neutral-500 sm:col-span-2">
                지급사 처리 시간이 지난 뒤에만 차단됩니다. 차단 후 외부 거래 내역을 확인해
                지급됨·미지급을 선택합니다.
              </span>
            </div>
          ) : null}
        </div>
      );
    }

    const availability = payoutAvailability(row.payableAt);
    if (
      row.status !== "PAYOUT_IN_PROGRESS" &&
      row.status !== "PAYOUT_BLOCKED" &&
      availability !== "ready"
    ) {
      return (
        <div className="ml-auto flex max-w-[360px] flex-col items-end gap-1">
          <Badge tone="warning">{availability === "held" ? "지급 유예" : "원장 확인 필요"}</Badge>
          <span className="text-xs text-neutral-500">
            {availability === "held"
              ? `${formatDateTime(row.payableAt)}부터 지급 가능`
              : "지급 가능 시각이 없어 서버가 지급을 잠갔습니다."}
          </span>
        </div>
      );
    }

    if (row.status === "PAYOUT_IN_PROGRESS") {
      const mine = row.payoutOperatorId === session?.accountId;
      return (
        <div className="ml-auto flex max-w-[420px] flex-col items-end gap-2">
          <Badge tone={mine ? "brand" : "warning"}>
            {mine
              ? "내 지급 작업"
              : row.payoutOperatorId
                ? "다른 운영자 처리 중"
                : "자동 지급 결과 확인 필요"}
          </Badge>
          {mine ? (
            <div className="grid w-full gap-2 sm:grid-cols-[minmax(0,1fr)_auto]">
              <Input
                value={references[row.orderId] ?? ""}
                onChange={(event) =>
                  setReferences((current) => ({ ...current, [row.orderId]: event.target.value }))
                }
                maxLength={120}
                placeholder="은행 거래번호 / 배치 ID"
                aria-label={`${shortId(row.orderId)} 지급 증빙 번호`}
                className="h-9 min-w-0 text-sm"
              />
              <Button size="sm" disabled={busy} onClick={() => void markPaid(row.orderId)}>
                {busy ? "처리 중" : "지급 완료"}
              </Button>
            </div>
          ) : null}
          <div className="grid w-full gap-2 sm:grid-cols-[minmax(0,1fr)_auto]">
            <Input
              value={reconcileReasons[row.orderId] ?? ""}
              onChange={(event) =>
                setReconcileReasons((current) => ({
                  ...current,
                  [row.orderId]: event.target.value,
                }))
              }
              maxLength={500}
              placeholder={
                mine
                  ? "현재 작업을 중단하고 외부 결과를 확인할 사유"
                  : "지급사 확인 결과와 장기 정체 사유"
              }
              aria-label={`${shortId(row.orderId)} 정산 재조정 사유`}
              className="h-9 min-w-0 text-sm"
            />
            <Button
              size="sm"
              variant="secondary"
              disabled={busy}
              onClick={() => void reconcilePayout(row.orderId)}
            >
              {mine ? "지급 결과 확인으로 이동" : "장기 정체 차단"}
            </Button>
          </div>
          {mine ? (
            <span className="text-left text-[11px] leading-relaxed text-neutral-500">
              바로 지급 대기로 돌아가지 않습니다. 외부 내역을 확인한 뒤 지급됨·미지급을 선택합니다.
            </span>
          ) : (
            <span className="text-left text-[11px] leading-relaxed text-neutral-500">
              진행 제한 시간이 지나기 전에는 서버가 차단을 거부합니다.
            </span>
          )}
        </div>
      );
    }

    if (row.status === "PAYOUT_BLOCKED") {
      return (
        <div className="ml-auto flex w-full max-w-[460px] flex-col items-stretch gap-2 text-left">
          <div className="flex items-center justify-between gap-2">
            <Badge tone="danger">외부 지급 확인 필요</Badge>
            {row.payoutAttempts > 0 ? (
              <span className="text-xs text-neutral-500">시도 {row.payoutAttempts}회</span>
            ) : null}
          </div>
          {row.payoutError ? (
            <p className="rounded-md bg-danger/5 px-3 py-2 text-xs leading-relaxed text-danger">
              {row.payoutError}
            </p>
          ) : null}
          <Input
            value={reconcileReasons[row.orderId] ?? ""}
            onChange={(event) =>
              setReconcileReasons((current) => ({
                ...current,
                [row.orderId]: event.target.value,
              }))
            }
            maxLength={500}
            placeholder="지급사·은행 조회 결과와 확인 근거"
            aria-label={`${shortId(row.orderId)} 외부 지급 확인 근거`}
            className="h-9 min-w-0 text-sm"
          />
          <Input
            value={references[row.orderId] ?? ""}
            onChange={(event) =>
              setReferences((current) => ({ ...current, [row.orderId]: event.target.value }))
            }
            maxLength={120}
            placeholder="지급됐을 때만 거래번호 / 지급사 증빙 ID"
            aria-label={`${shortId(row.orderId)} 외부 지급 증빙 번호`}
            className="h-9 min-w-0 text-sm"
          />
          <div className="grid gap-2 sm:grid-cols-2">
            <Button
              size="sm"
              variant="secondary"
              disabled={busy || (!manualReady && !providerReady) || availability !== "ready"}
              onClick={() => void recoverPayout(row.orderId, false)}
            >
              {busy
                ? "확인 중"
                : providerReady
                  ? "미지급 확인·자동 재시도"
                  : "미지급 확인·작업 시작"}
            </Button>
            <Button size="sm" disabled={busy} onClick={() => void recoverPayout(row.orderId, true)}>
              {busy ? "확인 중" : "지급됨 확인"}
            </Button>
          </div>
          {!manualReady && !providerReady ? (
            <span className="text-[11px] leading-relaxed text-neutral-500">
              실행 가능한 정산 모드와 지급 계약 확인 전에는 미지급 건을 재배정할 수 없습니다. 이미
              지급된 결과 기록은 현재 모드와 관계없이 가능합니다.
            </span>
          ) : null}
          {availability !== "ready" ? (
            <span className="text-[11px] leading-relaxed text-neutral-500">
              {availability === "held"
                ? `${formatDateTime(row.payableAt)}까지 미지급 재시도를 잠급니다.`
                : "지급 가능 시각이 없어 미지급 재시도를 잠갔습니다. 원장을 먼저 확인해 주세요."}
            </span>
          ) : null}
        </div>
      );
    }

    return (
      <div className="ml-auto flex max-w-[360px] flex-col items-end gap-2">
        {row.payoutError ? <span className="text-xs text-danger">{row.payoutError}</span> : null}
        <Button size="sm" disabled={busy} onClick={() => void claimPayout(row.orderId)}>
          {busy ? "배정 중" : "지급 작업 시작"}
        </Button>
        <span className="text-xs text-neutral-500">외부 이체 전에 먼저 작업을 배정받습니다.</span>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Heading level={2}>판매자 정산</Heading>
        <label className="flex w-full items-center gap-3 rounded-lg border border-neutral-200 bg-neutral-50 px-3 py-2 text-sm font-medium text-neutral-700 sm:w-auto">
          <span className="shrink-0">상태</span>
          <Select
            className="min-w-0 flex-1 sm:w-44"
            value={status}
            onChange={(event) => setStatus(event.target.value as StatusFilter)}
          >
            <option value="PENDING">지급 대기</option>
            <option value="PAYOUT_IN_PROGRESS">지급 처리 중</option>
            <option value="PAYOUT_FAILED">자동 지급 실패</option>
            <option value="PAYOUT_BLOCKED">운영 확인 필요</option>
            <option value="PAID">지급 완료</option>
            <option value="ALL">전체</option>
          </Select>
        </label>
      </div>

      <Text tone="muted" size="sm">
        구매 확정된 주문의 정산액입니다. 현재 실행 모드는 {launch?.settlementMode ?? "확인 중"}
        입니다. 수동 정산 모드에서만 송금 증빙을 직접 기록할 수 있습니다.
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
            <td className="px-3 py-2.5 text-right">{renderAction(row)}</td>
          </tr>
        ))}
      </AdminTable>
    </div>
  );
}
