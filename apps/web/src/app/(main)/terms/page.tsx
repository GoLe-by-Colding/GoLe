import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "이용약관 · GoLe",
  robots: { index: true, follow: true },
};

const EFFECTIVE_DATE = "2025년 1월 1일";

export default function TermsPage() {
  return (
    <main className="min-h-screen bg-white">
      <article className="mx-auto max-w-3xl px-6 py-16 [word-break:keep-all]">
        <header className="mb-12">
          <p className="mb-2 font-mono text-xs uppercase tracking-widest text-neutral-400">
            Terms of Service
          </p>
          <h1 className="text-3xl font-extrabold tracking-tight text-neutral-900">이용약관</h1>
          <p className="mt-3 text-sm text-neutral-500">시행일: {EFFECTIVE_DATE}</p>
        </header>

        <div className="space-y-10 text-sm leading-7 text-neutral-700">
          <Section title="제1조 (목적)">
            <p>
              본 약관은 GoLe(이하 &quot;서비스&quot;)가 제공하는 레고 중고거래·커뮤니티 서비스의
              이용 조건과 절차, 서비스 제공자와 이용자 간의 권리·의무를 규정함을 목적으로 합니다.
            </p>
          </Section>

          <Section title="제2조 (서비스 정의)">
            <p>GoLe는 개인 간 레고 제품 중고거래 중개, 시세 조회, 커뮤니티 기능을 제공합니다.</p>
            <ul className="list-disc space-y-1 pl-5">
              <li>본 서비스는 통신판매중개업자로서 직접 판매자가 아닙니다.</li>
              <li>거래 당사자 간 분쟁에 대해 운영자는 책임을 지지 않습니다.</li>
            </ul>
          </Section>

          <Section title="제3조 (LEGO® 상표 고지)">
            <p>
              LEGO®, 레고®, 미니피겨(Minifigure)는 LEGO Group의 등록상표입니다. 본 사이트는 LEGO
              Group이 후원·승인·운영하는 사이트가 아닙니다. 상품 정보의 세트명·번호는 식별 목적의
              텍스트이며, 상품 사진은 판매자가 직접 촬영해 등록한 이미지입니다. 공식 페이지 링크는
              외부 사이트로 연결됩니다.
            </p>
          </Section>

          <Section title="제4조 (회원 의무)">
            <ul className="list-disc space-y-1 pl-5">
              <li>타인의 개인정보 도용 및 허위 정보 등록 금지</li>
              <li>타인의 권리를 침해하거나 명예를 훼손하는 행위 금지</li>
              <li>레고 공식 이미지·저작물 무단 복제·게시 금지(판매자 직접 촬영 사진만 사용)</li>
              <li>사기·허위 매물 등록 및 에스크로 악용 금지</li>
              <li>서비스 자동화·크롤링·역공학 금지</li>
            </ul>
          </Section>

          <Section title="제5조 (거래 및 에스크로)">
            <p>
              안전결제(에스크로)는 구매자가 구매 확정 전까지 결제 대금을 시스템이 보관하는
              방식입니다. 구매자의 구매 확정 또는 자동 확정(지정 기간 내 미이의) 시 판매자에게
              정산됩니다.
            </p>
            <ul className="list-disc space-y-1 pl-5">
              <li>결제 및 정산 서비스는 PortOne(포트원)을 통해 처리됩니다.</li>
              <li>결제 취소·환불은 구매 확정 전까지만 가능합니다.</li>
            </ul>
          </Section>

          <Section title="제6조 (서비스 제한 및 면책)">
            <ul className="list-disc space-y-1 pl-5">
              <li>운영자는 서비스의 일시 중단, 변경, 종료를 사전 공지 후 시행할 수 있습니다.</li>
              <li>천재지변, 서버 장애 등 불가항력 상황에서의 손해에 대해 책임을 지지 않습니다.</li>
              <li>이용자가 게시한 콘텐츠에 대한 저작권 및 법적 책임은 게시자 본인에게 있습니다.</li>
            </ul>
          </Section>

          <Section title="제7조 (개인정보 처리)">
            <p>
              개인정보 처리에 관한 사항은{" "}
              <Link href="/privacy" className="text-brand-600 underline">
                개인정보처리방침
              </Link>
              에 따릅니다.
            </p>
          </Section>

          <Section title="제8조 (준거법 및 분쟁 해결)">
            <p>
              본 약관은 대한민국 법률에 따라 해석·적용되며, 서비스 이용과 관련한 분쟁은 관할 법원에
              제소함을 원칙으로 합니다.
            </p>
          </Section>

          <Section title="사업자 정보">
            <address className="not-italic">
              <p>상호: GoLe</p>
              <p>운영자: 김승찬</p>
              <p>
                이메일:{" "}
                <a href="mailto:developerkscold@gmail.com" className="underline">
                  developerkscold@gmail.com
                </a>
              </p>
            </address>
          </Section>
        </div>
      </article>
    </main>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section>
      <h2 className="mb-3 text-base font-bold text-neutral-900">{title}</h2>
      <div className="space-y-2">{children}</div>
    </section>
  );
}
