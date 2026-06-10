import Link from "next/link";
import { Container, Logo } from "@shared/ui";

const NAV: ReadonlyArray<{ readonly href: string; readonly label: string }> = [
  { href: "/search", label: "탐색" },
  { href: "/prices", label: "시세" },
  { href: "/community", label: "커뮤니티" },
  { href: "/collection", label: "컬렉션" },
];

const LEGAL: ReadonlyArray<{ readonly href: string; readonly label: string }> = [
  { href: "/terms", label: "이용약관" },
  { href: "/privacy", label: "개인정보처리방침" },
];

/**
 * 전역 푸터 — 딥 오션(고래가 사는 깊은 바다) 다크 네이비.
 * 브릭 스터드 패턴 + 골드 분수 스트립으로 브랜드 컨셉을 마무리한다.
 * 레고 상표 고지(비후원/비승인) + 이용약관/개인정보처리방침 링크 포함.
 */
export function SiteFooter() {
  return (
    <footer className="ocean-surface relative mt-20 overflow-hidden text-white">
      {/* 브릭 코스 스트립 — 수면 위 골드 라인 */}
      <div
        aria-hidden="true"
        className="h-1 bg-gradient-to-r from-brand-500 via-accent-400 to-brand-500"
      />
      {/* 스터드 패턴 오버레이 */}
      <div
        aria-hidden="true"
        className="stud-pattern pointer-events-none absolute inset-0 text-white/[0.04]"
      />

      <Container width="xl" className="relative">
        <div className="flex flex-col gap-7 py-12">
          <div className="flex flex-wrap items-center justify-between gap-4">
            <Logo size={30} className="text-lg text-white" accentClassName="text-accent-400" />
            <nav className="flex flex-wrap gap-x-6 gap-y-2">
              {NAV.map((n) => (
                <Link
                  key={n.href}
                  href={n.href}
                  className="text-sm font-medium text-brand-200/80 transition-colors hover:text-white"
                >
                  {n.label}
                </Link>
              ))}
            </nav>
          </div>

          <p className="max-w-md text-sm leading-relaxed text-brand-200/70">
            깊은 바다에서 건져 올린 브릭 — 체결가 시세와 에스크로 안전거래로 레고를 가장 합리적으로
            거래하세요.
          </p>

          {/* LEGO® 상표 고지 (IP 안전 콘텐츠) */}
          <p className="max-w-3xl text-xs leading-relaxed text-brand-300/50">
            본 사이트는 LEGO Group이 후원·승인·운영하는 사이트가 아닙니다. 상품 정보의 세트명·번호는
            식별 목적의 텍스트이며, 상품 사진은 판매자가 직접 촬영해 등록한 이미지입니다. 공식
            페이지 링크는 외부 사이트로 연결됩니다.
          </p>

          <div className="flex flex-wrap items-center justify-between gap-3 border-t border-white/10 pt-6">
            <span className="text-xs text-brand-300/60">
              © {new Date().getFullYear()} GoLe. 레고 중고거래 · 커뮤니티 플랫폼.
            </span>
            <nav className="flex flex-wrap gap-x-4 gap-y-1">
              {LEGAL.map((l) => (
                <Link
                  key={l.href}
                  href={l.href}
                  className="text-xs text-brand-300/60 transition-colors hover:text-white"
                >
                  {l.label}
                </Link>
              ))}
            </nav>
          </div>
        </div>
      </Container>
    </footer>
  );
}
