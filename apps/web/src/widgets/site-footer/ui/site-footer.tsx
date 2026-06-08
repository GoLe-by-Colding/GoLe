import Link from "next/link";
import { Container } from "@shared/ui";

const NAV: ReadonlyArray<{ readonly href: string; readonly label: string }> = [
  { href: "/search", label: "탐색" },
  { href: "/prices", label: "시세" },
  { href: "/community", label: "커뮤니티" },
  { href: "/collection", label: "컬렉션" },
];

/**
 * 전역 푸터. 레고 상표 고지(비후원/비승인)와 보조 내비를 제공한다. (IP 안전 콘텐츠)
 */
export function SiteFooter() {
  return (
    <footer className="mt-16 border-t border-neutral-200 bg-white">
      <Container width="xl">
        <div className="flex flex-col gap-6 py-10">
          <div className="flex flex-wrap items-center justify-between gap-4">
            <span className="inline-flex items-center gap-2 text-base font-bold text-neutral-900">
              🧱 GoLe
            </span>
            <nav className="flex flex-wrap gap-x-5 gap-y-2">
              {NAV.map((n) => (
                <Link
                  key={n.href}
                  href={n.href}
                  className="text-sm text-neutral-500 transition-colors hover:text-neutral-800"
                >
                  {n.label}
                </Link>
              ))}
            </nav>
          </div>

          <p className="max-w-3xl text-xs leading-relaxed text-neutral-500">
            LEGO®, 레고®, 미니피겨(Minifigure)는 LEGO Group의 상표입니다. 본 사이트는 LEGO
            Group이 후원·승인·운영하는 사이트가 아닙니다. 상품 정보의 세트명·번호는 식별 목적의
            텍스트이며, 상품 사진은 판매자가 직접 촬영해 등록한 이미지입니다. 공식 페이지 링크는
            외부 사이트로 연결됩니다.
          </p>

          <span className="text-xs text-neutral-400">
            © {new Date().getFullYear()} GoLe. 레고 중고거래 · 커뮤니티 플랫폼.
          </span>
        </div>
      </Container>
    </footer>
  );
}
