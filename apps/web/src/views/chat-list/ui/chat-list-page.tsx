"use client";

import { type FormEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import {
  createDirectRoom,
  createGroupRoom,
  createSupportRoom,
  blockChatUser,
  cancelDirectTradeConfirmation,
  confirmDirectTrade,
  fetchChatRoom,
  fetchBlockedChatUserIds,
  fetchMyRooms,
  fetchMySocialRooms,
  fetchUnreadCounts,
  inviteGroupMember,
  leaveGroupRoom,
  unblockChatUser,
  type ChatRoom,
  type SocialChatRoom,
  type SupportCategory,
  type ChatUnreadCounts,
} from "@entities/chat";
import { ApiError } from "@shared/api";
import { fetchLaunchConfig } from "@entities/launch";
import { useSession } from "@entities/user";
import { DirectTradeConfirmation } from "@features/chat-listing";
import { ChatPanel } from "@widgets/chat-panel";
import {
  Badge,
  Button,
  Container,
  EmptyState,
  Heading,
  LinkButton,
  MessageCircleIcon,
  Select,
  Skeleton,
  Text,
} from "@shared/ui";

type Conversation =
  | { readonly kind: "LISTING"; readonly room: ChatRoom }
  | { readonly kind: "SOCIAL"; readonly room: SocialChatRoom };

type ComposerMode = "DIRECT" | "GROUP" | "SUPPORT";

const SUPPORT_CATEGORY_LABEL: Record<SupportCategory, string> = {
  GENERAL: "일반 이용 문의",
  TRADE: "거래·직거래 문의",
  PAYMENT: "결제·환불 문의",
  PRODUCT_FEEDBACK: "기능 제안·불편 신고",
  PRIVACY_ACCESS: "개인정보 열람 요청",
  PRIVACY_CORRECTION_DELETION: "개인정보 정정·삭제 요청",
  PRIVACY_PROCESSING_STOP: "개인정보 처리정지·동의 철회",
};

const SUPPORT_CATEGORIES = Object.keys(SUPPORT_CATEGORY_LABEL) as SupportCategory[];

interface RoomResolveIssue {
  readonly message: string;
  readonly action: "LOGIN" | "RETRY" | null;
}

const MAX_ROOM_RESOLVE_RETRIES = 3;

export function ChatListPage() {
  const { session } = useSession();
  const sessionAccountId = session?.accountId ?? null;
  const router = useRouter();
  const searchParams = useSearchParams();
  const searchParamsString = searchParams.toString();
  const requestedPeerId = searchParams.get("direct")?.trim() ?? "";
  const requestedComposer = searchParams.get("compose")?.trim().toLowerCase() ?? "";
  const requestedSupportCategory = supportCategory(searchParams.get("category"));
  const requestedRoomId = searchParams.get("room")?.trim() ?? "";
  const [listingRooms, setListingRooms] = useState<readonly ChatRoom[] | null>(null);
  const [socialRooms, setSocialRooms] = useState<readonly SocialChatRoom[] | null>(null);
  const [roomsOwnerId, setRoomsOwnerId] = useState<string | null>(null);
  const [resolvedConversation, setResolvedConversation] = useState<Conversation | null>(null);
  const [resolvedConversationOwnerId, setResolvedConversationOwnerId] = useState<string | null>(
    null,
  );
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [composer, setComposer] = useState<ComposerMode | null>(null);
  const [inviteOpen, setInviteOpen] = useState(false);
  const [inviteAccountId, setInviteAccountId] = useState("");
  const [roomActionError, setRoomActionError] = useState<string | undefined>();
  const [loadWarning, setLoadWarning] = useState<string | undefined>();
  const [roomResolveIssue, setRoomResolveIssue] = useState<RoomResolveIssue | null>(null);
  const [roomResolveRetryNonce, setRoomResolveRetryNonce] = useState(0);
  const [tradeBusy, setTradeBusy] = useState(false);
  const [directTradeOpen, setDirectTradeOpen] = useState(false);
  const [blockedAccountIds, setBlockedAccountIds] = useState<readonly string[] | null>(null);
  const [unreadCounts, setUnreadCounts] = useState<ChatUnreadCounts | null>(null);
  const [unreadOwnerId, setUnreadOwnerId] = useState<string | null>(null);
  const [blockStateWarning, setBlockStateWarning] = useState<string | undefined>();
  const [blockRetryBusy, setBlockRetryBusy] = useState(false);
  const [blockManageRoomId, setBlockManageRoomId] = useState<string | null>(null);
  const [blockManageTargetId, setBlockManageTargetId] = useState<string | null>(null);
  const [blockReason, setBlockReason] = useState("");
  const [blockActionBusy, setBlockActionBusy] = useState(false);
  const listingRoomsRef = useRef<readonly ChatRoom[] | null>(null);
  const socialRoomsRef = useRef<readonly SocialChatRoom[] | null>(null);
  const resolvedConversationRef = useRef<Conversation | null>(null);
  const resolvedConversationOwnerIdRef = useRef<string | null>(null);
  const blockedAccountIdsRef = useRef<readonly string[] | null>(null);
  const unreadMutationVersionRef = useRef(0);
  const sessionAccountIdRef = useRef(sessionAccountId);
  const handledRoomRequestRef = useRef<string | null>(null);
  const suppressDefaultSelectionRef = useRef(requestedRoomId.length > 0);

  useEffect(() => {
    sessionAccountIdRef.current = sessionAccountId;
  }, [sessionAccountId]);

  useEffect(() => {
    if (requestedRoomId.length > 0) suppressDefaultSelectionRef.current = true;
  }, [requestedRoomId]);

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
      resolvedConversationRef.current = null;
      resolvedConversationOwnerIdRef.current = null;
      blockedAccountIdsRef.current = null;
      unreadMutationVersionRef.current = 0;
      return;
    }

    let active = true;
    let pollingTimer: number | undefined;
    let requestRunning = false;

    // 계정이 바뀌는 순간부터 이전 계정의 스냅샷을 새 요청의 폴백으로도 사용하지 않는다.
    listingRoomsRef.current = null;
    socialRoomsRef.current = null;
    resolvedConversationRef.current = null;
    resolvedConversationOwnerIdRef.current = null;
    blockedAccountIdsRef.current = null;
    unreadMutationVersionRef.current = 0;

    const loadRooms = async () => {
      if (!active || requestRunning || document.visibilityState !== "visible") return;
      requestRunning = true;
      const unreadVersionAtStart = unreadMutationVersionRef.current;
      const resolvedAtStart =
        resolvedConversationOwnerIdRef.current === sessionAccountId
          ? resolvedConversationRef.current
          : null;
      try {
        const [
          listingResult,
          socialResult,
          launchResult,
          blockedResult,
          unreadResult,
          resolvedResult,
        ] = await Promise.allSettled([
          fetchMyRooms(),
          fetchMySocialRooms(),
          fetchLaunchConfig(),
          fetchBlockedChatUserIds(),
          fetchUnreadCounts(),
          resolvedAtStart === null ? Promise.resolve(null) : fetchChatRoom(resolvedAtStart.room.id),
        ]);
        if (!active) return;

        const nextListing =
          listingResult.status === "fulfilled"
            ? listingResult.value
            : (listingRoomsRef.current ?? []);
        const nextSocial =
          socialResult.status === "fulfilled" ? socialResult.value : (socialRoomsRef.current ?? []);
        const nextBlocked =
          blockedResult.status === "fulfilled" ? blockedResult.value : blockedAccountIdsRef.current;

        listingRoomsRef.current = nextListing;
        socialRoomsRef.current = nextSocial;
        blockedAccountIdsRef.current = nextBlocked;
        setListingRooms(nextListing);
        setSocialRooms(nextSocial);
        setBlockedAccountIds(nextBlocked);
        if (
          unreadResult.status === "fulfilled" &&
          unreadMutationVersionRef.current === unreadVersionAtStart
        ) {
          setUnreadCounts(unreadResult.value);
          setUnreadOwnerId(sessionAccountId);
        }
        setBlockStateWarning(
          blockedResult.status === "rejected"
            ? nextBlocked === null
              ? "차단 정보를 확인하지 못해 대화를 잠시 잠갔습니다."
              : "차단 정보 갱신이 지연되고 있어 마지막 확인 상태를 유지합니다."
            : undefined,
        );
        setRoomsOwnerId(sessionAccountId);
        setLoadWarning(
          listingResult.status === "rejected" ||
            socialResult.status === "rejected" ||
            unreadResult.status === "rejected"
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
        let currentResolved =
          resolvedConversationOwnerIdRef.current === sessionAccountId
            ? resolvedConversationRef.current
            : null;
        if (resolvedResult.status === "fulfilled" && resolvedResult.value !== null) {
          currentResolved =
            resolvedResult.value.kind === "LISTING"
              ? { kind: "LISTING", room: resolvedResult.value.listingRoom }
              : { kind: "SOCIAL", room: resolvedResult.value.socialRoom };
          resolvedConversationRef.current = currentResolved;
          setResolvedConversation(currentResolved);
        } else if (
          resolvedResult.status === "rejected" &&
          resolvedResult.reason instanceof ApiError &&
          (resolvedResult.reason.status === 403 || resolvedResult.reason.status === 404)
        ) {
          currentResolved = null;
          resolvedConversationRef.current = null;
          resolvedConversationOwnerIdRef.current = null;
          setResolvedConversation(null);
          setResolvedConversationOwnerId(null);
        }
        if (currentResolved !== null) availableIds.add(currentResolved.room.id);
        const first = [
          ...nextSocial.map((room) => ({ kind: "SOCIAL" as const, room })),
          ...nextListing.map((room) => ({ kind: "LISTING" as const, room })),
          ...(currentResolved === null ? [] : [currentResolved]),
        ].toSorted(
          (left, right) =>
            new Date(activityAt(right)).getTime() - new Date(activityAt(left)).getTime(),
        )[0];
        setSelectedId((current) => {
          if (current !== null && availableIds.has(current)) return current;
          if (suppressDefaultSelectionRef.current) return null;
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
  const visibleUnreadCounts = unreadOwnerId === sessionAccountId ? unreadCounts : null;
  const visibleResolvedConversation =
    resolvedConversationOwnerId === sessionAccountId ? resolvedConversation : null;
  const loading = visibleListingRooms === null || visibleSocialRooms === null;

  const conversations = useMemo<readonly Conversation[]>(() => {
    const rows: Conversation[] = [
      ...(visibleSocialRooms ?? []).map((room) => ({ kind: "SOCIAL" as const, room })),
      ...(visibleListingRooms ?? []).map((room) => ({ kind: "LISTING" as const, room })),
    ];
    if (
      visibleResolvedConversation !== null &&
      !rows.some((conversation) => conversation.room.id === visibleResolvedConversation.room.id)
    ) {
      rows.push(visibleResolvedConversation);
    }
    return rows.toSorted(
      (a, b) => new Date(activityAt(b)).getTime() - new Date(activityAt(a)).getTime(),
    );
  }, [visibleListingRooms, visibleResolvedConversation, visibleSocialRooms]);

  const selected = useMemo(
    () => conversations.find((conversation) => conversation.room.id === selectedId) ?? null,
    [conversations, selectedId],
  );

  useEffect(() => {
    if (requestedRoomId.length === 0) {
      handledRoomRequestRef.current = null;
      return;
    }
    if (sessionAccountId === null) return;

    const requestKey = `${sessionAccountId}:${requestedRoomId}`;
    if (handledRoomRequestRef.current === requestKey) return;
    let active = true;
    let retryTimer: number | undefined;
    let automaticRetryCount = 0;

    const consumeRoomQuery = () => {
      const nextSearchParams = new URLSearchParams(searchParamsString);
      nextSearchParams.delete("room");
      const nextQuery = nextSearchParams.toString();
      router.replace(`/chat${nextQuery.length > 0 ? `?${nextQuery}` : ""}`, { scroll: false });
    };

    const resolveRoom = async () => {
      try {
        const response = await fetchChatRoom(requestedRoomId);
        if (!active) return;
        const conversation: Conversation =
          response.kind === "LISTING"
            ? { kind: "LISTING", room: response.listingRoom }
            : { kind: "SOCIAL", room: response.socialRoom };
        handledRoomRequestRef.current = requestKey;
        suppressDefaultSelectionRef.current = false;
        resolvedConversationRef.current = conversation;
        resolvedConversationOwnerIdRef.current = sessionAccountId;
        setResolvedConversation(conversation);
        setResolvedConversationOwnerId(sessionAccountId);
        setRoomResolveIssue(null);
        setSelectedId(requestedRoomId);
        consumeRoomQuery();
      } catch (failure) {
        if (!active) return;
        if (failure instanceof ApiError && (failure.status === 403 || failure.status === 404)) {
          handledRoomRequestRef.current = requestKey;
          suppressDefaultSelectionRef.current = true;
          resolvedConversationRef.current = null;
          resolvedConversationOwnerIdRef.current = null;
          setResolvedConversation(null);
          setResolvedConversationOwnerId(null);
          setRoomResolveIssue(null);
          setSelectedId(null);
          consumeRoomQuery();
          return;
        }
        if (failure instanceof ApiError && failure.status === 400) {
          handledRoomRequestRef.current = requestKey;
          setRoomResolveIssue({
            message: "알림의 대화 링크 형식이 올바르지 않습니다.",
            action: null,
          });
          consumeRoomQuery();
          return;
        }
        if (failure instanceof ApiError && failure.status === 401) {
          setRoomResolveIssue({
            message: "로그인이 만료되어 알림의 대화방을 열지 못했습니다.",
            action: "LOGIN",
          });
          return;
        }
        if (
          failure instanceof ApiError &&
          failure.status >= 400 &&
          failure.status < 500 &&
          failure.status !== 429
        ) {
          setRoomResolveIssue({
            message: "현재 상태에서는 알림의 대화방을 열 수 없습니다.",
            action: null,
          });
          return;
        }

        if (automaticRetryCount >= MAX_ROOM_RESOLVE_RETRIES) {
          setRoomResolveIssue({
            message: "알림의 대화방 연결이 계속 지연되고 있습니다.",
            action: "RETRY",
          });
          return;
        }

        const retryDelayMs =
          failure instanceof ApiError && failure.status === 429
            ? (failure.retryAfterMs ?? 5_000)
            : Math.min(1_000 * 2 ** automaticRetryCount + Math.floor(Math.random() * 250), 30_000);
        automaticRetryCount += 1;
        setRoomResolveIssue({
          message: "알림의 대화방을 확인하지 못했습니다. 연결이 복구되면 자동으로 다시 열게요.",
          action: null,
        });
        retryTimer = window.setTimeout(() => void resolveRoom(), retryDelayMs);
      }
    };

    void resolveRoom();
    return () => {
      active = false;
      if (retryTimer !== undefined) window.clearTimeout(retryTimer);
    };
  }, [requestedRoomId, roomResolveRetryNonce, router, searchParamsString, sessionAccountId]);

  const selectedBlockTarget = selected === null ? null : blockTargetId(selected, sessionAccountId);
  const selectedTargetBlocked =
    selectedBlockTarget !== null && (blockedAccountIds?.includes(selectedBlockTarget) ?? false);
  const blockManageOpen = selectedId !== null && blockManageRoomId === selectedId;
  const managedBlockTarget = blockManageOpen ? blockManageTargetId : null;
  const managedTargetBlocked =
    managedBlockTarget !== null && (blockedAccountIds?.includes(managedBlockTarget) ?? false);

  const handleRoomRead = useCallback((roomId: string) => {
    unreadMutationVersionRef.current += 1;
    setUnreadCounts((current) => {
      if (current === null) return current;
      const next = { ...current, [roomId]: 0 };
      return next;
    });
  }, []);

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
      if (resolvedConversationRef.current?.room.id === roomId) {
        resolvedConversationRef.current = null;
        resolvedConversationOwnerIdRef.current = null;
        setResolvedConversation(null);
        setResolvedConversationOwnerId(null);
      }
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
      if (resolvedConversationRef.current?.room.id === updated.id) {
        const conversation: Conversation = { kind: "SOCIAL", room: updated };
        resolvedConversationRef.current = conversation;
        setResolvedConversation(conversation);
      }
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
      if (resolvedConversationRef.current?.room.id === updated.id) {
        const conversation: Conversation = { kind: "LISTING", room: updated };
        resolvedConversationRef.current = conversation;
        setResolvedConversation(conversation);
      }
    } catch {
      setRoomActionError(
        "거래 완료 상태를 바꾸지 못했습니다. 매물 상태와 공개 단계를 확인해 주세요.",
      );
    } finally {
      setTradeBusy(false);
    }
  }

  async function updateSelectedBlock() {
    if (managedBlockTarget === null || blockedAccountIds === null || blockActionBusy) return;
    setBlockActionBusy(true);
    setRoomActionError(undefined);
    try {
      if (managedTargetBlocked) {
        await unblockChatUser(managedBlockTarget);
      } else {
        await blockChatUser(managedBlockTarget, blockReason.trim() || undefined);
      }
      setBlockedAccountIds((current) => {
        const next = managedTargetBlocked
          ? (current ?? []).filter((accountId) => accountId !== managedBlockTarget)
          : [...new Set([...(current ?? []), managedBlockTarget])];
        blockedAccountIdsRef.current = next;
        return next;
      });
      setBlockReason("");
      setBlockManageRoomId(null);
      setBlockManageTargetId(null);
    } catch {
      setRoomActionError(
        managedTargetBlocked
          ? "차단을 해제하지 못했습니다. 잠시 후 다시 시도해 주세요."
          : "사용자를 차단하지 못했습니다. 잠시 후 다시 시도해 주세요.",
      );
    } finally {
      setBlockActionBusy(false);
    }
  }

  async function retryBlockedAccounts() {
    const accountId = sessionAccountId;
    if (accountId === null || blockRetryBusy) return;
    setBlockRetryBusy(true);
    try {
      const next = await fetchBlockedChatUserIds();
      if (sessionAccountIdRef.current !== accountId) return;
      blockedAccountIdsRef.current = next;
      setBlockedAccountIds(next);
      setBlockStateWarning(undefined);
    } catch {
      if (sessionAccountIdRef.current !== accountId) return;
      setBlockStateWarning("차단 정보를 확인하지 못해 대화를 잠시 잠갔습니다.");
    } finally {
      if (sessionAccountIdRef.current === accountId) setBlockRetryBusy(false);
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
            key={
              composer === "DIRECT"
                ? `${composer}:${requestedPeerId}`
                : composer === "SUPPORT"
                  ? `${composer}:${requestedSupportCategory}`
                  : composer
            }
            mode={composer}
            initialPeerId={composer === "DIRECT" ? requestedPeerId : ""}
            initialSupportCategory={requestedSupportCategory}
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

        {roomResolveIssue && roomsBelongToCurrentAccount ? (
          <div
            role="status"
            className="flex flex-wrap items-center justify-between gap-3 rounded-lg bg-warning-soft px-4 py-2 text-sm text-neutral-700"
          >
            <span>{roomResolveIssue.message}</span>
            {roomResolveIssue.action === "LOGIN" ? (
              <LinkButton
                size="sm"
                variant="secondary"
                href={`/login?returnTo=${encodeURIComponent(`/chat?${searchParamsString}`)}`}
              >
                다시 로그인
              </LinkButton>
            ) : null}
            {roomResolveIssue.action === "RETRY" ? (
              <Button
                size="sm"
                variant="secondary"
                onClick={() => {
                  setRoomResolveIssue(null);
                  setRoomResolveRetryNonce((current) => current + 1);
                }}
              >
                다시 시도
              </Button>
            ) : null}
          </div>
        ) : null}

        {blockStateWarning && roomsBelongToCurrentAccount ? (
          <p
            role="status"
            className="rounded-lg bg-warning-soft px-4 py-2 text-sm text-neutral-700"
          >
            {blockStateWarning}
          </p>
        ) : null}

        {loading ? (
          <LoadingRows />
        ) : conversations.length === 0 ? (
          <EmptyState
            variant="inline"
            icon={<MessageCircleIcon className="h-8 w-8 text-neutral-400" strokeWidth={1.5} />}
            title="아직 대화가 없어요"
            description="판매자와 거래를 상의하거나, 다른 사용자와 모임을 시작해 보세요."
            className="min-h-[440px] bg-neutral-50/50"
            action={
              <div className="flex flex-wrap justify-center gap-2">
                <Button onClick={() => setComposer("DIRECT")}>첫 대화 시작</Button>
                <Button variant="secondary" onClick={() => setComposer("SUPPORT")}>
                  운영팀 문의
                </Button>
              </div>
            }
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
                  const unread = visibleUnreadCounts?.[conversation.room.id] ?? 0;
                  return (
                    <li key={conversation.room.id}>
                      <button
                        type="button"
                        aria-current={active ? "true" : undefined}
                        onClick={() => {
                          suppressDefaultSelectionRef.current = false;
                          setSelectedId(conversation.room.id);
                        }}
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
                            <span
                              className={`truncate text-sm text-neutral-900 ${unread > 0 ? "font-extrabold" : "font-semibold"}`}
                            >
                              {title}
                            </span>
                            <RoomBadge conversation={conversation} />
                          </span>
                          <span className="truncate text-xs text-neutral-500">
                            {conversationSubtitle(conversation, myId)}
                          </span>
                        </span>
                        <span className="flex shrink-0 flex-col items-end gap-1.5">
                          <time className="text-[11px] text-neutral-400">
                            {shortDate(activityAt(conversation))}
                          </time>
                          {unread > 0 ? (
                            <span className="grid min-h-5 min-w-5 place-items-center rounded-full bg-brand-600 px-1.5 text-[10px] font-extrabold tabular-nums text-white">
                              <span aria-hidden="true">{unread > 99 ? "99+" : unread}</span>
                              <span className="sr-only">읽지 않은 메시지 {unread}개</span>
                            </span>
                          ) : null}
                        </span>
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
                    {selectedBlockTarget !== null && blockedAccountIds !== null ? (
                      <button
                        type="button"
                        onClick={() => {
                          setBlockReason("");
                          setBlockManageTargetId(selectedBlockTarget);
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
                {blockManageOpen && managedBlockTarget !== null && blockedAccountIds !== null ? (
                  <div className="gole-rise flex flex-col gap-3 border-b border-danger/15 bg-danger/5 px-4 py-3 sm:px-5">
                    <div>
                      <p className="text-sm font-semibold text-neutral-900">
                        {managedTargetBlocked
                          ? "이 사용자의 차단을 해제할까요?"
                          : "이 사용자를 차단할까요?"}
                      </p>
                      <p className="mt-1 text-xs leading-relaxed text-neutral-600">
                        {managedTargetBlocked
                          ? selected.kind === "SOCIAL" && selected.room.type === "GROUP"
                            ? "해제하면 이 사용자의 새 메시지가 내 화면에 다시 표시됩니다."
                            : "해제하면 서로 다시 메시지를 보낼 수 있습니다."
                          : selected.kind === "SOCIAL" && selected.room.type === "GROUP"
                            ? "그룹 대화는 계속되며 이 사용자의 메시지만 내 화면에서 숨깁니다."
                            : "기존 대화는 보존되지만, 해제하기 전까지 서로 새 메시지를 보낼 수 없습니다."}
                      </p>
                    </div>
                    {!managedTargetBlocked ? (
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
                        onClick={() => {
                          setBlockManageRoomId(null);
                          setBlockManageTargetId(null);
                        }}
                      >
                        취소
                      </Button>
                      <Button
                        size="sm"
                        variant={managedTargetBlocked ? "secondary" : "danger"}
                        disabled={blockActionBusy}
                        onClick={() => void updateSelectedBlock()}
                      >
                        {blockActionBusy
                          ? "처리 중"
                          : managedTargetBlocked
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
                {directTradeOpen && selected.kind === "LISTING" && blockedAccountIds !== null ? (
                  <DirectTradeConfirmation
                    key={selected.room.id}
                    room={selected.room}
                    myId={myId}
                    busy={tradeBusy}
                    onToggle={() => void toggleDirectTradeConfirmation()}
                  />
                ) : null}
                <div className="min-h-0 flex-1">
                  {blockedAccountIds === null ? (
                    <div className="flex h-full min-h-64 flex-col items-center justify-center gap-3 p-6 text-center">
                      <p className="text-sm font-semibold text-neutral-900">
                        차단 정보를 확인하는 동안 대화를 잠시 잠갔어요.
                      </p>
                      <p className="max-w-sm text-xs leading-relaxed text-neutral-500">
                        차단한 사용자의 메시지가 노출되지 않도록 확인이 끝난 뒤 대화를 열어드려요.
                      </p>
                      <Button
                        type="button"
                        size="sm"
                        variant="secondary"
                        disabled={blockRetryBusy}
                        onClick={() => void retryBlockedAccounts()}
                      >
                        {blockRetryBusy ? "확인 중" : "다시 확인"}
                      </Button>
                    </div>
                  ) : (
                    <ChatPanel
                      key={selected.room.id}
                      roomId={selected.room.id}
                      myId={myId}
                      onRoomRead={handleRoomRead}
                      hiddenSenderIds={blockedAccountIds}
                      showSenderIdentity={
                        selected.kind === "SOCIAL" && selected.room.type === "GROUP"
                      }
                      {...(selected.kind === "SOCIAL" && selected.room.type === "GROUP"
                        ? {
                            onManageSender: (senderId: string) => {
                              setBlockReason("");
                              setBlockManageTargetId(senderId);
                              setBlockManageRoomId(selected.room.id);
                            },
                          }
                        : {})}
                      {...(selectedTargetBlocked
                        ? {
                            readOnlyReason:
                              "차단을 해제하면 이 대화에서 다시 메시지를 보낼 수 있습니다.",
                          }
                        : {})}
                    />
                  )}
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

function ConversationComposer({
  mode,
  initialPeerId,
  initialSupportCategory,
  onClose,
  onCreated,
}: {
  readonly mode: ComposerMode;
  readonly initialPeerId: string;
  readonly initialSupportCategory: SupportCategory;
  readonly onClose: () => void;
  readonly onCreated: (room: SocialChatRoom) => void;
}) {
  const [peerId, setPeerId] = useState(initialPeerId);
  const [category, setCategory] = useState(initialSupportCategory);
  const [title, setTitle] = useState(
    mode === "SUPPORT" ? SUPPORT_CATEGORY_LABEL[initialSupportCategory] : "",
  );
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
        onCreated(await createSupportRoom(title.trim(), message.trim(), category));
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
          <label className="flex flex-col gap-1.5 text-sm font-semibold text-neutral-700">
            문의 유형
            <Select
              value={category}
              onChange={(event) => {
                const next = event.target.value as SupportCategory;
                if (title.trim().length === 0 || title === SUPPORT_CATEGORY_LABEL[category]) {
                  setTitle(SUPPORT_CATEGORY_LABEL[next]);
                }
                setCategory(next);
              }}
            >
              {SUPPORT_CATEGORIES.map((value) => (
                <option key={value} value={value}>
                  {SUPPORT_CATEGORY_LABEL[value]}
                </option>
              ))}
            </Select>
          </label>
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
          {category.startsWith("PRIVACY_") ? (
            <p className="rounded-lg border border-brand-100 bg-white px-3 py-2 text-xs leading-5 text-neutral-600">
              로그인된 본인 계정으로 접수되며 운영팀은 10일 안의 첫 처리 안내를 목표로 합니다.
              법령상 보존이 필요한 거래·분쟁 기록은 바로 삭제되지 않을 수 있고, 그 사유를 이
              대화에서 안내합니다.
            </p>
          ) : null}
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
  const category = conversation.room.supportCategory;
  return category === null
    ? supportLabel(conversation.room.supportStatus)
    : `${SUPPORT_CATEGORY_LABEL[category]} · ${supportLabel(conversation.room.supportStatus)}`;
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
  if (conversation.room.type === "SUPPORT") {
    return (
      <Badge tone={conversation.room.supportCategory?.startsWith("PRIVACY_") ? "warning" : "brand"}>
        {conversation.room.supportCategory?.startsWith("PRIVACY_") ? "권리 요청" : "운영팀"}
      </Badge>
    );
  }
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

function supportCategory(value: string | null): SupportCategory {
  return SUPPORT_CATEGORIES.includes(value as SupportCategory)
    ? (value as SupportCategory)
    : "GENERAL";
}
