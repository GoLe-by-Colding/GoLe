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
              본 약관은 GoLe(이하 &ldquo;서비스&rdquo;)가 제공하는 레고 중고거래·커뮤니티 서비스의
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

          <Section title="제3조 (LEGO® 상표 및 IP 고지)">
            <p>
              LEGO®, 레고®, 미니피겨(Minifigure)는 LEGO Group의 등록상표입니다. 본 사이트는 LEGO
              Group이 후원·승인·운영하는 사이트가 아닙니다. 상품 정보의 세트명·번호는 식별 목적의
              텍스트이며, 상품 사진은 판매자가 직접 촬영해 등록한 이미지입니다. 공식 페이지 링크는
              외부 사이트로 연결됩니다.
            </p>
          </Section>

          <Section title="제4조 (이미지 활용 원칙)">
            <p>서비스에 업로드하는 모든 이미지는 아래 원칙을 준수해야 합니다.</p>
            <ul className="list-disc space-y-1 pl-5">
              <li>
                <strong>직접 촬영 의무:</strong> 매물 사진은 판매자가 실물을 직접 촬영한 사진만
                사용할 수 있습니다. LEGO 공식 제품 이미지, 공식 홈페이지·카탈로그·광고 이미지의 무단
                복제·게시를 금지합니다.
              </li>
              <li>
                <strong>저작권 존중:</strong> 타인의 저작물(사진, 일러스트, 디자인 등)을 허가 없이
                업로드하는 것을 금지합니다.
              </li>
              <li>
                <strong>창작품(MOC):</strong> 커뮤니티에 게시하는 창작물 사진은 본인이 직접
                창작·촬영한 경우에만 허용됩니다. 타인 창작물의 무단 도용을 금지합니다.
              </li>
              <li>
                <strong>불법·유해 이미지 금지:</strong> 불법·음란·혐오·폭력적 이미지 업로드를
                금지합니다.
              </li>
            </ul>
            <p className="mt-2 text-neutral-500">
              위 원칙 위반으로 발생하는 저작권·상표권 침해 등 법적 분쟁의 책임은 업로드한 이용자
              본인에게 있습니다.
            </p>
          </Section>

          <Section title="제5조 (콘텐츠 라이선스 및 이용자 권리)">
            <p>
              이용자가 서비스에 게시한 콘텐츠(글·이미지·댓글 등)의 저작권은 이용자 본인에게
              있습니다.
            </p>
            <ul className="list-disc space-y-1 pl-5">
              <li>
                이용자는 서비스 운영 목적(서비스 내 표시, 썸네일 생성, 검색 색인 등)에 한해
                운영자에게 비독점적·무상 이용 허락을 부여합니다.
              </li>
              <li>운영자는 이용자 콘텐츠를 상업적 광고·제3자 판매에 이용하지 않습니다.</li>
              <li>
                이용자는 언제든지 게시물을 삭제하여 이용 허락을 철회할 수 있습니다. 단, 이미
                이루어진 거래와 연관된 기록은 분쟁 해결을 위해 일정 기간 보관될 수 있습니다.
              </li>
            </ul>
          </Section>

          <Section title="제6조 (금지 행위)">
            <ul className="list-disc space-y-1 pl-5">
              <li>타인의 개인정보 도용 및 허위 정보 등록</li>
              <li>타인의 권리를 침해하거나 명예를 훼손하는 행위</li>
              <li>레고 공식 이미지·저작물 무단 복제·게시 (제4조 참조)</li>
              <li>
                <strong>가품·위조품(비정품 호환 브릭 포함) 판매 및 정품으로 오인시키는 행위</strong>{" "}
                — 위반 시 매물 삭제·계정 제한 및 상표법 등 관련 법령에 따른 법적 책임이 발생할 수
                있습니다
              </li>
              <li>사기·허위 매물 등록 및 안전결제 절차 악용</li>
              <li>서비스 자동화·크롤링·역공학</li>
              <li>타인 사칭 또는 사기 목적 연락처 교환</li>
            </ul>
            <p>
              가품·이미지 도용·사기가 의심되는 매물이나 게시글은 각 상세 화면의 신고하기 기능으로
              접수할 수 있으며, 운영자는 확인 후 게시 중단(notice &amp; takedown) 등 필요한 조치를
              취합니다.
            </p>
          </Section>

          <Section title="제7조 (거래 및 안전결제)">
            <p>
              결제 승인 후 구매자가 구매를 확정하기 전까지 판매자 정산을 보류합니다. 결제 승인은
              PortOne과 카카오페이를 통해 처리하며, GoLe가 직접 결제대금을 예치하는 에스크로 상품은
              아닙니다. 구매확정 또는 약관상 자동 확정 요건 충족 후 판매자 정산 절차를 진행합니다.
            </p>
            <ul className="list-disc space-y-1 pl-5">
              <li>결제 및 정산 서비스는 PortOne(포트원)을 통해 처리됩니다.</li>
              <li>결제 취소·환불은 구매 확정 전까지만 가능합니다.</li>
            </ul>
          </Section>

          <Section title="제8조 (서비스 제한 및 면책)">
            <ul className="list-disc space-y-1 pl-5">
              <li>운영자는 서비스의 일시 중단, 변경, 종료를 사전 공지 후 시행할 수 있습니다.</li>
              <li>천재지변, 서버 장애 등 불가항력 상황의 손해에 대해 책임을 지지 않습니다.</li>
              <li>이용자가 게시한 콘텐츠의 저작권 및 법적 책임은 게시자 본인에게 있습니다.</li>
            </ul>
          </Section>

          <Section title="제9조 (개인정보 처리)">
            <p>
              개인정보 처리에 관한 사항은{" "}
              <Link href="/privacy" className="text-brand-600 underline">
                개인정보처리방침
              </Link>
              에 따릅니다.
            </p>
          </Section>

          <Section title="제10조 (준거법 및 분쟁 해결)">
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
