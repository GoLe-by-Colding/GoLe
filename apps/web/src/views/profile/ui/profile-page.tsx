"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { fetchMyOrders, orderStatusLabel, type Order } from "@entities/order";
import { searchListings, type Listing } from "@entities/listing";
import { fetchMe, useSession, type Me } from "@entities/user";
import { formatKrw } from "@shared/lib";
import {
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

export function ProfilePage() {
  const router = useRouter();
  const { session, signOut } = useSession();
  const token = session?.sessionToken ?? null;
  const [tab, setTab] = useState<Tab>("info");
  const [me, setMe] = useState<Me | null>(null);
  const [orders, setOrders] = useState<readonly Order[] | null>(null);
  const [listings, setListings] = useState<readonly Listing[] | null>(null);
  const [error, setError] = useState<string | undefined>(undefined);

  useEffect(() => {
    if (!session || !token) return;
    const controller = new AbortController();

    fetchMe(token)
      .then((r) => {
        if (!controller.signal.aborted) setMe(r);
      })
      .catch(() => {
        if (!controller.signal.aborted) setError("내 정보를 불러오지 못했습니다.");
      });

    fetchMyOrders(session.accountId, controller.signal)
      .then((r) => {
        if (!controller.signal.aborted) setOrders(r);
      })
      .catch(() => {
        if (!controller.signal.aborted) setOrders([]);
      });

    searchListings({ query: "", sort: "newest" }, controller.signal)
      .then((all) => {
        const mine = all.filter((l) => l.sellerId === session.accountId);
        if (!controller.signal.aborted) setListings(mine);
      })
      .catch(() => {
        if (!controller.signal.aborted) setListings([]);
      });

    return () => controller.abort();
  }, [session, token]);

  function handleSignOut() {
    signOut();
    router.push("/");
  }

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
          <div className="grid h-14 w-14 place-items-center rounded-full bg-brand-50 text-xl font-extrabold text-brand-700">
            {session.accountId.slice(0, 1).toUpperCase()}
          </div>
          <div>
            <Heading level={2}>{me?.email ?? session.accountId.slice(0, 12)}</Heading>
            <Badge tone={session.role === "ADMIN" ? "brand" : "neutral"} className="mt-1">
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

        {error ? (
          <p className="rounded-md bg-danger-soft p-3 text-sm text-danger" role="alert">
            {error}
          </p>
        ) : null}

        {/* 내 정보 */}
        {tab === "info" && (
          <Card padded className="flex flex-col gap-4">
            <InfoRow label="이메일" value={me?.email ?? "불러오는 중..."} />
            <InfoRow label="계정 ID" value={session.accountId} mono />
            <div className="border-t border-neutral-100 pt-3">
              <Button variant="secondary" size="lg" fullWidth onClick={handleSignOut}>
                로그아웃
              </Button>
            </div>
          </Card>
        )}

        {/* 구매 내역 */}
        {tab === "orders" && (
          <div className="flex flex-col gap-3">
            {orders === null ? (
              [1, 2, 3].map((i) => <Skeleton key={i} className="h-16 w-full rounded-lg" />)
            ) : orders.length === 0 ? (
              <div className="flex flex-col items-center gap-2 rounded-lg border border-dashed border-neutral-300 py-16 text-center">
                <ShoppingBagIcon className="h-8 w-8 text-neutral-400" strokeWidth={1.5} />
                <Text tone="secondary" weight="medium">
                  구매 내역이 없어요
                </Text>
              </div>
            ) : (
              orders.map((o) => (
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
            {listings === null ? (
              [1, 2, 3].map((i) => <Skeleton key={i} className="h-16 w-full rounded-lg" />)
            ) : listings.length === 0 ? (
              <div className="flex flex-col items-center gap-2 rounded-lg border border-dashed border-neutral-300 py-16 text-center">
                <PackageIcon className="h-8 w-8 text-neutral-400" strokeWidth={1.5} />
                <Text tone="secondary" weight="medium">
                  등록한 매물이 없어요
                </Text>
                <LinkButton href="/sell" size="sm">
                  첫 매물 등록하기
                </LinkButton>
              </div>
            ) : (
              listings.map((l) => (
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

function InfoRow({
  label,
  value,
  mono = false,
}: {
  readonly label: string;
  readonly value: string;
  readonly mono?: boolean;
}) {
  return (
    <div className="flex flex-col gap-1">
      <Text tone="muted" size="sm">
        {label}
      </Text>
      <p className={mono ? "font-mono text-sm text-neutral-700" : "text-neutral-900"}>{value}</p>
    </div>
  );
}
