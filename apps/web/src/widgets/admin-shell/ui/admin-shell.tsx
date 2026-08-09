"use client";

import { useEffect, useState, type ReactNode } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { fetchAdminOverview } from "@entities/admin";
import { useSession } from "@entities/user";
import { cn } from "@shared/lib";
import { Badge, Button, Container, Heading, Text } from "@shared/ui";

interface NavItem {
  readonly href: string;
  readonly label: string;
  readonly exact?: boolean;
}

const NAV: readonly NavItem[] = [
  { href: "/admin", label: "대시보드", exact: true },
  { href: "/admin/reports", label: "신고" },
  { href: "/admin/listings", label: "매물" },
  { href: "/admin/orders", label: "주문" },
  { href: "/admin/settlements", label: "정산" },
  { href: "/admin/community", label: "커뮤니티" },
  { href: "/admin/accounts", label: "회원" },
  { href: "/admin/catalog", label: "카탈로그" },
  { href: "/admin/audit", label: "감사 로그" },
];

/**
 * 운영자 콘솔 셸 — 권한 게이트 + 좌측 내비. (요구사항 1.3, 1.4, 2.1, 2.3)
 *
 * 별도 어드민 앱이 아니라 같은 사이트의 한 영역이므로 사이트 헤더/푸터 안에 들어간다.
 * 비로그인/일반 사용자에게는 안내만 보여주고 운영 데이터를 아예 요청하지 않는다.
 */
export function AdminShell({ children }: { readonly children: ReactNode }) {
  const { session } = useSession();
  const pathname = usePathname();
  const isAdmin = session?.role === "ADMIN";
  const token = session?.sessionToken ?? null;
  const [pendingReports, setPendingReports] = useState(0);

  useEffect(() => {
    if (token === null || !isAdmin) {
      return;
    }
    let active = true;
    void fetchAdminOverview(token)
      .then((overview) => {
        if (active) {
          setPendingReports(overview.pendingReports ?? 0);
        }
      })
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, [token, isAdmin, pathname]);

  if (session === null) {
    return (
      <Gate
        title="관리자 로그인이 필요합니다"
        body="관리자 계정으로 로그인해 주세요."
        href="/login"
        cta="로그인"
      />
    );
  }

  if (!isAdmin) {
    return (
      <Gate
        title="접근 권한이 없습니다"
        body="이 영역은 관리자(ADMIN)만 이용할 수 있습니다."
        href="/"
        cta="홈으로"
      />
    );
  }

  return (
    <Container width="xl">
      <div className="flex flex-col gap-6 pt-8 pb-16">
        <div className="flex items-center gap-3">
          <Heading level={1}>운영자 콘솔</Heading>
          <Badge tone="brand">ADMIN</Badge>
        </div>

        <div className="grid overflow-hidden rounded-2xl border border-neutral-200/70 bg-neutral-50 shadow-soft lg:[grid-template-columns:220px_1fr]">
          <nav
            aria-label="운영자 메뉴"
            className="border-b border-neutral-200/70 bg-white p-3 max-lg:overflow-x-auto lg:border-r lg:border-b-0"
          >
            <ul className="flex gap-1 lg:sticky lg:top-20 lg:flex-col">
              {NAV.map((item) => {
                const active =
                  item.exact === true ? pathname === item.href : pathname.startsWith(item.href);
                return (
                  <li key={item.href}>
                    <Link
                      href={item.href}
                      aria-current={active ? "page" : undefined}
                      className={cn(
                        "flex items-center justify-between gap-2 whitespace-nowrap rounded-lg px-3 py-2.5 text-sm font-medium transition",
                        active
                          ? "bg-brand-600 text-white shadow-brand"
                          : "text-neutral-600 hover:bg-neutral-100 hover:text-neutral-900",
                      )}
                    >
                      {item.label}
                      {item.href === "/admin/reports" && pendingReports > 0 ? (
                        <Badge tone="warning">{pendingReports}</Badge>
                      ) : null}
                    </Link>
                  </li>
                );
              })}
            </ul>
          </nav>

          <div className="min-w-0 p-4 sm:p-6 lg:p-8">{children}</div>
        </div>
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
    <Container width="sm">
      <div className="flex flex-col items-center gap-4 pt-20 pb-16 text-center">
        <Heading level={2}>{title}</Heading>
        <Text tone="muted">{body}</Text>
        <Link href={href}>
          <Button>{cta}</Button>
        </Link>
      </div>
    </Container>
  );
}
