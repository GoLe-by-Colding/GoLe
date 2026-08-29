"use client";

import { useCallback, useEffect, useState } from "react";
import {
  changeAdminLaunchStage,
  fetchAdminLaunchConfig,
  fetchAdminLaunchHistory,
  setAdminLaunchFeature,
  type AdminLaunchChange,
  type AdminLaunchConfig,
} from "@entities/admin";
import { useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import { Badge, Button, Card, Heading, Input, Text } from "@shared/ui";
import { formatDateTime } from "../model/labels";
import { AdminStatus, AdminTable } from "./table";

const STAGES = [
  { value: 0 as const, title: "Stage 0 · 점검", body: "신규 결제는 닫고 매물·대화만 유지" },
  { value: 1 as const, title: "Stage 1 · 커뮤니티", body: "당근형 직거래와 커뮤니티 운영" },
  { value: 2 as const, title: "Stage 2 · 결제", body: "PG 결제와 운영자 수동 정산" },
  { value: 3 as const, title: "Stage 3 · 지급대행", body: "파트너 정산을 통한 자동 지급" },
] as const;

const FEATURES = [
  { key: "payments" as const, label: "결제" },
  { key: "reviews" as const, label: "거래 후기" },
  { key: "partnerPayout" as const, label: "파트너 자동 지급" },
] as const;

/** 배포 없이 서비스 개방 단계를 바꾸되, 실제 결제·정산 모드와의 모순은 서버가 거부한다. */
export function AdminLaunchView() {
  const { session } = useSession();
  const token = session?.sessionToken ?? null;
  const [config, setConfig] = useState<AdminLaunchConfig | null>(null);
  const [history, setHistory] = useState<readonly AdminLaunchChange[]>([]);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string>();

  const load = useCallback(async () => {
    if (token === null) return;
    setError(undefined);
    try {
      const [nextConfig, nextHistory] = await Promise.all([
        fetchAdminLaunchConfig(token),
        fetchAdminLaunchHistory(token, 50),
      ]);
      setConfig(nextConfig);
      setHistory(nextHistory);
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "출시 설정을 불러오지 못했습니다.");
    }
  }, [token]);

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  function requiredReason(): string | null {
    const value = reason.trim();
    if (value.length === 0) {
      setError("변경 사유를 먼저 입력해 주세요.");
      return null;
    }
    return value;
  }

  async function changeStage(stage: 0 | 1 | 2 | 3) {
    if (token === null) return;
    const value = requiredReason();
    if (value === null) return;
    setBusy(`stage-${stage}`);
    setError(undefined);
    try {
      setConfig(await changeAdminLaunchStage(token, stage, value));
      setReason("");
      setHistory(await fetchAdminLaunchHistory(token, 50));
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "단계를 변경하지 못했습니다.");
    } finally {
      setBusy(null);
    }
  }

  async function changeFeature(
    feature: "payments" | "reviews" | "partnerPayout",
    enabled: boolean | null,
  ) {
    if (token === null) return;
    const value = requiredReason();
    if (value === null) return;
    setBusy(`feature-${feature}`);
    setError(undefined);
    try {
      setConfig(await setAdminLaunchFeature(token, feature, enabled, value));
      setReason("");
      setHistory(await fetchAdminLaunchHistory(token, 50));
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "기능 설정을 변경하지 못했습니다.");
    } finally {
      setBusy(null);
    }
  }

  const mode = config?.settlementMode;

  return (
    <div className="gole-rise-in flex flex-col gap-7">
      <div>
        <Heading level={2}>출시 단계</Heading>
        <Text tone="muted" size="sm" className="mt-1">
          사용자 화면과 서버 게이트를 함께 바꿉니다. 모든 변경은 사유와 조치자가 기록됩니다.
        </Text>
      </div>

      <AdminStatus error={error} loading={config === null && error === undefined} />

      {config !== null ? (
        <>
          <Card padded className="flex flex-col gap-4 border-brand-100 bg-brand-50/40">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <Text weight="semibold">실행 환경</Text>
                <Text tone="muted" size="sm">
                  저장 단계와 실제 정산 모드가 다르면 서버가 더 낮은 단계로 자동 잠급니다.
                </Text>
              </div>
              <div className="flex flex-wrap gap-2">
                <Badge tone="brand">실행 Stage {config.config.stage}</Badge>
                {config.requestedStage !== config.config.stage ? (
                  <Badge tone="warning">요청 Stage {config.requestedStage}</Badge>
                ) : null}
                <Badge tone={mode === "PROVIDER" ? "success" : "neutral"}>정산 {mode}</Badge>
                <Badge tone={config.payoutContractVerified ? "success" : "danger"}>
                  지급 계약 {config.payoutContractVerified ? "확인됨" : "미확인"}
                </Badge>
                <Badge tone={config.config.features.payments ? "success" : "warning"}>
                  결제 {config.config.features.payments ? "열림" : "닫힘"}
                </Badge>
              </div>
            </div>
            <label className="flex flex-col gap-1.5 text-sm font-medium text-neutral-700">
              변경 사유
              <Input
                value={reason}
                onChange={(event) => setReason(event.target.value)}
                maxLength={500}
                placeholder="예: PG 운영 계약 및 카카오페이 실결제 검증 완료"
              />
            </label>
          </Card>

          <section className="flex flex-col gap-3">
            <Heading level={3}>단계 전환</Heading>
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
              {STAGES.map((stage) => {
                const incompatible =
                  (stage.value >= 2 && !config.payoutContractVerified) ||
                  (stage.value === 2 && mode !== "MANUAL") ||
                  (stage.value === 3 && mode !== "PROVIDER");
                const current = config.requestedStage === stage.value;
                return (
                  <Card
                    key={stage.value}
                    padded
                    className={current ? "border-brand-300 bg-brand-50" : "bg-white"}
                  >
                    <div className="flex h-full flex-col gap-3">
                      <div>
                        <Text weight="semibold">{stage.title}</Text>
                        <Text tone="muted" size="sm" className="mt-1">
                          {stage.body}
                        </Text>
                      </div>
                      {incompatible ? (
                        <Text size="sm" className="text-danger">
                          정산 모드가 준비되지 않음
                        </Text>
                      ) : null}
                      <Button
                        className="mt-auto"
                        size="sm"
                        variant={current ? "secondary" : "primary"}
                        disabled={current || incompatible || busy !== null}
                        onClick={() => void changeStage(stage.value)}
                      >
                        {current
                          ? "현재 단계"
                          : busy === `stage-${stage.value}`
                            ? "변경 중"
                            : "전환"}
                      </Button>
                    </div>
                  </Card>
                );
              })}
            </div>
          </section>

          <section className="flex flex-col gap-3">
            <Heading level={3}>기능별 예외</Heading>
            <div className="divide-y divide-neutral-100 rounded-xl border border-neutral-200 bg-white">
              {FEATURES.map((feature) => {
                const override = config.overrides[feature.key];
                const resolved = config.config.features[feature.key];
                return (
                  <div
                    key={feature.key}
                    className="flex flex-wrap items-center justify-between gap-3 px-4 py-3"
                  >
                    <div>
                      <Text weight="medium">{feature.label}</Text>
                      <Text tone="muted" size="sm" className="text-xs">
                        현재 {resolved ? "열림" : "닫힘"} ·{" "}
                        {override === undefined ? "단계 기본값" : "운영 override"}
                      </Text>
                    </div>
                    <div className="flex gap-2">
                      <Button
                        size="sm"
                        variant="secondary"
                        disabled={busy !== null || override === undefined}
                        onClick={() => void changeFeature(feature.key, null)}
                      >
                        기본값
                      </Button>
                      <Button
                        size="sm"
                        variant="secondary"
                        disabled={busy !== null || override === false}
                        onClick={() => void changeFeature(feature.key, false)}
                      >
                        닫기
                      </Button>
                      <Button
                        size="sm"
                        disabled={busy !== null || override === true}
                        onClick={() => void changeFeature(feature.key, true)}
                      >
                        열기
                      </Button>
                    </div>
                  </div>
                );
              })}
            </div>
          </section>
        </>
      ) : null}

      <section className="flex flex-col gap-3">
        <Heading level={3}>변경 이력</Heading>
        <AdminTable
          caption="출시 단계 및 기능 변경 이력"
          headers={["시각", "대상", "변경", "사유", "조치자"]}
          minWidth={820}
          empty="아직 변경 이력이 없습니다."
          rowCount={history.length}
        >
          {history.map((row) => (
            <tr key={row.id} className="border-t border-neutral-100">
              <td className="px-3 py-2.5 text-xs text-neutral-500">
                {formatDateTime(row.occurredAt)}
              </td>
              <td className="px-3 py-2.5 font-medium">{row.target}</td>
              <td className="px-3 py-2.5 font-mono text-xs">
                {row.before} → {row.after}
              </td>
              <td className="px-3 py-2.5 text-sm text-neutral-600">{row.reason}</td>
              <td className="px-3 py-2.5 text-xs text-neutral-500">{row.actorEmail}</td>
            </tr>
          ))}
        </AdminTable>
      </section>
    </div>
  );
}
