"use client";

import { useEffect, useState } from "react";
import { fetchAdminPromotionMetrics, type PromotionMetrics } from "@entities/admin";
import { useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import { Badge, Card, Heading, Text } from "@shared/ui";
import {
  EVALUATION_CRITERION_LABEL,
  PROMOTION_POST_STATUS_LABEL,
  PROMOTION_POST_STATUS_TONE,
  formatRate,
  formatSeconds,
} from "../model/labels";
import { AdminStatus } from "./table";

const CRITERIA_ORDER = [
  "FACT_BASIS",
  "SCREEN_MATCH",
  "READER_VALUE",
  "PERSONA_NATURALNESS",
  "SPECIFICITY_VARIETY",
] as const;

/**
 * 홍보 지표 — 운영 지표는 `PromotionPost`·감사 로그에서 자동 집계하고, 품질 지표는 관리자가
 * `/admin/promotion`에서 직접 입력한 루브릭 평가를 집계한다(promotion-review/eval.md).
 * 채점 자체는 여전히 사람이 한다 — 이 화면은 자동 채점 결과가 아니다.
 */
export function AdminPromotionMetricsView() {
  const { session } = useSession();
  const token = session?.sessionToken ?? null;

  const [metrics, setMetrics] = useState<PromotionMetrics | null>(null);
  const [error, setError] = useState<string | undefined>(undefined);

  useEffect(() => {
    if (token === null) return;
    let active = true;
    void fetchAdminPromotionMetrics(token)
      .then((data) => {
        if (active) setMetrics(data);
      })
      .catch((cause: unknown) => {
        if (active) {
          setError(cause instanceof ApiError ? cause.message : "홍보 지표를 불러오지 못했습니다.");
        }
      });
    return () => {
      active = false;
    };
  }, [token]);

  return (
    <div className="flex flex-col gap-6">
      <Heading level={2}>홍보 지표</Heading>
      <Text tone="muted" size="sm">
        운영 지표는 자동 집계이고, 품질 지표는 관리자가 홍보 게시 목록에서 직접 채점한 결과를 집계한
        것입니다. 아직 평가를 입력하지 않은 초안은 품질 지표에 반영되지 않습니다.
      </Text>

      <AdminStatus error={error} loading={metrics === null && error === undefined} />

      {metrics !== null ? (
        <>
          <section className="flex flex-col gap-3">
            <Heading level={3}>운영 지표</Heading>
            <div className="grid gap-4 sm:grid-cols-3">
              <Card padded className="flex flex-col gap-2">
                <Text tone="secondary" size="sm">
                  상태별 건수
                </Text>
                <div className="flex flex-wrap gap-1.5">
                  {Object.entries(metrics.operational.countByStatus).map(([status, n]) => (
                    <Badge key={status} tone={PROMOTION_POST_STATUS_TONE[status] ?? "neutral"}>
                      {PROMOTION_POST_STATUS_LABEL[status] ?? status} {n}
                    </Badge>
                  ))}
                </div>
              </Card>
              <Card padded className="flex flex-col gap-1">
                <Text tone="secondary" size="sm">
                  반려율(감사 로그 기준)
                </Text>
                <span className="text-2xl font-extrabold tracking-tight">
                  {formatRate(metrics.operational.rejectionRate)}
                </span>
                <Text tone="muted" size="sm">
                  승인 {metrics.operational.approveCount} · 반려 {metrics.operational.rejectCount} ·
                  발행 {metrics.operational.publishCount}
                </Text>
              </Card>
              <Card padded className="flex flex-col gap-1">
                <Text tone="secondary" size="sm">
                  검토 소요시간(제출~검토 완료)
                </Text>
                <span className="text-xl font-bold tracking-tight">
                  중앙값 {formatSeconds(metrics.operational.reviewDuration.medianSeconds)}
                </span>
                <Text tone="muted" size="sm">
                  최댓값 {formatSeconds(metrics.operational.reviewDuration.maxSeconds)}
                </Text>
              </Card>
            </div>
          </section>

          <section className="flex flex-col gap-3">
            <div className="flex items-end justify-between gap-3">
              <Heading level={3}>품질 지표</Heading>
              <Text tone="muted" size="sm">
                평가 완료 {metrics.quality.evaluationCount}건
              </Text>
            </div>
            <div className="grid gap-4 [grid-template-columns:repeat(auto-fill,minmax(180px,1fr))]">
              <Card padded className="flex flex-col gap-1">
                <Text tone="secondary" size="sm">
                  첫 검토 채택률
                </Text>
                <span className="text-2xl font-extrabold tracking-tight text-brand-600">
                  {formatRate(metrics.quality.firstReviewAdoptionRate)}
                </span>
              </Card>
              <Card padded className="flex flex-col gap-1">
                <Text tone="secondary" size="sm">
                  중대한 결함률
                </Text>
                <span className="text-2xl font-extrabold tracking-tight">
                  {formatRate(metrics.quality.confirmedDefectRate)}
                </span>
              </Card>
              <Card padded className="flex flex-col gap-1">
                <Text tone="secondary" size="sm">
                  증거 부족률
                </Text>
                <span className="text-2xl font-extrabold tracking-tight">
                  {formatRate(metrics.quality.evidenceGapRate)}
                </span>
              </Card>
              <Card padded className="flex flex-col gap-1">
                <Text tone="secondary" size="sm">
                  사실 수정 필요율
                </Text>
                <span className="text-2xl font-extrabold tracking-tight">
                  {formatRate(metrics.quality.factualFixNeededRate)}
                </span>
              </Card>
            </div>
          </section>

          <section className="flex flex-col gap-3">
            <Heading level={3}>루브릭 항목별 분포</Heading>
            <Card padded className="flex flex-col divide-y divide-neutral-100">
              {CRITERIA_ORDER.map((criterion) => {
                const distribution = metrics.quality.criterionScoreDistribution[criterion];
                return (
                  <div
                    key={criterion}
                    className="flex flex-wrap items-center justify-between gap-2 py-2.5 text-sm"
                  >
                    <Text weight="medium">{EVALUATION_CRITERION_LABEL[criterion]}</Text>
                    <div className="flex flex-wrap gap-1.5">
                      <Badge tone="danger">0점 {distribution?.score0 ?? 0}</Badge>
                      <Badge tone="warning">1점 {distribution?.score1 ?? 0}</Badge>
                      <Badge tone="success">2점 {distribution?.score2 ?? 0}</Badge>
                      <Badge tone="neutral">N/A {distribution?.notApplicable ?? 0}</Badge>
                    </div>
                  </div>
                );
              })}
            </Card>
          </section>

          <section className="flex flex-col gap-3">
            <Heading level={3}>검토·수정 소요시간(평가 기록 기준)</Heading>
            <div className="grid gap-4 sm:grid-cols-2">
              <Card padded className="flex flex-col gap-1">
                <Text tone="secondary" size="sm">
                  검토 시간
                </Text>
                <span className="text-xl font-bold tracking-tight">
                  중앙값 {formatSeconds(metrics.quality.reviewSeconds.medianSeconds)}
                </span>
                <Text tone="muted" size="sm">
                  최댓값 {formatSeconds(metrics.quality.reviewSeconds.maxSeconds)}
                </Text>
              </Card>
              <Card padded className="flex flex-col gap-1">
                <Text tone="secondary" size="sm">
                  수정 시간
                </Text>
                <span className="text-xl font-bold tracking-tight">
                  중앙값 {formatSeconds(metrics.quality.reviseSeconds.medianSeconds)}
                </span>
                <Text tone="muted" size="sm">
                  최댓값 {formatSeconds(metrics.quality.reviseSeconds.maxSeconds)}
                </Text>
              </Card>
            </div>
          </section>

          <Text tone="muted" size="sm">
            초안 확보율·적절한 건너뛰기율·기술적 실패율·비용은 아직 계측하지 않습니다 — 후보
            선정·건너뛰기 이벤트가 지금은 에이전트 세션 로컬 기록(7일 보존)에만 남고 백엔드로
            전송되지 않습니다.
          </Text>
        </>
      ) : null}
    </div>
  );
}
