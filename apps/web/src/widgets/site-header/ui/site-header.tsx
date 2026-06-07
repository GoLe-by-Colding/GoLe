"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useSession } from "@entities/user";
import { Button, Container } from "@shared/ui";
import styles from "./site-header.module.css";

const NAV_ITEMS: ReadonlyArray<{ readonly href: string; readonly label: string }> = [
  { href: "/", label: "홈" },
  { href: "/search", label: "탐색" },
  { href: "/prices", label: "시세" },
  { href: "/community", label: "커뮤니티" },
];

export function SiteHeader() {
  const router = useRouter();
  const { session, signOut } = useSession();

  function handleSignOut() {
    signOut();
    router.push("/");
  }

  return (
    <header className={styles.header}>
      <Container width="xl">
        <div className={styles.inner}>
          <Link href="/" className={styles.brand}>
            🧱 GoLe
          </Link>
          <nav className={styles.nav}>
            {NAV_ITEMS.map((item) => (
              <Link key={item.href} href={item.href} className={styles.navLink}>
                {item.label}
              </Link>
            ))}
          </nav>
          <div className={styles.spacer} />
          <div className={styles.actions}>
            {session ? (
              <div className={styles.userChip}>
                <span className={styles.avatar} aria-hidden="true">
                  {session.accountId.slice(0, 1).toUpperCase()}
                </span>
                <Button variant="ghost" size="sm" onClick={handleSignOut}>
                  로그아웃
                </Button>
              </div>
            ) : (
              <>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => router.push("/login")}
                >
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
