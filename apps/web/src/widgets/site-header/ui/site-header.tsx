"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useSession } from "@entities/user";
import { NotificationBell } from "@features/notification-bell";
import { Button, Container, LinkButton, Logo } from "@shared/ui";

const NAV_ITEMS: ReadonlyArray<{ readonly href: string; readonly label: string }> = [
  { href: "/", label: "홈" },
  { href: "/search", label: "탐색" },
  { href: "/prices", label: "시세" },
  { href: "/community", label: "커뮤니티" },
  { href: "/collection", label: "컬렉션" },
];

export function SiteHeader() {
  const router = useRouter();
  const { session, signOut } = useSession();
  const [menuOpen, setMenuOpen] = useState(false);

  function handleSignOut() {
    signOut();
    setMenuOpen(false);
    router.push("/");
  }

  return (
    <header className="sticky top-0 z-20 border-b border-neutral-200/50 bg-white/80 backdrop-blur-xl backdrop-saturate-[1.8]">
      <Container width="xl">
        <div className="flex h-16 items-center gap-8">
          <Link
            href="/"
            className="inline-flex items-center text-xl text-neutral-900"
          >
            <Logo size={32} className="text-xl" />
          </Link>
          <nav className="flex items-center gap-5 max-sm:hidden">
            {NAV_ITEMS.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                className="text-sm font-medium text-neutral-500 transition-colors hover:text-neutral-900"
              >
                {item.label}
              </Link>
            ))}
          </nav>
          <div className="flex-1" />
          <div className="flex items-center gap-2 max-sm:hidden">
            {session ? (
              <div className="inline-flex items-center gap-2">
                {session.role === "ADMIN" ? (
                  <LinkButton href="/admin" size="sm" variant="ghost">
                    관리자
                  </LinkButton>
                ) : null}
                <LinkButton href="/sell" size="sm" variant="secondary">
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
              <>
                <Button variant="ghost" size="sm" onClick={() => router.push("/login")}>
                  로그인
                </Button>
                <Button size="sm" onClick={() => router.push("/signup")}>
                  회원가입
                </Button>
              </>
            )}
          </div>

          {/* 모바일 햄버거 */}
          <button
            type="button"
            aria-label="메뉴 열기"
            aria-expanded={menuOpen}
            aria-controls="mobile-menu"
            onClick={() => setMenuOpen((v) => !v)}
            className="hidden h-10 w-10 items-center justify-center rounded-lg text-neutral-700 hover:bg-neutral-100 max-sm:inline-flex"
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
          <div id="mobile-menu" className="flex flex-col gap-1 pb-4 sm:hidden">
            <nav className="flex flex-col">
              {NAV_ITEMS.map((item) => (
                <Link
                  key={item.href}
                  href={item.href}
                  onClick={() => setMenuOpen(false)}
                  className="rounded-lg px-3 py-2.5 text-base font-medium text-neutral-700 hover:bg-neutral-100"
                >
                  {item.label}
                </Link>
              ))}
            </nav>
            <div className="mt-2 flex flex-col gap-2 border-t border-neutral-100 pt-3">
              {session ? (
                <>
                  {session.role === "ADMIN" ? (
                    <LinkButton href="/admin" fullWidth variant="ghost" onClick={() => setMenuOpen(false)}>
                      관리자
                    </LinkButton>
                  ) : null}
                  <LinkButton href="/sell" fullWidth variant="secondary" onClick={() => setMenuOpen(false)}>
                    판매하기
                  </LinkButton>
                  <LinkButton href="/profile" fullWidth variant="ghost" onClick={() => setMenuOpen(false)}>
                    내 정보
                  </LinkButton>
                  <LinkButton href="/notifications" fullWidth variant="ghost" onClick={() => setMenuOpen(false)}>
                    알림
                  </LinkButton>
                  <Button fullWidth variant="ghost" onClick={handleSignOut}>
                    로그아웃
                  </Button>
                </>
              ) : (
                <>
                  <Button
                    fullWidth
                    variant="secondary"
                    onClick={() => {
                      setMenuOpen(false);
                      router.push("/login");
                    }}
                  >
                    로그인
                  </Button>
                  <Button
                    fullWidth
                    onClick={() => {
                      setMenuOpen(false);
                      router.push("/signup");
                    }}
                  >
                    회원가입
                  </Button>
                </>
              )}
            </div>
          </div>
        ) : null}
      </Container>
    </header>
  );
}
