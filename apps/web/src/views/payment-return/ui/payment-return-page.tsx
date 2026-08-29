"use client";

import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { payOrder } from "@entities/order";
import { ApiError } from "@shared/api";
import {
  AlertCircleIcon,
  Button,
  Card,
  CheckCircleIcon,
  Container,
  Heading,
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

type VerificationState = "checking" | "verified" | "failed" | "cancelled";

/** 모바일 결제 redirect 복귀 지점. 쿼리는 표시용이고 결제 승인 여부는 서버가 재검증한다. */
export function PaymentReturnPage({ paymentId, code, message, pgMessage }: PaymentReturnPageProps) {
  const router = useRouter();
  const started = useRef(false);
  const returnedAsCancelled =
    code !== undefined && /cancel|취소/i.test(`${code} ${message ?? ""} ${pgMessage ?? ""}`);
  const invalidPaymentId =
    paymentId === undefined || paymentId.length === 0 || paymentId.length > 100;
  const [state, setState] = useState<VerificationState>(
    code === undefined && !invalidPaymentId
      ? "checking"
      : returnedAsCancelled
        ? "cancelled"
        : "failed",
  );
  const [description, setDescription] = useState(
    invalidPaymentId
      ? "결제 주문번호를 확인할 수 없습니다. 고객지원에 문의해 주세요."
      : code === undefined
        ? "결제 승인 결과를 안전하게 확인하고 있습니다."
        : returnedAsCancelled
          ? "결제가 취소되었습니다. 주문 상세에서 다시 시도할 수 있습니다."
          : (message ?? pgMessage ?? "결제를 완료하지 못했습니다."),
  );

  useEffect(() => {
    if (started.current || code !== undefined || invalidPaymentId) {
      return;
    }
    started.current = true;

    void payOrder(paymentId)
      .then(() => {
        setState("verified");
        setDescription("결제 승인이 확인되었습니다. 주문 상세에서 다음 단계를 진행해 주세요.");
      })
      .catch((cause: unknown) => {
        setState("failed");
        if (cause instanceof ApiError && cause.status >= 500) {
          setDescription(
            "승인 여부를 다시 확인하고 있습니다. 중복 결제하지 말고 잠시 뒤 주문 상태를 확인해 주세요.",
          );
        } else {
          setDescription(
            cause instanceof Error ? cause.message : "결제 결과를 확인하지 못했습니다.",
          );
        }
      });
  }, [code, invalidPaymentId, paymentId]);

  const orderHref =
    paymentId === undefined ? "/profile" : `/orders/${encodeURIComponent(paymentId)}`;
  return (
    <Container width="sm">
      <div className="flex min-h-[55vh] items-center py-12">
        <Card padded className="flex w-full flex-col items-center gap-5 text-center">
          <div
            aria-hidden="true"
            className="grid size-14 place-items-center rounded-2xl bg-brand-50"
          >
            {state === "checking" ? (
              <LoaderIcon className="h-7 w-7 animate-spin text-brand-600" />
            ) : state === "verified" ? (
              <CheckCircleIcon className="h-7 w-7 text-success" />
            ) : state === "cancelled" ? (
              <UndoIcon className="h-7 w-7 text-neutral-500" />
            ) : (
              <AlertCircleIcon className="h-7 w-7 text-danger" />
            )}
          </div>
          <div className="flex flex-col gap-2" role="status" aria-live="polite">
            <Heading level={1}>
              {state === "checking"
                ? "결제 확인 중"
                : state === "verified"
                  ? "결제 확인 완료"
                  : state === "cancelled"
                    ? "결제 취소"
                    : "결제 확인 필요"}
            </Heading>
            <Text tone="secondary">{description}</Text>
          </div>
          {state !== "checking" ? (
            <div className="flex w-full flex-col gap-2 sm:flex-row">
              <Button size="lg" fullWidth onClick={() => router.push(orderHref)}>
                주문 상세로 이동
              </Button>
              {state === "failed" ? (
                <Button
                  size="lg"
                  variant="secondary"
                  fullWidth
                  onClick={() => router.push("/chat?compose=support")}
                >
                  운영팀 문의
                </Button>
              ) : null}
            </div>
          ) : null}
          <Text tone="muted" size="sm">
            결제 결과는 브라우저 응답이 아닌 PortOne 원장을 서버에서 다시 확인합니다.
          </Text>
        </Card>
      </div>
    </Container>
  );
}
