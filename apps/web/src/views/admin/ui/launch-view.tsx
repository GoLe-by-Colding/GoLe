"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import {
  changeAdminLaunchStage,
  fetchAdminLaunchConfig,
  fetchAdminLaunchHistory,
  fetchAdminOverview,
  setAdminLaunchFeature,
  setAdminLaunchReadiness,
  type AdminLaunchChange,
  type AdminLaunchConfig,
  type AdminLaunchReadinessKey,
  type AdminPaymentReadiness,
} from "@entities/admin";
import { useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import { Badge, Button, Card, Heading, Input, Text } from "@shared/ui";
import { formatDateTime } from "../model/labels";
import { PaymentReadinessPanel } from "./payment-readiness-panel";
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

const READINESS_CHECKS: readonly {
  key: AdminLaunchReadinessKey;
  title: string;
  detail: string;
  requiredFrom: 2 | 3;
  links?: boolean;
}[] = [
  {
    key: "businessDisclosure",
    title: "사업자·고객센터 고지",
    detail: "상호·대표자·사업자번호·주소·연락처와 실제 운영 도메인 표시를 확인",
    requiredFrom: 2,
  },
  {
    key: "termsPrivacy",
    title: "약관·개인정보 검토",
    detail: "C2C 중개 범위, 취소·분쟁, 보존기간과 처리 위탁 내용을 현재 운영 방식과 대조",
    requiredFrom: 2,
    links: true,
  },
  {
    key: "paymentFlow",
    title: "결제·웹훅·환불 실거래",
    detail: "소액 승인부터 웹훅, 금액 대조, 취소·환불과 오류 복구까지 운영 증빙을 확인",
    requiredFrom: 2,
  },
  {
    key: "payoutFlow",
    title: "판매자 본인확인·지급",
    detail: "판매자 본인·계좌 확인, 지급 증빙·세무 처리, 실패 재처리와 분쟁 유예를 확인",
    requiredFrom: 3,
  },
];

/** 배포 없이 서비스 개방 단계를 바꾸되, 실제 결제·정산 모드와의 모순은 서버가 거부한다. */
export function AdminLaunchView() {
  const { session } = useSession();
  const token = session?.sessionToken ?? null;
  const [config, setConfig] = useState<AdminLaunchConfig | null>(null);
  const [history, setHistory] = useState<readonly AdminLaunchChange[]>([]);
  const [paymentReadiness, setPaymentReadiness] = useState<AdminPaymentReadiness>();
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string>();

  const load = useCallback(async () => {
    if (token === null) return;
    setError(undefined);
    try {
      const [nextConfig, nextHistory, nextPaymentReadiness] = await Promise.all([
        fetchAdminLaunchConfig(token),
        fetchAdminLaunchHistory(token, 50),
        // 대시보드 집계가 일시적으로 실패해도 긴급 단계 하향·기능 닫기는 가능해야 한다.
        // 준비 상태를 모르면 Stage 2 이상 상향만 fail-closed로 막는다.
        fetchAdminOverview(token)
          .then((overview) => overview.paymentReadiness)
          .catch(() => undefined),
      ]);
      setConfig(nextConfig);
      setHistory(nextHistory);
      setPaymentReadiness(nextPaymentReadiness);
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

  async function changeReadiness(check: AdminLaunchReadinessKey, confirmed: boolean) {
    if (token === null) return;
    const value = requiredReason();
    if (value === null) return;
    setBusy(`readiness-${check}`);
    setError(undefined);
    try {
      setConfig(await setAdminLaunchReadiness(token, check, confirmed, value));
      setReason("");
      setHistory(await fetchAdminLaunchHistory(token, 50));
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "운영 준비 확인을 변경하지 못했습니다.");
    } finally {
      setBusy(null);
    }
  }

  const mode = config?.settlementMode;
  const paymentReady = paymentReadiness?.ready === true;

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

          <PaymentReadinessPanel readiness={paymentReadiness} />

          <section className="flex flex-col gap-3">
            <div>
              <Heading level={3}>자동 준비 상태</Heading>
              <Text tone="muted" size="sm">
                서버가 현재 환경과 외부 연동 설정을 직접 확인한 결과입니다.
              </Text>
            </div>
            <div className="grid gap-3 md:grid-cols-2">
              <ReadinessCard
                title="결제 승인·웹훅·환불"
                ready={paymentReady}
                detail={
                  paymentReady
                    ? `${paymentReadiness?.channelType ?? "UNKNOWN"} 채널 설정 확인됨 · 실제 소액 거래 검증 필요`
                    : "PortOne 설정이 준비되지 않아 Stage 2 이상을 열 수 없음"
                }
              />
              <ReadinessCard
                title="신규 매물 · 판매자 신원확인"
                ready={config.config.sellerIdentityVerificationReady === true}
                detail={
                  config.config.sellerIdentityVerificationReady === true
                    ? "배포 래치 확인됨 · 각 판매자의 실제 전화번호 인증이 추가로 필요하며 관리자 승인으로 대체할 수 없음"
                    : "배포 래치 미확인 · 신규 매물과 새 거래 대화만 서버에서 차단하고 조회·커뮤니티·운영 문의는 유지"
                }
              />
              <ReadinessCard
                title="판매자 지급 계약"
                ready={config.payoutContractVerified}
                detail={
                  config.payoutContractVerified
                    ? "현재 C2C·개인 판매자 모델에 대한 서면 확인값이 설정됨"
                    : "카카오페이 결제 계약과 판매자 지급대행 계약은 별개 · 서면 확인 필요"
                }
              />
              <ReadinessCard
                title="정산 실행 방식"
                ready={mode === "MANUAL" || mode === "PROVIDER"}
                detail={
                  mode === "MANUAL"
                    ? "운영자 선점 → 외부 지급 → 증빙 기록"
                    : mode === "PROVIDER"
                      ? "지급대행 어댑터 자동 실행"
                      : "결제·지급 비활성 · 직거래 대화만 운영"
                }
              />
            </div>
          </section>

          <section className="flex flex-col gap-3">
            <div>
              <Heading level={3}>운영 승인 체크</Heading>
              <Text tone="muted" size="sm" className="mt-1 max-w-3xl leading-relaxed">
                시스템이 대신 판단할 수 없는 항목입니다. 관련 계약·테스트 결과를 실제로 확인한 뒤
                사유에 증빙 위치나 티켓을 적어 승인하세요. 이 확인은 법률·세무 자문 자체를 대신하지
                않습니다.
              </Text>
            </div>
            <div className="grid gap-3 md:grid-cols-2">
              {READINESS_CHECKS.map((check) => (
                <OperationalCheckCard
                  key={check.key}
                  check={check}
                  confirmed={config.readiness?.[check.key] === true}
                  evidence={latestReadinessEvidence(history, check.key)}
                  busy={busy !== null}
                  onChange={(confirmed) => void changeReadiness(check.key, confirmed)}
                />
              ))}
            </div>
          </section>

          <section className="flex flex-col gap-3">
            <Heading level={3}>단계 전환</Heading>
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
              {STAGES.map((stage) => {
                const blockReason = stageBlockReason(stage.value, config, paymentReadiness);
                const incompatible = blockReason !== null;
                const current = config.requestedStage === stage.value;
                return (
                  <Card
                    key={stage.value}
                    padded
                    role="group"
                    aria-label={stage.title}
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
                          {blockReason}
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
                const openBlocked = featureOpenBlockReason(feature.key, config, paymentReadiness);
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
                      {openBlocked !== null && !resolved ? (
                        <Text size="sm" className="mt-1 text-xs text-danger">
                          열기 잠금 · {openBlocked}
                        </Text>
                      ) : null}
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
                        disabled={busy !== null || override === true || openBlocked !== null}
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

function stageBlockReason(
  stage: 0 | 1 | 2 | 3,
  config: AdminLaunchConfig,
  readiness: AdminPaymentReadiness | undefined,
): string | null {
  if (stage < 2) return null;
  if (!config.payoutContractVerified) return "지급 계약 서면 확인이 필요함";
  if (stage === 2 && config.settlementMode !== "MANUAL") return "MANUAL 정산 모드가 필요함";
  if (stage === 3 && config.settlementMode !== "PROVIDER") return "PROVIDER 지급 모드가 필요함";
  const missing = missingReadinessFor(stage, config);
  if (missing.length > 0) return `운영 승인 미확인 · ${missing.join(", ")}`;
  if (readiness?.ready !== true) return "PortOne 결제 설정이 준비되지 않음";
  return null;
}

function featureOpenBlockReason(
  feature: "payments" | "reviews" | "partnerPayout",
  config: AdminLaunchConfig,
  readiness: AdminPaymentReadiness | undefined,
): string | null {
  if (feature === "reviews") return null;
  if (feature === "payments") {
    if (config.config.stage < 2) return "Stage 2 이상에서만 가능";
    const missing = missingReadinessFor(2, config);
    if (missing.length > 0) return `운영 승인 확인 필요 · ${missing.join(", ")}`;
    if (readiness?.ready !== true) return "PortOne 설정 확인 필요";
    return null;
  }
  if (config.config.stage < 3) return "Stage 3에서만 가능";
  const missing = missingReadinessFor(3, config);
  if (missing.length > 0) return `운영 승인 확인 필요 · ${missing.join(", ")}`;
  if (!config.payoutContractVerified) return "지급 계약 확인 필요";
  if (config.settlementMode !== "PROVIDER") return "PROVIDER 정산 모드 필요";
  if (readiness?.ready !== true) return "PortOne 설정 확인 필요";
  return null;
}

function missingReadinessFor(stage: 0 | 1 | 2 | 3, config: AdminLaunchConfig): readonly string[] {
  return READINESS_CHECKS.filter(
    (check) => check.requiredFrom <= stage && config.readiness?.[check.key] !== true,
  ).map((check) => check.title);
}

function ReadinessCard({
  title,
  ready,
  detail,
}: {
  readonly title: string;
  readonly ready: boolean;
  readonly detail: string;
}) {
  return (
    <Card padded className="h-full bg-white">
      <div className="flex items-start justify-between gap-3">
        <Text weight="semibold">{title}</Text>
        <Badge tone={ready ? "success" : "danger"}>{ready ? "확인됨" : "미준비"}</Badge>
      </div>
      <Text tone="secondary" size="sm" className="mt-2 leading-relaxed">
        {detail}
      </Text>
    </Card>
  );
}

function OperationalCheckCard({
  check,
  confirmed,
  evidence,
  busy,
  onChange,
}: {
  readonly check: (typeof READINESS_CHECKS)[number];
  readonly confirmed: boolean;
  readonly evidence: AdminLaunchChange | undefined;
  readonly busy: boolean;
  readonly onChange: (confirmed: boolean) => void;
}) {
  return (
    <Card
      padded
      role="group"
      aria-label={check.title}
      className={confirmed ? "h-full border-success/30 bg-success-soft" : "h-full bg-white"}
    >
      <div className="flex h-full flex-col gap-3">
        <div className="flex items-start justify-between gap-3">
          <div>
            <Text weight="semibold">{check.title}</Text>
            <Text tone="muted" size="sm" className="mt-0.5 text-xs">
              Stage {check.requiredFrom}부터 필수
            </Text>
          </div>
          <Badge tone={confirmed ? "success" : "warning"}>{confirmed ? "승인됨" : "미확인"}</Badge>
        </div>
        <Text tone="secondary" size="sm" className="leading-relaxed">
          {check.detail}
        </Text>
        {check.links === true ? (
          <div className="flex gap-3 text-xs font-semibold text-brand-700">
            <Link href="/terms" target="_blank">
              이용약관 ↗
            </Link>
            <Link href="/privacy" target="_blank">
              개인정보처리방침 ↗
            </Link>
          </div>
        ) : null}
        {confirmed ? (
          evidence === undefined ? (
            <p className="rounded-lg bg-white/70 px-3 py-2 text-xs leading-relaxed text-neutral-600">
              현재 승인 근거가 최근 이력 범위에 없습니다. 아래 변경 이력에서 이전 기록을 확인해
              주세요.
            </p>
          ) : (
            <div className="rounded-lg border border-success/20 bg-white/80 px-3 py-2.5">
              <p className="text-xs font-bold text-neutral-700">최근 확인 근거</p>
              <p className="mt-1 text-sm leading-relaxed text-neutral-700">{evidence.reason}</p>
              <p className="mt-1.5 text-xs text-neutral-500">
                {evidence.actorEmail} · {formatDateTime(evidence.occurredAt)}
              </p>
            </div>
          )
        ) : null}
        <Button
          className="mt-auto"
          size="sm"
          variant={confirmed ? "secondary" : "primary"}
          disabled={busy}
          onClick={() => onChange(!confirmed)}
        >
          {confirmed ? "확인 취소" : "확인 완료"}
        </Button>
      </div>
    </Card>
  );
}

/** 현재 승인값과 짝이 맞는 가장 최근 추가 전용 이력을 카드 안에 다시 노출한다. */
function latestReadinessEvidence(
  history: readonly AdminLaunchChange[],
  check: AdminLaunchReadinessKey,
): AdminLaunchChange | undefined {
  return history.find(
    (change) =>
      change.type === "READINESS" &&
      change.target === check &&
      change.after.toLowerCase() === "true",
  );
}
