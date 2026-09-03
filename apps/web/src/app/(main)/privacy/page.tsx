import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "개인정보처리방침 · GoLe",
  robots: { index: true, follow: true },
};

const EFFECTIVE_DATE = "2026년 9월 3일";

export default function PrivacyPage() {
  return (
    <main className="min-h-screen bg-white">
      <article className="mx-auto max-w-3xl px-6 py-16 [word-break:keep-all]">
        <header className="mb-12">
          <p className="mb-2 font-mono text-xs uppercase tracking-widest text-neutral-400">
            Privacy Policy
          </p>
          <h1 className="text-3xl font-extrabold tracking-tight text-neutral-900">
            개인정보처리방침
          </h1>
          <p className="mt-3 text-sm text-neutral-500">시행일: {EFFECTIVE_DATE}</p>
        </header>

        <div className="space-y-10 text-sm leading-7 text-neutral-700">
          <p>
            GoLe 운영자(이하 &quot;운영자&quot;)는 개인정보 보호법을 준수하며, 이용자의 개인정보를
            보호하기 위해 본 방침을 수립·공개합니다.
          </p>

          <Section title="1. 수집하는 개인정보 항목">
            <ul className="list-disc space-y-1 pl-5">
              <li>회원가입 시: 이메일, 비밀번호(BCrypt 해시 저장)</li>
              <li>소셜 로그인 시: OAuth 프로필 이메일(Google/Kakao/Naver)</li>
              <li>
                <strong>매물·커뮤니티 이미지:</strong> 판매자·이용자가 직접 촬영해 업로드한 상품
                사진 및 커뮤니티 게시 이미지. 서버(MinIO)에 저장되며, 공개 URL로 서빙됩니다. 본
                서비스는 LEGO 공식 이미지를 수집·저장하지 않습니다.
              </li>
              <li>자동 수집: 접속 IP, 서비스 이용 로그, 세션 토큰</li>
            </ul>
          </Section>

          <Section title="2. 개인정보 수집 및 이용 목적">
            <ul className="list-disc space-y-1 pl-5">
              <li>회원 식별, 로그인, 계정 관리</li>
              <li>중고거래 대화 및 결제 기능 활성 시 결제 승인·구매확정·판매자 지급 관리</li>
              <li>거래 후기·시세 통계·커뮤니티 기능 제공</li>
              <li>부정 이용 방지 및 보안 운영</li>
            </ul>
          </Section>

          <Section title="3. 개인정보 보유 및 이용 기간">
            <ul className="list-disc space-y-1 pl-5">
              <li>
                회원 정보: 회원 탈퇴 시 지체 없이 파기. 단, 분쟁·부정 이용 방지나 법령상 보존 의무가
                있는 정보는 해당 목적과 기간 동안 분리 보관
              </li>
              <li>접속 로그·IP: 3개월 (통신비밀보호법)</li>
              <li>표시·광고 기록: 6개월</li>
              <li>계약·청약철회 및 대금결제·재화 공급 기록: 5년</li>
              <li>소비자 불만·분쟁처리 기록: 3년</li>
            </ul>
            <p className="text-neutral-500">
              위 전자상거래 기록은 플랫폼 결제 등 해당 거래 기능이 활성화되어 실제 기록이 생성된
              경우에만 적용합니다.
            </p>
          </Section>

          <Section title="4. 개인정보의 제3자 제공">
            <p>
              운영자는 원칙적으로 이용자의 개인정보를 외부에 제공하지 않습니다. 법령에 근거하거나
              수사기관의 적법한 요청이 있는 경우에만 제공합니다.
            </p>
          </Section>

          <Section title="5. 개인정보 처리 위탁">
            <ul className="list-disc space-y-1 pl-5">
              <li>
                결제 기능 활성 시: 결제 화면에 고지된 결제대행·결제수단 사업자 — 결제 승인·취소·환불
                처리
              </li>
              <li>
                이미지 저장: MinIO(자체 운영 서버) — 상품 사진·커뮤니티 이미지 보관. 이용자가
                업로드한 이미지는 서비스 화면에서 조회될 수 있으며, 삭제 요청 시 거래·분쟁 기록의
                법정 보존 필요성을 확인한 뒤 파기합니다.
              </li>
            </ul>
          </Section>

          <Section title="6. 이용자 권리">
            <ul className="list-disc space-y-1 pl-5">
              <li>개인정보 조회·수정·삭제 요청 가능</li>
              <li>만 14세 미만 아동의 회원가입을 받지 않습니다</li>
              <li>
                로그인한 계정의 대화 화면에서 본인 확인이 연결된 요청을 접수할 수 있습니다. 운영팀은
                10일 안의 첫 처리 안내를 목표로 하며, 법령상 보존이 필요한 기록은 보존 사유와 기간을
                별도로 안내합니다.
              </li>
              <li>
                이메일 접수는{" "}
                <a href="mailto:developerkscold@gmail.com" className="text-brand-600 underline">
                  developerkscold@gmail.com
                </a>
                에서도 받습니다.
              </li>
            </ul>
            <div className="mt-4 grid gap-2 sm:grid-cols-3">
              <RightsLink category="PRIVACY_ACCESS">열람 요청</RightsLink>
              <RightsLink category="PRIVACY_CORRECTION_DELETION">정정·삭제 요청</RightsLink>
              <RightsLink category="PRIVACY_PROCESSING_STOP">처리정지 요청</RightsLink>
            </div>
          </Section>

          <Section title="7. 개인정보의 안전성 확보 조치">
            <ul className="list-disc space-y-1 pl-5">
              <li>비밀번호 BCrypt 해시 저장, 평문 미보관</li>
              <li>불투명 세션 토큰 기반 인증, HTTPS 전 구간 암호화</li>
              <li>관리자 전용 접근 통제(RBAC)</li>
            </ul>
          </Section>

          <Section title="8. 쿠키 및 세션">
            <p>
              로그인 상태 유지를 위해 세션 토큰을 HttpOnly·SameSite 쿠키에 저장합니다. 이 쿠키는
              페이지의 JavaScript에서 읽을 수 없으며, 브라우저 설정에서 저장을 거부하면 로그인 기반
              기능을 이용할 수 없습니다. 화면 표시를 위한 계정 ID와 권한 정보만 브라우저 로컬
              저장소에 보관합니다.
            </p>
          </Section>

          <Section title="9. 개인정보 보호책임자">
            <address className="not-italic">
              <p>운영자: 김승찬</p>
              <p>
                이메일:{" "}
                <a href="mailto:developerkscold@gmail.com" className="underline">
                  developerkscold@gmail.com
                </a>
              </p>
            </address>
          </Section>

          <Section title="10. 방침 변경 고지">
            <p>
              본 방침은 법령 변경 또는 서비스 정책 변경에 따라 수정될 수 있으며, 변경 시 본 페이지를
              통해 공지합니다.
            </p>
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

function RightsLink({ category, children }: { category: string; children: React.ReactNode }) {
  return (
    <Link
      href={`/chat?compose=support&category=${category}`}
      className="rounded-lg border border-brand-200 bg-brand-50 px-3 py-2 text-center text-sm font-semibold text-brand-700 transition-colors hover:border-brand-300 hover:bg-brand-100"
    >
      {children}
    </Link>
  );
}
