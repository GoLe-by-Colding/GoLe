"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useSession } from "@entities/user";
import { Button, Container, LinkButton } from "@shared/ui";

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

  function handleSignOut() {
    signOut();
    router.push("/");
  }

  return (
    <header className="sticky top-0 z-20 border-b border-neutral-200 bg-white/85 backdrop-blur-md backdrop-saturate-150">
      <Container width="xl">
        <div className="flex h-16 items-center gap-6">
          <Link
            href="/"
            className="inline-flex items-center gap-2 text-lg font-bold tracking-tight text-neutral-900"
          >
            🧱 GoLe
          </Link>
          <nav className="flex items-center gap-5 max-sm:hidden">
            {NAV_ITEMS.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                className="text-sm font-medium text-neutral-600 transition-colors hover:text-neutral-900"
              >
                {item.label}
              </Link>
            ))}
          </nav>
          <div className="flex-1" />
          <div className="flex items-center gap-2">
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
                <span
                  className="grid h-8 w-8 place-items-center rounded-full bg-brand-50 text-sm font-bold text-brand-700"
                  aria-hidden="true"
                >
                  {session.accountId.slice(0, 1).toUpperCase()}
                </span>
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
        </div>
      </Container>
    </header>
  );
}
