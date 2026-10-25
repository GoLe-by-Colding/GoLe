"use client";

import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import {
  addAdminSupportNote,
  assignAdminSupportTicket,
  fetchAdminSupportMessages,
  fetchAdminSupportNotes,
  fetchAdminSupportTickets,
  fetchAdminSupportNoticeSummary,
  fetchAdminSupportNotices,
  reopenAdminSupportTicket,
  replyAdminSupport,
  resolveAdminSupportTicket,
  takeoverAdminSupportTicket,
  transferAdminSupportTicket,
  type AdminSupportMessage,
  type AdminSupportNote,
  type AdminSupportCategory,
  type AdminSupportPriority,
  type AdminSupportStatus,
  type AdminSupportTicket,
  type AdminSupportNotice,
  type AdminSupportNoticeKind,
  type AdminSupportNoticeFilter,
  type AdminSupportNoticeSummary,
} from "@entities/admin";
import { useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import { cn } from "@shared/lib";
import { Badge, Button, Heading, Input, Select, Text, Textarea } from "@shared/ui";
import { formatDateTime, shortId } from "../model/labels";
import {
  SupportDeadlineBadge,
  SupportNoticePanel,
  SupportNoticeTargets,
} from "./support-notice-panel";

type StatusFilter = "ALL" | AdminSupportStatus;
type CategoryFilter = "ALL" | AdminSupportCategory;
type NoticeKindInput = "GENERAL" | AdminSupportNoticeKind;
type SupportDraft = {
  accountId: string;
  reply: string;
  noticeKind: NoticeKindInput;
  note: string;
};

const CATEGORY_LABEL: Record<AdminSupportCategory, string> = {
  GENERAL: "일반",
  TRADE: "거래",
  PAYMENT: "결제·환불",
  PRODUCT_FEEDBACK: "제품 피드백",
  PRIVACY_ACCESS: "개인정보 열람",
  PRIVACY_CORRECTION_DELETION: "개인정보 정정·삭제",
  PRIVACY_PROCESSING_STOP: "개인정보 처리정지",
};

const STATUS_LABEL: Record<AdminSupportStatus, string> = {
  UNASSIGNED: "미배정",
  IN_PROGRESS: "답변 중",
  WAITING_USER: "사용자 답변 대기",
  RESOLVED: "해결",
};

const STATUS_TONE: Record<AdminSupportStatus, "neutral" | "brand" | "warning" | "success"> = {
  UNASSIGNED: "warning",
  IN_PROGRESS: "brand",
  WAITING_USER: "neutral",
  RESOLVED: "success",
};

const PRIORITY_LABEL: Record<AdminSupportPriority, string> = {
  LOW: "낮음",
  NORMAL: "보통",
  HIGH: "높음",
  URGENT: "긴급",
};

const PRIORITY_TONE: Record<AdminSupportPriority, "neutral" | "brand" | "warning" | "danger"> = {
  LOW: "neutral",
  NORMAL: "brand",
  HIGH: "warning",
  URGENT: "danger",
};

/** 문의 인박스. 운영자는 배정받은 문의의 본문만 읽고 답할 수 있다. */
export function AdminSupportView() {
  const { session } = useSession();
  return (
    <AdminSupportInbox
      key={session?.accountId ?? "signed-out"}
      token={session?.sessionToken ?? null}
      accountId={session?.accountId ?? null}
    />
  );
}

function AdminSupportInbox({
  token,
  accountId,
}: {
  readonly token: string | null;
  readonly accountId: string | null;
}) {
  const [status, setStatus] = useState<StatusFilter>("ALL");
  const [category, setCategory] = useState<CategoryFilter>("ALL");
  const [noticeFilter, setNoticeFilter] = useState<"ALL" | AdminSupportNoticeFilter>("ALL");
  const [filterRevision, setFilterRevision] = useState(0);
  const [noticeSummary, setNoticeSummary] = useState<AdminSupportNoticeSummary | null>(null);
  const [summaryError, setSummaryError] = useState<string | undefined>();
  const [notices, setNotices] = useState<readonly AdminSupportNotice[] | null>(null);
  const [noticesError, setNoticesError] = useState<string | undefined>();
  const [noticeKind, setNoticeKind] = useState<NoticeKindInput>("GENERAL");
  const [tickets, setTickets] = useState<readonly AdminSupportTicket[] | null>(null);
  const [selectedRoomId, setSelectedRoomId] = useState<string | null>(null);
  const [messages, setMessages] = useState<readonly AdminSupportMessage[]>([]);
  const [conversationLoaded, setConversationLoaded] = useState(false);
  const [hasOlderMessages, setHasOlderMessages] = useState(false);
  const [loadingOlderMessages, setLoadingOlderMessages] = useState(false);
  const [notes, setNotes] = useState<readonly AdminSupportNote[]>([]);
  const [reply, setReply] = useState("");
  const [confirmDraftReplacement, setConfirmDraftReplacement] = useState(false);
  const [note, setNote] = useState("");
  const [transferTo, setTransferTo] = useState("");
  const [takeoverReason, setTakeoverReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [sendingReply, setSendingReply] = useState(false);
  const [sentConfirmation, setSentConfirmation] = useState<string | null>(null);
  const [replyFeedback, setReplyFeedback] = useState<{
    kind: "success" | "error";
    text: string;
  } | null>(null);
  const [listError, setListError] = useState<string | undefined>();
  const [conversationError, setConversationError] = useState<string | undefined>();
  const [actionError, setActionError] = useState<string | undefined>();
  const [nowMs, setNowMs] = useState<number | null>(null);
  const selectedRoomRef = useRef<string | null>(null);
  const ticketsGenerationRef = useRef(0);
  const conversationScopeGenerationRef = useRef(0);
  const conversationRequestGenerationRef = useRef(0);
  const actionGenerationRef = useRef(0);
  const ownsSelectedConversationRef = useRef(false);
  const selectionInitializedRef = useRef(false);
  const ownedRoomIdsRef = useRef(new Set<string>());
  // 이 화면 안에서만 보관한다. 권한을 잃은 문의나 다른 계정에는 초안을 복원하지 않는다.
  const draftsRef = useRef(new Map<string, SupportDraft>());
  // 문의를 오가도 저장 중인 답변과 메모를 중복 제출하지 않는다.
  const pendingWritesRef = useRef(new Map<string, "reply" | "note">());
  // 다른 문의를 보는 동안의 거부는 반영하되, 재인수 전의 낡은 응답은 무시한다.
  const roomReadRequestRef = useRef(new Map<string, number>());
  const detailHeadingRef = useRef<HTMLHeadingElement>(null);
  const inboxHeadingRef = useRef<HTMLDivElement>(null);
  const inboxButtonsRef = useRef(new Map<string, HTMLButtonElement>());
  const pendingFocusRef = useRef<{ target: "detail" | "list"; roomId: string | null } | null>(null);

  const invalidateConversation = useCallback(() => {
    conversationScopeGenerationRef.current += 1;
    conversationRequestGenerationRef.current += 1;
    actionGenerationRef.current += 1;
    ownsSelectedConversationRef.current = false;
    setMessages([]);
    setConversationLoaded(false);
    setHasOlderMessages(false);
    setLoadingOlderMessages(false);
    setNotes([]);
    setNotices(null);
    setNoticesError(undefined);
    setNoticeKind("GENERAL");
    setReply("");
    setConfirmDraftReplacement(false);
    setNote("");
    setTransferTo("");
    setTakeoverReason("");
    setBusy(false);
    setSendingReply(false);
    setReplyFeedback(null);
    setConversationError(undefined);
    setActionError(undefined);
  }, []);

  const selectRoom = useCallback(
    (roomId: string | null) => {
      selectionInitializedRef.current = true;
      if (selectedRoomRef.current === roomId) return;
      selectedRoomRef.current = roomId;
      if (roomId !== null) setSentConfirmation(null);
      invalidateConversation();
      ownsSelectedConversationRef.current = roomId !== null && ownedRoomIdsRef.current.has(roomId);
      const draft = roomId === null ? undefined : draftsRef.current.get(roomId);
      if (
        roomId !== null &&
        draft &&
        draft.accountId === accountId &&
        ownedRoomIdsRef.current.has(roomId)
      ) {
        setReply(draft.reply);
        setNoticeKind(draft.noticeKind);
        setNote(draft.note);
      }
      if (roomId !== null && pendingWritesRef.current.has(roomId)) {
        setBusy(true);
        setSendingReply(pendingWritesRef.current.get(roomId) === "reply");
      }
      setSelectedRoomId(roomId);
    },
    [accountId, invalidateConversation],
  );

  function updateDraft(patch: Partial<Omit<SupportDraft, "accountId">>) {
    const roomId = selectedRoomRef.current;
    if (roomId === null || accountId === null) return;
    const cached = draftsRef.current.get(roomId);
    const previous = cached?.accountId === accountId ? cached : undefined;
    const next = { accountId, reply, noticeKind, note, ...previous, ...patch };
    draftsRef.current.set(roomId, next);
    if (patch.reply !== undefined) setReply(patch.reply);
    if (patch.noticeKind !== undefined) setNoticeKind(patch.noticeKind);
    if (patch.note !== undefined) setNote(patch.note);
  }

  function resetInboxForFilter() {
    ++ticketsGenerationRef.current;
    selectRoom(null);
    setTickets(null);
    setListError(undefined);
    setFilterRevision((value) => value + 1);
  }

  const selected = useMemo(
    () => tickets?.find((ticket) => ticket.roomId === selectedRoomId) ?? null,
    [selectedRoomId, tickets],
  );
  const isMine = selected !== null && selected.assigneeId === accountId;

  useEffect(() => {
    const focus = pendingFocusRef.current;
    if (focus?.target === "detail" && focus.roomId === selectedRoomId) {
      detailHeadingRef.current?.focus();
      pendingFocusRef.current = null;
    } else if (focus?.target === "list" && selectedRoomId === null && tickets !== null) {
      const previous =
        focus.roomId === null ? undefined : inboxButtonsRef.current.get(focus.roomId);
      (previous ?? inboxHeadingRef.current)?.focus();
      pendingFocusRef.current = null;
    }
  }, [selectedRoomId, tickets]);

  function openRoom(roomId: string) {
    if (selectedRoomRef.current === roomId) detailHeadingRef.current?.focus();
    else {
      pendingFocusRef.current = { target: "detail", roomId };
      selectRoom(roomId);
    }
  }

  function returnToList() {
    pendingFocusRef.current = { target: "list", roomId: selectedRoomRef.current };
    selectRoom(null);
  }

  useEffect(() => {
    const updateNow = () => setNowMs(Date.now());
    updateNow();
    const refresh = window.setInterval(updateNow, 60_000);
    return () => window.clearInterval(refresh);
  }, []);

  const loadTickets = useCallback(() => {
    if (token === null) return;
    const generation = ++ticketsGenerationRef.current;
    void fetchAdminSupportNoticeSummary(token)
      .then((summary) => {
        if (ticketsGenerationRef.current !== generation) return;
        if (!summary.activeProgress || !summary.activeResult)
          throw new Error("안내 현황을 불러오지 못했습니다.");
        setNoticeSummary(summary);
        setSummaryError(undefined);
      })
      .catch((cause: unknown) => {
        if (ticketsGenerationRef.current !== generation) return;
        setNoticeSummary(null);
        setSummaryError(messageOf(cause, "안내 현황을 불러오지 못했습니다."));
      });
    void fetchAdminSupportTickets(
      token,
      status === "ALL" ? undefined : status,
      category === "ALL" ? undefined : category,
      50,
      noticeFilter === "ALL" ? undefined : noticeFilter,
      noticeFilter === "ALL"
        ? undefined
        : noticeFilter.endsWith("_MISSING")
          ? "RESOLVED"
          : "ACTIVE",
    )
      .then((next) => {
        if (ticketsGenerationRef.current !== generation) return;
        setListError(undefined);
        const current = selectedRoomRef.current;
        const currentTicket = next.find((ticket) => ticket.roomId === current);
        const stillMine = currentTicket?.assigneeId === accountId;
        ownedRoomIdsRef.current = new Set(
          next.filter((row) => row.assigneeId === accountId).map((row) => row.roomId),
        );
        for (const row of next) {
          if (row.assigneeId !== accountId) {
            draftsRef.current.delete(row.roomId);
            roomReadRequestRef.current.delete(row.roomId);
          }
        }
        if (currentTicket !== undefined && ownsSelectedConversationRef.current && !stillMine) {
          if (current !== null) draftsRef.current.delete(current);
          invalidateConversation();
        }
        ownsSelectedConversationRef.current = stillMine;
        setTickets(next);
        selectRoom(
          current !== null && currentTicket !== undefined
            ? current
            : selectionInitializedRef.current
              ? null
              : (next[0]?.roomId ?? null),
        );
      })
      .catch((cause: unknown) => {
        if (ticketsGenerationRef.current !== generation) return;
        setListError(messageOf(cause, "문의 목록을 새로고치지 못했습니다."));
      });
  }, [accountId, category, invalidateConversation, selectRoom, status, token, noticeFilter]);

  const revokeRoomAccess = useCallback(
    (roomId: string) => {
      draftsRef.current.delete(roomId);
      ownedRoomIdsRef.current.delete(roomId);
      roomReadRequestRef.current.delete(roomId);
      if (selectedRoomRef.current === roomId) selectRoom(null);
      setTickets((current) => current?.filter((row) => row.roomId !== roomId) ?? null);
      setActionError(
        "담당자가 변경되어 작성 중인 답변과 메모를 지웠습니다. 문의 목록을 다시 확인해 주세요.",
      );
      setFilterRevision((value) => value + 1);
    },
    [selectRoom],
  );

  const loadConversation = useCallback(() => {
    const scopeGeneration = conversationScopeGenerationRef.current;
    const requestGeneration = ++conversationRequestGenerationRef.current;
    ownsSelectedConversationRef.current = isMine;
    if (token === null || selected === null || !isMine) {
      setMessages([]);
      setNotes([]);
      setNotices(null);
      setNoticesError(undefined);
      return;
    }
    const roomId = selected.roomId;
    roomReadRequestRef.current.set(roomId, requestGeneration);
    setConversationError(undefined);
    const isCurrent = () =>
      conversationScopeGenerationRef.current === scopeGeneration &&
      conversationRequestGenerationRef.current === requestGeneration &&
      selectedRoomRef.current === roomId;
    const guardAccess = <T,>(request: Promise<T>): Promise<T> =>
      request.catch((cause: unknown) => {
        if (
          isOwnershipDenied(cause) &&
          roomReadRequestRef.current.get(roomId) === requestGeneration
        ) {
          revokeRoomAccess(roomId);
        }
        throw cause;
      });
    void guardAccess(fetchAdminSupportNotices(token, roomId))
      .then((nextNotices) => {
        if (!isCurrent()) return;
        setNotices(nextNotices);
        setNoticesError(undefined);
      })
      .catch((cause: unknown) => {
        if (!isCurrent()) return;
        setNoticesError(messageOf(cause, "안내 이력을 불러오지 못했습니다."));
      });
    void Promise.all([
      guardAccess(fetchAdminSupportMessages(token, roomId)),
      guardAccess(fetchAdminSupportNotes(token, roomId)),
    ])
      .then(([nextMessages, nextNotes]) => {
        if (
          conversationScopeGenerationRef.current !== scopeGeneration ||
          conversationRequestGenerationRef.current !== requestGeneration ||
          selectedRoomRef.current !== roomId
        ) {
          return;
        }
        setMessages((current) => mergeRows(current, nextMessages, (row) => row.sentAt));
        setHasOlderMessages((current) => current || nextMessages.length === 60);
        setNotes((current) => mergeRows(current, nextNotes, (row) => row.createdAt));
        setConversationLoaded(true);
        setConversationError(undefined);
      })
      .catch((cause: unknown) => {
        if (
          conversationScopeGenerationRef.current !== scopeGeneration ||
          conversationRequestGenerationRef.current !== requestGeneration ||
          selectedRoomRef.current !== roomId
        ) {
          return;
        }
        setConversationError(messageOf(cause, "문의 대화를 불러오지 못했습니다."));
      });
  }, [isMine, selected, token, revokeRoomAccess]);

  useEffect(() => {
    const timer = window.setTimeout(loadTickets, 0);
    const refresh = window.setInterval(() => {
      if (document.visibilityState === "visible") loadTickets();
    }, 10_000);
    const refreshWhenVisible = () => {
      if (document.visibilityState === "visible") loadTickets();
    };
    document.addEventListener("visibilitychange", refreshWhenVisible);
    return () => {
      window.clearTimeout(timer);
      window.clearInterval(refresh);
      document.removeEventListener("visibilitychange", refreshWhenVisible);
    };
  }, [loadTickets, filterRevision]);

  useEffect(() => {
    const timer = window.setTimeout(loadConversation, 0);
    if (!isMine) return () => window.clearTimeout(timer);
    const refresh = window.setInterval(loadConversation, 5000);
    return () => {
      window.clearTimeout(timer);
      window.clearInterval(refresh);
    };
  }, [isMine, loadConversation]);

  function replaceTicket(updated: AdminSupportTicket) {
    roomReadRequestRef.current.delete(updated.roomId);
    if (updated.assigneeId === accountId) ownedRoomIdsRef.current.add(updated.roomId);
    else ownedRoomIdsRef.current.delete(updated.roomId);
    if (selectedRoomRef.current === updated.roomId) {
      const stillMine = updated.assigneeId === accountId;
      if (ownsSelectedConversationRef.current && !stillMine) {
        draftsRef.current.delete(updated.roomId);
        invalidateConversation();
      }
      ownsSelectedConversationRef.current = stillMine;
    }
    setTickets((current) =>
      (current ?? []).map((ticket) => (ticket.roomId === updated.roomId ? updated : ticket)),
    );
  }

  async function run(roomId: string, action: () => Promise<AdminSupportTicket>): Promise<boolean> {
    const generation = ++actionGenerationRef.current;
    setBusy(true);
    setActionError(undefined);
    try {
      const updated = await action();
      if (actionGenerationRef.current !== generation || selectedRoomRef.current !== roomId) {
        return false;
      }
      replaceTicket(updated);
      return true;
    } catch (cause) {
      if (actionGenerationRef.current !== generation || selectedRoomRef.current !== roomId) {
        return false;
      }
      setActionError(messageOf(cause, "문의 처리에 실패했습니다."));
      return false;
    } finally {
      if (actionGenerationRef.current === generation && selectedRoomRef.current === roomId) {
        setBusy(false);
      }
    }
  }

  async function submitReply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (token === null || selected === null || reply.trim().length === 0) return;
    const roomId = selected.roomId;
    if (pendingWritesRef.current.has(roomId)) return;
    const content = reply.trim();
    const submittedKind = noticeKind;
    pendingWritesRef.current.set(roomId, "reply");
    ++actionGenerationRef.current;
    setBusy(true);
    setSendingReply(true);
    setReplyFeedback(null);
    setSentConfirmation(null);
    setActionError(undefined);
    try {
      const sent = await replyAdminSupport(
        token,
        roomId,
        content,
        submittedKind === "GENERAL" ? undefined : submittedKind,
      );
      const draft = draftsRef.current.get(roomId);
      if (draft?.reply.trim() === content && draft.noticeKind === submittedKind) {
        draftsRef.current.set(roomId, { ...draft, reply: "", noticeKind: "GENERAL" });
      }
      if (selectedRoomRef.current !== roomId || !ownsSelectedConversationRef.current) {
        return;
      }
      setMessages((current) => mergeRows(current, [sent], (row) => row.sentAt));
      setReply(draftsRef.current.get(roomId)?.reply ?? "");
      setNoticeKind(draftsRef.current.get(roomId)?.noticeKind ?? "GENERAL");
      const confirmation =
        submittedKind === "GENERAL"
          ? "답변을 대화에 남겼습니다."
          : submittedKind === "PROGRESS"
            ? "진행 안내를 대화에 남겼습니다."
            : "결과·처리방안 안내를 대화에 남겼습니다.";
      setReplyFeedback({ kind: "success", text: confirmation });
      setSentConfirmation(confirmation);
    } catch (cause) {
      if (isOwnershipDenied(cause)) revokeRoomAccess(roomId);
      else if (selectedRoomRef.current === roomId && ownsSelectedConversationRef.current) {
        setReplyFeedback({
          kind: "error",
          text:
            messageOf(cause, "답변 저장 결과를 확인하지 못했습니다.") +
            " 최근 대화와 안내 이력을 확인한 뒤 다시 보내 주세요.",
        });
      }
    } finally {
      pendingWritesRef.current.delete(roomId);
      if (selectedRoomRef.current === roomId) {
        setBusy(false);
        setSendingReply(false);
      }
      // 완료 때의 최신 필터로 조회한다. 요청 시작 당시 필터를 되살리지 않는다.
      setFilterRevision((value) => value + 1);
    }
  }

  async function submitNote(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (token === null || selected === null || note.trim().length === 0) return;
    const roomId = selected.roomId;
    if (pendingWritesRef.current.has(roomId)) return;
    const content = note.trim();
    pendingWritesRef.current.set(roomId, "note");
    ++actionGenerationRef.current;
    setBusy(true);
    setActionError(undefined);
    try {
      await addAdminSupportNote(token, roomId, content);
      const draft = draftsRef.current.get(roomId);
      if (draft?.note.trim() === content) draftsRef.current.set(roomId, { ...draft, note: "" });
      if (selectedRoomRef.current !== roomId || !ownsSelectedConversationRef.current) {
        return;
      }
      setNote(draftsRef.current.get(roomId)?.note ?? "");
      const scopeGeneration = conversationScopeGenerationRef.current;
      const nextNotes = await fetchAdminSupportNotes(token, roomId);
      if (
        conversationScopeGenerationRef.current === scopeGeneration &&
        selectedRoomRef.current === roomId
      ) {
        setNotes((current) => mergeRows(current, nextNotes, (row) => row.createdAt));
      }
    } catch (cause) {
      if (isOwnershipDenied(cause)) revokeRoomAccess(roomId);
      else if (selectedRoomRef.current === roomId && ownsSelectedConversationRef.current) {
        setActionError(messageOf(cause, "내부 메모를 남기지 못했습니다."));
      }
    } finally {
      pendingWritesRef.current.delete(roomId);
      if (selectedRoomRef.current === roomId) {
        setBusy(false);
      }
      setFilterRevision((value) => value + 1);
    }
  }

  async function loadOlderMessages() {
    if (
      token === null ||
      selected === null ||
      !isMine ||
      loadingOlderMessages ||
      messages.length === 0
    ) {
      return;
    }
    const roomId = selected.roomId;
    const scopeGeneration = conversationScopeGenerationRef.current;
    const accessRequest = roomReadRequestRef.current.get(roomId);
    const oldest = messages[0];
    if (oldest === undefined) return;
    setLoadingOlderMessages(true);
    setConversationError(undefined);
    try {
      const older = await fetchAdminSupportMessages(token, roomId, {
        beforeSentAt: oldest.sentAt,
        beforeId: oldest.id,
      });
      if (
        conversationScopeGenerationRef.current !== scopeGeneration ||
        selectedRoomRef.current !== roomId
      ) {
        return;
      }
      setMessages((current) => mergeRows(current, older, (row) => row.sentAt));
      setHasOlderMessages(older.length === 60);
    } catch (cause) {
      if (isOwnershipDenied(cause) && roomReadRequestRef.current.get(roomId) === accessRequest) {
        revokeRoomAccess(roomId);
        return;
      }
      if (
        conversationScopeGenerationRef.current === scopeGeneration &&
        selectedRoomRef.current === roomId
      ) {
        setConversationError(messageOf(cause, "이전 문의 대화를 불러오지 못했습니다."));
      }
    } finally {
      if (
        conversationScopeGenerationRef.current === scopeGeneration &&
        selectedRoomRef.current === roomId
      ) {
        setLoadingOlderMessages(false);
      }
    }
  }

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <Heading level={2}>운영 문의</Heading>
          <Text className="mt-1" size="sm" tone="muted">
            먼저 배정받은 뒤 필요한 대화만 확인합니다. 답변·이관·메모·열람은 감사 로그에 남습니다.
            접수 후 72시간 안에 진행 경과, 240시간 안에 결과나 처리방안을 안내하는 내부 목표를
            확인합니다.
          </Text>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <label className="flex min-w-0 items-center gap-2 text-sm text-neutral-600">
            <span className="shrink-0 whitespace-nowrap">상태</span>
            <Select
              className="min-w-32"
              value={status}
              onChange={(event) => {
                resetInboxForFilter();
                setStatus(event.target.value as StatusFilter);
              }}
            >
              <option value="ALL">전체</option>
              <option value="UNASSIGNED">미배정</option>
              <option value="IN_PROGRESS">답변 중</option>
              <option value="WAITING_USER">사용자 답변 대기</option>
              <option value="RESOLVED">해결</option>
            </Select>
          </label>
          <label className="flex min-w-0 items-center gap-2 text-sm text-neutral-600">
            <span className="shrink-0 whitespace-nowrap">안내 목표</span>
            <Select
              className="min-w-0 max-w-full"
              value={noticeFilter}
              onChange={(event) => {
                resetInboxForFilter();
                setNoticeFilter(event.target.value as "ALL" | AdminSupportNoticeFilter);
              }}
            >
              <option value="ALL">전체</option>
              <option value="PROGRESS_DUE_SOON">미해결 · 진행 안내 임박</option>
              <option value="PROGRESS_OVERDUE">미해결 · 진행 안내 초과</option>
              <option value="RESULT_DUE_SOON">미해결 · 결과 안내 임박</option>
              <option value="RESULT_OVERDUE">미해결 · 결과 안내 초과</option>
              <option value="PROGRESS_MISSING">해결 · 진행 안내 미기록</option>
              <option value="RESULT_MISSING">해결 · 결과 안내 미기록</option>
            </Select>
          </label>
          <label className="flex min-w-0 items-center gap-2 text-sm text-neutral-600">
            <span className="shrink-0 whitespace-nowrap">유형</span>
            <Select
              className="min-w-32"
              value={category}
              onChange={(event) => {
                resetInboxForFilter();
                setCategory(event.target.value as CategoryFilter);
              }}
            >
              <option value="ALL">전체</option>
              {Object.entries(CATEGORY_LABEL).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>
          </label>
          <Button size="sm" variant="ghost" onClick={loadTickets}>
            새로고침
          </Button>
        </div>
      </div>

      <SupportNoticePanel
        summary={noticeSummary}
        activeFilter={noticeFilter}
        error={summaryError}
        onRetry={loadTickets}
        onSelect={(filter) => {
          pendingFocusRef.current = { target: "list", roomId: null };
          resetInboxForFilter();
          setStatus("ALL");
          setCategory("ALL");
          setNoticeFilter(filter);
        }}
      />

      {selectedRoomId === null && sentConfirmation ? (
        <p
          role="status"
          className="rounded-lg border border-success/20 bg-success-soft p-3 text-sm text-neutral-800"
        >
          {sentConfirmation} 현재 조건에 맞는 문의 목록을 확인해 주세요.
        </p>
      ) : null}
      {listError ? (
        <div
          role="alert"
          className="flex flex-wrap items-center gap-3 rounded-lg border border-danger/20 bg-danger-soft p-3"
        >
          <p className="text-sm text-danger">
            {listError}
            {tickets !== null
              ? " 마지막으로 조회한 목록입니다."
              : " 조건에 맞는 문의를 아직 확인하지 못했습니다."}
          </p>
          <Button size="sm" variant="secondary" onClick={loadTickets}>
            문의 목록 다시 확인
          </Button>
        </div>
      ) : null}
      {conversationError ? (
        <div
          role="alert"
          className="flex flex-wrap items-center gap-3 rounded-lg border border-danger/20 bg-danger-soft p-3"
        >
          <p className="text-sm text-danger">{conversationError}</p>
          <Button size="sm" variant="secondary" onClick={loadConversation}>
            문의 대화 다시 확인
          </Button>
        </div>
      ) : null}
      {[actionError].map((message, index) =>
        message ? (
          <Text key={`${index}:${message}`} role="alert" size="sm" className="text-danger">
            {message}
          </Text>
        ) : null,
      )}

      <div className="grid overflow-hidden rounded-xl border border-neutral-200 bg-white xl:min-h-[560px] xl:grid-cols-[280px_minmax(0,1fr)]">
        <aside
          className={cn(
            "border-b border-neutral-200 bg-neutral-50/70 xl:border-r xl:border-b-0",
            selectedRoomId !== null && "max-xl:hidden",
          )}
        >
          <div
            ref={inboxHeadingRef}
            tabIndex={-1}
            className="border-b border-neutral-200 px-4 py-3 text-xs font-semibold text-neutral-500"
          >
            {tickets === null
              ? listError
                ? "문의 목록 확인 필요"
                : "문의 불러오는 중…"
              : `${tickets.length}개 문의${noticeFilter === "ALL" ? " · 최근 활동순" : " · 오래된 접수순"}`}
            {tickets?.length === 50 ? (
              <p className="mt-1 font-normal">
                {noticeFilter === "ALL"
                  ? "최대 50건 표시 · 안내 목표별로 문의를 확인하세요."
                  : "조건에 맞는 문의 중 오래된 50건을 표시합니다."}
              </p>
            ) : null}
          </div>
          <ul className="max-h-[min(68dvh,680px)] overflow-y-auto p-2">
            {(tickets ?? []).map((ticket) => (
              <li key={ticket.roomId}>
                <button
                  type="button"
                  ref={(element) => {
                    if (element) inboxButtonsRef.current.set(ticket.roomId, element);
                    else inboxButtonsRef.current.delete(ticket.roomId);
                  }}
                  onClick={() => openRoom(ticket.roomId)}
                  className={cn(
                    "flex w-full flex-col gap-2 rounded-lg px-3 py-3 text-left transition-colors",
                    selectedRoomId === ticket.roomId
                      ? "bg-white shadow-sm ring-1 ring-brand-100"
                      : "hover:bg-white/80",
                  )}
                >
                  <span className="flex w-full items-center justify-between gap-2">
                    <span className="truncate text-sm font-semibold text-neutral-900">
                      {ticket.title}
                    </span>
                    <Badge tone={STATUS_TONE[ticket.status]}>{STATUS_LABEL[ticket.status]}</Badge>
                  </span>
                  <span className="flex flex-wrap items-center gap-1.5">
                    <Badge tone={ticket.category.startsWith("PRIVACY_") ? "warning" : "neutral"}>
                      {CATEGORY_LABEL[ticket.category]}
                    </Badge>
                    <SupportDeadlineBadge ticket={ticket} nowMs={nowMs} kind="PROGRESS" />
                    <SupportDeadlineBadge ticket={ticket} nowMs={nowMs} kind="RESULT" />
                  </span>
                  <span className="flex w-full justify-between text-xs text-neutral-500">
                    <span>문의자 {shortId(ticket.requesterId)}</span>
                    <time>{formatDateTime(ticket.updatedAt)}</time>
                  </span>
                </button>
              </li>
            ))}
          </ul>
          {tickets?.length === 0 && !listError ? (
            <p className="px-5 py-14 text-center text-sm text-neutral-500">
              해당 상태의 문의가 없어요.
            </p>
          ) : null}
        </aside>

        {selected === null ? (
          <div className="hidden place-items-center p-8 text-center xl:grid">
            <div>
              <p className="font-semibold text-neutral-800">확인할 문의를 선택해 주세요</p>
              <p className="mt-1 text-sm text-neutral-500">
                미배정 문의는 본문을 열기 전에 먼저 배정합니다.
              </p>
            </div>
          </div>
        ) : (
          <section className="flex min-w-0 flex-col">
            <header className="flex flex-wrap items-start justify-between gap-3 border-b border-neutral-200 px-5 py-4">
              <div className="flex min-w-0 items-start gap-3">
                <Button
                  size="sm"
                  variant="ghost"
                  className="shrink-0 xl:hidden"
                  onClick={returnToList}
                >
                  목록
                </Button>
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <h3
                      ref={detailHeadingRef}
                      tabIndex={-1}
                      className="font-bold break-words text-neutral-900 [overflow-wrap:anywhere]"
                    >
                      {selected.title}
                    </h3>
                    <Badge tone={STATUS_TONE[selected.status]}>
                      {STATUS_LABEL[selected.status]}
                    </Badge>
                    <Badge tone={selected.category.startsWith("PRIVACY_") ? "warning" : "neutral"}>
                      {CATEGORY_LABEL[selected.category]}
                    </Badge>
                    <SupportDeadlineBadge ticket={selected} nowMs={nowMs} kind="PROGRESS" />
                    <SupportDeadlineBadge ticket={selected} nowMs={nowMs} kind="RESULT" />
                  </div>
                  <p className="mt-1 text-xs text-neutral-500">
                    문의자 {shortId(selected.requesterId)} · 담당{" "}
                    {selected.assigneeId ? shortId(selected.assigneeId) : "없음"}
                  </p>
                </div>
              </div>
              <div className="flex flex-wrap gap-2">
                {selected.assigneeId === null ? (
                  <Button
                    size="sm"
                    disabled={busy || token === null}
                    onClick={() =>
                      run(selected.roomId, () =>
                        assignAdminSupportTicket(token ?? "", selected.roomId),
                      )
                    }
                  >
                    내가 맡기
                  </Button>
                ) : null}
                {isMine && selected.status !== "RESOLVED" ? (
                  <Button
                    size="sm"
                    variant="secondary"
                    disabled={busy}
                    onClick={() =>
                      run(selected.roomId, () =>
                        resolveAdminSupportTicket(token ?? "", selected.roomId),
                      )
                    }
                  >
                    해결 처리
                  </Button>
                ) : null}
                {isMine && selected.status === "RESOLVED" ? (
                  <Button
                    size="sm"
                    variant="secondary"
                    disabled={busy}
                    onClick={() =>
                      run(selected.roomId, () =>
                        reopenAdminSupportTicket(token ?? "", selected.roomId),
                      )
                    }
                  >
                    문의 재개
                  </Button>
                ) : null}
              </div>
            </header>

            {selected.status === "RESOLVED" && !selected.resultNotifiedAt ? (
              <p className="border-b border-warning/20 bg-warning-soft px-5 py-3 text-sm leading-relaxed text-neutral-700">
                해결 처리했지만 결과 안내 기록은 없습니다. 기존 대화를 확인하고 추가 안내가 필요한
                경우에만 문의를 재개해 주세요.
              </p>
            ) : null}

            {!isMine ? (
              <div className="grid flex-1 place-items-center p-8 text-center">
                <div className="max-w-sm">
                  <p className="font-semibold text-neutral-800">
                    {selected.assigneeId === null
                      ? "본문은 배정 후 열립니다"
                      : "다른 관리자가 담당 중입니다"}
                  </p>
                  <p className="mt-2 text-sm leading-6 text-neutral-500">
                    문의 프라이버시를 위해 현재 담당자만 대화와 내부 메모를 볼 수 있습니다.
                  </p>
                  {selected.assigneeId !== null && selected.status !== "RESOLVED" ? (
                    <div className="mt-6 rounded-xl border border-warning/40 bg-warning-soft p-4 text-left">
                      <p className="text-sm font-semibold text-neutral-800">담당 문의 인수</p>
                      <p className="mt-1 text-xs leading-5 text-neutral-600">
                        기존 담당자가 응답할 수 없거나 잘못 배정된 경우에만 사용하세요. 인수 사유와
                        이전 담당자는 감사 로그에 남습니다.
                      </p>
                      <label
                        htmlFor="support-takeover-reason"
                        className="mt-3 block text-xs font-semibold text-neutral-700"
                      >
                        인수 사유 (필수)
                      </label>
                      <Textarea
                        id="support-takeover-reason"
                        className="mt-1"
                        rows={3}
                        maxLength={500}
                        value={takeoverReason}
                        onChange={(event) => setTakeoverReason(event.target.value)}
                        placeholder="예: 기존 담당자 계정 정지로 인한 인수"
                        disabled={busy}
                      />
                      <Button
                        className="mt-3 w-full"
                        size="sm"
                        variant="secondary"
                        disabled={busy || token === null || takeoverReason.trim().length === 0}
                        onClick={() => {
                          const roomId = selected.roomId;
                          const reason = takeoverReason.trim();
                          void run(roomId, () =>
                            takeoverAdminSupportTicket(token ?? "", roomId, reason),
                          ).then((succeeded) => {
                            if (succeeded && selectedRoomRef.current === roomId) {
                              setTakeoverReason("");
                            }
                          });
                        }}
                      >
                        사유 남기고 내가 인수
                      </Button>
                    </div>
                  ) : null}
                </div>
              </div>
            ) : (
              <>
                <div className="grid flex-1 2xl:grid-cols-[minmax(0,1fr)_260px]">
                  <div className="flex min-h-0 flex-col border-b border-neutral-200 2xl:border-r 2xl:border-b-0">
                    <ol className="flex min-h-[320px] max-h-[min(58dvh,560px)] flex-1 flex-col gap-3 overflow-y-auto px-5 py-5">
                      {hasOlderMessages && messages.length > 0 ? (
                        <li className="flex justify-center">
                          <Button
                            type="button"
                            size="sm"
                            variant="ghost"
                            disabled={loadingOlderMessages}
                            onClick={() => void loadOlderMessages()}
                          >
                            {loadingOlderMessages ? "불러오는 중" : "이전 문의 내용"}
                          </Button>
                        </li>
                      ) : null}
                      {messages.map((message) => {
                        const mine = message.senderId === accountId;
                        return (
                          <li
                            key={message.id}
                            className={cn("flex", mine ? "justify-end" : "justify-start")}
                          >
                            <div
                              className={cn(
                                "max-w-[78%] rounded-2xl px-4 py-2.5 text-sm",
                                mine
                                  ? "rounded-br-md bg-brand-600 text-white"
                                  : "rounded-bl-md bg-neutral-100 text-neutral-800",
                              )}
                            >
                              <p className="whitespace-pre-wrap break-words">{message.content}</p>
                              <time
                                className={cn(
                                  "mt-1 block text-[11px]",
                                  mine ? "text-brand-100" : "text-neutral-400",
                                )}
                              >
                                {formatDateTime(message.sentAt)}
                              </time>
                            </div>
                          </li>
                        );
                      })}
                      {!conversationLoaded && !conversationError ? (
                        <li role="status" className="py-14 text-center text-sm text-neutral-500">
                          문의 대화를 불러오고 있어요.
                        </li>
                      ) : null}
                      {conversationLoaded && messages.length === 0 && !conversationError ? (
                        <li className="py-14 text-center text-sm text-neutral-500">
                          아직 대화가 없습니다.
                        </li>
                      ) : null}
                    </ol>
                    <form
                      onSubmit={submitReply}
                      className="flex min-w-0 flex-col gap-3 border-t border-neutral-200 p-4"
                    >
                      <label className="flex flex-col gap-1.5 text-sm font-medium text-neutral-700">
                        답변 종류
                        <Select
                          value={noticeKind}
                          onChange={(event) =>
                            updateDraft({ noticeKind: event.target.value as NoticeKindInput })
                          }
                          disabled={busy || selected.status === "RESOLVED"}
                        >
                          <option value="GENERAL">일반 답변</option>
                          <option value="PROGRESS">진행 경과 안내</option>
                          <option value="RESULT">결과·처리방안 안내</option>
                        </Select>
                      </label>
                      <p className="text-xs leading-relaxed text-neutral-500">
                        진행·결과 안내는 이 문의에 실제로 보낸 답변과 함께 기록됩니다. 일반 답변은
                        안내 이행으로 세지 않습니다.
                      </p>
                      <Textarea
                        aria-label="사용자에게 보낼 답변"
                        rows={2}
                        maxLength={2000}
                        value={reply}
                        onChange={(event) => {
                          updateDraft({ reply: event.target.value });
                          setConfirmDraftReplacement(false);
                        }}
                        placeholder="사용자에게 보낼 답변"
                        disabled={busy || selected.status === "RESOLVED"}
                      />
                      <Button
                        type="submit"
                        className="self-end"
                        loading={sendingReply}
                        loadingLabel="답변 전송 중…"
                        disabled={
                          busy || reply.trim().length === 0 || selected.status === "RESOLVED"
                        }
                      >
                        {noticeKind === "GENERAL"
                          ? "보내기"
                          : noticeKind === "PROGRESS"
                            ? "진행 안내 보내기"
                            : "결과 안내 보내기"}
                      </Button>
                      {replyFeedback ? (
                        <p
                          role={replyFeedback.kind === "error" ? "alert" : "status"}
                          className={cn(
                            "text-sm leading-relaxed",
                            replyFeedback.kind === "error" ? "text-danger" : "text-success",
                          )}
                        >
                          {replyFeedback.text}
                        </p>
                      ) : null}
                    </form>
                  </div>

                  <aside className="flex flex-col gap-5 bg-neutral-50/70 p-4">
                    <SupportNoticeTargets ticket={selected} />
                    <section aria-label="실제 안내 이력">
                      <h4 className="text-sm font-bold text-neutral-800">실제 안내 이력</h4>
                      <p className="mt-1 text-xs leading-relaxed text-neutral-500">
                        고객 읽음이 아닌 답변 저장 시각입니다. 결과 안내는 진행 안내에도 포함합니다.
                      </p>
                      {noticesError ? (
                        <div role="alert" className="mt-3 space-y-2">
                          <p className="text-sm text-danger">{noticesError}</p>
                          <Button
                            type="button"
                            size="sm"
                            variant="secondary"
                            onClick={loadConversation}
                          >
                            안내 이력 다시 확인
                          </Button>
                        </div>
                      ) : notices === null ? (
                        <p role="status" className="mt-3 text-sm text-neutral-500">
                          안내 이력을 확인하고 있어요.
                        </p>
                      ) : notices.length === 0 ? (
                        <p className="mt-3 text-sm text-neutral-500">기록된 안내가 없습니다.</p>
                      ) : (
                        <ol className="mt-3 flex flex-col gap-3">
                          {notices.map((entry) => (
                            <li
                              key={entry.messageId}
                              className="border-l-2 border-brand-200 pl-3 text-sm"
                            >
                              <p className="font-semibold text-neutral-800">
                                {entry.kind === "PROGRESS"
                                  ? "진행 경과 안내"
                                  : "결과·처리방안 안내"}
                              </p>
                              <time className="mt-1 block text-xs text-neutral-600">
                                {formatDateTime(entry.sentAt)}
                              </time>
                              <p className="mt-1 text-xs text-neutral-500">
                                담당 {shortId(entry.senderId)} · 메시지 {shortId(entry.messageId)}
                              </p>
                            </li>
                          ))}
                        </ol>
                      )}
                    </section>
                    {selected.assistantAnalysis ? (
                      <section aria-label="AI 답변 보조">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <p className="text-xs font-bold uppercase tracking-wide text-neutral-500">
                            AI 답변 보조
                          </p>
                          <div className="flex flex-wrap gap-1">
                            <Badge tone={PRIORITY_TONE[selected.assistantAnalysis.priority]}>
                              {PRIORITY_LABEL[selected.assistantAnalysis.priority]}
                            </Badge>
                            <Badge
                              tone={
                                selected.assistantAnalysis.category.startsWith("PRIVACY_")
                                  ? "warning"
                                  : "neutral"
                              }
                            >
                              {CATEGORY_LABEL[selected.assistantAnalysis.category]}
                            </Badge>
                          </div>
                        </div>
                        <div className="mt-2 rounded-lg border border-neutral-200 bg-white p-3 text-xs text-neutral-700">
                          <p className="font-semibold">요약</p>
                          <p className="mt-1 whitespace-pre-wrap leading-5">
                            {selected.assistantAnalysis.summary}
                          </p>
                          {selected.assistantAnalysis.risk.length > 0 ? (
                            <p className="mt-2 break-words text-warning">
                              검토 신호 · {selected.assistantAnalysis.risk.join(", ")}
                            </p>
                          ) : null}
                          {selected.assistantAnalysis.humanReview ? (
                            <p className="mt-2 font-semibold text-danger">
                              사람의 검토가 필요합니다.
                            </p>
                          ) : null}
                          <p className="mt-3 font-semibold">답변 초안</p>
                          <p className="mt-1 whitespace-pre-wrap leading-5">
                            {selected.assistantAnalysis.draft}
                          </p>
                          <Button
                            type="button"
                            className="mt-3 w-full"
                            size="sm"
                            variant="secondary"
                            disabled={
                              busy ||
                              selected.status === "RESOLVED" ||
                              selected.assistantAnalysis.draft.trim().length === 0
                            }
                            onClick={() => {
                              const draft = selected.assistantAnalysis?.draft ?? "";
                              if (reply.trim().length > 0 && reply !== draft) {
                                setConfirmDraftReplacement(true);
                              } else {
                                updateDraft({ reply: draft });
                              }
                            }}
                          >
                            초안을 답변에 넣기
                          </Button>
                          {confirmDraftReplacement ? (
                            <div role="alert" className="mt-3 space-y-2">
                              <p>작성 중인 답변이 있습니다. AI 초안으로 바꾸시겠어요?</p>
                              <div className="flex flex-wrap gap-2">
                                <Button
                                  type="button"
                                  size="sm"
                                  variant="secondary"
                                  onClick={() => setConfirmDraftReplacement(false)}
                                >
                                  작성한 답변 유지
                                </Button>
                                <Button
                                  type="button"
                                  size="sm"
                                  disabled={busy || selected.status === "RESOLVED"}
                                  onClick={() => {
                                    updateDraft({ reply: selected.assistantAnalysis?.draft ?? "" });
                                    setConfirmDraftReplacement(false);
                                  }}
                                >
                                  AI 초안으로 바꾸기
                                </Button>
                              </div>
                            </div>
                          ) : null}
                          <p className="mt-2 text-[11px] leading-4 text-neutral-400">
                            {selected.assistantAnalysis.engine} ·
                            {selected.assistantAnalysis.externalModel
                              ? " 외부 모델 사용"
                              : " 내부 규칙 사용"}{" "}
                            · 검토 후 직접 전송
                          </p>
                        </div>
                      </section>
                    ) : null}

                    <section>
                      <p className="text-xs font-bold uppercase tracking-wide text-neutral-500">
                        내부 메모
                      </p>
                      <ul className="mt-2 flex max-h-44 flex-col gap-2 overflow-y-auto">
                        {notes.map((entry) => (
                          <li
                            key={entry.id}
                            className="rounded-md border border-neutral-200 bg-white p-2 text-xs"
                          >
                            <p className="whitespace-pre-wrap text-neutral-700">{entry.note}</p>
                            <p className="mt-1 text-neutral-400">
                              {shortId(entry.authorId)} · {formatDateTime(entry.createdAt)}
                            </p>
                          </li>
                        ))}
                      </ul>
                      <form onSubmit={submitNote} className="mt-2 flex flex-col gap-2">
                        <Textarea
                          rows={2}
                          maxLength={2000}
                          value={note}
                          onChange={(event) => updateDraft({ note: event.target.value })}
                          placeholder="운영팀만 보는 메모"
                          disabled={busy}
                        />
                        <Button
                          type="submit"
                          size="sm"
                          variant="secondary"
                          disabled={busy || note.trim().length === 0}
                        >
                          메모 남기기
                        </Button>
                      </form>
                    </section>

                    <section className="border-t border-neutral-200 pt-4">
                      <p className="text-xs font-bold uppercase tracking-wide text-neutral-500">
                        담당 이관
                      </p>
                      <div className="mt-2 flex flex-col gap-2">
                        <Input
                          aria-label="이관할 관리자 계정 ID"
                          value={transferTo}
                          onChange={(event) => setTransferTo(event.target.value)}
                          placeholder="관리자 계정 ID"
                          disabled={busy}
                        />
                        <Button
                          size="sm"
                          variant="ghost"
                          disabled={busy || transferTo.trim().length === 0}
                          onClick={() => {
                            const target = transferTo.trim();
                            void run(selected.roomId, () =>
                              transferAdminSupportTicket(token ?? "", selected.roomId, target),
                            ).then((succeeded) => {
                              if (succeeded && selectedRoomRef.current === selected.roomId)
                                setTransferTo("");
                            });
                          }}
                        >
                          이관하기
                        </Button>
                      </div>
                    </section>
                  </aside>
                </div>
              </>
            )}
          </section>
        )}
      </div>
    </div>
  );
}

function messageOf(cause: unknown, fallback: string): string {
  return cause instanceof ApiError ? cause.message : fallback;
}

function isOwnershipDenied(cause: unknown): boolean {
  return cause instanceof ApiError && cause.code === "SUPPORT_ASSIGNEE_ONLY";
}

function mergeRows<T extends { readonly id: string }>(
  current: readonly T[],
  incoming: readonly T[],
  timestamp: (row: T) => string,
): readonly T[] {
  const rows = new Map(current.map((row) => [row.id, row]));
  for (const row of incoming) rows.set(row.id, row);
  return [...rows.values()].toSorted((a, b) => {
    const byTime = new Date(timestamp(a)).getTime() - new Date(timestamp(b)).getTime();
    return byTime === 0 ? a.id.localeCompare(b.id) : byTime;
  });
}
