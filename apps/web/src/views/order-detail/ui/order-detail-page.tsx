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
import { formatKrw } from "@shared/lib";
import { Badge, Button, Card, Container, Heading, Text } from "@shared/ui";

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
        <div className="pt-10">
          <Text tone="muted">불러오는 중...</Text>
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
          <div className="flex justify-between text-sm text-neutral-500">
            <span>주문번호</span>
            <span className="font-mono">{order.id.slice(0, 8)}</span>
          </div>
        </Card>

        {error ? <p className="text-sm text-danger">{error}</p> : null}

        <div className="flex gap-3">
          {order.status === "payment_pending" ? (
            <Button size="lg" fullWidth disabled={busy} onClick={() => run(payOrder)}>
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
      </div>
    </Container>
  );
}
