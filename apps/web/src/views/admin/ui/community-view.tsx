"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { fetchAdminPosts, removeAdminPost, type AdminPost } from "@entities/admin";
import { useSession } from "@entities/user";
import { ReasonPrompt, useModerationAction } from "@features/admin-moderation";
import { ApiError } from "@shared/api";
import { Badge, Button, Heading, Input, Select, Text } from "@shared/ui";
import { formatDateTime, shortId } from "../model/labels";
import { AdminStatus, AdminTable } from "./table";

/** 커뮤니티 모더레이션 — 작성자 확인 없이 사유와 함께 게시글을 내린다. (요구사항 5) */
export function AdminCommunityView() {
  const { session } = useSession();
  const token = session?.sessionToken ?? null;

  const [status, setStatus] = useState("");
  const [query, setQuery] = useState("");
  // null = 아직 불러오지 않음. 로딩 상태를 파생시켜 effect 안에서 setState를 동기 호출하지 않는다.
  const [rows, setRows] = useState<readonly AdminPost[] | null>(null);
  const [error, setError] = useState<string | undefined>(undefined);

  const load = useCallback(() => {
    if (token === null) {
      return;
    }
    setError(undefined);
    void fetchAdminPosts(token, 100, status === "" ? undefined : status, query)
      .then(setRows)
      .catch((cause: unknown) => {
        setRows([]);
        setError(cause instanceof ApiError ? cause.message : "게시글을 불러오지 못했습니다.");
      });
  }, [query, status, token]);

  useEffect(() => {
    const timer = window.setTimeout(load, 250);
    return () => window.clearTimeout(timer);
  }, [load]);
  const action = useModerationAction(load);

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Heading level={2}>커뮤니티 모더레이션</Heading>
        <div className="flex flex-wrap items-center gap-2">
          <Input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="내용·작성자·주제 검색"
            aria-label="게시글 검색"
            className="w-60"
          />
          <Select
            value={status}
            onChange={(e) => setStatus(e.target.value)}
            aria-label="게시글 상태"
          >
            <option value="">전체 상태</option>
            <option value="PUBLISHED">게시중</option>
            <option value="DELETED">삭제됨</option>
          </Select>
        </div>
      </div>
      <Text tone="muted" size="sm">
        운영자 삭제는 작성자 확인을 거치지 않습니다. 사유는 감사 로그에 남습니다.
      </Text>

      <AdminStatus error={error} loading={rows === null} />

      <AdminTable
        headers={["내용", "작성자", "주제", "상태", "작성", "관리"]}
        alignRight={[5]}
        minWidth={720}
        empty="게시글이 없습니다."
        rowCount={(rows ?? []).length}
      >
        {(rows ?? []).map((p) => (
          <tr key={p.id} className="border-t border-neutral-100">
            <td className="max-w-[280px] truncate px-3 py-2.5">
              <Link href={`/community/${p.id}`} className="text-neutral-800 hover:text-brand-600">
                {p.content.length > 0 ? p.content : "(내용 없음)"}
              </Link>
            </td>
            <td className="px-3 py-2.5 text-neutral-600">{shortId(p.authorId)}</td>
            <td className="px-3 py-2.5">
              <Badge tone="neutral">{p.type}</Badge>
            </td>
            <td className="px-3 py-2.5">
              <Badge tone={p.status === "PUBLISHED" ? "success" : "danger"}>
                {p.status === "PUBLISHED" ? "게시중" : "삭제됨"}
              </Badge>
            </td>
            <td className="px-3 py-2.5 text-xs text-neutral-500">{formatDateTime(p.createdAt)}</td>
            <td className="px-3 py-2.5 text-right">
              {p.status === "DELETED" ? (
                <span className="text-xs text-neutral-400">삭제됨</span>
              ) : (
                <Button
                  size="sm"
                  variant="danger"
                  onClick={() =>
                    action.ask({
                      title: "게시글 삭제",
                      target: p.content.slice(0, 40),
                      confirmLabel: "삭제",
                      run: (reason) => removeAdminPost(token ?? "", p.id, reason),
                    })
                  }
                >
                  삭제
                </Button>
              )}
            </td>
          </tr>
        ))}
      </AdminTable>

      {action.pending !== null ? (
        <ReasonPrompt
          title={action.pending.title}
          target={action.pending.target}
          confirmLabel={action.pending.confirmLabel}
          busy={action.busy}
          error={action.error}
          onConfirm={action.confirm}
          onCancel={action.cancel}
        />
      ) : null}
    </div>
  );
}
