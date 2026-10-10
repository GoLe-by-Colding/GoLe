"use client";

import { useEffect, useState, useSyncExternalStore, type ReactNode } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { fetchAdminOverview } from "@entities/admin";
import { useAdminAccess, useSession } from "@entities/user";
import { cn } from "@shared/lib";
import { Badge, Button, Container, Heading, LinkButton, Text } from "@shared/ui";

interface NavItem {
  readonly href: string;
  readonly label: string;
  readonly exact?: boolean;
}

const NAV: readonly NavItem[] = [
  { href: "/admin", label: "대시보드", exact: true },
  { href: "/admin/launch", label: "출시 단계" },
  { href: "/admin/support", label: "문의" },
  { href: "/admin/reports", label: "신고" },
  { href: "/admin/promotion", label: "홍보 게시", exact: true },
  { href: "/admin/promotion/metrics", label: "홍보 지표" },
  { href: "/admin/listings", label: "매물" },
  { href: "/admin/orders", label: "주문" },
  { href: "/admin/exceptions", label: "예외 큐" },
  { href: "/admin/settlements", label: "정산" },
  { href: "/admin/community", label: "커뮤니티" },
  { href: "/admin/accounts", label: "회원" },
  { href: "/admin/account-deletions", label: "탈퇴 검토" },
  { href: "/admin/catalog", label: "카탈로그" },
  { href: "/admin/design", label: "디자인 토큰" },
  { href: "/admin/mascot", label: "마스코트" },
  { href: "/admin/operations", label: "운영 자동화" },
  { href: "/admin/integrations/tracker", label: "배송 연동" },
  { href: "/admin/audit", label: "감사 로그" },
];

function isCurrent(item: NavItem, pathname: string): boolean {
  return item.exact === true ? pathname === item.href : pathname.startsWith(item.href);
}

function subscribeLocation(onChange: () => void): () => void {
  window.addEventListener("popstate", onChange);
  return () => window.removeEventListener("popstate", onChange);
}

function getLocationSearch(): string {
  return window.location.search;
}

function getServerLocationSearch(): string {
  return "";
}

/**
 * 운영자 콘솔 셸 — 권한 게이트 + 좌측 내비. (요구사항 1.3, 1.4, 2.1, 2.3)
 *
 * 별도 어드민 앱이 아니라 같은 사이트의 한 영역이므로 사이트 헤더/푸터 안에 들어간다.
 * 비로그인/일반 사용자에게는 안내만 보여주고 운영 데이터를 아예 요청하지 않는다.
 */
export function AdminShell({ children }: { readonly children: ReactNode }) {
  const { session } = useSession();
  const pathname = usePathname();
  const token = session?.sessionToken ?? "";
  // 권한은 로컬 role이 아니라 서버 확인으로만 판정한다(확인 전·실패는 닫힌 쪽).
  const { access, retry } = useAdminAccess();
  const [pendingReports, setPendingReports] = useState(0);
  const [unassignedSupportTickets, setUnassignedSupportTickets] = useState(0);
  // 좁은 화면 메뉴는 연 경로에서만 열려 있다 — 다른 화면으로 옮기면 저절로 접힌다.
  const [menuOpenedAt, setMenuOpenedAt] = useState<string | null>(null);
  const search = useSyncExternalStore(
    subscribeLocation,
    getLocationSearch,
    getServerLocationSearch,
  );

  // 운영 데이터는 서버 확인이 끝난 뒤에만 요청한다.
  useEffect(() => {
    if (access !== "granted") {
      return;
    }
    let active = true;
    void fetchAdminOverview(token)
      .then((overview) => {
        if (active) {
          setPendingReports(overview.pendingReports ?? 0);
          setUnassignedSupportTickets(overview.unassignedSupportTickets ?? 0);
        }
      })
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, [access, token, pathname]);

  // 확인 중에도 콘솔과 같은 골격(폭·제목 줄·240px 사이드바 그리드)을 유지한다.
  // 좁은 안내 카드로 떨어뜨리면 확인이 끝나는 순간 640px에서 1280px로 벌어지며
  // 화면 전체가 한 번 출렁인다. 메뉴 항목은 아직 노출하지 않는다.
  if (access === "checking") {
    return (
      <ConsoleFrame badge={<Badge tone="neutral">확인 중</Badge>} nav={null}>
        <div className="flex min-h-[320px] items-center justify-center p-6" aria-busy="true">
          <p role="status" aria-live="polite" className="text-sm text-neutral-500">
            권한을 확인하는 중입니다…
          </p>
        </div>
      </ConsoleFrame>
    );
  }

  if (access === "unauthenticated") {
    return (
      <Gate
        title="관리자 로그인이 필요합니다"
        body="관리자 계정으로 로그인해 주세요."
        href={adminLoginHref(pathname, search)}
        cta="로그인"
      />
    );
  }

  if (access === "forbidden") {
    return (
      <Gate
        title="접근 권한이 없습니다"
        body="이 영역은 관리자(ADMIN)만 이용할 수 있습니다."
        href="/"
        cta="홈으로"
      />
    );
  }

  if (access === "error") {
    return (
      <GateFrame>
        <Heading level={1}>권한을 확인할 수 없습니다</Heading>
        <Text tone="secondary">
          일시적인 네트워크 오류입니다. 로그인 상태는 유지되며 다시 확인할 수 있습니다.
        </Text>
        <div className="flex flex-wrap justify-center gap-3">
          <Button onClick={retry}>다시 시도</Button>
          <LinkButton href="/" variant="secondary">
            홈으로
          </LinkButton>
        </div>
      </GateFrame>
    );
  }

  const currentItem = NAV.find((item) => isCurrent(item, pathname));
  const menuOpen = menuOpenedAt === pathname;
  const pendingTotal = pendingReports + unassignedSupportTickets;

  return (
    <ConsoleFrame
      badge={<Badge tone="brand">ADMIN</Badge>}
      nav={
        <nav
          aria-label="운영자 메뉴"
          className="border-b border-neutral-200/70 bg-white p-3 sm:p-4 lg:border-r lg:border-b-0"
        >
          {/* 메뉴가 19개라 lg 미만에서 격자로 펼치면 본문이 첫 화면 아래(320px에서 y≈670)로 밀린다.
              좁은 화면에서는 지금 화면 이름만 보이는 버튼으로 접고, 누르면 같은 격자를 연다. */}
          <button
            type="button"
            aria-expanded={menuOpen}
            aria-controls="admin-menu-list"
            onClick={() => setMenuOpenedAt(menuOpen ? null : pathname)}
            className="flex h-11 w-full items-center justify-between gap-2 rounded-lg border border-neutral-200 bg-surface-raised px-3 text-sm font-semibold text-neutral-900 lg:hidden"
          >
            <span className="min-w-0 truncate">메뉴 · {currentItem?.label ?? "대시보드"}</span>
            <span className="flex shrink-0 items-center gap-2">
              {pendingTotal > 0 ? <Badge tone="warning">{pendingTotal}</Badge> : null}
              <svg
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                aria-hidden="true"
                className={cn(
                  "transition-transform motion-reduce:transition-none",
                  menuOpen && "rotate-180",
                )}
              >
                <path d="m6 9 6 6 6-6" />
              </svg>
            </span>
          </button>
          <ul
            id="admin-menu-list"
            className={cn(
              "grid grid-cols-2 gap-2 max-lg:mt-3 min-[360px]:grid-cols-3 lg:sticky lg:top-20 lg:flex lg:flex-col",
              !menuOpen && "max-lg:hidden",
            )}
          >
            {NAV.map((item) => {
              const active = isCurrent(item, pathname);
              return (
                <li key={item.href}>
                  <AdminNavigationItem href={item.href} label={item.label} active={active}>
                    {item.href === "/admin/support" && unassignedSupportTickets > 0 ? (
                      <Badge tone="warning">{unassignedSupportTickets}</Badge>
                    ) : null}
                    {item.href === "/admin/reports" && pendingReports > 0 ? (
                      <Badge tone="warning">{pendingReports}</Badge>
                    ) : null}
                  </AdminNavigationItem>
                </li>
              );
            })}
          </ul>
        </nav>
      }
    >
      <div className="min-w-0 p-4 sm:p-6 lg:p-8">{children}</div>
    </ConsoleFrame>
  );
}

/**
 * 콘솔의 바깥 골격. 권한 확인 중과 확인 후가 같은 폭·같은 그리드를 쓰도록
 * 한곳에서만 정의한다. 사이드바가 아직 없어도 240px 트랙은 그대로 유지된다.
 */
function ConsoleFrame({
  badge,
  nav,
  children,
}: {
  readonly badge: ReactNode;
  readonly nav: ReactNode;
  readonly children: ReactNode;
}) {
  return (
    <Container width="xl">
      <div className="flex flex-col gap-6 pt-8 pb-16">
        <div className="flex items-center gap-3">
          <Heading level={1}>운영자 콘솔</Heading>
          {badge}
        </div>

        {/* overflow-clip: 둥근 모서리는 자르되 스크롤 컨테이너를 만들지 않는다. overflow-hidden 이면
            이 격자가 sticky 의 기준이 되어 사이드바·마스코트 미리보기가 붙지 않고 위에 빈 칸이 생긴다. */}
        <div className="grid min-w-0 grid-cols-[minmax(0,1fr)] overflow-clip rounded-2xl border border-neutral-200/70 bg-neutral-50 shadow-soft lg:[grid-template-columns:240px_minmax(0,1fr)]">
          {nav ?? (
            <div
              aria-hidden="true"
              className="bg-white max-lg:h-0 lg:border-r lg:border-neutral-200/70"
            />
          )}
          {children}
        </div>
      </div>
    </Container>
  );
}

/**
 * 콘솔 경로만 복귀 대상으로 넘긴다. 인증 화면이 값을 한 번 더 검증하므로
 * 여기서는 관리자 영역이 아닌 값이 섞여 들어가지 않게만 막는다.
 */
function adminLoginHref(pathname: string, search = ""): string {
  const isAdminArea = pathname === "/admin" || pathname.startsWith("/admin/");
  const target = `${pathname}${search}`;
  return isAdminArea ? `/login?returnTo=${encodeURIComponent(target)}` : "/login";
}

/** Figma `Admin/Navigation Item` (107:5) — 200 × 40 기본 상태를 코드 토큰으로 재현한다. */
function AdminNavigationItem({
  href,
  label,
  active,
  children,
}: {
  readonly href: string;
  readonly label: string;
  readonly active: boolean;
  readonly children?: ReactNode;
}) {
  return (
    <Link
      href={href}
      aria-current={active ? "page" : undefined}
      className={cn(
        "flex h-10 min-w-0 w-full items-center justify-center gap-2 whitespace-nowrap rounded-lg px-2.5 text-sm font-medium transition-colors motion-reduce:transition-none lg:w-[200px] lg:justify-between lg:p-3",
        active
          ? "bg-brand-600 text-white shadow-brand"
          : "bg-surface-raised text-text-secondary hover:bg-neutral-100 hover:text-neutral-900",
      )}
    >
      <span>{label}</span>
      {children}
    </Link>
  );
}

/**
 * 확인 중/차단 화면이 같은 높이를 쓰도록 골격을 공유한다.
 * 판정이 바뀌어도 화면이 위아래로 튀지 않는다.
 */
function GateFrame({
  children,
  busy = false,
}: {
  readonly children: ReactNode;
  readonly busy?: boolean;
}) {
  return (
    <Container width="sm">
      <div
        className="flex min-h-[260px] flex-col items-center justify-center gap-4 pt-20 pb-16 text-center"
        {...(busy ? { "aria-busy": true } : {})}
      >
        {busy ? (
          <p role="status" aria-live="polite" className="text-sm text-neutral-500">
            {children}
          </p>
        ) : (
          children
        )}
      </div>
    </Container>
  );
}

function Gate({
  title,
  body,
  href,
  cta,
}: {
  readonly title: string;
  readonly body: string;
  readonly href: string;
  readonly cta: string;
}) {
  return (
    <GateFrame>
      <Heading level={2}>{title}</Heading>
      <Text tone="muted">{body}</Text>
      <LinkButton href={href}>{cta}</LinkButton>
    </GateFrame>
  );
}
