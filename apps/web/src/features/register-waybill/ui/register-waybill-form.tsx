"use client";

import { useState } from "react";
import { CARRIERS, registerWaybill, type Shipment } from "@entities/shipment";
import { ApiError } from "@shared/api";
import { Button, Field, Input, Select, Text } from "@shared/ui";

export interface RegisterWaybillFormProps {
  readonly orderId: string;
  /** 교체 모드 — 이미 등록된 운송장이 있을 때 문구·연락처 필수 여부가 달라진다. */
  readonly existing?: Shipment | null;
  readonly onRegistered: (shipment: Shipment) => void;
}

/**
 * 판매자 운송장 입력 폼. (shipping-and-fees E2)
 *
 * CS 연락처는 최초 등록 시 함께 수집한다(R8.2) — 발송하는 순간이
 * 판매자에게 연락이 필요해지는 시점이다.
 */
export function RegisterWaybillForm({ orderId, existing, onRegistered }: RegisterWaybillFormProps) {
  const [carrier, setCarrier] = useState(existing?.carrier ?? CARRIERS[0]!.key);
  const [waybillNumber, setWaybillNumber] = useState("");
  const [sellerPhone, setSellerPhone] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const replacing = existing != null;

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      const shipment = await registerWaybill(orderId, {
        carrier,
        waybillNumber,
        ...(sellerPhone.trim() ? { sellerPhone: sellerPhone.trim() } : {}),
      });
      onRegistered(shipment);
    } catch (cause) {
      setError(
        cause instanceof ApiError ? cause.message : "운송장 등록에 실패했어요. 다시 시도해 주세요",
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-3">
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="택배사">
          {({ inputId }) => (
            <Select id={inputId} value={carrier} onChange={(e) => setCarrier(e.target.value)}>
              {CARRIERS.map((c) => (
                <option key={c.key} value={c.key}>
                  {c.label}
                </option>
              ))}
            </Select>
          )}
        </Field>
        <Field label="송장번호">
          {({ inputId }) => (
            <Input
              id={inputId}
              value={waybillNumber}
              onChange={(e) => setWaybillNumber(e.target.value)}
              placeholder="숫자만 입력 (예: 123456789012)"
              inputMode="numeric"
              required
            />
          )}
        </Field>
      </div>
      <Field label={replacing ? "CS 연락처 (변경 시에만 입력)" : "CS 연락처"}>
        {({ inputId }) => (
          <Input
            id={inputId}
            value={sellerPhone}
            onChange={(e) => setSellerPhone(e.target.value)}
            placeholder="010-1234-5678"
            inputMode="tel"
            type="tel"
            required={!replacing}
          />
        )}
      </Field>
      <Text size="sm" tone="muted">
        연락처는 거래 분쟁 대응 목적으로만 사용되며, 구매자에게는 마스킹되어 보입니다.
      </Text>
      {error ? (
        <Text size="sm" className="text-danger" role="alert">
          {error}
        </Text>
      ) : null}
      <Button type="submit" disabled={busy}>
        {busy ? "등록 중…" : replacing ? "운송장 변경" : "운송장 등록"}
      </Button>
      {replacing ? (
        <Text size="sm" tone="muted">
          변경하면 이전 운송장({existing.carrierLabel} {existing.waybillNumber})은 이력에
          보존됩니다.
        </Text>
      ) : null}
    </form>
  );
}
