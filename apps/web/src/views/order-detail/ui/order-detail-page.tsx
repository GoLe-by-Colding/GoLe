"use client";

import { useCallback, useEffect, useState } from "react";
import {
  completeOrder,
  fetchOrder,
  orderStatusLabel,
  payOrder,
  refundOrder,
  type Order,
} from "@entities/order";
import { ApiError } from "@shared/api";
import {
  formatKrw,
  isPortOneEnabled,
  paymentMethodLabel,
  requestPortOnePayment,
} from "@shared/lib";
import { Badge, Button, Card, Container, Heading, Skeleton, Text } from "@shared/ui";
import { WriteReviewForm } from "@features/write-review";

export interface OrderDetailPageProps {
  readonly orderId: string;
}

export function OrderDetailPage({ orderId }: OrderDetailPageProps) {
  const [order, setOrder] = useState<Order | null>(null);
  const [error, setError] = useState<string | undefined>(undefined);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let active = true;
    void (async () => {
      try {
        const data = await fetchOrder(orderId);
        if (active) {
          setOrder(data);
        }
      } catch {
        if (active) {
          setError("주문을 불러오지 못했습니다.");
        }
      }
    })();
    return () => {
      active = false;
    };
  }, [orderId]);

  const run = useCallback(
    async (action: (id: string) => Promise<Order>) => {
      setError(undefined);
      setBusy(true);
      try {
        setOrder(await action(orderId));
      } catch (cause) {
        setError(cause instanceof ApiError ? cause.message : "처리 중 오류가 발생했습니다.");
      } finally {
        setBusy(false);
      }
    },
    [orderId],
  );

  // 결제: 포트원이 설정돼 있으면 브라우저 결제창을 먼저 띄우고(paymentId=주문 id),
  // 성공 후 서버가 결제를 검증한다(payOrder). 미설정 시 서버 스텁 결제로 진행한다.
  const handlePay = useCallback(async () => {
    setError(undefined);
    setBusy(true);
    try {
      const current = await fetchOrder(orderId);
      if (isPortOneEnabled()) {
        await requestPortOnePayment({
          paymentId: current.id,
          orderName: `GoLe 주문 ${current.id.slice(0, 8)}`,
          totalAmount: current.amount,
        });
      }
      setOrder(await payOrder(orderId));
    } catch (cause) {
      if (cause instanceof ApiError) {
        setError(cause.message);
      } else if (cause instanceof Error) {
        setError(cause.message);
      } else {
        setError("결제 중 오류가 발생했습니다.");
      }
    } finally {
      setBusy(false);
    }
  }, [orderId]);

  if (error && order === null) {
    return (
      <Container width="sm">
        <div className="pt-10">
          <Text tone="muted">{error}</Text>
        </div>
      </Container>
    );
  }

  if (order === null) {
    return (
      <Container width="sm">
        <div className="flex flex-col gap-5 pt-10 pb-16">
          <div className="flex items-center justify-between">
            <Skeleton className="h-9 w-20" />
            <Skeleton className="h-6 w-24 rounded-full" />
          </div>
          <Skeleton className="h-28 w-full rounded-lg" />
          <div className="flex gap-3">
            <Skeleton className="h-12 flex-1 rounded-md" />
          </div>
        </div>
      </Container>
    );
  }

  return (
    <Container width="sm">
      <div className="flex flex-col gap-6 pt-10 pb-16">
        <div className="flex items-center justify-between">
          <Heading level={1}>주문</Heading>
          <Badge
            tone={order.status === "completed" ? "success" : "brand"}
            data-testid="order-status"
          >
            {orderStatusLabel(order.status)}
          </Badge>
        </div>

        <Card padded className="flex flex-col gap-3">
          <div className="flex justify-between">
            <Text tone="secondary">결제 금액</Text>
            <span className="text-xl font-bold">{formatKrw(order.amount)}</span>
          </div>
          {order.paymentMethod !== null ? (
            <div className="flex justify-between text-sm text-neutral-500">
              <span>결제수단</span>
              <span data-testid="order-payment-method">
                {paymentMethodLabel(order.paymentMethod)}
              </span>
            </div>
          ) : null}
          <div className="flex justify-between text-sm text-neutral-500">
            <span>주문번호</span>
            <span className="font-mono">{order.id.slice(0, 8)}</span>
          </div>
        </Card>

        {error ? <p className="text-sm text-danger">{error}</p> : null}

        <div className="flex gap-3">
          {order.status === "payment_pending" ? (
            <Button size="lg" fullWidth disabled={busy} onClick={handlePay}>
              {busy ? "처리 중..." : "결제하기"}
            </Button>
          ) : null}
          {order.status === "funds_held" ? (
            <>
              <Button size="lg" fullWidth disabled={busy} onClick={() => run(completeOrder)}>
                구매 확정
              </Button>
              <Button
                size="lg"
                variant="secondary"
                disabled={busy}
                onClick={() => run(refundOrder)}
              >
                환불
              </Button>
            </>
          ) : null}
        </div>

        {/* 통신판매중개자 고지 (전자상거래법 제20조) */}
        <p className="rounded-lg bg-neutral-50 px-4 py-3 text-xs leading-relaxed text-neutral-500">
          GoLe는 통신판매중개자로서 거래 당사자가 아니며, 상품 정보·거래에 대한 책임은 판매자에게
          있습니다. 결제 대금은 에스크로로 보호되며, 구매 확정 전까지 판매자에게 지급되지 않습니다.
        </p>

        <div className="flex flex-col gap-2">
          <Text weight="semibold">진행 내역</Text>
          <ol className="flex flex-col gap-1">
            {order.history.map((h, i) => (
              <li key={i} className="flex justify-between text-sm text-neutral-600">
                <span>{orderStatusLabel(h.status)}</span>
                <span className="text-neutral-400">
                  {new Date(h.occurredAt).toLocaleString("ko-KR")}
                </span>
              </li>
            ))}
          </ol>
        </div>

        {/* 자기거래 주문(차단 이전에 쌓인 건)에는 후기 동선을 열지 않는다. 서버도 거부한다. */}
        {order.status === "completed" && order.buyerId !== order.sellerId ? (
          <Card padded className="flex flex-col gap-3">
            <Text weight="semibold">판매자 후기 남기기</Text>
            <WriteReviewForm orderId={order.id} reviewerId={order.buyerId} />
          </Card>
        ) : null}
      </div>
    </Container>
  );
}
