"use client";

import { useEffect, useState } from "react";
import { fetchBlockedChatUserIds, unblockChatUser } from "@entities/chat";
import { useDisplayNames, useSession } from "@entities/user";
import { Button, Card, Container, Heading, LinkButton, Text } from "@shared/ui";

type ListState =
  | { readonly status: "loading" }
  | { readonly status: "failed" }
  | { readonly status: "ready"; readonly ids: readonly string[] };

/** 차단한 사용자 목록. 계정 ID는 보이지 않고 닉네임만 보인다. */
export function BlockedUsersPage() {
  const { session } = useSession();
  const loggedIn = session !== null;
  const [list, setList] = useState<ListState>({ status: "loading" });
  const [attempt, setAttempt] = useState(0);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [failedIds, setFailedIds] = useState<ReadonlySet<string>>(new Set());
  const nameOf = useDisplayNames(list.status === "ready" ? list.ids : []);

  useEffect(() => {
    if (!loggedIn) return;
    let active = true;
    fetchBlockedChatUserIds()
      .then((ids) => {
        if (active) setList({ status: "ready", ids });
      })
      .catch(() => {
        if (active) setList({ status: "failed" });
      });
    return () => {
      active = false;
    };
  }, [loggedIn, attempt]);

  if (session === null) {
    return (
      <Container width="sm">
        <div className="flex flex-col items-start gap-4 py-12">
          <Heading level={1}>차단 관리</Heading>
          <Text tone="secondary">차단한 사용자를 보려면 로그인이 필요합니다.</Text>
          <LinkButton href="/login?returnTo=%2Fprofile%2Fblocks">로그인하러 가기</LinkButton>
        </div>
      </Container>
    );
  }

  async function unblock(id: string) {
    setBusyId(id);
    setFailedIds((prev) => new Set([...prev].filter((failed) => failed !== id)));
    try {
      await unblockChatUser(id);
      setList((prev) =>
        prev.status === "ready" ? { status: "ready", ids: prev.ids.filter((x) => x !== id) } : prev,
      );
    } catch {
      setFailedIds((prev) => new Set(prev).add(id));
    } finally {
      setBusyId(null);
    }
  }

  function retry() {
    setList({ status: "loading" });
    setAttempt((n) => n + 1);
  }

  return (
    <Container width="sm">
      <div className="flex flex-col gap-6 py-8">
        <div className="flex flex-col gap-2">
          <Heading level={1}>차단 관리</Heading>
          <Text tone="secondary">
            차단한 사용자와는 서로의 글·댓글이 보이지 않고 대화·팔로우를 할 수 없어요. 해제해도
            팔로우는 복원되지 않아요.
          </Text>
        </div>

        {list.status === "loading" ? <Text tone="secondary">불러오는 중...</Text> : null}

        {list.status === "failed" ? (
          <div className="flex flex-col items-start gap-3">
            <p role="alert" className="text-sm text-danger">
              차단 목록을 불러오지 못했어요.
            </p>
            <Button variant="secondary" size="sm" onClick={retry}>
              다시 시도
            </Button>
          </div>
        ) : null}

        {list.status === "ready" && list.ids.length === 0 ? (
          <Text tone="secondary">차단한 사용자가 없어요.</Text>
        ) : null}

        {list.status === "ready" && list.ids.length > 0 ? (
          <Card padded className="flex flex-col divide-y divide-neutral-100">
            {list.ids.map((id) => (
              <div
                key={id}
                className="flex flex-wrap items-center justify-between gap-3 py-3 first:pt-0 last:pb-0"
              >
                <div className="flex flex-col gap-1">
                  <Text weight="medium">{nameOf(id, "알 수 없는 사용자")}</Text>
                  {failedIds.has(id) ? (
                    <p role="alert" className="text-sm text-danger">
                      해제하지 못했어요. 다시 시도해 주세요.
                    </p>
                  ) : null}
                </div>
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={busyId === id}
                  onClick={() => void unblock(id)}
                >
                  차단 해제
                </Button>
              </div>
            ))}
          </Card>
        ) : null}
      </div>
    </Container>
  );
}
