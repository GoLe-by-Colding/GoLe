import type { Metadata } from "next";
import Link from "next/link";
import { BUSINESS_INFO } from "@shared/config";

export const metadata: Metadata = {
  title: "후기 운영정책",
  description:
    "GoLe 거래 후기의 작성 권한, 게시기간, 평점 계산, 숨김 기준과 이의제기 절차를 안내합니다.",
  alternates: { canonical: "/review-policy" },
  openGraph: {
    title: "후기 운영정책 · GoLe",
    description:
      "GoLe 거래 후기의 작성 권한, 게시기간, 평점 계산, 숨김 기준과 이의제기 절차를 안내합니다.",
    url: "/review-policy",
    type: "website",
  },
  robots: { index: true, follow: true },
};

const EFFECTIVE_DATE = "2026년 9월 4일";

export default function ReviewPolicyPage() {
  return (
    <div className="min-h-screen bg-white">
      <article className="mx-auto max-w-3xl px-6 py-16 [word-break:keep-all]">
        <header className="mb-12">
          <p className="mb-2 font-mono text-xs uppercase tracking-widest text-neutral-400">
            Review Policy
          </p>
          <h1 className="text-3xl font-extrabold tracking-tight text-neutral-900">후기 운영정책</h1>
          <p className="mt-3 text-sm text-neutral-500">시행일: {EFFECTIVE_DATE}</p>
        </header>

        <div className="space-y-10 text-sm leading-7 text-neutral-700">
          <Section title="1. 작성 권한과 수집 방법">
            <ul className="list-disc space-y-1 pl-5">
              <li>
                후기 기능이 열린 경우, GoLe에서 완료 상태가 된 주문의 구매자만 해당 주문 판매자에게
                후기를 작성할 수 있습니다. 채팅으로만 진행한 직접 거래에는 후기 작성 권한이 생기지
                않습니다.
              </li>
              <li>
                한 주문에는 후기를 한 번만 작성할 수 있으며 자기 자신에게는 작성할 수 없습니다.
              </li>
              <li>
                작성자가 직접 선택한 1점부터 5점까지의 평점과 1,000자 이하의 후기 내용을 수집합니다.
                판매자는 공개 후기에 1,000자 이하의 답글을 남기거나 수정할 수 있습니다.
              </li>
              <li>후기나 판매자 답글을 대가를 주고 작성·변경하도록 운영하지 않습니다.</li>
            </ul>
          </Section>

          <Section title="2. 게시기간">
            <p>
              판매자 상점에는 숨김 처리되지 않은 최신 후기 최대 100건을 최신순으로 게시합니다. 새
              후기가 등록되어 최신 100건의 범위에서 벗어나거나 아래 공개 중단·삭제 기준에 해당하면
              상점의 공개 후기 목록에서 내려갑니다.
            </p>
            <p>
              공개가 중단된 후기는 판매자 공개 목록과 평점 계산에서 즉시 제외합니다. 분쟁·신고 처리,
              조치 이력 증명 또는 법령상 보존이 필요한 원문은 해당 목적과 개인정보처리방침에 고지한
              기간 동안 접근을 제한해 보관한 뒤 파기하며, 자세한 내용은{" "}
              <Link href="/privacy" className="font-semibold text-brand-700 underline">
                개인정보처리방침
              </Link>
              에 따릅니다.
            </p>
          </Section>

          <Section title="3. 평점 기준과 표시 효과">
            <ul className="list-disc space-y-1 pl-5">
              <li>
                1점은 매우 불만족, 2점은 불만족, 3점은 보통, 4점은 만족, 5점은 매우 만족입니다.
              </li>
              <li>
                판매자 평점은 공개 상태인 최신 후기 최대 100건의 별점을 단순 산술평균해 소수점 첫째
                자리로 반올림합니다. 광고·판매액·판매자 답글 여부에 따른 가중치는 두지 않습니다.
              </li>
              <li>숨김 처리된 후기는 공개 후기 수와 평균 평점에 포함하지 않습니다.</li>
              <li>
                개별 등급과 평균은 판매자 상점·매물의 후기 정보 표시에만 사용하며, 검색 노출·계정
                제한·판매자 혜택을 자동으로 바꾸지 않습니다.
              </li>
            </ul>
          </Section>

          <Section title="4. 공개 중단·삭제 기준">
            <p>
              GoLe는 서비스 화면에서 후기를 삭제하는 조치를 공개 중단(숨김)으로 처리합니다. 신고
              접수 후 관리자가 확인하여 다음 중 하나에 해당하면 공개를 중단할 수 있습니다.
            </p>
            <ul className="list-disc space-y-1 pl-5">
              <li>실제 주문·상품·거래 경험과 관련 없는 내용 또는 허위·조작된 내용</li>
              <li>개인정보 노출, 사칭, 사기 유도 또는 타인의 권리를 침해하는 내용</li>
              <li>불법·음란·혐오·폭력적 표현, 욕설, 반복 게시 또는 서비스 운영 방해</li>
              <li>이용약관이나 관계 법령을 위반하는 내용</li>
            </ul>
            <p>
              낮은 평점, 판매자에게 불리한 평가 또는 정당한 비판이라는 이유만으로 숨기지 않습니다.
              조치할 때에는 관리자 감사 기록에 대상과 사유를 남깁니다.
            </p>
          </Section>

          <Section title="5. 공개 중단·삭제 조치 이의제기">
            <p>
              작성자나 판매자는{" "}
              <Link
                href="/chat?compose=support&category=GENERAL"
                className="font-semibold text-brand-700 underline"
              >
                운영 문의
              </Link>
              에서 후기 식별 정보와 이의 사유를 제출하거나{" "}
              <a
                href={`mailto:${BUSINESS_INFO.contactEmail}`}
                className="font-semibold text-brand-700 underline"
              >
                {BUSINESS_INFO.contactEmail}
              </a>
              로 재검토를 요청할 수 있습니다. 운영자는 원문, 신고 사유와 제출된 소명을 다시 확인해
              공개 중단 유지 여부와 필요한 후속 조치 결과를 안내합니다.
            </p>
          </Section>
        </div>
      </article>
    </div>
  );
}

function Section({
  title,
  children,
}: {
  readonly title: string;
  readonly children: React.ReactNode;
}) {
  return (
    <section>
      <h2 className="mb-3 text-base font-bold text-neutral-900">{title}</h2>
      <div className="space-y-2">{children}</div>
    </section>
  );
}
