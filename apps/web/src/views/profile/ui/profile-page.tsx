"use client";

import { useCallback, useEffect, useState, type ReactNode } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { fetchMyOrders, orderStatusLabel, type Order } from "@entities/order";
import { searchListings, type Listing } from "@entities/listing";
import { fetchMe, useSession, type Me } from "@entities/user";
import { formatKrw } from "@shared/lib";
import {
  AlertCircleIcon,
  Badge,
  Button,
  Card,
  Container,
  Heading,
  LinkButton,
  PackageIcon,
  ShoppingBagIcon,
  Skeleton,
  Text,
} from "@shared/ui";

type Tab = "info" | "orders" | "listings";

const TAB_LABEL: Record<Tab, string> = {
  info: "내 정보",
  orders: "구매 내역",
  listings: "내 매물",
};

/**
 * 조회 상태. 실패를 빈 값으로 뭉개지 않는다 — "없음"과 "못 불러옴"은 사용자가 할 행동이
 * 다르다(전자는 등록하러 가고, 후자는 다시 시도한다).
 */
type Load<T> =
  | { readonly status: "loading" }
  | { readonly status: "ready"; readonly data: T }
  | { readonly status: "failed" };

const LOADING = { status: "loading" } as const;
const FAILED = { status: "failed" } as const;

/** 계정 ID는 UUID라 그대로 두면 줄을 넘긴다. 앞뒤만 남기고 원본은 복사로 가져가게 한다. */
function shortenId(id: string): string {
  return id.length <= 20 ? id : `${id.slice(0, 8)}…${id.slice(-6)}`;
}

export function ProfilePage() {
  const router = useRouter();
  const { session, signOut } = useSession();
  // saveSession이 로컬 저장소에 토큰을 비워서 넣으므로(인증은 HttpOnly 쿠키가 담당)
  // 새로고침 이후 이 값은 항상 빈 문자열이다. 빈 문자열을 "미인증"으로 읽으면 안 된다.
  const token = session?.sessionToken ?? "";
  const [tab, setTab] = useState<Tab>("info");
  const [me, setMe] = useState<Load<Me>>(LOADING);
  const [orders, setOrders] = useState<Load<readonly Order[]>>(LOADING);
  const [listings, setListings] = useState<Load<readonly Listing[]>>(LOADING);
  const [attempt, setAttempt] = useState(0);
  const [copied, setCopied] = useState(false);

  const reload = useCallback(() => {
    setMe(LOADING);
    setOrders(LOADING);
    setListings(LOADING);
    setAttempt((n) => n + 1);
  }, []);

  useEffect(() => {
    // 토큰 유무로 막지 않는다 — 막으면 세 요청이 모두 나가지 않아 이메일·구매 내역·내 매물이
    // 영구히 "불러오는 중"에 멈춘다. apiRequest가 쿠키(credentials:"include")로 인증하고
    // 토큰이 있을 때만 Bearer를 덧붙이므로, 빈 토큰으로도 정상 조회된다(AdminShell과 동일).
    if (!session) return;
    const controller = new AbortController();
    const accountId = session.accountId;

    fetchMe(token)
      .then((r) => {
        if (!controller.signal.aborted) setMe({ status: "ready", data: r });
      })
      .catch(() => {
        if (!controller.signal.aborted) setMe(FAILED);
      });

    fetchMyOrders(accountId, controller.signal)
      .then((r) => {
        if (!controller.signal.aborted) setOrders({ status: "ready", data: r });
      })
      .catch(() => {
        if (!controller.signal.aborted) setOrders(FAILED);
      });

    searchListings({ query: "", sort: "newest" }, controller.signal)
      .then((all) => {
        const mine = all.filter((l) => l.sellerId === accountId);
        if (!controller.signal.aborted) setListings({ status: "ready", data: mine });
      })
      .catch(() => {
        if (!controller.signal.aborted) setListings(FAILED);
      });

    return () => controller.abort();
  }, [session, token, attempt]);

  // 복사 피드백은 잠깐만 남긴다. 언마운트 후 setState를 막으려 타이머를 정리한다.
  useEffect(() => {
    if (!copied) return;
    const timer = window.setTimeout(() => setCopied(false), 1500);
    return () => window.clearTimeout(timer);
  }, [copied]);

  if (!session) {
    return (
      <Container width="sm">
        <div className="flex flex-col items-start gap-4 pt-12 pb-16">
          <Heading level={1}>내 정보</Heading>
          <Text tone="secondary">로그인이 필요합니다.</Text>
          <LinkButton href="/login">로그인하러 가기</LinkButton>
        </div>
      </Container>
    );
  }

  const accountId = session.accountId;
  const email = me.status === "ready" ? me.data.email : null;

  function handleSignOut() {
    signOut();
    router.push("/");
  }

  async function copyAccountId() {
    try {
      await navigator.clipboard.writeText(accountId);
      setCopied(true);
    } catch {
      // 클립보드를 못 쓰는 환경(비보안 컨텍스트 등)에서는 조용히 넘긴다 — 값은 화면에 이미 있다.
    }
  }

  function tabClass(t: Tab) {
    return `flex-1 border-b-2 py-2.5 text-sm font-semibold transition-colors ${
      tab === t
        ? "border-brand-600 text-brand-700"
        : "border-transparent text-neutral-500 hover:border-neutral-300 hover:text-neutral-800"
    }`;
  }

  return (
    <Container width="sm">
      <div className="flex flex-col gap-6 pt-10 pb-16">
        {/* 아바타 + 기본 */}
        <div className="flex items-center gap-4">
          <div className="grid h-14 w-14 shrink-0 place-items-center rounded-full bg-brand-50 text-xl font-extrabold text-brand-700">
            {/* 이메일 첫 글자가 사람이 알아보는 유일한 단서다. UUID 첫 글자는 의미가 없다. */}
            {(email ?? accountId).slice(0, 1).toUpperCase()}
          </div>
          <div className="flex min-w-0 flex-col gap-1.5">
            {me.status === "loading" ? (
              <Skeleton className="h-7 w-52 rounded-md" />
            ) : (
              <Heading level={2} className="truncate">
                {email ?? shortenId(accountId)}
              </Heading>
            )}
            <Badge tone={session.role === "ADMIN" ? "brand" : "neutral"}>
              {session.role === "ADMIN" ? "관리자" : "일반 회원"}
            </Badge>
          </div>
        </div>

        {/* 탭 */}
        <div className="grid grid-cols-3 border-b border-neutral-200">
          {(["info", "orders", "listings"] as Tab[]).map((t) => (
            <button key={t} type="button" className={tabClass(t)} onClick={() => setTab(t)}>
              {TAB_LABEL[t]}
            </button>
          ))}
        </div>

        {/* 내 정보 */}
        {tab === "info" && (
          <div className="flex flex-col gap-4">
            <Card padded className="flex flex-col divide-y divide-neutral-100">
              <InfoRow label="이메일">
                {me.status === "loading" ? (
                  <Skeleton className="h-5 w-48 rounded" />
                ) : me.status === "failed" ? (
                  <InlineFailure onRetry={reload} />
                ) : (
                  <p className="text-neutral-900">{me.data.email}</p>
                )}
              </InfoRow>

              <InfoRow label="계정 ID">
                <div className="flex flex-wrap items-center gap-2">
                  <p className="font-mono text-sm text-neutral-700" title={accountId}>
                    {shortenId(accountId)}
                  </p>
                  <Button variant="ghost" size="sm" onClick={copyAccountId}>
                    {copied ? "복사됨" : "복사"}
                  </Button>
                </div>
              </InfoRow>
            </Card>

            {/* 헤더에도 로그아웃이 있다. 여기서는 눈에 덜 띄게 두고 오른쪽으로 뺀다. */}
            <div className="flex justify-end">
              <Button variant="ghost" size="sm" onClick={handleSignOut}>
                로그아웃
              </Button>
            </div>
          </div>
        )}

        {/* 구매 내역 */}
        {tab === "orders" && (
          <div className="flex flex-col gap-3">
            {orders.status === "loading" ? (
              [1, 2, 3].map((i) => <Skeleton key={i} className="h-16 w-full rounded-lg" />)
            ) : orders.status === "failed" ? (
              <PanelFailure onRetry={reload} />
            ) : orders.data.length === 0 ? (
              <PanelEmpty
                icon={<ShoppingBagIcon className="h-8 w-8 text-neutral-400" strokeWidth={1.5} />}
                message="구매 내역이 없어요"
              />
            ) : (
              orders.data.map((o) => (
                <Link
                  key={o.id}
                  href={`/orders/${o.id}`}
                  className="flex items-center justify-between rounded-lg border border-neutral-200 bg-white px-4 py-3.5 hover:bg-neutral-50"
                >
                  <div className="flex flex-col gap-0.5">
                    <span className="font-mono text-xs text-neutral-400">{o.id.slice(0, 8)}</span>
                    <span className="text-base font-semibold tabular-nums text-neutral-900">
                      {formatKrw(o.amount)}
                    </span>
                  </div>
                  <Badge
                    tone={
                      o.status === "completed"
                        ? "success"
                        : o.status === "payment_review" || o.status === "refund_pending"
                          ? "warning"
                          : o.status === "refunded" || o.status === "payment_failed"
                            ? "danger"
                            : "brand"
                    }
                  >
                    {orderStatusLabel(o.status)}
                  </Badge>
                </Link>
              ))
            )}
          </div>
        )}

        {/* 내 매물 */}
        {tab === "listings" && (
          <div className="flex flex-col gap-3">
            <div className="flex justify-end">
              <LinkButton href="/sell" size="sm">
                매물 등록
              </LinkButton>
            </div>
            {listings.status === "loading" ? (
              [1, 2, 3].map((i) => <Skeleton key={i} className="h-16 w-full rounded-lg" />)
            ) : listings.status === "failed" ? (
              <PanelFailure onRetry={reload} />
            ) : listings.data.length === 0 ? (
              <PanelEmpty
                icon={<PackageIcon className="h-8 w-8 text-neutral-400" strokeWidth={1.5} />}
                message="등록한 매물이 없어요"
                action={
                  <LinkButton href="/sell" size="sm">
                    첫 매물 등록하기
                  </LinkButton>
                }
              />
            ) : (
              listings.data.map((l) => (
                <Link
                  key={l.id}
                  href={`/listings/${l.id}`}
                  className="flex items-center justify-between rounded-lg border border-neutral-200 bg-white px-4 py-3.5 hover:bg-neutral-50"
                >
                  <div className="flex min-w-0 flex-col gap-0.5">
                    <span className="truncate font-medium text-neutral-900">{l.title}</span>
                    <span className="text-sm tabular-nums text-neutral-500">
                      {formatKrw(l.price)}
                    </span>
                  </div>
                  <Badge
                    tone={
                      l.status === "active"
                        ? "success"
                        : l.status === "reserved"
                          ? "warning"
                          : l.status === "sold"
                            ? "neutral"
                            : "danger"
                    }
                  >
                    {l.status === "active"
                      ? "판매중"
                      : l.status === "reserved"
                        ? "예약중"
                        : l.status === "sold"
                          ? "판매완료"
                          : "삭제됨"}
                  </Badge>
                </Link>
              ))
            )}
          </div>
        )}
      </div>
    </Container>
  );
}

function InfoRow({ label, children }: { readonly label: string; readonly children: ReactNode }) {
  return (
    <div className="flex flex-col gap-1.5 py-3 first:pt-0 last:pb-0">
      <Text tone="muted" size="sm">
        {label}
      </Text>
      {children}
    </div>
  );
}

/** 한 줄짜리 값이 실패했을 때. "불러오는 중"으로 남겨두면 영원히 기다리게 만든다. */
function InlineFailure({ onRetry }: { readonly onRetry: () => void }) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      <Text tone="secondary" size="sm">
        불러오지 못했어요
      </Text>
      <Button variant="ghost" size="sm" onClick={onRetry}>
        다시 시도
      </Button>
    </div>
  );
}

/** 목록 패널이 실패했을 때. 빈 목록과 구분해서 보여준다. */
function PanelFailure({ onRetry }: { readonly onRetry: () => void }) {
  return (
    <div className="flex flex-col items-center gap-2 rounded-lg border border-dashed border-neutral-300 py-16 text-center">
      <AlertCircleIcon className="h-8 w-8 text-neutral-400" strokeWidth={1.5} />
      <Text tone="secondary" weight="medium">
        불러오지 못했어요
      </Text>
      <Button variant="ghost" size="sm" onClick={onRetry}>
        다시 시도
      </Button>
    </div>
  );
}

function PanelEmpty({
  icon,
  message,
  action,
}: {
  readonly icon: ReactNode;
  readonly message: string;
  readonly action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center gap-2 rounded-lg border border-dashed border-neutral-300 py-16 text-center">
      {icon}
      <Text tone="secondary" weight="medium">
        {message}
      </Text>
      {action}
    </div>
  );
}
