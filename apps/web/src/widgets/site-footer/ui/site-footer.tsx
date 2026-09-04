import Link from "next/link";
import { fetchLaunchConfig } from "@entities/launch";
import { analyticsRuntimeConfig, BUSINESS_INFO, isPaymentRuntimeAvailable } from "@shared/config";
import { Container, Logo } from "@shared/ui";
import { AnalyticsSettingsButton } from "./analytics-settings-button";

const NAV: ReadonlyArray<{ readonly href: string; readonly label: string }> = [
  { href: "/search", label: "탐색" },
  { href: "/prices", label: "시세" },
  { href: "/community", label: "커뮤니티" },
  { href: "/chat", label: "대화" },
  { href: "/collection", label: "컬렉션" },
];

const LEGAL: ReadonlyArray<{ readonly href: string; readonly label: string }> = [
  { href: "/terms", label: "이용약관" },
  { href: "/privacy", label: "개인정보처리방침" },
  { href: "/review-policy", label: "후기 운영정책" },
];

/**
 * 전역 푸터 — 브랜드 다크 네이비 단색 표면.
 * 제3자 상표 고지(비후원/비승인) + 이용약관/개인정보처리방침 링크 포함.
 */
export async function SiteFooter() {
  const launch = await fetchLaunchConfig();
  const sellerTradingOpen = launch.sellerIdentityVerificationReady;
  const paymentsOpen = sellerTradingOpen && launch.features.payments && isPaymentRuntimeAvailable();
  return (
    <footer className="mt-20 border-t border-brand-800 bg-brand-950 text-white">
      <Container width="xl">
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
            {paymentsOpen
              ? "깊은 바다에서 건져 올린 브릭 — 체결가 시세와 구매확정 안전거래로 합리적으로 거래하세요."
              : sellerTradingOpen
                ? "깊은 바다에서 건져 올린 브릭 — 매물을 발견하고 판매자와 직접 이야기해 거래하세요."
                : "깊은 바다에서 건져 올린 브릭 — 시세와 매물을 탐색하고 커뮤니티에서 이야기를 나누세요."}
          </p>

          {/* LEGO® 상표 고지 (IP 안전 콘텐츠) */}
          <p className="max-w-3xl text-xs leading-relaxed text-brand-300/50">
            LEGO®는 LEGO Group의 등록상표이며, LEGO Group은 본 사이트를 후원·승인·보증하지 않습니다.
            상품 정보의 세트명·번호는 식별 목적의 텍스트이며, 상품 사진은 판매자가 직접 촬영해
            등록한 이미지입니다. 제조사 페이지 링크는 외부 사이트로 연결됩니다.
          </p>

          {/* 통신판매중개자 고지 (전자상거래법 제20조) */}
          <p className="max-w-3xl text-xs leading-relaxed text-brand-300/50">
            {paymentsOpen
              ? "GoLe는 통신판매중개자로서 거래 당사자가 아니며, 판매자가 등록한 상품 정보 및 거래에 대한 책임은 각 판매자에게 있습니다. 계약된 결제 처리와 구매확정·분쟁 접수 절차를 통해 거래를 지원합니다."
              : sellerTradingOpen
                ? "GoLe는 통신판매중개자로서 거래 당사자가 아니며, 판매자가 등록한 상품 정보 및 거래에 대한 책임은 각 판매자에게 있습니다. 현재는 플랫폼 결제 없이 이용자 간 채팅을 통한 직거래만 지원합니다. 거래 조건과 상품 상태를 직접 확인해 주세요."
                : "GoLe는 현재 판매자 신원확인 절차를 준비 중이며 신규 상품 등록, 새 거래 연결과 플랫폼 결제를 받지 않습니다. 기존 공개 콘텐츠 열람·커뮤니티·운영 문의만 제공합니다."}
          </p>

          <address className="border-t border-white/10 pt-6 text-xs not-italic leading-relaxed text-brand-200/70">
            <dl className="grid max-w-4xl gap-x-8 gap-y-2 sm:grid-cols-2 lg:grid-cols-3">
              <BusinessItem label="상호">{BUSINESS_INFO.name}</BusinessItem>
              <BusinessItem label="대표">{BUSINESS_INFO.representative}</BusinessItem>
              <BusinessItem label="사업자등록번호">{BUSINESS_INFO.registrationNumber}</BusinessItem>
              <BusinessItem label="주소" className="sm:col-span-2 lg:col-span-3">
                {BUSINESS_INFO.address}
              </BusinessItem>
              <BusinessItem label="대표전화">
                <a className="hover:text-white hover:underline" href={`tel:${BUSINESS_INFO.phone}`}>
                  {BUSINESS_INFO.phone}
                </a>
              </BusinessItem>
              <BusinessItem label="개발자">
                <a
                  className="hover:text-white hover:underline"
                  href={`mailto:${BUSINESS_INFO.developerEmail}`}
                >
                  {BUSINESS_INFO.developerEmail}
                </a>
              </BusinessItem>
              <BusinessItem label="사업자">
                <a
                  className="hover:text-white hover:underline"
                  href={`mailto:${BUSINESS_INFO.contactEmail}`}
                >
                  {BUSINESS_INFO.contactEmail}
                </a>
              </BusinessItem>
              <BusinessItem label="호스팅서비스 제공자">
                {BUSINESS_INFO.hostingProvider}
              </BusinessItem>
              <BusinessItem label="사업자정보">
                <a
                  className="hover:text-white hover:underline"
                  href={BUSINESS_INFO.businessVerificationUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  공정거래위원회에서 확인
                </a>
              </BusinessItem>
            </dl>
          </address>

          <div className="flex flex-wrap items-center justify-between gap-3 border-t border-white/10 pt-6">
            <span className="text-xs text-brand-300/60">{BUSINESS_INFO.copyright}</span>
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
              {analyticsRuntimeConfig.provider !== "disabled" ? <AnalyticsSettingsButton /> : null}
            </nav>
          </div>
        </div>
      </Container>
    </footer>
  );
}

function BusinessItem({
  label,
  className,
  children,
}: {
  readonly label: string;
  readonly className?: string;
  readonly children: React.ReactNode;
}) {
  return (
    <div className={className}>
      <dt className="inline font-semibold text-brand-100">{label} </dt>
      <dd className="inline">{children}</dd>
    </div>
  );
}
