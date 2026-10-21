"use client";

import { useEffect, useRef, useState } from "react";
import { isPaymentId } from "@gole/core/lib";
import { fetchOrder, payOrder, type OrderStatus } from "@entities/order";
import { ApiError } from "@shared/api";
import { loginHrefWithReturnTo } from "@shared/lib";
import {
  AlertCircleIcon,
  Button,
  Card,
  CheckCircleIcon,
  Container,
  Heading,
  LinkButton,
  LoaderIcon,
  Text,
  UndoIcon,
} from "@shared/ui";

export interface PaymentReturnPageProps {
  readonly paymentId: string | undefined;
  readonly code: string | undefined;
  readonly message: string | undefined;
  readonly pgMessage: string | undefined;
}

interface Result {
  readonly kind: "checking" | "verified" | "attention" | "cancelled" | "login";
  readonly title: string;
  readonly description: string;
  readonly retry?: boolean;
}

const orderResults: Record<OrderStatus, Result> = {
  funds_held: {
    kind: "verified",
    title: "결제 확인 완료",
    description: "결제가 확인되었어요. 주문 상세에서 배송과 거래 진행 상황을 확인하세요.",
  },
  completed: {
    kind: "verified",
    title: "거래 완료",
    description: "이미 완료된 거래예요. 주문 상세에서 거래 내역을 확인할 수 있어요.",
  },
  payment_pending: {
    kind: "attention",
    title: "결제 확인 중",
    description:
      "아직 결제가 확인되지 않았어요. 결제를 마쳤다면 중복 결제하지 말고 잠시 후 다시 확인해 주세요.",
    retry: true,
  },
  payment_review: {
    kind: "attention",
    title: "결제 검토 중",
    description:
      "결제 내역을 운영팀에서 확인하고 있어요. 다시 결제하지 말고 주문 상세에서 진행 상황을 확인해 주세요.",
    retry: true,
  },
  payment_failed: {
    kind: "attention",
    title: "결제 미완료",
    description:
      "결제가 완료되지 않은 주문이에요. 주문 상세에서 진행 가능한 다음 단계를 확인해 주세요.",
  },
  disputed: {
    kind: "attention",
    title: "거래 확인 중",
    description: "문제 해결을 위해 확인 중인 거래예요. 주문 상세에서 처리 상황을 확인해 주세요.",
  },
  refund_pending: {
    kind: "attention",
    title: "환불 처리 중",
    description: "환불 요청을 처리하고 있어요. 처리 결과는 주문 상세에서 확인할 수 있어요.",
    retry: true,
  },
  refunded: {
    kind: "cancelled",
    title: "환불 완료",
    description: "환불된 주문이에요. 결제수단에 따라 실제 반환까지 시간이 걸릴 수 있어요.",
  },
};

async function verify(paymentId: string): Promise<Result> {
  try {
    const existing = await fetchOrder(paymentId);
    // 이미 결제·거래·환불이 끝난 주문에 결제 승인을 다시 요청하지 않는다.
    const order =
      existing.status === "payment_pending" || existing.status === "payment_review"
        ? await payOrder(paymentId)
        : existing;
    const result = orderResults[order.status];
    if (order.id !== paymentId || result === undefined)
      throw new Error("주문 상태를 확인할 수 없습니다.");
    return result;
  } catch (cause) {
    if (cause instanceof ApiError && cause.status === 401) {
      return {
        kind: "login",
        title: "로그인 후 결제 확인",
        description:
          "로그인이 만료되었어요. 다시 로그인하면 이 화면에서 결제 결과를 이어서 확인할 수 있어요.",
      };
    }
    return {
      kind: "attention",
      title: "결제 확인 필요",
      description:
        cause instanceof ApiError && cause.status < 500
          ? cause.message
          : "결제 결과를 불러오지 못했어요. 중복 결제하지 말고 연결을 확인한 뒤 다시 확인해 주세요.",
      retry: true,
    };
  }
}

/** PG 복귀 쿼리는 표시용이다. 성공 여부는 서버의 실제 주문 상태로만 정한다. */
export function PaymentReturnPage({ paymentId, code, message, pgMessage }: PaymentReturnPageProps) {
  const validId = isPaymentId(paymentId);
  const cancelled =
    code !== undefined && /cancel|취소|중단/i.test(`${code} ${message ?? ""} ${pgMessage ?? ""}`);
  const [attempt, setAttempt] = useState(0);
  const key = `${paymentId ?? ""}:${code ?? ""}:${attempt}`;
  const [settled, setSettled] = useState<{ key: string; result: Result } | null>(null);
  // StrictMode의 effect 재실행에서도 동일 주문·시도의 승인 요청을 두 번 만들지 않는다.
  const inFlight = useRef<{ key: string; promise: Promise<Result> } | null>(null);

  useEffect(() => {
    if (!validId || code !== undefined) return;
    let active = true;
    if (inFlight.current?.key !== key) inFlight.current = { key, promise: verify(paymentId) };
    void inFlight.current.promise.then((result) => {
      if (active) setSettled({ key, result });
    });
    return () => {
      active = false;
    };
  }, [key, validId, paymentId, code]);

  const result: Result = !validId
    ? {
        kind: "attention",
        title: "주문번호 확인 필요",
        description: "결제 주문번호를 확인할 수 없어요. 내 주문에서 결제 내역을 확인해 주세요.",
      }
    : code !== undefined
      ? cancelled
        ? {
            kind: "cancelled",
            title: "결제 취소",
            description:
              "결제창을 닫았어요. 주문은 보존되며, 결제 내역이 있다면 주문 상세에서 상태를 확인해 주세요.",
          }
        : {
            kind: "attention",
            title: "결제 확인 필요",
            description: (
              message ??
              pgMessage ??
              "결제를 완료하지 못했어요. 주문 상세에서 확인해 주세요."
            ).slice(0, 500),
          }
      : settled?.key === key
        ? settled.result
        : {
            kind: "checking",
            title: "결제 확인 중",
            description: "주문과 결제 결과를 확인하고 있어요. 잠시만 기다려 주세요.",
          };

  const query = new URLSearchParams();
  if (validId) query.set("paymentId", paymentId);
  const loginHref = loginHrefWithReturnTo(`/payments/portone/return?${query.toString()}`);
  return (
    <Container width="sm">
      <div className="flex min-h-[55vh] items-center py-10 sm:py-16">
        <Card padded className="flex w-full flex-col items-center gap-6 text-center">
          <div
            aria-hidden="true"
            className="grid size-14 place-items-center rounded-2xl bg-brand-50"
          >
            {result.kind === "checking" ? (
              <LoaderIcon className="size-7 animate-spin text-brand-600" />
            ) : result.kind === "verified" ? (
              <CheckCircleIcon className="size-7 text-success" />
            ) : result.kind === "cancelled" ? (
              <UndoIcon className="size-7 text-neutral-500" />
            ) : (
              <AlertCircleIcon className="size-7 text-brand-600" />
            )}
          </div>
          <div className="flex flex-col gap-3" role="status" aria-live="polite">
            <Heading level={1}>{result.title}</Heading>
            <Text tone="secondary">{result.description}</Text>
          </div>
          <div className="flex w-full flex-col gap-2">
            {result.retry ? (
              <Button size="lg" fullWidth onClick={() => setAttempt((value) => value + 1)}>
                결제 상태 다시 확인
              </Button>
            ) : null}
            {result.kind === "login" ? (
              <LinkButton href={loginHref} size="lg" fullWidth>
                로그인하고 확인하기
              </LinkButton>
            ) : null}
            <LinkButton
              href={validId ? `/orders/${paymentId}` : "/profile"}
              variant={
                result.kind === "verified" || result.kind === "cancelled" ? "primary" : "secondary"
              }
              size="lg"
              fullWidth
            >
              {validId ? "주문 상세로 이동" : "내 주문 확인"}
            </LinkButton>
            {result.kind === "attention" ? (
              <LinkButton href="/chat?compose=support&category=GENERAL" variant="ghost" fullWidth>
                운영팀 문의
              </LinkButton>
            ) : null}
          </div>
          <Text tone="muted" size="sm">
            결제 내역을 확인한 뒤 주문 상태에 반영합니다.
          </Text>
        </Card>
      </div>
    </Container>
  );
}
