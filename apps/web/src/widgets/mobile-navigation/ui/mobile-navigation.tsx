"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@shared/lib";
import { HomeIcon, MessageCircleIcon, PlusIcon, SearchIcon, UserIcon } from "@shared/ui";

const items = [
  { href: "/", label: "홈", icon: HomeIcon },
  { href: "/search", label: "검색", icon: SearchIcon },
  { href: "/sell", label: "판매", icon: PlusIcon },
  { href: "/chat", label: "채팅", icon: MessageCircleIcon },
  { href: "/profile", label: "내 정보", icon: UserIcon },
] as const;

/** 모바일 웹의 주요 동선. 앱에는 RN 탭이 있으므로 서버 셸에서 이 메뉴를 제외한다. */
export function MobileNavigation() {
  const pathname = usePathname();
  return (
    <nav
      aria-label="모바일 주요 메뉴"
      className="fixed inset-x-0 bottom-0 z-30 border-t border-neutral-200 bg-white pb-[env(safe-area-inset-bottom)] lg:hidden print:hidden"
    >
      <div className="mx-auto grid h-16 max-w-lg grid-cols-5">
        {items.map(({ href, label, icon: Icon }) => {
          const active = pathname === href || (href !== "/" && pathname.startsWith(`${href}/`));
          return (
            <Link
              key={href}
              href={href}
              aria-current={active ? "page" : undefined}
              className={cn(
                "flex min-w-0 flex-col items-center justify-center gap-1 text-[11px] font-medium transition-colors focus-visible:outline-2 focus-visible:-outline-offset-4 focus-visible:outline-brand-600",
                active ? "text-brand-700" : "text-neutral-500 hover:text-brand-700",
              )}
            >
              <Icon
                className={cn(
                  "size-5",
                  href === "/sell" && "rounded-md bg-brand-600 p-0.5 text-white",
                )}
              />
              <span>{label}</span>
            </Link>
          );
        })}
      </div>
    </nav>
  );
}
