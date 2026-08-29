"use client";

import { type FormEvent, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import {
  createDirectRoom,
  createGroupRoom,
  createSupportRoom,
  blockChatUser,
  cancelDirectTradeConfirmation,
  confirmDirectTrade,
  fetchBlockedChatUserIds,
  fetchMyRooms,
  fetchMySocialRooms,
  inviteGroupMember,
  leaveGroupRoom,
  unblockChatUser,
  type ChatRoom,
  type SocialChatRoom,
} from "@entities/chat";
import { fetchLaunchConfig } from "@entities/launch";
import { useSession } from "@entities/user";
import { ChatPanel } from "@widgets/chat-panel";
import {
  Badge,
  Button,
  Container,
  Heading,
  LinkButton,
  MessageCircleIcon,
  Skeleton,
  Text,
} from "@shared/ui";

type Conversation =
  | { readonly kind: "LISTING"; readonly room: ChatRoom }
  | { readonly kind: "SOCIAL"; readonly room: SocialChatRoom };

type ComposerMode = "DIRECT" | "GROUP" | "SUPPORT";

export function ChatListPage() {
  const { session } = useSession();
  const sessionAccountId = session?.accountId ?? null;
  const searchParams = useSearchParams();
  const requestedPeerId = searchParams.get("direct")?.trim() ?? "";
  const requestedComposer = searchParams.get("compose")?.trim().toLowerCase() ?? "";
  const [listingRooms, setListingRooms] = useState<readonly ChatRoom[] | null>(null);
  const [socialRooms, setSocialRooms] = useState<readonly SocialChatRoom[] | null>(null);
  const [roomsOwnerId, setRoomsOwnerId] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [composer, setComposer] = useState<ComposerMode | null>(null);
  const [inviteOpen, setInviteOpen] = useState(false);
  const [inviteAccountId, setInviteAccountId] = useState("");
  const [roomActionError, setRoomActionError] = useState<string | undefined>();
  const [loadWarning, setLoadWarning] = useState<string | undefined>();
  const [tradeBusy, setTradeBusy] = useState(false);
  const [directTradeOpen, setDirectTradeOpen] = useState(false);
  const [blockedAccountIds, setBlockedAccountIds] = useState<readonly string[] | null>(null);
  const [blockManageRoomId, setBlockManageRoomId] = useState<string | null>(null);
  const [blockReason, setBlockReason] = useState("");
  const [blockActionBusy, setBlockActionBusy] = useState(false);
  const listingRoomsRef = useRef<readonly ChatRoom[] | null>(null);
  const socialRoomsRef = useRef<readonly SocialChatRoom[] | null>(null);
  const blockedAccountIdsRef = useRef<readonly string[] | null>(null);

  useEffect(() => {
    if (session === null) return;
    const requested =
      requestedPeerId.length > 0 ? "DIRECT" : requestedComposer === "support" ? "SUPPORT" : null;
    if (requested === null) return;
    const timer = window.setTimeout(() => setComposer(requested), 0);
    return () => window.clearTimeout(timer);
  }, [requestedComposer, requestedPeerId, session]);

  useEffect(() => {
    if (sessionAccountId === null) {
      listingRoomsRef.current = null;
      socialRoomsRef.current = null;
      blockedAccountIdsRef.current = null;
      return;
    }

    let active = true;
    let pollingTimer: number | undefined;
    let requestRunning = false;

    // 계정이 바뀌는 순간부터 이전 계정의 스냅샷을 새 요청의 폴백으로도 사용하지 않는다.
    listingRoomsRef.current = null;
    socialRoomsRef.current = null;
    blockedAccountIdsRef.current = null;

    const loadRooms = async () => {
      if (!active || requestRunning || document.visibilityState !== "visible") return;
      requestRunning = true;
      try {
        const [listingResult, socialResult, launchResult, blockedResult] = await Promise.allSettled(
          [fetchMyRooms(), fetchMySocialRooms(), fetchLaunchConfig(), fetchBlockedChatUserIds()],
        );
        if (!active) return;

        const nextListing =
          listingResult.status === "fulfilled"
            ? listingResult.value
            : (listingRoomsRef.current ?? []);
        const nextSocial =
          socialResult.status === "fulfilled" ? socialResult.value : (socialRoomsRef.current ?? []);
        const nextBlocked =
          blockedResult.status === "fulfilled"
            ? blockedResult.value
            : (blockedAccountIdsRef.current ?? []);

        listingRoomsRef.current = nextListing;
        socialRoomsRef.current = nextSocial;
        blockedAccountIdsRef.current = nextBlocked;
        setListingRooms(nextListing);
        setSocialRooms(nextSocial);
        setBlockedAccountIds(nextBlocked);
        setRoomsOwnerId(sessionAccountId);
        setLoadWarning(
          listingResult.status === "rejected" ||
            socialResult.status === "rejected" ||
            blockedResult.status === "rejected"
            ? "일부 대화를 불러오지 못했습니다. 보이는 대화는 그대로 사용할 수 있어요."
            : undefined,
        );
        setDirectTradeOpen(
          launchResult.status === "fulfilled" && launchResult.value.tradeMode === "DIRECT_CHAT",
        );

        const availableIds = new Set([
          ...nextSocial.map((room) => room.id),
          ...nextListing.map((room) => room.id),
        ]);
        const first = [
          ...nextSocial.map((room) => ({ kind: "SOCIAL" as const, room })),
          ...nextListing.map((room) => ({ kind: "LISTING" as const, room })),
        ].toSorted(
          (left, right) =>
            new Date(activityAt(right)).getTime() - new Date(activityAt(left)).getTime(),
        )[0];
        setSelectedId((current) => {
          if (current !== null && availableIds.has(current)) return current;
          return window.innerWidth >= 768 ? (first?.room.id ?? null) : null;
        });
      } finally {
        requestRunning = false;
      }
    };

    const stopPolling = () => {
      if (pollingTimer === undefined) return;
      window.clearInterval(pollingTimer);
      pollingTimer = undefined;
    };

    const startPolling = () => {
      if (pollingTimer !== undefined || document.visibilityState !== "visible") return;
      void loadRooms();
      pollingTimer = window.setInterval(() => void loadRooms(), 10_000);
    };

    const handleVisibilityChange = () => {
      if (document.visibilityState === "visible") startPolling();
      else stopPolling();
    };

    startPolling();
    document.addEventListener("visibilitychange", handleVisibilityChange);
    return () => {
      active = false;
      stopPolling();
      document.removeEventListener("visibilitychange", handleVisibilityChange);
    };
  }, [sessionAccountId]);

  const roomsBelongToCurrentAccount =
    sessionAccountId !== null && roomsOwnerId === sessionAccountId;
  const visibleListingRooms = roomsBelongToCurrentAccount ? listingRooms : null;
  const visibleSocialRooms = roomsBelongToCurrentAccount ? socialRooms : null;

  const conversations = useMemo<readonly Conversation[]>(() => {
    if (visibleListingRooms === null || visibleSocialRooms === null) return [];
    const rows: Conversation[] = [
      ...visibleSocialRooms.map((room) => ({ kind: "SOCIAL" as const, room })),
      ...visibleListingRooms.map((room) => ({ kind: "LISTING" as const, room })),
    ];
    return rows.toSorted(
      (a, b) => new Date(activityAt(b)).getTime() - new Date(activityAt(a)).getTime(),
    );
  }, [visibleListingRooms, visibleSocialRooms]);

  const selected = useMemo(
    () => conversations.find((conversation) => conversation.room.id === selectedId) ?? null,
    [conversations, selectedId],
  );
  const selectedBlockTarget = selected === null ? null : blockTargetId(selected, sessionAccountId);
  const selectedTargetBlocked =
    selectedBlockTarget !== null && (blockedAccountIds?.includes(selectedBlockTarget) ?? false);
  const blockManageOpen = selectedId !== null && blockManageRoomId === selectedId;

  if (!session) {
    return (
      <Container width="sm">
        <div className="flex min-h-[420px] flex-col items-center justify-center gap-4 py-16 text-center">
          <span className="grid h-14 w-14 place-items-center rounded-2xl bg-brand-50 text-brand-700">
            <MessageCircleIcon className="h-7 w-7" strokeWidth={1.7} />
          </span>
          <Heading level={1}>채팅</Heading>
          <Text tone="secondary">
            로그인하면 판매자·다른 사용자·운영팀과 바로 대화할 수 있어요.
          </Text>
          <LinkButton
            href={`/login?returnTo=${encodeURIComponent(
              `/chat${searchParams.toString() ? `?${searchParams.toString()}` : ""}`,
            )}`}
          >
            로그인하러 가기
          </LinkButton>
        </div>
      </Container>
    );
  }

  const loading = visibleListingRooms === null || visibleSocialRooms === null;
  const myId = session.accountId;

  function addSocialRoom(room: SocialChatRoom) {
    setSocialRooms((current) => {
      const rows = current ?? [];
      const next = [room, ...rows.filter((candidate) => candidate.id !== room.id)];
      socialRoomsRef.current = next;
      return next;
    });
    setSelectedId(room.id);
    setComposer(null);
  }

  async function leaveSelectedGroup() {
    if (selected?.kind !== "SOCIAL" || selected.room.type !== "GROUP") return;
    const roomId = selected.room.id;
    setRoomActionError(undefined);
    try {
      await leaveGroupRoom(roomId);
      setSocialRooms((current) => {
        const next = current?.filter((room) => room.id !== roomId) ?? [];
        socialRoomsRef.current = next;
        return next;
      });
      setSelectedId(null);
    } catch {
      setRoomActionError("그룹에서 나가지 못했습니다. 잠시 후 다시 시도해 주세요.");
    }
  }

  async function inviteSelectedGroup() {
    if (
      selected?.kind !== "SOCIAL" ||
      selected.room.type !== "GROUP" ||
      inviteAccountId.trim().length === 0
    ) {
      return;
    }
    setRoomActionError(undefined);
    try {
      const updated = await inviteGroupMember(selected.room.id, inviteAccountId.trim());
      setSocialRooms((current) => {
        const next = (current ?? []).map((room) => (room.id === updated.id ? updated : room));
        socialRoomsRef.current = next;
        return next;
      });
      setInviteAccountId("");
      setInviteOpen(false);
    } catch {
      setRoomActionError("초대할 수 없는 계정입니다. 계정 ID와 차단 상태를 확인해 주세요.");
    }
  }

  async function toggleDirectTradeConfirmation() {
    if (selected?.kind !== "LISTING" || tradeBusy || selected.room.directTradeCompletedAt !== null)
      return;
    setTradeBusy(true);
    setRoomActionError(undefined);
    try {
      const mine =
        selected.room.buyerId === myId
          ? selected.room.buyerConfirmedAt
          : selected.room.sellerConfirmedAt;
      const updated =
        mine === null
          ? await confirmDirectTrade(selected.room.id)
          : await cancelDirectTradeConfirmation(selected.room.id);
      setListingRooms((current) => {
        const next = (current ?? []).map((room) => (room.id === updated.id ? updated : room));
        listingRoomsRef.current = next;
        return next;
      });
    } catch {
      setRoomActionError(
        "거래 완료 상태를 바꾸지 못했습니다. 매물 상태와 공개 단계를 확인해 주세요.",
      );
    } finally {
      setTradeBusy(false);
    }
  }

  async function updateSelectedBlock() {
    if (selectedBlockTarget === null || blockActionBusy) return;
    setBlockActionBusy(true);
    setRoomActionError(undefined);
    try {
      if (selectedTargetBlocked) {
        await unblockChatUser(selectedBlockTarget);
      } else {
        await blockChatUser(selectedBlockTarget, blockReason.trim() || undefined);
      }
      setBlockedAccountIds((current) => {
        const next = selectedTargetBlocked
          ? (current ?? []).filter((accountId) => accountId !== selectedBlockTarget)
          : [...new Set([...(current ?? []), selectedBlockTarget])];
        blockedAccountIdsRef.current = next;
        return next;
      });
      setBlockReason("");
      setBlockManageRoomId(null);
    } catch {
      setRoomActionError(
        selectedTargetBlocked
          ? "차단을 해제하지 못했습니다. 잠시 후 다시 시도해 주세요."
          : "사용자를 차단하지 못했습니다. 잠시 후 다시 시도해 주세요.",
      );
    } finally {
      setBlockActionBusy(false);
    }
  }

  return (
    <Container width="xl">
      <div className="flex flex-col gap-6 pt-8 pb-16">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div className="flex flex-col gap-1">
            <Heading level={1}>대화</Heading>
            <Text tone="secondary">거래부터 모임, 운영팀 문의까지 한곳에서 이어집니다.</Text>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button variant="secondary" size="sm" onClick={() => setComposer("DIRECT")}>
              1:1 대화
            </Button>
            <Button variant="secondary" size="sm" onClick={() => setComposer("GROUP")}>
              그룹 만들기
            </Button>
            <Button size="sm" onClick={() => setComposer("SUPPORT")}>
              운영팀 문의
            </Button>
          </div>
        </div>

        {composer && roomsBelongToCurrentAccount ? (
          <ConversationComposer
            mode={composer}
            initialPeerId={composer === "DIRECT" ? requestedPeerId : ""}
            onClose={() => setComposer(null)}
            onCreated={addSocialRoom}
          />
        ) : null}

        {loadWarning && roomsBelongToCurrentAccount ? (
          <p
            role="status"
            className="rounded-lg bg-warning-soft px-4 py-2 text-sm text-neutral-700"
          >
            {loadWarning}
          </p>
        ) : null}

        {loading ? (
          <LoadingRows />
        ) : conversations.length === 0 ? (
          <EmptyState
            onStart={() => setComposer("DIRECT")}
            onSupport={() => setComposer("SUPPORT")}
          />
        ) : (
          <div className="grid min-h-[660px] overflow-hidden rounded-2xl border border-neutral-200 bg-white shadow-soft md:grid-cols-[340px_minmax(0,1fr)]">
            <aside
              className={`min-w-0 border-r border-neutral-200 ${selected !== null ? "max-md:hidden" : ""}`}
            >
              <div className="border-b border-neutral-100 px-4 py-3">
                <p className="text-xs font-semibold tracking-wide text-neutral-500">
                  전체 대화 {conversations.length}
                </p>
              </div>
              <ul className="max-h-[612px] divide-y divide-neutral-100 overflow-y-auto">
                {conversations.map((conversation) => {
                  const active = conversation.room.id === selectedId;
                  const title = conversationTitle(conversation, myId);
                  return (
                    <li key={conversation.room.id}>
                      <button
                        type="button"
                        onClick={() => setSelectedId(conversation.room.id)}
                        className={`flex w-full items-center gap-3 px-4 py-4 text-left transition-[background-color,transform] duration-200 motion-safe:active:scale-[0.99] ${
                          active ? "bg-brand-50" : "hover:bg-neutral-50"
                        }`}
                      >
                        <span
                          className={`grid h-11 w-11 shrink-0 place-items-center rounded-2xl text-sm font-extrabold ${roomAvatarTone(conversation)}`}
                        >
                          {roomInitial(conversation, myId)}
                        </span>
                        <span className="flex min-w-0 flex-1 flex-col gap-1">
                          <span className="flex items-center gap-2">
                            <span className="truncate text-sm font-semibold text-neutral-900">
                              {title}
                            </span>
                            <RoomBadge conversation={conversation} />
                          </span>
                          <span className="truncate text-xs text-neutral-500">
                            {conversationSubtitle(conversation, myId)}
                          </span>
                        </span>
                        <time className="shrink-0 text-[11px] text-neutral-400">
                          {shortDate(activityAt(conversation))}
                        </time>
                      </button>
                    </li>
                  );
                })}
              </ul>
            </aside>

            {selected ? (
              <section className="flex min-w-0 flex-col">
                <header className="flex min-h-16 items-center justify-between gap-3 border-b border-neutral-100 px-4 py-3 sm:px-5">
                  <div className="flex min-w-0 items-center gap-3">
                    <button
                      type="button"
                      onClick={() => setSelectedId(null)}
                      className="shrink-0 text-sm font-medium text-neutral-500 hover:text-neutral-900 md:hidden"
                    >
                      목록
                    </button>
                    <div className="min-w-0">
                      <p className="truncate text-sm font-bold text-neutral-900">
                        {conversationTitle(selected, myId)}
                      </p>
                      <p className="truncate text-xs text-neutral-500">
                        {conversationSubtitle(selected, myId)}
                      </p>
                    </div>
                  </div>
                  <div className="flex shrink-0 items-center gap-2">
                    {selected.kind === "LISTING" ? (
                      <Link
                        href={`/listings/${selected.room.listingId}`}
                        className="rounded-md px-2 py-1.5 text-xs font-semibold text-brand-700 hover:bg-brand-50"
                      >
                        매물 보기
                      </Link>
                    ) : null}
                    {selected.kind === "SOCIAL" && selected.room.type === "GROUP" ? (
                      <>
                        <button
                          type="button"
                          onClick={() => setInviteOpen((open) => !open)}
                          className="rounded-md px-2 py-1.5 text-xs font-semibold text-brand-700 hover:bg-brand-50"
                        >
                          멤버 초대
                        </button>
                        <button
                          type="button"
                          onClick={() => void leaveSelectedGroup()}
                          className="rounded-md px-2 py-1.5 text-xs font-medium text-neutral-500 hover:bg-neutral-100 hover:text-danger"
                        >
                          나가기
                        </button>
                      </>
                    ) : null}
                    {selectedBlockTarget !== null ? (
                      <button
                        type="button"
                        onClick={() => {
                          setBlockReason("");
                          setBlockManageRoomId((current) =>
                            current === selected.room.id ? null : selected.room.id,
                          );
                        }}
                        className="rounded-md px-2 py-1.5 text-xs font-medium text-neutral-500 hover:bg-neutral-100 hover:text-danger"
                      >
                        {selectedTargetBlocked ? "차단 해제" : "차단"}
                      </button>
                    ) : null}
                  </div>
                </header>
                {blockManageOpen && selectedBlockTarget !== null ? (
                  <div className="gole-rise flex flex-col gap-3 border-b border-danger/15 bg-danger/5 px-4 py-3 sm:px-5">
                    <div>
                      <p className="text-sm font-semibold text-neutral-900">
                        {selectedTargetBlocked
                          ? "이 사용자의 차단을 해제할까요?"
                          : "이 사용자를 차단할까요?"}
                      </p>
                      <p className="mt-1 text-xs leading-relaxed text-neutral-600">
                        {selectedTargetBlocked
                          ? "해제하면 서로 다시 메시지를 보낼 수 있습니다."
                          : "기존 대화는 보존되지만, 해제하기 전까지 서로 새 메시지를 보낼 수 없습니다."}
                      </p>
                    </div>
                    {!selectedTargetBlocked ? (
                      <input
                        value={blockReason}
                        maxLength={200}
                        onChange={(event) => setBlockReason(event.target.value)}
                        placeholder="차단 사유 (선택)"
                        className="h-10 rounded-md border border-neutral-200 bg-white px-3 text-sm outline-none focus:border-brand-400 focus:ring-2 focus:ring-brand-100"
                      />
                    ) : null}
                    <div className="flex justify-end gap-2">
                      <Button
                        size="sm"
                        variant="ghost"
                        disabled={blockActionBusy}
                        onClick={() => setBlockManageRoomId(null)}
                      >
                        취소
                      </Button>
                      <Button
                        size="sm"
                        variant={selectedTargetBlocked ? "secondary" : "danger"}
                        disabled={blockActionBusy}
                        onClick={() => void updateSelectedBlock()}
                      >
                        {blockActionBusy
                          ? "처리 중"
                          : selectedTargetBlocked
                            ? "차단 해제"
                            : "차단하기"}
                      </Button>
                    </div>
                  </div>
                ) : null}
                {inviteOpen && selected.kind === "SOCIAL" && selected.room.type === "GROUP" ? (
                  <div className="gole-rise flex flex-wrap items-end gap-2 border-b border-brand-100 bg-brand-50/60 px-4 py-3 sm:px-5">
                    <label className="min-w-[220px] flex-1 text-xs font-semibold text-neutral-600">
                      초대할 계정 ID
                      <input
                        value={inviteAccountId}
                        onChange={(event) => setInviteAccountId(event.target.value)}
                        placeholder="계정 ID를 입력하세요"
                        className="mt-1 h-9 w-full rounded-md border border-neutral-200 bg-white px-3 text-sm outline-none focus:border-brand-400"
                      />
                    </label>
                    <Button
                      size="sm"
                      onClick={() => void inviteSelectedGroup()}
                      disabled={inviteAccountId.trim().length === 0}
                    >
                      초대하기
                    </Button>
                  </div>
                ) : null}
                {roomActionError ? (
                  <p
                    role="alert"
                    className="border-b border-danger/20 bg-danger-soft px-5 py-2 text-xs text-danger"
                  >
                    {roomActionError}
                  </p>
                ) : null}
                {directTradeOpen && selected.kind === "LISTING" ? (
                  <DirectTradeConfirmation
                    room={selected.room}
                    myId={myId}
                    busy={tradeBusy}
                    onToggle={() => void toggleDirectTradeConfirmation()}
                  />
                ) : null}
                <div className="min-h-0 flex-1">
                  <ChatPanel
                    key={selected.room.id}
                    roomId={selected.room.id}
                    myId={myId}
                    hiddenSenderIds={blockedAccountIds ?? []}
                    {...(selectedTargetBlocked
                      ? {
                          readOnlyReason:
                            "차단을 해제하면 이 대화에서 다시 메시지를 보낼 수 있습니다.",
                        }
                      : {})}
                  />
                </div>
              </section>
            ) : (
              <div className="hidden place-items-center bg-neutral-50/40 text-center md:grid">
                <div className="flex flex-col items-center gap-3">
                  <span className="grid h-12 w-12 place-items-center rounded-2xl bg-white text-brand-600 shadow-soft">
                    <MessageCircleIcon className="h-6 w-6" strokeWidth={1.7} />
                  </span>
                  <Text tone="secondary" size="sm">
                    대화를 선택하면 여기에서 바로 이어집니다.
                  </Text>
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </Container>
  );
}

function DirectTradeConfirmation({
  room,
  myId,
  busy,
  onToggle,
}: {
  readonly room: ChatRoom;
  readonly myId: string;
  readonly busy: boolean;
  readonly onToggle: () => void;
}) {
  const mine = room.buyerId === myId ? room.buyerConfirmedAt : room.sellerConfirmedAt;
  const other = room.buyerId === myId ? room.sellerConfirmedAt : room.buyerConfirmedAt;
  if (room.directTradeCompletedAt !== null) {
    return (
      <p className="border-b border-success/20 bg-success-soft px-5 py-3 text-sm font-semibold text-success">
        양쪽이 확인해 거래가 완료됐어요.
      </p>
    );
  }
  return (
    <div className="flex items-center justify-between gap-3 border-b border-brand-100 bg-brand-50/50 px-5 py-3">
      <div>
        <p className="text-sm font-semibold text-neutral-900">직거래 완료 확인</p>
        <p className="text-xs text-neutral-500">
          {other === null ? "상대방 확인을 기다리고 있어요" : "상대방이 거래 완료를 확인했어요"}
        </p>
      </div>
      <Button size="sm" variant="secondary" disabled={busy} onClick={onToggle}>
        {busy ? "처리 중" : mine === null ? "거래 완료" : "확인 취소"}
      </Button>
    </div>
  );
}

function ConversationComposer({
  mode,
  initialPeerId,
  onClose,
  onCreated,
}: {
  readonly mode: ComposerMode;
  readonly initialPeerId: string;
  readonly onClose: () => void;
  readonly onCreated: (room: SocialChatRoom) => void;
}) {
  const [peerId, setPeerId] = useState(initialPeerId);
  const [title, setTitle] = useState(mode === "SUPPORT" ? "서비스 이용 문의" : "");
  const [members, setMembers] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | undefined>();

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError(undefined);
    try {
      if (mode === "DIRECT") {
        onCreated(await createDirectRoom(peerId.trim()));
      } else if (mode === "GROUP") {
        const memberIds = members
          .split(/[\s,]+/)
          .map((value) => value.trim())
          .filter(Boolean);
        onCreated(await createGroupRoom(title.trim(), memberIds));
      } else {
        onCreated(await createSupportRoom(title.trim(), message.trim()));
      }
    } catch {
      setError(
        mode === "GROUP"
          ? "그룹은 본인 포함 3명 이상이어야 하며, 대화 가능한 계정만 초대할 수 있습니다."
          : "대화를 만들지 못했습니다. 입력한 계정과 내용을 확인해 주세요.",
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <form
      onSubmit={submit}
      className="gole-rise flex flex-col gap-4 rounded-2xl border border-brand-100 bg-brand-50/50 p-5 sm:p-6"
    >
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="text-xs font-bold tracking-wide text-brand-600">{composerEyebrow(mode)}</p>
          <h2 className="mt-1 text-lg font-extrabold text-neutral-900">{composerTitle(mode)}</h2>
          <p className="mt-1 text-sm text-neutral-600">{composerDescription(mode)}</p>
        </div>
        <button
          type="button"
          onClick={onClose}
          className="text-sm text-neutral-500 hover:text-neutral-900"
        >
          닫기
        </button>
      </div>

      {mode === "DIRECT" ? (
        <LabeledInput
          label="상대 계정 ID"
          value={peerId}
          onChange={setPeerId}
          placeholder="판매자·커뮤니티 작성자의 계정 ID"
        />
      ) : null}
      {mode === "GROUP" ? (
        <div className="grid gap-3 sm:grid-cols-2">
          <LabeledInput
            label="방 이름"
            value={title}
            onChange={setTitle}
            placeholder="예: 테크닉 조립 모임"
          />
          <LabeledInput
            label="초대할 계정 ID 2명 이상"
            value={members}
            onChange={setMembers}
            placeholder="쉼표 또는 공백으로 구분"
          />
        </div>
      ) : null}
      {mode === "SUPPORT" ? (
        <div className="flex flex-col gap-3">
          <LabeledInput
            label="문의 제목"
            value={title}
            onChange={setTitle}
            placeholder="무엇을 도와드릴까요?"
          />
          <label className="flex flex-col gap-1.5 text-sm font-semibold text-neutral-700">
            문의 내용
            <textarea
              required
              maxLength={2000}
              rows={3}
              value={message}
              onChange={(event) => setMessage(event.target.value)}
              placeholder="겪고 있는 상황을 적어 주세요. 운영팀이 이 방에서 이어서 답변합니다."
              className="resize-none rounded-lg border border-neutral-200 bg-white px-3 py-2.5 text-sm font-normal outline-none focus:border-brand-400 focus:ring-2 focus:ring-brand-100"
            />
          </label>
        </div>
      ) : null}

      {error ? (
        <p role="alert" className="text-sm text-danger">
          {error}
        </p>
      ) : null}
      <div className="flex justify-end">
        <Button type="submit" disabled={busy}>
          {busy ? "만드는 중" : mode === "SUPPORT" ? "문의 시작" : "대화 시작"}
        </Button>
      </div>
    </form>
  );
}

function LabeledInput({
  label,
  value,
  onChange,
  placeholder,
}: {
  readonly label: string;
  readonly value: string;
  readonly onChange: (value: string) => void;
  readonly placeholder: string;
}) {
  return (
    <label className="flex flex-col gap-1.5 text-sm font-semibold text-neutral-700">
      {label}
      <input
        required
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        className="h-11 rounded-lg border border-neutral-200 bg-white px-3 text-sm font-normal outline-none focus:border-brand-400 focus:ring-2 focus:ring-brand-100"
      />
    </label>
  );
}

function LoadingRows() {
  return (
    <div className="grid gap-4 md:grid-cols-[340px_minmax(0,1fr)]">
      <div className="flex flex-col gap-3 rounded-2xl border border-neutral-200 p-4">
        {Array.from({ length: 5 }).map((_, index) => (
          <div key={index} className="flex items-center gap-3 py-2">
            <Skeleton circle className="h-11 w-11" />
            <div className="flex flex-1 flex-col gap-2">
              <Skeleton className="h-4 w-1/2" />
              <Skeleton className="h-3 w-2/3" />
            </div>
          </div>
        ))}
      </div>
      <Skeleton className="hidden min-h-[660px] rounded-2xl md:block" />
    </div>
  );
}

function EmptyState({
  onStart,
  onSupport,
}: {
  readonly onStart: () => void;
  readonly onSupport: () => void;
}) {
  return (
    <div className="flex min-h-[440px] flex-col items-center justify-center gap-4 rounded-2xl border border-dashed border-neutral-300 bg-neutral-50/50 px-6 text-center">
      <span className="grid h-14 w-14 place-items-center rounded-2xl bg-white text-brand-600 shadow-soft">
        <MessageCircleIcon className="h-7 w-7" strokeWidth={1.7} />
      </span>
      <div>
        <p className="font-bold text-neutral-900">아직 대화가 없어요</p>
        <p className="mt-1 text-sm text-neutral-500">
          판매자와 거래를 상의하거나, 다른 사용자와 모임을 시작해 보세요.
        </p>
      </div>
      <div className="flex gap-2">
        <Button onClick={onStart}>첫 대화 시작</Button>
        <Button variant="secondary" onClick={onSupport}>
          운영팀 문의
        </Button>
      </div>
    </div>
  );
}

function conversationTitle(conversation: Conversation, myId: string): string {
  if (conversation.kind === "LISTING") {
    return partnerId(conversation.room, myId);
  }
  if (conversation.room.type === "DIRECT") {
    return conversation.room.memberIds.find((member) => member !== myId) ?? "1:1 대화";
  }
  return (
    conversation.room.title ?? (conversation.room.type === "SUPPORT" ? "운영팀 문의" : "그룹 대화")
  );
}

function blockTargetId(conversation: Conversation, myId: string | null): string | null {
  if (myId === null) return null;
  if (conversation.kind === "LISTING") {
    return conversation.room.buyerId === myId
      ? conversation.room.sellerId
      : conversation.room.buyerId;
  }
  if (conversation.room.type !== "DIRECT") return null;
  return conversation.room.memberIds.find((accountId) => accountId !== myId) ?? null;
}

function conversationSubtitle(conversation: Conversation, myId: string): string {
  if (conversation.kind === "LISTING") {
    const role = conversation.room.buyerId === myId ? "구매 문의" : "판매 문의";
    return `${role} · 매물 ${conversation.room.listingId.slice(0, 8)}`;
  }
  if (conversation.room.type === "DIRECT") return "개인 대화";
  if (conversation.room.type === "GROUP")
    return `${conversation.room.memberIds.length}명이 함께하는 대화`;
  return supportLabel(conversation.room.supportStatus);
}

function partnerId(room: ChatRoom, myId: string): string {
  return room.buyerId === myId ? room.sellerId : room.buyerId;
}

function activityAt(conversation: Conversation): string {
  return conversation.room.lastMessageAt;
}

function shortDate(value: string): string {
  const date = new Date(value);
  const now = new Date();
  if (date.toDateString() === now.toDateString()) {
    return date.toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" });
  }
  return date.toLocaleDateString("ko-KR", { month: "numeric", day: "numeric" });
}

function roomInitial(conversation: Conversation, myId: string): string {
  if (conversation.kind === "SOCIAL" && conversation.room.type === "SUPPORT") return "GO";
  return conversationTitle(conversation, myId).slice(0, 1).toUpperCase();
}

function roomAvatarTone(conversation: Conversation): string {
  if (conversation.kind === "LISTING") return "bg-accent-100 text-accent-800";
  if (conversation.room.type === "SUPPORT") return "bg-brand-600 text-white";
  if (conversation.room.type === "GROUP") return "bg-violet-100 text-violet-700";
  return "bg-brand-50 text-brand-700";
}

function RoomBadge({ conversation }: { readonly conversation: Conversation }) {
  if (conversation.kind === "LISTING") return <Badge tone="warning">거래</Badge>;
  if (conversation.room.type === "SUPPORT") return <Badge tone="brand">운영팀</Badge>;
  if (conversation.room.type === "GROUP") return <Badge tone="neutral">그룹</Badge>;
  return null;
}

function supportLabel(status: SocialChatRoom["supportStatus"]): string {
  switch (status) {
    case "UNASSIGNED":
      return "운영팀 확인을 기다리고 있어요";
    case "IN_PROGRESS":
      return "운영팀이 확인 중이에요";
    case "WAITING_USER":
      return "내 답변을 기다리고 있어요";
    case "RESOLVED":
      return "완료된 문의 · 메시지를 보내면 다시 열려요";
    default:
      return "운영팀 문의";
  }
}

function composerEyebrow(mode: ComposerMode): string {
  return mode === "DIRECT" ? "PERSON TO PERSON" : mode === "GROUP" ? "GROUP ROOM" : "GOLE SUPPORT";
}

function composerTitle(mode: ComposerMode): string {
  return mode === "DIRECT"
    ? "누구와 대화할까요?"
    : mode === "GROUP"
      ? "함께 이야기할 방을 만들어요"
      : "운영팀과 바로 이야기해요";
}

function composerDescription(mode: ComposerMode): string {
  if (mode === "DIRECT") return "판매자나 커뮤니티 작성자의 계정 ID로 1:1 대화를 시작합니다.";
  if (mode === "GROUP") return "본인을 포함해 3명 이상이면 그룹 대화를 만들 수 있습니다.";
  return "별도 디스코드로 이동하지 않고 이 대화방에서 답변과 진행 상황을 확인합니다.";
}
