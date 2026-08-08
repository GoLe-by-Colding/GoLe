"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { fetchAdminReports, removeAdminPost, takedownListing } from "@entities/admin";
import { useSession } from "@entities/user";
import { ReasonPrompt, useModerationAction } from "@features/admin-moderation";
import { Badge, Button } from "@shared/ui";

const COLLAPSE_KEY = "gole.adminBar.collapsed";

/** 현재 경로에서 조치 가능한 대상을 찾는다. 상세 화면일 때만 컨텍스트 조치가 열린다. */
function detectTarget(pathname: string): { kind: "listing" | "post"; id: string } | null {
  const listing = /^\/listings\/([^/]+)$/.exec(pathname);
  if (listing?.[1] !== undefined) {
    return { kind: "listing", id: listing[1] };
  }
  const post = /^\/community\/([^/]+)$/.exec(pathname);
  if (post?.[1] !== undefined && post[1] !== "new") {
    return { kind: "post", id: post[1] };
  }
  return null;
}

/**
 * 온사이트 어드민 모드. (요구사항 1.6, 1.7)
 *
 * 운영자가 콘솔과 일반 화면을 오가지 않아도 되도록, 보고 있는 그 화면에서 바로 조치하게 한다.
 * 매물 상세를 보다가 가품이라고 판단하면 그 자리에서 내릴 수 있다.
 *
 * ADMIN이 아니면 아무것도 렌더링하지 않는다 — 일반 사용자에게는 존재 자체가 드러나지 않는다.
 */
export function AdminBar() {
  const { session } = useSession();
  const pathname = usePathname();
  const router = useRouter();
  const isAdmin = session?.role === "ADMIN";
  const token = session?.sessionToken ?? null;

  // 접힘 상태는 첫 렌더에 곧바로 반영해야 깜빡임이 없다. 서버 렌더에는 window가 없으므로 방어한다.
  const [collapsed, setCollapsed] = useState<boolean>(() =>
    typeof window === "undefined" ? false : window.localStorage.getItem(COLLAPSE_KEY) === "1",
  );
  const [pendingReports, setPendingReports] = useState(0);

  const refresh = useCallback(() => router.refresh(), [router]);
  const action = useModerationAction(refresh);

  useEffect(() => {
    if (token === null || !isAdmin) {
      return;
    }
    let active = true;
    void fetchAdminReports(token, 100, "PENDING")
      .then((rows) => {
        if (active) {
          setPendingReports(rows.length);
        }
      })
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, [token, isAdmin, pathname]);

  // 콘솔 안에서는 중복이므로 띄우지 않는다.
  if (!isAdmin || token === null || pathname.startsWith("/admin")) {
    return null;
  }

  function toggleCollapsed() {
    setCollapsed((prev) => {
      const next = !prev;
      window.localStorage.setItem(COLLAPSE_KEY, next ? "1" : "0");
      return next;
    });
  }

  const target = detectTarget(pathname);

  return (
    <>
      <div className="fixed inset-x-0 bottom-0 z-40 flex justify-center px-3 pb-3 print:hidden">
        <div className="flex items-center gap-2 rounded-full border border-white/10 bg-neutral-900/95 px-3 py-2 text-sm text-white shadow-lg backdrop-blur">
          <Badge tone="brand">ADMIN</Badge>

          {collapsed ? null : (
            <>
              <Link href="/admin" className="px-1 font-medium hover:text-accent-300">
                콘솔
              </Link>
              <Link
                href="/admin/reports"
                className="flex items-center gap-1 px-1 font-medium hover:text-accent-300"
              >
                신고
                {pendingReports > 0 ? <Badge tone="warning">{pendingReports}</Badge> : null}
              </Link>

              {target !== null ? (
                <>
                  <span aria-hidden="true" className="text-white/25">
                    |
                  </span>
                  {target.kind === "listing" ? (
                    <Button
                      size="sm"
                      variant="danger"
                      onClick={() =>
                        action.ask({
                          title: "이 매물 내리기",
                          target: `매물 ${target.id}`,
                          confirmLabel: "내리기",
                          run: (reason) => takedownListing(token, target.id, reason),
                        })
                      }
                    >
                      이 매물 내리기
                    </Button>
                  ) : (
                    <Button
                      size="sm"
                      variant="danger"
                      onClick={() =>
                        action.ask({
                          title: "이 게시글 삭제",
                          target: `게시글 ${target.id}`,
                          confirmLabel: "삭제",
                          run: (reason) => removeAdminPost(token, target.id, reason),
                        })
                      }
                    >
                      이 게시글 삭제
                    </Button>
                  )}
                </>
              ) : null}
            </>
          )}

          <button
            type="button"
            onClick={toggleCollapsed}
            aria-label={collapsed ? "관리자 바 펼치기" : "관리자 바 접기"}
            className="ml-1 rounded-full px-2 py-0.5 text-white/60 transition hover:bg-white/10 hover:text-white"
          >
            {collapsed ? "›" : "‹"}
          </button>
        </div>
      </div>

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
    </>
  );
}
