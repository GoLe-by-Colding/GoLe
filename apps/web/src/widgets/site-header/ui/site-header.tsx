"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter, usePathname } from "next/navigation";
import { useSession } from "@entities/user";
import { NotificationBell } from "@features/notification-bell";
import { env } from "@shared/config";
import { Button, Container, LinkButton, Logo } from "@shared/ui";
import { HeaderSearch } from "./header-search";

const NAV_ITEMS: ReadonlyArray<{ readonly href: string; readonly label: string }> = [
  { href: "/", label: "홈" },
  { href: "/search", label: "탐색" },
  { href: "/prices", label: "시세" },
  { href: "/community", label: "커뮤니티" },
  { href: "/collection", label: "컬렉션" },
  { href: "/chat", label: "채팅" },
];

export function SiteHeader() {
  const router = useRouter();
  const pathname = usePathname();
  const { session, signOut } = useSession();
  const [menuOpen, setMenuOpen] = useState(false);

  function isActive(href: string): boolean {
    return href === "/" ? pathname === "/" : pathname.startsWith(href);
  }

  function handleSignOut() {
    signOut();
    setMenuOpen(false);
    router.push("/");
  }

  return (
    <header className="sticky top-0 z-20 border-b border-neutral-200 bg-white">
      <Container width="xl">
        <div className="flex h-16 items-center gap-8">
          <Link href="/" className="inline-flex items-center text-xl text-neutral-900">
            <Logo size={32} className="text-xl" />
          </Link>
          {/*
            브레이크포인트는 실제 콘텐츠 폭 기준이다. 로고 + 메뉴 7개 + 검색창 + 액션까지
            한 줄에 들어가려면 약 1275px가 필요한데 Container width="xl"의 내용 폭은 1240px다.
            그래서 데스크톱 nav는 lg(1024px), 검색 입력은 xl(1280px)부터만 노출한다.
            (이 값을 낮추면 640~1100px 구간에서 링크 텍스트가 글자 단위로 줄바꿈되며 깨진다.)
          */}
          <nav className="flex shrink-0 items-center gap-1 whitespace-nowrap max-lg:hidden">
            {NAV_ITEMS.map((item) => {
              const active = isActive(item.href);
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  aria-current={active ? "page" : undefined}
                  className={`relative border-b-2 px-3 py-2 text-sm transition-colors ${
                    active
                      ? "border-brand-600 font-semibold text-brand-700"
                      : "border-transparent font-medium text-neutral-500 hover:border-neutral-300 hover:text-neutral-900"
                  }`}
                >
                  {item.label}
                </Link>
              );
            })}
            <a
              href={env.discordInviteUrl}
              target="_blank"
              rel="noreferrer"
              className="rounded-lg px-3 py-2 text-sm font-semibold text-[#5865F2] transition-colors hover:bg-[#F1F2FF] hover:text-[#4752C4]"
            >
              고래방 ↗
            </a>
          </nav>
          {/* 스페이서는 항상 남겨 둔다. 이게 없으면 액션 영역이 왼쪽으로 붙는다. */}
          <div className="flex flex-1 justify-center px-4">
            <div className="flex w-full justify-center max-xl:hidden">
              <HeaderSearch />
            </div>
          </div>
          <div className="flex shrink-0 items-center gap-2 max-sm:hidden">
            {/* 검색 입력이 빠지는 lg~xl 구간에서도 검색 진입점은 남긴다. */}
            <Link
              href="/search"
              aria-label="검색"
              className="hidden h-9 w-9 shrink-0 place-items-center rounded-full text-neutral-500 transition-colors hover:bg-neutral-100 hover:text-neutral-900 lg:max-xl:grid"
            >
              <svg
                width="18"
                height="18"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                aria-hidden="true"
              >
                <circle cx="11" cy="11" r="7" strokeWidth="2" />
                <path d="m20 20-3.2-3.2" strokeWidth="2" strokeLinecap="round" />
              </svg>
            </Link>
            {session ? (
              <div className="inline-flex items-center gap-2">
                {session.role === "ADMIN" ? (
                  <LinkButton href="/admin" size="sm" variant="ghost">
                    관리자
                  </LinkButton>
                ) : null}
                <LinkButton href="/sell" size="sm" variant="accent">
                  판매하기
                </LinkButton>
                <NotificationBell />
                <Link
                  href="/profile"
                  aria-label="내 정보"
                  className="grid h-8 w-8 place-items-center rounded-full bg-brand-50 text-sm font-bold text-brand-700 transition-colors hover:bg-brand-100"
                >
                  {session.accountId.slice(0, 1).toUpperCase()}
                </Link>
                <Button variant="ghost" size="sm" onClick={handleSignOut}>
                  로그아웃
                </Button>
              </div>
            ) : (
              <Button size="sm" onClick={() => router.push("/login")}>
                로그인
              </Button>
            )}
          </div>

          {/* 모바일 햄버거 */}
          <button
            type="button"
            aria-label="메뉴 열기"
            aria-expanded={menuOpen}
            aria-controls="mobile-menu"
            onClick={() => setMenuOpen((v) => !v)}
            className="hidden h-10 w-10 shrink-0 items-center justify-center rounded-lg text-neutral-700 hover:bg-neutral-100 max-lg:inline-flex"
          >
            <span className="relative block h-4 w-5">
              <span
                className={`absolute left-0 block h-0.5 w-5 bg-current transition-all ${menuOpen ? "top-1.5 rotate-45" : "top-0"}`}
              />
              <span
                className={`absolute left-0 top-1.5 block h-0.5 w-5 bg-current transition-opacity ${menuOpen ? "opacity-0" : "opacity-100"}`}
              />
              <span
                className={`absolute left-0 block h-0.5 w-5 bg-current transition-all ${menuOpen ? "top-1.5 -rotate-45" : "top-3"}`}
              />
            </span>
          </button>
        </div>

        {/* 모바일 메뉴 패널 */}
        {menuOpen ? (
          <div id="mobile-menu" className="flex flex-col gap-1 pb-4 lg:hidden">
            <div className="px-1 pb-3">
              <HeaderSearch fullWidth onSubmitted={() => setMenuOpen(false)} />
            </div>
            <nav className="flex flex-col">
              {NAV_ITEMS.map((item) => {
                const active = isActive(item.href);
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    aria-current={active ? "page" : undefined}
                    onClick={() => setMenuOpen(false)}
                    className={`rounded-lg px-3 py-2.5 text-base font-medium ${
                      active
                        ? "bg-brand-50 text-brand-700"
                        : "text-neutral-700 hover:bg-neutral-100"
                    }`}
                  >
                    {item.label}
                  </Link>
                );
              })}
              <a
                href={env.discordInviteUrl}
                target="_blank"
                rel="noreferrer"
                onClick={() => setMenuOpen(false)}
                className="rounded-lg px-3 py-2.5 text-base font-semibold text-[#5865F2] hover:bg-[#F1F2FF]"
              >
                Discord 고래방 ↗
              </a>
            </nav>
            <div className="mt-2 flex flex-col gap-2 border-t border-neutral-100 pt-3 sm:hidden">
              {session ? (
                <>
                  {session.role === "ADMIN" ? (
                    <LinkButton
                      href="/admin"
                      fullWidth
                      variant="ghost"
                      onClick={() => setMenuOpen(false)}
                    >
                      관리자
                    </LinkButton>
                  ) : null}
                  <LinkButton
                    href="/sell"
                    fullWidth
                    variant="accent"
                    onClick={() => setMenuOpen(false)}
                  >
                    판매하기
                  </LinkButton>
                  <LinkButton
                    href="/profile"
                    fullWidth
                    variant="ghost"
                    onClick={() => setMenuOpen(false)}
                  >
                    내 정보
                  </LinkButton>
                  <LinkButton
                    href="/notifications"
                    fullWidth
                    variant="ghost"
                    onClick={() => setMenuOpen(false)}
                  >
                    알림
                  </LinkButton>
                  <Button fullWidth variant="ghost" onClick={handleSignOut}>
                    로그아웃
                  </Button>
                </>
              ) : (
                <Button
                  fullWidth
                  onClick={() => {
                    setMenuOpen(false);
                    router.push("/login");
                  }}
                >
                  로그인
                </Button>
              )}
            </div>
          </div>
        ) : null}
      </Container>
    </header>
  );
}
