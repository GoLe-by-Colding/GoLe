"use client";

import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import {
  addAdminSupportNote,
  assignAdminSupportTicket,
  fetchAdminSupportMessages,
  fetchAdminSupportNotes,
  fetchAdminSupportTickets,
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
} from "@entities/admin";
import { useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import { cn } from "@shared/lib";
import { Badge, Button, Heading, Input, Select, Text, Textarea } from "@shared/ui";
import { formatDateTime, shortId } from "../model/labels";

type StatusFilter = "ALL" | AdminSupportStatus;
type CategoryFilter = "ALL" | AdminSupportCategory;

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

function DeadlineBadge({
  ticket,
  nowMs,
  kind,
}: {
  readonly ticket: AdminSupportTicket;
  readonly nowMs: number | null;
  readonly kind: "PROGRESS" | "RESULT";
}) {
  const value = kind === "PROGRESS" ? ticket.progressDueAt : ticket.responseDueAt;
  if (value === null || value === undefined) return null;
  const dueAt = new Date(value).getTime();
  if (!Number.isFinite(dueAt)) return null;
  const label = kind === "PROGRESS" ? "진행 안내" : "결과·방안";
  if (ticket.status === "RESOLVED" && ticket.resolvedAt !== null) {
    const resolvedAt = new Date(ticket.resolvedAt).getTime();
    if (Number.isFinite(resolvedAt)) {
      const late = resolvedAt > dueAt;
      return (
        <Badge
          tone={late ? "danger" : "success"}
          title="해결 처리 시각과 내부 목표를 비교한 표시이며 실제 안내 발송 이행 원장은 아닙니다."
        >
          {label} {late ? "기한 후 처리" : "기한 내 처리"}
        </Badge>
      );
    }
  }
  if (nowMs === null) {
    return (
      <Badge tone="brand">
        {label} 목표 {formatDateTime(value)}
      </Badge>
    );
  }
  const remainingDays = Math.ceil((dueAt - nowMs) / 86_400_000);
  if (remainingDays < 0) {
    return (
      <Badge tone="danger">
        {label} {Math.abs(remainingDays)}일 지남
      </Badge>
    );
  }
  const warningThreshold = kind === "PROGRESS" ? 1 : 2;
  return (
    <Badge tone={remainingDays <= warningThreshold ? "warning" : "brand"}>
      {label} {remainingDays}일 남음
    </Badge>
  );
}

/** 문의 인박스. 운영자는 배정받은 문의의 본문만 읽고 답할 수 있다. */
export function AdminSupportView() {
  const { session } = useSession();
  const token = session?.sessionToken ?? null;
  const accountId = session?.accountId ?? null;
  const [status, setStatus] = useState<StatusFilter>("ALL");
  const [category, setCategory] = useState<CategoryFilter>("ALL");
  const [tickets, setTickets] = useState<readonly AdminSupportTicket[] | null>(null);
  const [selectedRoomId, setSelectedRoomId] = useState<string | null>(null);
  const [messages, setMessages] = useState<readonly AdminSupportMessage[]>([]);
  const [hasOlderMessages, setHasOlderMessages] = useState(false);
  const [loadingOlderMessages, setLoadingOlderMessages] = useState(false);
  const [notes, setNotes] = useState<readonly AdminSupportNote[]>([]);
  const [reply, setReply] = useState("");
  const [note, setNote] = useState("");
  const [transferTo, setTransferTo] = useState("");
  const [takeoverReason, setTakeoverReason] = useState("");
  const [busy, setBusy] = useState(false);
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

  const invalidateConversation = useCallback(() => {
    conversationScopeGenerationRef.current += 1;
    conversationRequestGenerationRef.current += 1;
    actionGenerationRef.current += 1;
    ownsSelectedConversationRef.current = false;
    setMessages([]);
    setHasOlderMessages(false);
    setLoadingOlderMessages(false);
    setNotes([]);
    setReply("");
    setNote("");
    setTransferTo("");
    setTakeoverReason("");
    setBusy(false);
    setConversationError(undefined);
    setActionError(undefined);
  }, []);

  const selectRoom = useCallback(
    (roomId: string | null) => {
      if (selectedRoomRef.current === roomId) return;
      selectedRoomRef.current = roomId;
      invalidateConversation();
      setSelectedRoomId(roomId);
    },
    [invalidateConversation],
  );

  const selected = useMemo(
    () => tickets?.find((ticket) => ticket.roomId === selectedRoomId) ?? null,
    [selectedRoomId, tickets],
  );
  const isMine = selected !== null && selected.assigneeId === accountId;

  useEffect(() => {
    const updateNow = () => setNowMs(Date.now());
    updateNow();
    const refresh = window.setInterval(updateNow, 60_000);
    return () => window.clearInterval(refresh);
  }, []);

  const loadTickets = useCallback(() => {
    if (token === null) return;
    const generation = ++ticketsGenerationRef.current;
    void fetchAdminSupportTickets(
      token,
      status === "ALL" ? undefined : status,
      category === "ALL" ? undefined : category,
    )
      .then((next) => {
        if (ticketsGenerationRef.current !== generation) return;
        setListError(undefined);
        const current = selectedRoomRef.current;
        const currentTicket = next.find((ticket) => ticket.roomId === current);
        const stillMine = currentTicket?.assigneeId === accountId;
        if (ownsSelectedConversationRef.current && !stillMine) {
          invalidateConversation();
        }
        ownsSelectedConversationRef.current = stillMine;
        setTickets(next);
        selectRoom(
          current !== null && currentTicket !== undefined ? current : (next[0]?.roomId ?? null),
        );
      })
      .catch((cause: unknown) => {
        if (ticketsGenerationRef.current !== generation) return;
        setTickets((current) => current ?? []);
        setListError(messageOf(cause, "문의 목록을 새로고치지 못했습니다."));
      });
  }, [accountId, category, invalidateConversation, selectRoom, status, token]);

  const loadConversation = useCallback(() => {
    const scopeGeneration = conversationScopeGenerationRef.current;
    const requestGeneration = ++conversationRequestGenerationRef.current;
    ownsSelectedConversationRef.current = isMine;
    if (token === null || selected === null || !isMine) {
      setMessages([]);
      setNotes([]);
      return;
    }
    const roomId = selected.roomId;
    setConversationError(undefined);
    void Promise.all([
      fetchAdminSupportMessages(token, roomId),
      fetchAdminSupportNotes(token, roomId),
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
  }, [isMine, selected, token]);

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
  }, [loadTickets]);

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
    if (selectedRoomRef.current === updated.roomId) {
      const stillMine = updated.assigneeId === accountId;
      if (ownsSelectedConversationRef.current && !stillMine) {
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
    const content = reply.trim();
    const generation = ++actionGenerationRef.current;
    setBusy(true);
    setActionError(undefined);
    try {
      const sent = await replyAdminSupport(token, roomId, content);
      if (actionGenerationRef.current !== generation || selectedRoomRef.current !== roomId) {
        return;
      }
      setMessages((current) => mergeRows(current, [sent], (row) => row.sentAt));
      setReply("");
      loadTickets();
    } catch (cause) {
      if (actionGenerationRef.current === generation && selectedRoomRef.current === roomId) {
        setActionError(messageOf(cause, "답변을 보내지 못했습니다."));
      }
    } finally {
      if (actionGenerationRef.current === generation && selectedRoomRef.current === roomId) {
        setBusy(false);
      }
    }
  }

  async function submitNote(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (token === null || selected === null || note.trim().length === 0) return;
    const roomId = selected.roomId;
    const content = note.trim();
    const generation = ++actionGenerationRef.current;
    setBusy(true);
    setActionError(undefined);
    try {
      await addAdminSupportNote(token, roomId, content);
      if (actionGenerationRef.current !== generation || selectedRoomRef.current !== roomId) {
        return;
      }
      setNote("");
      const nextNotes = await fetchAdminSupportNotes(token, roomId);
      if (actionGenerationRef.current === generation && selectedRoomRef.current === roomId) {
        setNotes((current) => mergeRows(current, nextNotes, (row) => row.createdAt));
      }
    } catch (cause) {
      if (actionGenerationRef.current === generation && selectedRoomRef.current === roomId) {
        setActionError(messageOf(cause, "내부 메모를 남기지 못했습니다."));
      }
    } finally {
      if (actionGenerationRef.current === generation && selectedRoomRef.current === roomId) {
        setBusy(false);
      }
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
            모든 문의에는 3영업일 진행 경과·10영업일 결과 또는 처리방안 안내보다 이른 내부 목표가
            표시됩니다. 해결 티켓의 기한 표시는 해결 시각 기준이며 실제 안내 발송 이력 증명은
            아닙니다.
          </Text>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <label className="flex items-center gap-2 text-sm text-neutral-600">
            상태
            <Select
              value={status}
              onChange={(event) => setStatus(event.target.value as StatusFilter)}
            >
              <option value="ALL">전체</option>
              <option value="UNASSIGNED">미배정</option>
              <option value="IN_PROGRESS">답변 중</option>
              <option value="WAITING_USER">사용자 답변 대기</option>
              <option value="RESOLVED">해결</option>
            </Select>
          </label>
          <label className="flex items-center gap-2 text-sm text-neutral-600">
            유형
            <Select
              value={category}
              onChange={(event) => setCategory(event.target.value as CategoryFilter)}
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

      {[listError, conversationError, actionError].map((message, index) =>
        message ? (
          <Text key={`${index}:${message}`} role="alert" size="sm" className="text-danger">
            {message}
          </Text>
        ) : null,
      )}

      <div className="grid min-h-[560px] overflow-hidden rounded-xl border border-neutral-200 bg-white xl:grid-cols-[280px_minmax(0,1fr)]">
        <aside
          className={cn(
            "border-b border-neutral-200 bg-neutral-50/70 xl:border-r xl:border-b-0",
            selectedRoomId !== null && "max-xl:hidden",
          )}
        >
          <div className="border-b border-neutral-200 px-4 py-3 text-xs font-semibold text-neutral-500">
            {tickets === null ? "문의 불러오는 중…" : `${tickets.length}개 문의`}
          </div>
          <ul className="max-h-[min(68dvh,680px)] overflow-y-auto p-2">
            {(tickets ?? []).map((ticket) => (
              <li key={ticket.roomId}>
                <button
                  type="button"
                  onClick={() => selectRoom(ticket.roomId)}
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
                    <DeadlineBadge ticket={ticket} nowMs={nowMs} kind="PROGRESS" />
                    <DeadlineBadge ticket={ticket} nowMs={nowMs} kind="RESULT" />
                  </span>
                  <span className="flex w-full justify-between text-xs text-neutral-500">
                    <span>문의자 {shortId(ticket.requesterId)}</span>
                    <time>{formatDateTime(ticket.updatedAt)}</time>
                  </span>
                </button>
              </li>
            ))}
          </ul>
          {tickets?.length === 0 ? (
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
                  onClick={() => selectRoom(null)}
                >
                  목록
                </Button>
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <h3 className="truncate font-bold text-neutral-900">{selected.title}</h3>
                    <Badge tone={STATUS_TONE[selected.status]}>
                      {STATUS_LABEL[selected.status]}
                    </Badge>
                    <Badge tone={selected.category.startsWith("PRIVACY_") ? "warning" : "neutral"}>
                      {CATEGORY_LABEL[selected.category]}
                    </Badge>
                    <DeadlineBadge ticket={selected} nowMs={nowMs} kind="PROGRESS" />
                    <DeadlineBadge ticket={selected} nowMs={nowMs} kind="RESULT" />
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
                      {messages.length === 0 ? (
                        <li className="py-14 text-center text-sm text-neutral-500">
                          아직 대화가 없습니다.
                        </li>
                      ) : null}
                    </ol>
                    <form
                      onSubmit={submitReply}
                      className="flex gap-2 border-t border-neutral-200 p-4"
                    >
                      <Textarea
                        rows={2}
                        maxLength={2000}
                        value={reply}
                        onChange={(event) => setReply(event.target.value)}
                        placeholder="사용자에게 보낼 답변"
                        disabled={busy || selected.status === "RESOLVED"}
                      />
                      <Button
                        type="submit"
                        disabled={
                          busy || reply.trim().length === 0 || selected.status === "RESOLVED"
                        }
                      >
                        보내기
                      </Button>
                    </form>
                  </div>

                  <aside className="flex flex-col gap-5 bg-neutral-50/70 p-4">
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
                            onClick={() => setReply(selected.assistantAnalysis?.draft ?? "")}
                          >
                            초안을 답변에 넣기
                          </Button>
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
                          onChange={(event) => setNote(event.target.value)}
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
                            ).then(() => {
                              if (selectedRoomRef.current === selected.roomId) setTransferTo("");
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
