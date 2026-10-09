"use client";

import { type FormEvent, useEffect, useState } from "react";
import Link from "next/link";
import {
  fetchMyPartRequests,
  fetchPartRequests,
  PartItemsTable,
  partRequestHref,
  partRequestsHref,
  partRequestTitle,
  PartRequestStatusBadge,
  PART_REQUEST_RULES,
  RequestedAgo,
  type PartRequest,
} from "@entities/part-request";
import { useSession } from "@entities/user";
import {
  Button,
  Container,
  EmptyState,
  Heading,
  Input,
  LinkButton,
  Skeleton,
  Text,
} from "@shared/ui";
import { PARTS_BOARD_TABS, partsBoardPath, type PartsBoardTab } from "../model/board-tab";

/** 서버 상한(W3 limit 1~50)까지 한 번에 받는다. 요청은 짧은 글이라 페이지를 나누지 않는다. */
const BOARD_LIMIT = 50;

interface BoardResult {
  readonly key: string;
  readonly status: "ready" | "failed";
  readonly requests: readonly PartRequest[];
}

export interface PartsBoardPageProps {
  /** `/parts?set=` 로 들어오면 그 세트의 요청만 본다. */
  readonly initialSetNumber?: string | undefined;
  readonly initialTab?: PartsBoardTab | undefined;
}

function tabClass(active: boolean): string {
  return `rounded-md border px-3.5 py-1.5 text-sm font-semibold transition-colors ${
    active
      ? "border-brand-600 bg-brand-600 text-white"
      : "border-neutral-200 bg-white text-neutral-600 hover:border-neutral-300 hover:bg-neutral-50"
  }`;
}

/**
 * 부족 부품 요청 게시판. (wanted-parts W3·W4, F1)
 *
 * 세트 번호 필터와 열림/마감 탭을 주소에 남겨 세트 상세·컬렉션·알림에서 같은 화면으로 들어온다.
 * 목록은 브라우저에서 받는다 — "내 요청" 탭이 세션에 달려 있고, 요청은 수명이 짧은 글이라
 * 서버 렌더로 색인할 가치가 적다.
 */
export function PartsBoardPage({
  initialSetNumber = "",
  initialTab = "open",
}: PartsBoardPageProps) {
  const { session } = useSession();
  const [setNumber, setSetNumber] = useState(initialSetNumber.trim());
  const [setDraft, setSetDraft] = useState(initialSetNumber.trim());
  const [tab, setTab] = useState<PartsBoardTab>(initialTab);
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<BoardResult | null>(null);

  // 비로그인으로 `?status=mine`에 들어오면 기본 탭으로 본다. 탭 상태는 로그인 후를 위해 남긴다.
  const effectiveTab: PartsBoardTab = tab === "mine" && session === null ? "open" : tab;
  const requestKey = `${effectiveTab}|${setNumber}|${session?.accountId ?? ""}|${attempt}`;

  useEffect(() => {
    const controller = new AbortController();
    const request =
      effectiveTab === "mine"
        ? fetchMyPartRequests(controller.signal)
        : fetchPartRequests(
            {
              status: effectiveTab,
              limit: BOARD_LIMIT,
              ...(setNumber.length === 0 ? {} : { setNumber }),
            },
            controller.signal,
          );
    request
      .then((requests) => setResult({ key: requestKey, status: "ready", requests }))
      .catch(() => {
        if (!controller.signal.aborted) {
          setResult({ key: requestKey, status: "failed", requests: [] });
        }
      });
    return () => controller.abort();
  }, [effectiveTab, requestKey, setNumber]);

  const current = result?.key === requestKey ? result : null;
  // 내 요청은 서버가 세트 필터를 받지 않으므로 여기서 거른다.
  const visible =
    current === null
      ? []
      : effectiveTab === "mine" && setNumber.length > 0
        ? current.requests.filter((request) => request.setNumber === setNumber)
        : current.requests;

  /**
   * 필터를 주소에 남긴다(공유·뒤로 가기). 목록은 이미 상태로 다시 받으므로 서버 렌더를 다시
   * 부르지 않도록 라우터가 아니라 history를 쓴다 — Next가 이 호출을 라우터 상태와 맞춰 준다.
   */
  function navigate(nextSetNumber: string, nextTab: PartsBoardTab) {
    window.history.replaceState(null, "", partsBoardPath(nextSetNumber, nextTab));
  }

  function selectTab(next: PartsBoardTab) {
    setTab(next);
    navigate(setNumber, next);
  }

  function applySetFilter(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const next = setDraft.trim();
    setSetNumber(next);
    navigate(next, effectiveTab);
  }

  function clearSetFilter() {
    setSetDraft("");
    setSetNumber("");
    navigate("", effectiveTab);
  }

  const composeHref = partRequestsHref({ setNumber, compose: true });

  return (
    <Container width="lg">
      <div className="flex flex-col gap-6 pt-8 pb-16">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div className="flex flex-col gap-1">
            <Heading level={1}>부품 요청</Heading>
            <Text tone="secondary">
              조립하다 모자란 부품을 찾고, 가진 세트로 다른 빌더를 도와주세요.
            </Text>
          </div>
          <LinkButton href={composeHref}>부품 요청하기</LinkButton>
        </div>

        <div className="flex flex-col gap-3 rounded-xl border border-neutral-200 bg-white p-3">
          <form
            role="search"
            aria-label="세트 번호로 찾기"
            className="flex flex-wrap items-center gap-2"
            onSubmit={applySetFilter}
          >
            <label htmlFor="parts-set-filter" className="sr-only">
              세트 번호
            </label>
            <Input
              id="parts-set-filter"
              value={setDraft}
              placeholder="세트 번호 (예: 10307)"
              autoComplete="off"
              maxLength={PART_REQUEST_RULES.maxSetNumberLength}
              className="h-10 min-w-0 flex-1 sm:max-w-xs"
              onChange={(event) => setSetDraft(event.target.value)}
            />
            <Button type="submit" variant="secondary" size="sm">
              찾기
            </Button>
            {setNumber.length > 0 ? (
              <Button type="button" variant="ghost" size="sm" onClick={clearSetFilter}>
                세트 필터 지우기
              </Button>
            ) : null}
          </form>
          <div className="flex flex-wrap gap-2" role="group" aria-label="요청 상태">
            {PARTS_BOARD_TABS.filter((item) => item.key !== "mine" || session !== null).map(
              (item) => (
                <button
                  key={item.key}
                  type="button"
                  aria-pressed={effectiveTab === item.key}
                  className={tabClass(effectiveTab === item.key)}
                  onClick={() => selectTab(item.key)}
                >
                  {item.label}
                </button>
              ),
            )}
          </div>
        </div>

        {setNumber.length > 0 ? (
          <Text size="sm" tone="muted">
            <Link
              href={`/sets/${encodeURIComponent(setNumber)}`}
              className="font-mono font-semibold text-brand-700 hover:underline"
            >
              #{setNumber}
            </Link>{" "}
            세트의 요청만 보고 있어요.
          </Text>
        ) : null}

        {current === null ? (
          <ul className="flex flex-col gap-3" aria-busy="true" aria-label="부품 요청 불러오는 중">
            {Array.from({ length: 3 }, (_, index) => (
              <li
                key={index}
                className="flex flex-col gap-3 rounded-xl border border-neutral-200 p-4"
              >
                <Skeleton className="h-5 w-1/3" />
                <Skeleton className="h-16 w-full rounded-lg" />
              </li>
            ))}
          </ul>
        ) : current.status === "failed" ? (
          <EmptyState
            variant="inline"
            title="부품 요청을 불러오지 못했어요"
            description="연결이 잠시 지연되고 있어요. 다시 시도해 주세요."
            action={
              <Button variant="secondary" onClick={() => setAttempt((value) => value + 1)}>
                다시 시도
              </Button>
            }
          />
        ) : visible.length === 0 ? (
          <EmptyState
            eyebrow="부품 요청"
            title={
              effectiveTab === "mine"
                ? "아직 올린 요청이 없어요"
                : effectiveTab === "closed"
                  ? "마감된 요청이 없어요"
                  : "찾는 부품 요청이 없어요"
            }
            description={
              setNumber.length > 0
                ? `#${setNumber} 세트로 올라온 요청이 아직 없어요. 모자란 부품이 있다면 처음으로 올려 보세요.`
                : "부품 번호·색상·수량을 적어 올리면 그 세트를 가진 회원에게 알림이 가요."
            }
            action={<LinkButton href={composeHref}>부품 요청하기</LinkButton>}
          />
        ) : (
          <ul className="flex flex-col gap-3" aria-label="부품 요청 목록">
            {visible.map((request) => (
              <li key={request.id}>
                <PartRequestCard request={request} />
              </li>
            ))}
          </ul>
        )}
      </div>
    </Container>
  );
}

/**
 * 게시판 카드. 카드 전체를 누를 수 있게 하되 링크 이름은 요청 제목만 갖도록, 제목 링크의
 * 가상 요소를 카드 크기로 늘린다(표·메모가 링크 이름에 섞이지 않는다).
 */
function PartRequestCard({ request }: { readonly request: PartRequest }) {
  return (
    <article
      className="relative flex flex-col gap-3 rounded-xl border border-neutral-200 bg-white p-4 transition-colors hover:border-brand-300"
      data-testid="part-request-card"
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <PartRequestStatusBadge status={request.status} />
          {request.setNumber !== null ? (
            <span className="font-mono text-xs text-neutral-500">#{request.setNumber}</span>
          ) : null}
        </div>
        <RequestedAgo iso={request.createdAt} className="text-xs text-neutral-400" />
      </div>
      <h2 className="text-base font-semibold text-neutral-900">
        <Link
          href={partRequestHref(request.id)}
          className="after:absolute after:inset-0 after:rounded-xl focus-visible:outline-none focus-visible:after:ring-2 focus-visible:after:ring-brand-500"
        >
          {partRequestTitle(request)}
        </Link>
      </h2>
      <PartItemsTable items={request.items} limit={3} />
      {request.note.length > 0 ? (
        <p className="line-clamp-2 text-sm text-neutral-600">{request.note}</p>
      ) : null}
    </article>
  );
}
