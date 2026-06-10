"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { fetchMyRooms, type ChatRoom } from "@entities/chat";
import { useSession } from "@entities/user";
import { Container, Heading, LinkButton, Skeleton, Text } from "@shared/ui";

export function ChatListPage() {
  const { session } = useSession();
  const [rooms, setRooms] = useState<readonly ChatRoom[] | null>(null);

  useEffect(() => {
    if (!session) return;
    fetchMyRooms(session.accountId)
      .then(setRooms)
      .catch(() => setRooms([]));
  }, [session]);

  if (!session) {
    return (
      <Container width="sm">
        <div className="flex flex-col items-start gap-4 pt-12 pb-16">
          <Heading level={1}>채팅</Heading>
          <Text tone="secondary">로그인이 필요합니다.</Text>
          <LinkButton href="/login">로그인하러 가기</LinkButton>
        </div>
      </Container>
    );
  }

  return (
    <Container width="sm">
      <div className="flex flex-col gap-5 pt-10 pb-16">
        <Heading level={1}>채팅</Heading>

        {rooms === null ? (
          <div className="flex flex-col gap-3">
            {Array.from({ length: 4 }).map((_, i) => (
              <div
                key={i}
                className="flex items-center gap-3 rounded-2xl border border-neutral-100 p-4"
              >
                <Skeleton className="h-10 w-10 rounded-xl" />
                <div className="flex flex-1 flex-col gap-1.5">
                  <Skeleton className="h-4 w-1/2" />
                  <Skeleton className="h-3 w-1/3" />
                </div>
              </div>
            ))}
          </div>
        ) : rooms.length === 0 ? (
          <div className="flex flex-col items-center gap-3 rounded-2xl border border-dashed border-neutral-200 py-16 text-center">
            <span aria-hidden="true" className="text-3xl">
              💬
            </span>
            <Text tone="secondary" weight="medium">
              아직 채팅이 없어요
            </Text>
            <Text tone="muted" size="sm">
              상품 상세에서 채팅하기로 대화를 시작해보세요
            </Text>
          </div>
        ) : (
          <ul className="flex flex-col divide-y divide-neutral-100 overflow-hidden rounded-2xl border border-neutral-200/60 bg-white">
            {rooms.map((r) => {
              const isBuyer = r.buyerId === session.accountId;
              const partnerId = isBuyer ? r.sellerId : r.buyerId;
              const role = isBuyer ? "구매자" : "판매자";
              return (
                <li key={r.id}>
                  <Link
                    href={`/listings/${r.listingId}`}
                    className="flex items-center gap-3 px-4 py-3.5 hover:bg-neutral-50"
                  >
                    <div className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-brand-50 text-sm font-bold text-brand-700">
                      {partnerId.slice(0, 1).toUpperCase()}
                    </div>
                    <div className="flex min-w-0 flex-col gap-0.5">
                      <span className="truncate text-sm font-semibold text-neutral-900">
                        {partnerId.slice(0, 8)}
                      </span>
                      <span className="text-xs text-neutral-400">
                        {role} · 매물 {r.listingId.slice(0, 8)}
                      </span>
                    </div>
                    <span className="ml-auto text-xs text-neutral-400">
                      {new Date(r.createdAt).toLocaleDateString("ko-KR")}
                    </span>
                  </Link>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </Container>
  );
}
