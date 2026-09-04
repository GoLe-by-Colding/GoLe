import type { Metadata } from "next";
import Link from "next/link";
import { BUSINESS_INFO } from "@shared/config";

export const metadata: Metadata = {
  title: "이용약관",
  description: "GoLe 브릭 중고거래·커뮤니티 서비스의 이용 조건과 거래 원칙을 안내합니다.",
  alternates: { canonical: "/terms" },
  openGraph: {
    title: "이용약관 · GoLe",
    description: "GoLe 브릭 중고거래·커뮤니티 서비스의 이용 조건과 거래 원칙을 안내합니다.",
    url: "/terms",
    type: "website",
  },
  robots: { index: true, follow: true },
};

const EFFECTIVE_DATE = "2026년 9월 4일";

export default function TermsPage() {
  return (
    <div className="min-h-screen bg-white">
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
              본 약관은 GoLe(이하 &ldquo;서비스&rdquo;)가 제공하는 브릭 중고거래·커뮤니티 서비스의
              이용 조건과 절차, 서비스 제공자와 이용자 간의 권리·의무를 규정함을 목적으로 합니다.
            </p>
          </Section>

          <Section title="제2조 (서비스 정의)">
            <p>GoLe는 개인 간 브릭 제품 중고거래 중개, 시세 조회, 커뮤니티 기능을 제공합니다.</p>
            <ul className="list-disc space-y-1 pl-5">
              <li>GoLe는 거래를 연결하는 통신판매중개자이며 개별 상품의 판매자가 아닙니다.</li>
              <li>
                상품 정보와 계약 이행의 일차적 책임은 해당 판매자에게 있습니다. 다만 GoLe는 관련
                법령에 따라 신원정보 제공, 신고 접수, 원인·피해 파악과 분쟁 해결에 필요한 조치를
                수행하며 법령상 책임을 배제하지 않습니다.
              </li>
            </ul>
          </Section>

          <Section title="제3조 (LEGO® 상표 및 IP 고지)">
            <p>
              LEGO®는 LEGO Group의 등록상표이며, LEGO Group은 본 사이트를 후원·승인·보증하지
              않습니다. 상품 정보의 세트명·번호는 식별 목적의 텍스트이며, 상품 사진은 판매자가 직접
              촬영해 등록한 이미지입니다. 제조사 페이지 링크는 외부 사이트로 연결됩니다.
            </p>
          </Section>

          <Section title="제4조 (이미지 활용 원칙)">
            <p>서비스에 업로드하는 모든 이미지는 아래 원칙을 준수해야 합니다.</p>
            <ul className="list-disc space-y-1 pl-5">
              <li>
                <strong>직접 촬영 의무:</strong> 매물 사진은 판매자가 실물을 직접 촬영한 사진만
                사용할 수 있습니다. 제조사 공식 제품 이미지, 공식 홈페이지·카탈로그·광고 이미지의
                무단 복제·게시를 금지합니다.
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
              <li>제조사 공식 이미지·저작물 무단 복제·게시 (제4조 참조)</li>
              <li>
                <strong>가품·위조품(비정품 호환 브릭 포함) 판매 및 정품으로 오인시키는 행위</strong>{" "}
                — 위반 시 매물 삭제·계정 제한 및 상표법 등 관련 법령에 따른 법적 책임이 발생할 수
                있습니다
              </li>
              <li>사기·허위 매물 등록 및 거래·결제 절차 악용</li>
              <li>
                서비스의 안정성을 해치거나 robots.txt·운영자 허가를 위반하는 자동화·크롤링 및
                비정상적인 역공학
              </li>
              <li>타인 사칭 또는 사기 목적 연락처 교환</li>
            </ul>
            <p>
              가품·이미지 도용·사기가 의심되는 매물이나 게시글은 각 상세 화면의 신고하기 기능으로
              접수할 수 있으며, 운영자는 확인 후 게시 중단(notice &amp; takedown) 등 필요한 조치를
              취합니다.
            </p>
          </Section>

          <Section title="제7조 (거래 및 결제)">
            <p>
              판매자 신원확인 절차가 준비되지 않은 단계에서는 신규 상품 등록, 새 거래 연결과 플랫폼
              결제를 받지 않으며 기존 공개 콘텐츠 열람·커뮤니티·운영 문의만 제공합니다. 거래 기능이
              열리고 결제 기능만 비활성화된 단계에서는 GoLe가 거래대금을 수취·보관·정산하지 않고,
              이용자가 채팅으로 조건을 협의하는 직접 거래를 지원합니다. 결제 기능이 활성화되면 거래
              화면에 고지된 결제대행·결제수단 사업자를 통해 승인·취소·환불을 처리합니다. 판매자
              지급은 C2C 거래에 적용되는 별도 계약과 운영 검증이 완료된 방식으로만 제공합니다.
            </p>
            <ul className="list-disc space-y-1 pl-5">
              <li>
                현재 활성화된 거래 방식, 결제수단과 처리 사업자는 주문 전 화면에서 확인합니다.
              </li>
              <li>
                수수료, 예상 지급액, 취소·환불 가능 시점은 주문 전 고지와 주문 상태에 따릅니다.
              </li>
              <li>
                거래 관련 불만·분쟁은 서비스의 신고 또는 운영 문의 기능으로 접수할 수 있으며, GoLe는
                거래 기록과 제출 자료를 바탕으로 필요한 조치를 진행합니다.
              </li>
              <li>
                거래 상대방의 전화번호와 대화 내용은 계약 이행·배송·분쟁 대응 목적으로만 이용하고,
                목적 달성 후 지체 없이 삭제해야 합니다. 마케팅·재판매 등 목적 외 이용을 금지합니다.
              </li>
            </ul>
            <section
              id="complaint-resolution-policy"
              className="mt-5 scroll-mt-24 rounded-xl border border-neutral-200 bg-neutral-50 p-4"
            >
              <h3 className="font-bold text-neutral-900">불만·분쟁 처리기준</h3>
              <ol className="mt-2 list-decimal space-y-1 pl-5">
                <li>
                  각 상세 화면의 신고 또는 운영 문의로 접수하며, 문의 제목·내용과 관련
                  매물·주문·대화 기록을 확인합니다.
                </li>
                <li>
                  필요한 경우 거래 당사자에게 사실관계와 자료 제출을 요청하고, 서비스 기록과 제출
                  자료를 함께 검토합니다.
                </li>
                <li>
                  접수 후 원인 등을 조사해 3영업일 이내에 진행 경과를 알리고, 10영업일 이내에 조사
                  결과 또는 처리방안을 알립니다. 이 기한은 분쟁 자체의 종결을 보장하는 기간이
                  아니며, 추가 확인이 필요하면 그 사유와 다음 처리방안을 안내합니다.
                </li>
                <li>
                  확인 결과와 관련 법령·본 약관에 따라 콘텐츠 공개 중단, 계정·거래 기능 제한, 당사자
                  시정 안내 등 필요한 조치를 적용하고, 이의가 있으면 운영 문의로 재검토를 요청할 수
                  있습니다.
                </li>
              </ol>
            </section>
          </Section>

          <Section title="제8조 (서비스 제한 및 면책)">
            <ul className="list-disc space-y-1 pl-5">
              <li>
                운영자는 서비스의 일시 중단, 변경, 종료를 사전에 알립니다. 보안 사고나 긴급 장애처럼
                사전 고지가 어려운 경우에는 조치 후 지체 없이 알립니다.
              </li>
              <li>
                천재지변 등 합리적으로 통제하기 어려운 사유로 발생한 손해는 관련 법령이 허용하는
                범위에서 책임이 제한됩니다.
              </li>
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
              <p>상호: {BUSINESS_INFO.name}</p>
              <p>대표: {BUSINESS_INFO.representative}</p>
              <p>사업자등록번호: {BUSINESS_INFO.registrationNumber}</p>
              <p>주소: {BUSINESS_INFO.address}</p>
              <p>
                대표전화:{" "}
                <a href={`tel:${BUSINESS_INFO.phone}`} className="underline">
                  {BUSINESS_INFO.phone}
                </a>
              </p>
              <p>
                개발자:{" "}
                <a href={`mailto:${BUSINESS_INFO.developerEmail}`} className="underline">
                  {BUSINESS_INFO.developerEmail}
                </a>
              </p>
              <p>
                사업자:{" "}
                <a href={`mailto:${BUSINESS_INFO.contactEmail}`} className="underline">
                  {BUSINESS_INFO.contactEmail}
                </a>
              </p>
              <p>호스팅서비스 제공자: {BUSINESS_INFO.hostingProvider}</p>
              <p>
                <a
                  href={BUSINESS_INFO.businessVerificationUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="underline"
                >
                  공정거래위원회 사업자정보 확인
                </a>
              </p>
            </address>
          </Section>
        </div>
      </article>
    </div>
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
