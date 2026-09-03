import { paymentMethodLabel } from "@shared/lib";
import { Badge, Card, Heading, Text } from "@shared/ui";
import type { AdminPaymentReadiness } from "@entities/admin";

/** 결제수단 식별자를 분류명 또는 실제 간편결제 사업자 이름으로 옮긴다. */
function readinessMethodLabel(id: string): string {
  const asType = paymentMethodLabel({ type: id, provider: null });
  return asType === id ? paymentMethodLabel({ type: "UNKNOWN", provider: id }) : asType;
}

/** 비밀값 원문 없이 PortOne 설정의 존재 여부와 채널 종류만 운영자에게 보여준다. */
export function PaymentReadinessPanel({
  readiness,
}: {
  readonly readiness: AdminPaymentReadiness | undefined;
}) {
  if (readiness === undefined) {
    return (
      <section className="flex flex-col gap-3">
        <Heading level={3}>결제 연동</Heading>
        <Card padded className="border-warning/40 bg-warning-soft">
          <div className="flex items-center justify-between gap-3">
            <Text weight="medium">PortOne · 카카오페이</Text>
            <Badge tone="warning">상태 확인 불가</Badge>
          </div>
          <Text tone="secondary" size="sm" className="mt-2">
            현재 API가 결제 준비 상태를 제공하지 않습니다. 결제·단계 상향을 진행하지 마세요.
          </Text>
        </Card>
      </section>
    );
  }

  const methodLabels = (readiness.methods ?? ["KAKAOPAY"]).map(readinessMethodLabel).join(" · ");
  const isTest = readiness.ready && readiness.channelType === "TEST";
  const tone = readiness.ready ? (isTest ? "warning" : "success") : "danger";
  const label = readiness.ready
    ? isTest
      ? "테스트 설정 준비"
      : "실결제 설정 준비"
    : readiness.state === "DISABLED"
      ? "비활성"
      : "설정 확인 필요";
  const description = readiness.ready
    ? isTest
      ? `${methodLabels} TEST 결제에 필요한 서버 설정이 존재합니다. 실제 승인·웹훅·환불을 검증하기 전 운영 결제를 열지 마세요.`
      : `${methodLabels} LIVE 결제에 필요한 서버 설정이 존재합니다. 공개 전 최소 금액 승인·웹훅·환불을 확인하세요.`
    : readiness.state === "DISABLED"
      ? "PortOne 서버 검증이 비활성화되어 있습니다. 이 상태에서는 Stage 2 이상과 결제를 열 수 없습니다."
      : "필수 설정이 누락되었거나 허용되지 않은 값입니다. 아래 환경변수를 확인하세요.";

  return (
    <section className="flex flex-col gap-3">
      <div>
        <Heading level={3}>결제 연동</Heading>
        <Text tone="muted" size="sm">
          비밀값은 표시하지 않고 설정 존재 여부만 진단합니다. 키 유효성은 실제 테스트로 확인해야
          합니다.
        </Text>
      </div>
      <Card
        padded
        className={
          readiness.ready
            ? isTest
              ? "border-warning/40 bg-warning-soft"
              : "border-success/30 bg-success-soft"
            : "border-danger/30 bg-danger-soft"
        }
      >
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <Text weight="medium">PortOne · {methodLabels}</Text>
            <Text tone="secondary" size="sm">
              {readiness.currency} · {readiness.channelType} 채널
            </Text>
          </div>
          <Badge tone={tone}>{label}</Badge>
        </div>
        <Text tone="secondary" size="sm" className="mt-3">
          {description}
        </Text>
        {readiness.issues.length > 0 ? (
          <div className="mt-3 flex flex-wrap gap-1.5" aria-label="결제 설정 문제">
            {readiness.issues.map((issue) => (
              <Badge key={issue.setting} tone="danger">
                {issue.setting} · {issue.problem === "MISSING" ? "누락" : "값 오류"}
              </Badge>
            ))}
          </div>
        ) : null}
      </Card>
    </section>
  );
}
