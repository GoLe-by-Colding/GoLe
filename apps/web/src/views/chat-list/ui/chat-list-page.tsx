"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { fetchMyRooms, type ChatRoom } from "@entities/chat";
import { useSession } from "@entities/user";
import { ChatPanel } from "@widgets/chat-panel";
import { Container, Heading, LinkButton, MessageCircleIcon, Skeleton, Text } from "@shared/ui";

export function ChatListPage() {
  const { session } = useSession();
  const [rooms, setRooms] = useState<readonly ChatRoom[] | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  useEffect(() => {
    if (!session) return;
    fetchMyRooms()
      .then((list) => {
        setRooms(list);
        // 데스크톱: 첫 방 자동 선택
        if (list.length > 0 && typeof window !== "undefined" && window.innerWidth >= 768) {
          setSelectedId((cur) => cur ?? list[0]!.id);
        }
      })
      .catch(() => setRooms([]));
  }, [session]);

  const selected = useMemo(
    () => rooms?.find((r) => r.id === selectedId) ?? null,
    [rooms, selectedId],
  );

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

  const myId = session.accountId;

  function panelPropsFor(room: ChatRoom) {
    const isBuyer = room.buyerId === myId;
    return {
      listingId: room.listingId,
      myId,
      otherId: isBuyer ? room.sellerId : room.buyerId,
      isBuyer,
    };
  }

  return (
    <Container width="lg">
      <div className="flex flex-col gap-5 pt-10 pb-16">
        <Heading level={1}>채팅</Heading>

        {rooms === null ? (
          <div className="flex flex-col gap-3">
            {Array.from({ length: 4 }).map((_, i) => (
              <div
                key={i}
                className="flex items-center gap-3 rounded-lg border border-neutral-100 p-4"
              >
                <Skeleton circle className="h-10 w-10" />
                <div className="flex flex-1 flex-col gap-1.5">
                  <Skeleton className="h-4 w-1/2" />
                  <Skeleton className="h-3 w-1/3" />
                </div>
              </div>
            ))}
          </div>
        ) : rooms.length === 0 ? (
          <div className="flex flex-col items-center gap-3 rounded-lg border border-dashed border-neutral-300 py-16 text-center">
            <MessageCircleIcon className="h-8 w-8 text-neutral-400" strokeWidth={1.5} />
            <Text tone="secondary" weight="medium">
              아직 채팅이 없어요
            </Text>
            <Text tone="muted" size="sm">
              상품 상세에서 채팅하기로 대화를 시작해보세요
            </Text>
          </div>
        ) : (
          <div className="grid gap-4 md:grid-cols-[320px_1fr] md:items-start">
            {/* 방 목록 — 모바일에서 방 선택 시 숨김 */}
            <ul
              className={`flex flex-col divide-y divide-neutral-100 overflow-hidden rounded-lg border border-neutral-200 bg-white ${
                selected !== null ? "max-md:hidden" : ""
              }`}
            >
              {rooms.map((r) => {
                const isBuyer = r.buyerId === myId;
                const partnerId = isBuyer ? r.sellerId : r.buyerId;
                const role = isBuyer ? "구매자" : "판매자";
                const active = r.id === selectedId;
                return (
                  <li key={r.id}>
                    <button
                      type="button"
                      onClick={() => setSelectedId(r.id)}
                      className={`flex w-full items-center gap-3 px-4 py-3.5 text-left transition-colors hover:bg-neutral-50 ${
                        active ? "bg-brand-50/60" : ""
                      }`}
                    >
                      <div className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-brand-50 text-sm font-bold text-brand-700">
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
                      <span className="ml-auto shrink-0 text-xs text-neutral-400">
                        {new Date(r.createdAt).toLocaleDateString("ko-KR")}
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>

            {/* 대화 — 선택된 방의 ChatPanel */}
            {selected !== null ? (
              <div className="flex h-[640px] flex-col overflow-hidden rounded-lg border border-neutral-200 bg-white max-md:h-[70vh]">
                <div className="flex items-center justify-between gap-2 border-b border-neutral-100 px-4 py-3">
                  <button
                    type="button"
                    onClick={() => setSelectedId(null)}
                    className="text-sm font-medium text-neutral-500 hover:text-neutral-900 md:hidden"
                  >
                    ← 목록
                  </button>
                  <span className="truncate text-sm font-semibold text-neutral-900">
                    {(selected.buyerId === myId ? selected.sellerId : selected.buyerId).slice(0, 8)}{" "}
                    님과의 대화
                  </span>
                  <Link
                    href={`/listings/${selected.listingId}`}
                    className="shrink-0 text-xs font-medium text-brand-600 hover:text-brand-700"
                  >
                    매물 보기 →
                  </Link>
                </div>
                <div className="min-h-0 flex-1">
                  <ChatPanel key={selected.id} {...panelPropsFor(selected)} />
                </div>
              </div>
            ) : (
              <div className="hidden place-items-center rounded-lg border border-dashed border-neutral-300 text-center text-sm text-neutral-400 md:grid md:h-[640px]">
                왼쪽에서 대화를 선택하세요
              </div>
            )}
          </div>
        )}
      </div>
    </Container>
  );
}
