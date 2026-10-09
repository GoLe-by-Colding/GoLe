"use client";

import { type ReactNode, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { HelpPartRequestButton } from "@features/part-request/help";
import { PartRequestOwnerActions } from "@features/part-request/manage";
import {
  fetchPartRequest,
  PartItemsTable,
  partRequestsHref,
  partRequestTitle,
  PartRequestStatusBadge,
  RequestedAgo,
  requesterLabel,
  type PartRequest,
} from "@entities/part-request";
import { useSession } from "@entities/user";
import { isApiNotFoundError } from "@shared/api";
import { Button, Container, Heading, LinkButton, Skeleton, Text } from "@shared/ui";

type DetailLoad =
  | { readonly status: "loading" }
  | { readonly status: "ready"; readonly request: PartRequest }
  | { readonly status: "missing" }
  | { readonly status: "failed" };

export interface PartRequestDetailPageProps {
  readonly requestId: string;
}

/**
 * 부족 부품 요청 상세. (wanted-parts W5·W6·W7·W10, F1)
 *
 * 방문자에게는 "도와줄게요"(1:1 대화), 작성자에게는 마감·삭제를 보여 준다. 작성자 판정은
 * 브라우저 세션으로만 할 수 있어 클라이언트에서 그린다 — 서버도 403으로 다시 막는다.
 */
export function PartRequestDetailPage({ requestId }: PartRequestDetailPageProps) {
  const router = useRouter();
  const { session } = useSession();
  const [load, setLoad] = useState<DetailLoad>({ status: "loading" });
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    fetchPartRequest(requestId, controller.signal)
      .then((request) => {
        if (!controller.signal.aborted) setLoad({ status: "ready", request });
      })
      .catch((cause: unknown) => {
        if (controller.signal.aborted) return;
        setLoad({ status: isApiNotFoundError(cause) ? "missing" : "failed" });
      });
    return () => controller.abort();
  }, [requestId, reloadKey]);

  function retry() {
    setLoad({ status: "loading" });
    setReloadKey((current) => current + 1);
  }

  let body: ReactNode;
  if (load.status === "loading") {
    body = (
      <div className="flex flex-col gap-4" aria-busy="true">
        <Skeleton className="h-8 w-2/3" />
        <Skeleton className="h-4 w-1/3" />
        <Skeleton className="h-32 w-full rounded-lg" />
      </div>
    );
  } else if (load.status === "missing") {
    body = (
      <Notice
        title="요청을 찾을 수 없어요"
        body="삭제됐거나 주소가 잘못된 요청이에요."
        action={<LinkButton href="/parts">부품 요청 게시판으로</LinkButton>}
      />
    );
  } else if (load.status === "failed") {
    body = (
      <Notice
        title="요청을 불러오지 못했어요"
        body="잠시 후 다시 시도해 주세요."
        action={<Button onClick={retry}>다시 시도</Button>}
      />
    );
  } else {
    const { request } = load;
    const isOwner = session?.accountId === request.requesterId;
    body = (
      <article className="flex flex-col gap-6" aria-labelledby="part-request-title">
        <header className="flex flex-col gap-3">
          <div className="flex flex-wrap items-center gap-2">
            <PartRequestStatusBadge status={request.status} />
            {request.setNumber !== null ? (
              <Link
                href={`/sets/${encodeURIComponent(request.setNumber)}`}
                className="font-mono text-sm font-semibold text-brand-700 hover:underline"
              >
                #{request.setNumber} 세트
              </Link>
            ) : (
              <span className="text-sm text-neutral-500">세트 지정 없음</span>
            )}
          </div>
          <Heading level={1} id="part-request-title">
            {partRequestTitle(request)}
          </Heading>
          <Text size="sm" tone="muted">
            {isOwner ? "내 요청" : `요청자 ${requesterLabel(request.requesterId)}`} ·{" "}
            <RequestedAgo iso={request.createdAt} />
            {request.closedAt !== null ? (
              <>
                {" "}
                · 마감 <RequestedAgo iso={request.closedAt} />
              </>
            ) : null}
          </Text>
        </header>

        <section aria-labelledby="part-request-items" className="flex flex-col gap-2">
          <h2 id="part-request-items" className="text-sm font-semibold text-neutral-800">
            찾는 부품 {request.items.length}종
          </h2>
          <PartItemsTable items={request.items} />
        </section>

        {request.note.length > 0 ? (
          <section aria-labelledby="part-request-note" className="flex flex-col gap-2">
            <h2 id="part-request-note" className="text-sm font-semibold text-neutral-800">
              메모
            </h2>
            <p className="whitespace-pre-wrap leading-relaxed text-neutral-700">{request.note}</p>
          </section>
        ) : null}

        {isOwner ? (
          <section
            aria-labelledby="part-request-owner"
            className="flex flex-col gap-3 rounded-lg border border-neutral-200 bg-white p-4"
          >
            <div className="flex flex-col gap-0.5">
              <h2 id="part-request-owner" className="text-sm font-semibold text-neutral-900">
                내 요청 관리
              </h2>
              <p className="text-xs leading-relaxed text-neutral-500">
                {request.status === "open"
                  ? "부품을 구했다면 마감해 주세요. 게시판의 '찾는 중' 목록에서 빠져요."
                  : "마감된 요청이에요. 필요 없으면 삭제할 수 있어요."}
              </p>
            </div>
            <PartRequestOwnerActions
              request={request}
              onClosed={(closed) => setLoad({ status: "ready", request: closed })}
              onDeleted={() => router.push("/parts?status=mine")}
            />
          </section>
        ) : request.status === "open" ? (
          <div className="flex flex-col gap-2">
            <HelpPartRequestButton
              requestId={request.id}
              requesterId={request.requesterId}
              requestTitle={partRequestTitle(request)}
              setNumber={request.setNumber}
            />
            <Text size="sm" tone="muted">
              요청자와 1:1 대화가 열려요. 거래 방법과 가격은 대화에서 정해 주세요.
            </Text>
          </div>
        ) : (
          <p className="rounded-lg bg-neutral-100 px-4 py-3 text-sm text-neutral-600">
            요청자가 부품을 구해 마감한 요청이에요.
          </p>
        )}
      </article>
    );
  }

  const setNumber = load.status === "ready" ? load.request.setNumber : null;

  return (
    <Container width="md">
      <div className="flex flex-col gap-6 pt-8 pb-16">
        <nav aria-label="탐색 경로" className="text-sm text-neutral-500">
          <Link href="/parts" className="hover:text-brand-600">
            부품 요청
          </Link>
          {setNumber !== null ? (
            <>
              <span className="mx-2" aria-hidden="true">
                ›
              </span>
              <Link href={partRequestsHref({ setNumber })} className="hover:text-brand-600">
                #{setNumber}
              </Link>
            </>
          ) : null}
        </nav>
        {body}
      </div>
    </Container>
  );
}

function Notice({
  title,
  body,
  action,
}: {
  readonly title: string;
  readonly body: string;
  readonly action: ReactNode;
}) {
  return (
    <div className="flex flex-col items-start gap-4 rounded-lg border border-neutral-200 bg-white p-6">
      <div className="flex flex-col gap-1.5">
        <Text weight="semibold">{title}</Text>
        <Text tone="secondary">{body}</Text>
      </div>
      {action}
    </div>
  );
}
