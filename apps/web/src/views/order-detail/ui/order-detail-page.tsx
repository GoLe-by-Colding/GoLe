"use client";

import { useCallback, useEffect, useState, useSyncExternalStore } from "react";
import {
  completeOrder,
  DISPUTE_REASON_LABEL,
  fetchOrder,
  fetchOrderContacts,
  orderStatusLabel,
  payOrder,
  refundOrder,
  type Order,
  type OrderStatus,
} from "@entities/order";
import { fetchLaunchConfig } from "@entities/launch";
import { fetchShipment, refreshShipment, type Shipment } from "@entities/shipment";
import { fetchMe, useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import {
  formatKrw,
  getPortOneConfigurationError,
  isCardPaymentAvailable,
  isPortOneEnabled,
  paymentMethodLabel,
  PortOnePaymentError,
  requestPortOnePayment,
  type PortOneCustomer,
  type PortOneMethod,
} from "@shared/lib";
import {
  Badge,
  Button,
  Card,
  Container,
  Field,
  Heading,
  Input,
  LinkButton,
  Skeleton,
  Text,
} from "@shared/ui";
import { OpenDisputeButton } from "@features/open-dispute";
import { RegisterWaybillForm } from "@features/register-waybill";
import { WriteReviewForm } from "@features/write-review";
import { ShipmentTracker } from "@widgets/shipment-tracker";

const AUTO_REFRESH_STATUSES: ReadonlySet<OrderStatus> = new Set([
  "payment_pending",
  "payment_review",
  "refund_pending",
]);
const AUTO_REFRESH_INTERVAL_MS = 5_000;
const AUTO_REFRESH_MAX_ATTEMPTS = 12;

type ConfirmationAction = "complete" | "refund";

type InitialLoadFailureKind =
  | "unauthenticated"
  | "forbidden"
  | "not_found"
  | "retryable"
  | "unavailable";

interface InitialLoadFailure {
  readonly kind: InitialLoadFailureKind;
  readonly message: string;
}

type ShipmentLoadState = "loading" | "absent" | "ready" | "error";

function classifyInitialLoadFailure(cause: unknown): InitialLoadFailure {
  if (cause instanceof ApiError) {
    if (cause.status === 401) {
      return { kind: "unauthenticated", message: "주문을 보려면 로그인이 필요합니다." };
    }
    if (cause.status === 403) {
      return { kind: "forbidden", message: "이 주문을 볼 권한이 없습니다." };
    }
    if (cause.status === 404) {
      return { kind: "not_found", message: "주문을 찾을 수 없습니다." };
    }
    if (cause.status >= 500) {
      return {
        kind: "retryable",
        message: "주문 정보를 불러오지 못했습니다. 잠시 뒤 다시 시도해 주세요.",
      };
    }
  }
  return {
    kind: "unavailable",
    message: "주문 정보를 확인할 수 없습니다. 홈에서 다시 시작해 주세요.",
  };
}

/** orderId를 경로 한 칸으로 고정해 외부 URL·쿼리 주입이 불가능한 복귀 링크를 만든다. */
function orderLoginHref(orderId: string): string {
  const returnTo = `/orders/${encodeURIComponent(orderId)}`;
  return `/login?returnTo=${encodeURIComponent(returnTo)}`;
}

/**
 * 이 주문에서 결제창을 띄운 적이 있는지 기억한다.
 *
 * 서버는 이 사실을 모른다 — 결제 시도는 포트원 원장에만 남고, 주문 상세 조회는 PG를 호출하지
 * 않는다(호출하면 화면을 열 때마다 PG를 때린다). 그래서 브라우저가 기억한다.
 *
 * sessionStorage를 쓰는 이유: 모바일 결제는 리다이렉트로 페이지를 떠났다가 돌아오고, 사용자가
 * 새로고침도 한다. 메모리에만 두면 정작 안내가 필요한 그 순간에 사라진다.
 */
const PAYMENT_ATTEMPT_KEY_PREFIX = "gole.order.payment-attempted:";
const PAYMENT_ATTEMPT_CHANGE_EVENT = "gole:payment-attempt-change";

function readPaymentAttempted(orderId: string): boolean {
  if (typeof window === "undefined") return false;
  try {
    return window.sessionStorage.getItem(PAYMENT_ATTEMPT_KEY_PREFIX + orderId) !== null;
  } catch {
    // 프라이버시 모드 등에서 접근이 막히면 "시도 안 함"으로 본다.
    return false;
  }
}

function rememberPaymentAttempt(orderId: string): void {
  try {
    window.sessionStorage.setItem(PAYMENT_ATTEMPT_KEY_PREFIX + orderId, "1");
    window.dispatchEvent(new Event(PAYMENT_ATTEMPT_CHANGE_EVENT));
  } catch {
    // 기록에 실패해도 결제 자체를 막지는 않는다.
  }
}

/**
 * 카드 결제에 쓴 이름을 기억한다. 계정에 이름 필드가 없어 매번 다시 물어야 하는데,
 * 구매 때 연락처를 기억하는 것과 같은 이유로 한 번만 받는다.
 */
const CARD_NAME_STORAGE_KEY = "gole.buyer-name";

function readStoredCardName(): string {
  if (typeof window === "undefined") return "";
  try {
    return window.localStorage.getItem(CARD_NAME_STORAGE_KEY) ?? "";
  } catch {
    return "";
  }
}

/** useSyncExternalStore용 구독. session-store와 같은 방식이다(같은 탭은 커스텀 이벤트로 전파). */
function subscribePaymentAttempt(onChange: () => void): () => void {
  if (typeof window === "undefined") {
    return () => undefined;
  }
  window.addEventListener(PAYMENT_ATTEMPT_CHANGE_EVENT, onChange);
  window.addEventListener("storage", onChange);
  return () => {
    window.removeEventListener(PAYMENT_ATTEMPT_CHANGE_EVENT, onChange);
    window.removeEventListener("storage", onChange);
  };
}

export interface OrderDetailPageProps {
  readonly orderId: string;
}

export function OrderDetailPage({ orderId }: OrderDetailPageProps) {
  const { session } = useSession();
  const [order, setOrder] = useState<Order | null>(null);
  const [shipment, setShipment] = useState<Shipment | null>(null);
  const [shipmentLoadState, setShipmentLoadState] = useState<ShipmentLoadState>("loading");
  const [initialLoadFailure, setInitialLoadFailure] = useState<InitialLoadFailure | null>(null);
  const [loadRevision, setLoadRevision] = useState(0);
  const [trackerBusy, setTrackerBusy] = useState(false);
  const [shipmentRetrying, setShipmentRetrying] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);
  const [busy, setBusy] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [confirmation, setConfirmation] = useState<ConfirmationAction | null>(null);
  const [paymentsOpen, setPaymentsOpen] = useState(false);
  const [reviewsOpen, setReviewsOpen] = useState(false);
  // 결제수단 선택. 카드 채널이 설정되지 않았으면 선택 UI 자체를 노출하지 않는다.
  const cardAvailable = isCardPaymentAvailable();
  const [paymentMethod, setPaymentMethod] = useState<PortOneMethod>("kakaopay");
  const [cardStep, setCardStep] = useState(false);
  const [cardPrefilling, setCardPrefilling] = useState(false);
  const [cardName, setCardName] = useState("");
  const [cardEmail, setCardEmail] = useState("");
  const [cardPhone, setCardPhone] = useState("");
  // sessionStorage는 React 밖의 상태다. 서버 렌더에는 없으므로 서버 스냅샷은 항상 false다.
  const paymentAttempted = useSyncExternalStore(
    subscribePaymentAttempt,
    () => readPaymentAttempted(orderId),
    () => false,
  );
  const paymentConfigurationError = getPortOneConfigurationError();

  useEffect(() => {
    let active = true;
    void (async () => {
      try {
        const [data, launch] = await Promise.all([fetchOrder(orderId), fetchLaunchConfig()]);
        if (active) {
          setOrder(data);
          setInitialLoadFailure(null);
          setPaymentsOpen(launch.features.payments);
          setReviewsOpen(launch.features.reviews);
        }
      } catch (cause) {
        if (active) {
          setInitialLoadFailure(classifyInitialLoadFailure(cause));
          setRefreshing(false);
        }
        return;
      }
      // 운송장은 없을 수 있다(발송 전) — 404는 정상이다.
      try {
        const s = await fetchShipment(orderId);
        if (active) {
          setShipment(s);
          setShipmentLoadState("ready");
        }
      } catch (cause) {
        if (active) {
          setShipment(null);
          setShipmentLoadState(
            cause instanceof ApiError && cause.status === 404 ? "absent" : "error",
          );
        }
      } finally {
        if (active) setRefreshing(false);
      }
    })();
    return () => {
      active = false;
    };
  }, [loadRevision, orderId]);

  const retryInitialLoad = useCallback(() => {
    setRefreshing(true);
    setInitialLoadFailure(null);
    setError(undefined);
    setShipment(null);
    setShipmentLoadState("loading");
    setLoadRevision((current) => current + 1);
  }, []);

  const refreshOrder = useCallback(async () => {
    setRefreshing(true);
    setError(undefined);
    try {
      setOrder(await fetchOrder(orderId));
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "주문 상태를 확인하지 못했습니다.");
    } finally {
      setRefreshing(false);
    }
  }, [orderId]);

  // 결제 승인·운영 검토·비동기 환불은 웹훅으로 상태가 바뀔 수 있다. 무한 폴링을 피하려고
  // 5초 간격으로 최대 1분만 자동 확인하고, 이후에는 사용자가 직접 다시 확인할 수 있게 한다.
  //
  // 결제 대기는 시도한 뒤에만 확인한다. 주문을 만든 직후부터 돌리면 정작 결제를 마치고
  // 돌아왔을 때 1분 창이 이미 소진돼 있다. 검토·환불은 서버가 주도하므로 조건 없이 확인한다.
  useEffect(() => {
    const watchedStatus = order?.status;
    if (watchedStatus === undefined || !AUTO_REFRESH_STATUSES.has(watchedStatus)) {
      return;
    }
    if (watchedStatus === "payment_pending" && !paymentAttempted) {
      return;
    }

    let cancelled = false;
    let attempts = 0;
    let timer: number | undefined;

    const schedule = () => {
      timer = window.setTimeout(() => {
        attempts += 1;
        void fetchOrder(orderId)
          .then((latest) => {
            if (cancelled) return;
            setOrder(latest);
            if (latest.status !== watchedStatus) {
              setError(undefined);
              return;
            }
            if (attempts < AUTO_REFRESH_MAX_ATTEMPTS) schedule();
          })
          .catch(() => {
            if (!cancelled && attempts < AUTO_REFRESH_MAX_ATTEMPTS) schedule();
          });
      }, AUTO_REFRESH_INTERVAL_MS);
    };

    schedule();
    return () => {
      cancelled = true;
      if (timer !== undefined) window.clearTimeout(timer);
    };
  }, [order?.status, orderId, paymentAttempted]);

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

  const handleTrackerRefresh = useCallback(async () => {
    setTrackerBusy(true);
    try {
      setShipment(await refreshShipment(orderId));
      // 배송완료 → 자동확정 등 주문 상태가 함께 움직일 수 있다.
      setOrder(await fetchOrder(orderId));
    } catch {
      // 조회 실패는 치명적이지 않다 — 다음 폴링이 다시 시도한다.
    } finally {
      setTrackerBusy(false);
    }
  }, [orderId]);

  const retryShipment = useCallback(async () => {
    setShipmentRetrying(true);
    try {
      const latest = await fetchShipment(orderId);
      setShipment(latest);
      setShipmentLoadState("ready");
    } catch (cause) {
      setShipment(null);
      setShipmentLoadState(cause instanceof ApiError && cause.status === 404 ? "absent" : "error");
    } finally {
      setShipmentRetrying(false);
    }
  }, [orderId]);

  const confirmOrderAction = useCallback(async () => {
    const selected = confirmation;
    setConfirmation(null);
    if (selected === "complete") {
      await run(completeOrder);
    } else if (selected === "refund") {
      await run(refundOrder);
    }
  }, [confirmation, run]);

  // 결제: 포트원이 설정돼 있으면 브라우저 결제창을 먼저 띄우고(paymentId=주문 id),
  // 성공 후 서버가 결제를 검증한다(payOrder). 미설정 시 서버 스텁 결제로 진행한다.
  const pay = useCallback(
    async (method: PortOneMethod, customer?: PortOneCustomer) => {
      if (!paymentsOpen) {
        setError("현재 신규 결제가 일시 중지되어 있습니다. 주문 상태는 보존됩니다.");
        return;
      }
      setError(undefined);
      setBusy(true);
      try {
        const current = await fetchOrder(orderId);
        if (isPortOneEnabled()) {
          // 결제창을 여는 순간 기록한다. 모바일은 여기서 페이지를 떠나므로 이후에 기록할 기회가 없다.
          // 커스텀 이벤트가 같은 탭의 구독자에게 전파하므로 별도 setState가 필요 없다.
          rememberPaymentAttempt(orderId);
          await requestPortOnePayment({
            paymentId: current.id,
            orderName: `GoLe 주문 ${current.id.slice(0, 8)}`,
            totalAmount: current.amount,
            method,
            ...(customer === undefined ? {} : { customer }),
          });
        }
        setOrder(await payOrder(orderId));
        setCardStep(false);
      } catch (cause) {
        if (cause instanceof PortOnePaymentError && cause.userCancelled) {
          setError(
            "결제가 취소되었습니다. 주문은 그대로 보존되며 원할 때 다시 결제할 수 있습니다.",
          );
        } else if (cause instanceof PortOnePaymentError) {
          // 결제창에서 실패한 경우다. 사유 분류가 빗나가도 "주문이 보존된다"는 사실은 알려야 한다 —
          // 그걸 모르면 사용자가 중복 결제를 시도한다.
          setError(`${cause.message} 주문은 그대로 보존되며 원할 때 다시 결제할 수 있습니다.`);
        } else if (cause instanceof ApiError && cause.status >= 500) {
          setError(
            "결제 승인 여부를 확인하고 있습니다. 중복 결제하지 말고 잠시 뒤 주문 상태를 다시 확인해 주세요.",
          );
        } else if (cause instanceof ApiError) {
          setError(cause.message);
        } else if (cause instanceof Error) {
          setError(cause.message);
        } else {
          setError("결제 중 오류가 발생했습니다.");
        }
      } finally {
        setBusy(false);
      }
    },
    [orderId, paymentsOpen],
  );

  /**
   * 카드 결제 단계를 연다. 이니시스가 요구하는 세 값 중 이메일·연락처는 이미 우리가 아는
   * 사실이므로 미리 채운다 — 구매자가 새로 입력할 값은 이름 하나여야 한다.
   *
   * 프리필은 카드를 고른 이 순간에만 한다. 화면 진입마다 부르면 카카오페이만 쓰는 구매자에게도
   * 불필요한 왕복이 생긴다.
   */
  const openCardStep = useCallback(async () => {
    setError(undefined);
    setCardStep(true);
    setCardName((current) => (current.length > 0 ? current : readStoredCardName()));
    setCardPrefilling(true);
    // 한쪽이 실패해도 나머지는 채운다. 못 채운 칸은 구매자가 직접 입력하면 된다.
    const [me, contacts] = await Promise.allSettled([
      fetchMe(session?.sessionToken ?? ""),
      fetchOrderContacts(orderId),
    ]);
    if (me.status === "fulfilled") {
      setCardEmail((current) => (current.length > 0 ? current : me.value.email));
    }
    if (contacts.status === "fulfilled" && contacts.value.buyerPhone !== null) {
      const phone = contacts.value.buyerPhone;
      setCardPhone((current) => (current.length > 0 ? current : phone));
    }
    setCardPrefilling(false);
  }, [orderId, session?.sessionToken]);

  const handlePayClick = useCallback(() => {
    if (paymentMethod === "card") {
      void openCardStep();
      return;
    }
    void pay("kakaopay");
  }, [openCardStep, pay, paymentMethod]);

  const handleCardSubmit = useCallback(
    (event: React.FormEvent) => {
      event.preventDefault();
      const fullName = cardName.trim();
      try {
        window.localStorage.setItem(CARD_NAME_STORAGE_KEY, fullName);
      } catch {
        // 저장 실패는 무시 — 다음 결제에서 다시 입력하면 된다.
      }
      void pay("card", { fullName, email: cardEmail.trim(), phoneNumber: cardPhone.trim() });
    },
    [cardEmail, cardName, cardPhone, pay],
  );

  if (initialLoadFailure !== null && order === null) {
    return (
      <Container width="sm">
        <div className="flex flex-col items-start gap-4 pt-10 pb-16">
          <div className="flex flex-col gap-2">
            <Heading level={1}>
              {initialLoadFailure.kind === "unauthenticated"
                ? "로그인이 필요합니다"
                : initialLoadFailure.kind === "forbidden"
                  ? "접근할 수 없는 주문입니다"
                  : initialLoadFailure.kind === "not_found"
                    ? "주문을 찾을 수 없습니다"
                    : "주문을 불러오지 못했습니다"}
            </Heading>
            <Text tone="muted">{initialLoadFailure.message}</Text>
          </div>
          {initialLoadFailure.kind === "unauthenticated" ? (
            <LinkButton href={orderLoginHref(orderId)}>로그인하고 주문 보기</LinkButton>
          ) : initialLoadFailure.kind === "retryable" ? (
            <div className="flex flex-wrap gap-2">
              <Button
                size="sm"
                variant="secondary"
                disabled={refreshing}
                onClick={retryInitialLoad}
              >
                {refreshing ? "확인 중..." : "다시 시도"}
              </Button>
              <LinkButton href="/" size="sm" variant="ghost">
                홈으로
              </LinkButton>
            </div>
          ) : (
            <LinkButton href="/">홈으로</LinkButton>
          )}
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

  const isSeller = session !== null && session.accountId === order.sellerId;
  const isBuyer = session !== null && session.accountId === order.buyerId;

  return (
    <Container width="sm">
      <div className="flex flex-col gap-6 pt-10 pb-16">
        <div className="flex items-center justify-between">
          <Heading level={1}>{isSeller ? "판매 주문" : "주문"}</Heading>
          <Badge
            tone={
              order.status === "completed"
                ? "success"
                : order.status === "payment_review" ||
                    order.status === "refund_pending" ||
                    order.status === "disputed"
                  ? "warning"
                  : order.status === "payment_failed" || order.status === "refunded"
                    ? "danger"
                    : "brand"
            }
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
          {order.paymentMethod !== null && order.paymentMethod !== undefined ? (
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

        {error ? (
          <p className="text-sm text-danger" role="alert">
            {error}
          </p>
        ) : null}

        {paymentsOpen && paymentConfigurationError !== undefined ? (
          <p className="rounded-xl border border-danger/25 bg-danger-soft px-4 py-3 text-sm text-neutral-700">
            {paymentConfigurationError} 운영팀에 자동으로 전달된 설정을 확인해 주세요.
          </p>
        ) : null}

        {/*
          결제 시도 전에는 "기다리고 있어요"가 사실이 아니다 — 아직 아무것도 시작되지 않았고,
          "결제를 마쳤는데 상태가 그대로라면"은 결제한 사람에게 할 말이다. 시도 전에는 다음에
          무엇을 할지만 알려준다.
        */}
        {paymentsOpen && order.status === "payment_pending" && !paymentAttempted ? (
          <OrderStatusNotice
            title="아직 결제하지 않았어요"
            description={
              cardAvailable
                ? "결제수단을 고르고 결제하기를 누르면 결제창이 열립니다. 결제 전까지 매물은 다른 사람이 살 수 없도록 보관됩니다."
                : "아래 결제하기를 누르면 카카오페이 결제창이 열립니다. 결제 전까지 매물은 다른 사람이 살 수 없도록 보관됩니다."
            }
            refreshing={refreshing}
            onRefresh={refreshOrder}
          />
        ) : null}

        {!paymentsOpen && order.status === "payment_pending" && !paymentAttempted ? (
          <div className="flex flex-col gap-3 rounded-xl border border-brand-100 bg-brand-50 px-4 py-4">
            <div className="flex flex-col gap-1">
              <Text weight="semibold">신규 결제가 일시 중지됐어요</Text>
              <Text size="sm" tone="secondary">
                이 주문은 보존되지만 지금은 결제를 새로 시작할 수 없습니다. 매물 상세에서 판매자와
                직접 거래를 상의하거나 운영팀에 문의해 주세요.
              </Text>
            </div>
            <div className="flex flex-wrap gap-2">
              <LinkButton href={`/listings/${order.listingId}`} size="sm" variant="secondary">
                매물과 채팅 보기
              </LinkButton>
              <LinkButton href="/chat?compose=support" size="sm" variant="ghost">
                운영팀 문의
              </LinkButton>
            </div>
          </div>
        ) : null}

        {order.status === "payment_pending" && paymentAttempted ? (
          <OrderStatusNotice
            title="결제를 기다리고 있어요"
            description="결제를 마쳤는데 상태가 그대로라면 다시 결제하지 말고 승인 상태를 확인해 주세요. 최대 1분 동안 자동으로도 확인합니다."
            refreshing={refreshing}
            onRefresh={refreshOrder}
          />
        ) : null}

        {order.status === "payment_review" ? (
          <OrderStatusNotice
            tone="warning"
            title="운영팀이 결제를 확인하고 있어요"
            description="금액 또는 PG 상태를 대조 중입니다. 중복 결제하지 마세요. 주문과 매물은 보존되며 확인 후 상태가 자동으로 갱신됩니다."
            refreshing={refreshing}
            onRefresh={refreshOrder}
            showSupport
          />
        ) : null}

        {order.status === "refund_pending" ? (
          <OrderStatusNotice
            tone="warning"
            title="환불을 처리하고 있어요"
            description="결제수단에 환불이 반영되기까지 시간이 걸릴 수 있습니다. 최대 1분 동안 자동으로 확인하며, 이후에도 직접 상태를 확인할 수 있습니다."
            refreshing={refreshing}
            onRefresh={refreshOrder}
            showSupport
          />
        ) : null}

        {order.status === "disputed" ? (
          <OrderStatusNotice
            tone="warning"
            title="분쟁을 검토하고 있어요"
            description={`운영자가 배송 기록을 근거로 환불 또는 거래 완료를 판정합니다.${order.disputeReason ? ` (사유: ${DISPUTE_REASON_LABEL[order.disputeReason]})` : ""} 판정 결과는 알림으로 안내됩니다.`}
            refreshing={refreshing}
            onRefresh={refreshOrder}
            showSupport
          />
        ) : null}

        {order.status === "payment_failed" ? (
          <div className="flex flex-col gap-3 rounded-xl border border-danger/25 bg-danger-soft px-4 py-4">
            <div className="flex flex-col gap-1">
              <Text weight="semibold">결제를 완료하지 못했어요</Text>
              <Text size="sm" tone="secondary">
                결제되지 않았으며 매물 예약은 해제됩니다. 매물이 아직 판매 중이면 상세 화면에서 새
                주문으로 다시 시도할 수 있습니다.
              </Text>
            </div>
            <div className="flex flex-wrap gap-2">
              <LinkButton href={`/listings/${order.listingId}`} size="sm" variant="secondary">
                매물 다시 보기
              </LinkButton>
              <LinkButton href="/chat?compose=support" size="sm" variant="ghost">
                운영팀 문의
              </LinkButton>
            </div>
          </div>
        ) : null}

        {/* ── 배송 (shipping-and-fees) ── */}
        {isSeller && order.status === "funds_held" ? (
          <Card padded className="flex flex-col gap-4">
            <Text weight="semibold">
              {shipmentLoadState === "ready" ? "배송 현황" : "발송 처리"}
            </Text>
            {shipmentLoadState === "loading" ? (
              <Text size="sm" tone="muted" role="status">
                배송 정보를 확인하고 있습니다.
              </Text>
            ) : shipmentLoadState === "error" ? (
              <ShipmentLoadFailure
                retrying={shipmentRetrying}
                onRetry={() => void retryShipment()}
              />
            ) : null}
            {shipmentLoadState === "ready" && shipment !== null ? (
              <ShipmentTracker
                shipment={shipment}
                onRefresh={() => void handleTrackerRefresh()}
                refreshing={trackerBusy}
              />
            ) : null}
            {shipmentLoadState === "ready" || shipmentLoadState === "absent" ? (
              <RegisterWaybillForm
                orderId={order.id}
                existing={shipment}
                onRegistered={(s) => {
                  setShipment(s);
                  setShipmentLoadState("ready");
                }}
              />
            ) : null}
          </Card>
        ) : null}

        {!isSeller &&
        shipmentLoadState === "ready" &&
        shipment !== null &&
        order.status !== "payment_pending" ? (
          <Card padded className="flex flex-col gap-4">
            <Text weight="semibold">배송 현황</Text>
            <ShipmentTracker
              shipment={shipment}
              onRefresh={() => void handleTrackerRefresh()}
              refreshing={trackerBusy}
            />
          </Card>
        ) : null}

        {!isSeller && shipmentLoadState === "error" && order.status !== "payment_pending" ? (
          <Card padded className="flex flex-col gap-4">
            <Text weight="semibold">배송 현황</Text>
            <ShipmentLoadFailure retrying={shipmentRetrying} onRetry={() => void retryShipment()} />
          </Card>
        ) : null}

        {paymentsOpen && order.status === "payment_pending" && !isSeller ? (
          <div className="flex flex-col gap-4">
            {cardAvailable ? (
              <fieldset className="flex flex-col gap-2" disabled={busy}>
                <legend className="pb-2 text-sm font-semibold text-neutral-700">결제수단</legend>
                <div className="grid gap-2 sm:grid-cols-2">
                  <PaymentMethodOption
                    value="kakaopay"
                    label="카카오페이"
                    hint="간편결제"
                    checked={paymentMethod === "kakaopay"}
                    onSelect={(next) => {
                      setPaymentMethod(next);
                      setCardStep(false);
                    }}
                  />
                  <PaymentMethodOption
                    value="card"
                    label="신용·체크카드"
                    hint="KG이니시스"
                    checked={paymentMethod === "card"}
                    onSelect={setPaymentMethod}
                  />
                </div>
              </fieldset>
            ) : null}

            {cardStep ? (
              <form
                onSubmit={handleCardSubmit}
                className="flex flex-col gap-3 rounded-xl border border-neutral-200 px-4 py-4"
              >
                <div className="flex flex-col gap-1">
                  <Text weight="semibold">카드 결제 정보</Text>
                  <Text size="sm" tone="secondary">
                    카드사가 요구하는 정보예요. 연락처와 이메일은 주문·계정 정보로 미리 채웠습니다.
                  </Text>
                </div>
                <Field label="이름">
                  {({ inputId, describedBy }) => (
                    <Input
                      id={inputId}
                      aria-describedby={describedBy}
                      value={cardName}
                      onChange={(e) => setCardName(e.target.value)}
                      placeholder="홍길동"
                      autoComplete="name"
                      required
                      autoFocus
                    />
                  )}
                </Field>
                <Field label="이메일">
                  {({ inputId, describedBy }) => (
                    <Input
                      id={inputId}
                      aria-describedby={describedBy}
                      value={cardEmail}
                      onChange={(e) => setCardEmail(e.target.value)}
                      placeholder={cardPrefilling ? "불러오는 중..." : "buyer@example.com"}
                      type="email"
                      autoComplete="email"
                      required
                    />
                  )}
                </Field>
                <Field label="연락처">
                  {({ inputId, describedBy }) => (
                    <Input
                      id={inputId}
                      aria-describedby={describedBy}
                      value={cardPhone}
                      onChange={(e) => setCardPhone(e.target.value)}
                      placeholder={cardPrefilling ? "불러오는 중..." : "010-1234-5678"}
                      inputMode="tel"
                      type="tel"
                      autoComplete="tel"
                      required
                    />
                  )}
                </Field>
                <div className="flex gap-2">
                  <Button size="lg" fullWidth type="submit" disabled={busy || cardPrefilling}>
                    {busy ? "처리 중..." : "카드로 결제하기"}
                  </Button>
                  <Button
                    size="lg"
                    variant="ghost"
                    type="button"
                    disabled={busy}
                    onClick={() => setCardStep(false)}
                  >
                    취소
                  </Button>
                </div>
              </form>
            ) : (
              <Button
                size="lg"
                fullWidth
                disabled={busy || paymentConfigurationError !== undefined}
                onClick={handlePayClick}
              >
                {busy ? "처리 중..." : "결제하기"}
              </Button>
            )}
          </div>
        ) : null}

        <div className="flex gap-3">
          {order.status === "funds_held" && !isSeller ? (
            <>
              <Button
                size="lg"
                fullWidth
                disabled={busy}
                onClick={() => setConfirmation("complete")}
              >
                구매 확정
              </Button>
              {shipmentLoadState === "absent" ? (
                // 발송 전에만 일방 환불이 가능하다(R4.5). 발송 후 문제는 분쟁으로.
                <Button
                  size="lg"
                  variant="secondary"
                  disabled={busy}
                  onClick={() => setConfirmation("refund")}
                >
                  환불
                </Button>
              ) : null}
            </>
          ) : null}
        </div>

        {!isSeller &&
        order.status === "funds_held" &&
        shipmentLoadState === "ready" &&
        shipment !== null ? (
          <OpenDisputeButton orderId={order.id} onDisputed={(o) => setOrder(o)} />
        ) : null}

        {/* 통신판매중개자 고지 (전자상거래법 제20조) */}
        <p className="rounded-lg bg-neutral-50 px-4 py-3 text-xs leading-relaxed text-neutral-500">
          GoLe는 이용자 간 거래를 연결하는 중개 서비스이며 거래 당사자가 아닙니다. 상품 정보에 대한
          책임은 판매자에게 있고, 결제가 활성화된 주문의 구매확정·환불·분쟁 처리는 주문 상태와
          약관에 따릅니다.
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
        {reviewsOpen &&
        isBuyer &&
        order.status === "completed" &&
        order.buyerId !== order.sellerId ? (
          <Card id="review" padded className="scroll-mt-24 flex flex-col gap-3">
            <Text weight="semibold">판매자 후기 남기기</Text>
            <WriteReviewForm orderId={order.id} reviewerId={order.buyerId} />
          </Card>
        ) : null}
      </div>

      {confirmation !== null ? (
        <OrderActionConfirmation
          action={confirmation}
          onCancel={() => setConfirmation(null)}
          onConfirm={() => void confirmOrderAction()}
        />
      ) : null}
    </Container>
  );
}

interface PaymentMethodOptionProps {
  readonly value: PortOneMethod;
  readonly label: string;
  readonly hint: string;
  readonly checked: boolean;
  readonly onSelect: (value: PortOneMethod) => void;
}

function PaymentMethodOption({ value, label, hint, checked, onSelect }: PaymentMethodOptionProps) {
  return (
    <label
      className={`flex cursor-pointer items-center gap-3 rounded-xl border px-4 py-3 transition-colors ${
        checked ? "border-brand-400 bg-brand-50/60" : "border-neutral-200 hover:border-neutral-300"
      }`}
    >
      <input
        type="radio"
        name="payment-method"
        value={value}
        checked={checked}
        onChange={() => onSelect(value)}
        className="accent-brand-600"
      />
      <span className="flex flex-col">
        <Text size="sm" weight="semibold">
          {label}
        </Text>
        <Text size="sm" tone="muted">
          {hint}
        </Text>
      </span>
    </label>
  );
}

interface ShipmentLoadFailureProps {
  readonly retrying: boolean;
  readonly onRetry: () => void;
}

function ShipmentLoadFailure({ retrying, onRetry }: ShipmentLoadFailureProps) {
  return (
    <div className="flex flex-col items-start gap-3" role="alert">
      <Text size="sm" tone="secondary">
        배송 정보를 불러오지 못했어요. 확인 전에는 환불하거나 운송장을 등록·변경할 수 없습니다.
      </Text>
      <Button size="sm" variant="secondary" disabled={retrying} onClick={onRetry}>
        {retrying ? "확인 중..." : "배송 정보 다시 확인"}
      </Button>
    </div>
  );
}

interface OrderStatusNoticeProps {
  readonly title: string;
  readonly description: string;
  readonly refreshing: boolean;
  readonly onRefresh: () => void;
  readonly tone?: "neutral" | "warning";
  readonly showSupport?: boolean;
}

function OrderStatusNotice({
  title,
  description,
  refreshing,
  onRefresh,
  tone = "neutral",
  showSupport = false,
}: OrderStatusNoticeProps) {
  return (
    <div
      className={`flex flex-col gap-3 rounded-xl border px-4 py-4 ${
        tone === "warning" ? "border-warning/30 bg-warning-soft" : "border-brand-100 bg-brand-50/60"
      }`}
      role="status"
    >
      <div className="flex flex-col gap-1">
        <Text weight="semibold">{title}</Text>
        <Text size="sm" tone="secondary">
          {description}
        </Text>
      </div>
      <div className="flex flex-wrap gap-2">
        <Button size="sm" variant="secondary" disabled={refreshing} onClick={onRefresh}>
          {refreshing ? "확인 중..." : "상태 다시 확인"}
        </Button>
        {showSupport ? (
          <LinkButton href="/chat?compose=support" size="sm" variant="ghost">
            운영팀 문의
          </LinkButton>
        ) : null}
      </div>
    </div>
  );
}

interface OrderActionConfirmationProps {
  readonly action: ConfirmationAction;
  readonly onCancel: () => void;
  readonly onConfirm: () => void;
}

function OrderActionConfirmation({ action, onCancel, onConfirm }: OrderActionConfirmationProps) {
  useEffect(() => {
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") onCancel();
    };
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [onCancel]);

  const completing = action === "complete";
  const title = completing ? "구매를 확정할까요?" : "환불을 요청할까요?";
  const description = completing
    ? "상품을 정상적으로 받았는지 확인해 주세요. 구매를 확정하면 판매자 정산이 시작되어 되돌리기 어렵습니다."
    : "구매 확정 전 주문만 환불할 수 있습니다. 요청 후 결제수단에 반영되기까지 시간이 걸릴 수 있습니다.";

  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-neutral-950/45 px-4">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="order-confirmation-title"
        aria-describedby="order-confirmation-description"
        className="flex w-full max-w-sm flex-col gap-5 rounded-2xl bg-white p-6 shadow-2xl"
      >
        <div className="flex flex-col gap-2">
          <Heading level={2} id="order-confirmation-title">
            {title}
          </Heading>
          <p
            id="order-confirmation-description"
            className="text-sm leading-relaxed text-neutral-600"
          >
            {description}
          </p>
        </div>
        <div className="flex gap-2">
          <Button fullWidth variant="secondary" autoFocus onClick={onCancel}>
            돌아가기
          </Button>
          <Button fullWidth variant={completing ? "primary" : "danger"} onClick={onConfirm}>
            {completing ? "상품을 받았어요" : "환불 요청"}
          </Button>
        </div>
      </div>
    </div>
  );
}
