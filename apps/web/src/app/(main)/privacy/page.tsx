import type { Metadata } from "next";
import Link from "next/link";
import { BUSINESS_INFO } from "@shared/config";

export const metadata: Metadata = {
  title: "개인정보처리방침",
  description: "GoLe가 처리하는 개인정보 항목, 이용 목적, 보유 기간과 이용자 권리를 안내합니다.",
  alternates: { canonical: "/privacy" },
  openGraph: {
    title: "개인정보처리방침 · GoLe",
    description: "GoLe가 처리하는 개인정보 항목, 이용 목적, 보유 기간과 이용자 권리를 안내합니다.",
    url: "/privacy",
    type: "website",
  },
  robots: { index: true, follow: true },
};

const EFFECTIVE_DATE = "2026년 9월 4일";

export default function PrivacyPage() {
  return (
    <div className="min-h-screen bg-white">
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
            GoLe를 운영하는 {BUSINESS_INFO.name}(이하 &quot;운영자&quot;)는 개인정보 보호법을
            준수하며, 이용자의 개인정보를 보호하기 위해 본 방침을 수립·공개합니다.
          </p>

          <Section title="1. 수집하는 개인정보 항목">
            <ul className="list-disc space-y-1 pl-5">
              <li>
                계정·인증: 이메일, 비밀번호 해시, 계정 식별자·상태·권한, 이메일·전화번호 인증 상태와
                일시, 로그인 실패·잠금 기록
              </li>
              <li>
                소셜 로그인: 이용자가 선택한 Google·Kakao·Naver OAuth 프로필 이메일과 로그인 제공자.
                소셜 제공자의 접근 토큰은 로그인 처리 후 보관하지 않습니다.
              </li>
              <li>
                프로필·선택 정보: 닉네임, 관심 태그, 전화번호(전화 인증 또는 거래 연락처를 입력한
                경우), 마케팅 수신 동의 여부와 일시
              </li>
              <li>
                정책 확인 이력: 이용약관·개인정보처리방침 버전, 확인·동의 여부, 만 14세 이상 확인,
                확인 경로와 일시
              </li>
              <li>
                제3자 제공 동의 이력: 안내 버전, 동의·철회 결정, 동의가 이루어진 기능 경로와 일시,
                중복 기록 방지를 위한 요청 식별자
              </li>
              <li>
                서비스 이용: 매물 정보·사진·문의, 찜·컬렉션·팔로우, 커뮤니티 게시글·댓글·좋아요,
                채팅·운영 문의·신고·후기와 각 처리 상태
              </li>
              <li>
                문의 처리 보조: 문의방 식별자, 신청 카테고리, 문의 제목·최초 본문과 언어 코드를
                입력으로 이용하고, 추천 카테고리·우선순위·요약·답변 초안·위험 표시·사람 검토 필요
                여부·외부 모델 사용 여부·엔진 버전 및 처리·재시도 상태와 시각을 생성·저장. 제목과
                원문은 기존 문의방·메시지에 저장하며 별도 분석 기록에 다시 복제하지 않습니다.
              </li>
              <li>
                거래 기능 이용 시: 주문 식별자, 거래 당사자 계정 식별자, 상품·금액·상태 이력, 구매자
                연락처, 판매자 CS 연락처, 배송사·운송장·배송 상태, 결제 식별자·수단·상태·취소 및
                정산 기록
              </li>
              <li>
                카드 결제 기능 이용 시: 결제창에서 입력한 이름·이메일·전화번호가 PortOne과 선택한
                결제대행사에 직접 전달됩니다. GoLe 서버는 카드번호·유효기간·CVC를 수집하지 않습니다.
              </li>
              <li>
                <strong>매물·커뮤니티 이미지:</strong> 판매자·이용자가 직접 촬영해 업로드한 상품
                사진 및 커뮤니티 게시 이미지. 비공개 저장소(MinIO)에 저장하고, 공개 콘텐츠의
                이미지만 서비스의 정책 확인이 적용되는 공개 조회 경로로 제공합니다. 본 서비스는
                제조사 공식 이미지를 수집·저장하지 않습니다.
              </li>
              <li>자동 수집: 접속 IP, 요청·오류 등 서비스 이용 로그, 세션 및 OAuth 일회성 토큰</li>
              <li>
                선택적 이용 분석: 이용자가 분석을 허용한 경우에만 식별자를 경로 템플릿으로 치환한
                화면 유형·이전 페이지 출처, 방문 시각·세션, 브라우저·기기·운영체제·언어·화면 정보,
                수집 시점의 IP와 그로부터 산출한 대략적 지역, 무작위 클라이언트 식별자(_ga 쿠키).
                URL의 쿼리·해시와 주문·매물·판매자·게시글·OAuth 경로 식별자, 사용자 작성 제목, 계정
                ID·이메일·전화번호, 채팅·문의·결제 입력 내용은 분석 이벤트로 전송하지 않습니다.
              </li>
            </ul>
          </Section>

          <Section title="2. 개인정보 수집 및 이용 목적">
            <ul className="list-disc space-y-1 pl-5">
              <li>회원 식별, 로그인, 계정 관리</li>
              <li>프로필 완성, 관심 콘텐츠 추천과 컬렉션·팔로우 기능 제공</li>
              <li>중고거래 대화 및 결제 기능 활성 시 결제 승인·구매확정·판매자 지급 관리</li>
              <li>매물·배송·분쟁·운영 문의 처리, 거래 후기·시세 통계·커뮤니티 기능 제공</li>
              <li>정책 동의 증명, 부정 이용 방지, 장애 대응과 보안 운영</li>
              <li>선택적 방문 통계를 통한 콘텐츠 이용 현황 파악과 서비스 품질 개선</li>
            </ul>
          </Section>

          <Section title="3. 개인정보 보유 및 이용 기간">
            <ul className="list-disc space-y-1 pl-5">
              <li>
                회원 정보: 계정 설정에서 이메일 일회성 코드와 확인 문구로 탈퇴를 요청하면 계정
                접근과 모든 로그인 세션을 즉시 종료합니다. 운영자가 진행 중인 거래·정산·분쟁·신고,
                문의 기록, 공개 콘텐츠 수명주기와 법령상 보존 대상을 확인한 뒤 계정 핵심정보와
                개인화 데이터를 파기합니다. 계속 보존해야 하는 기록은 해당 목적과 기간 동안 분리
                보관합니다.
              </li>
              <li>
                법정 보존 대상 거래 기록: 탈퇴 처리 뒤에도 완료·환불 등 종결 주문, 그 주문의 배송과
                지급 완료 정산 기록에는 원래 거래 당사자 계정 식별자가 남으며, 주문의 구매자
                연락처와 배송의 판매자 CS 연락처·운송장 정보도 해당 기록에 남을 수 있습니다. 계정
                핵심정보와 개인화 데이터와는 구분된 주문·배송·정산 컬렉션에 보관하고 거래 당사자와
                업무상 필요한 관리자 경로로 접근을 제한합니다. 계약·대금결제·재화 공급 기록은 법령상
                보존 의무가 적용되는 경우 5년, 소비자 불만·분쟁처리 기록은 3년 동안 해당 증명·분쟁
                대응 목적으로만 보관한 뒤 파기합니다.
              </li>
              <li>
                매물·커뮤니티·컬렉션·팔로우·채팅·문의·신고·후기: 이용자가 삭제하거나 계정 탈퇴·삭제
                요청을 처리할 때까지. 거래·분쟁 또는 타인의 권리와 연결된 기록은 필요한 범위에서
                아래 법정 보존기간까지 분리 보관
              </li>
              <li>
                문의 처리 보조 결과: 연결된 문의 기록의 처리·보유기간 동안 보관하고, 문의 기록을
                파기할 때 함께 파기. 법령상 보존 또는 진행 중인 분쟁 때문에 문의를 분리 보관하는
                경우에는 같은 기간 동안 해당 목적에만 이용
              </li>
              <li>
                이메일 회원가입 인증번호 원문은 메일 발송에만 사용하고 데이터베이스에 저장하지
                않습니다. 가입 인증용 단방향 해시와 발급 시각은 인증 완료·재발급·계정 삭제 시까지
                보관하지만 발급 후 10분 동안만 검증에 사용합니다. 비밀번호 재설정·회원 탈퇴용 단방향
                해시는 사용·재발급 또는 발급 후 10분까지, 휴대전화 인증번호는 사용·재발급 또는 발급
                후 5분까지, OAuth 일회성 상태는 사용 또는 발급 후 10분까지 보관합니다.
              </li>
              <li>로그인 세션: 로그아웃, 유효기간 만료 또는 계정 이용 종료 시까지</li>
              <li>
                선택적 이용 분석: 브라우저의 동의 선택은 이용자가 초기화하거나 브라우저 저장소를
                지울 때까지 보관합니다. _ga 계열 쿠키는 생성 후 최대 90일이며 방문 때 만료일을
                연장하지 않습니다. Google Analytics의 사용자·이벤트 단위 데이터는 운영 속성에서
                2개월로 설정하고, 기간이 끝나면 Google의 월별 삭제 절차에 따라 삭제됩니다. 표준 집계
                보고서는 이 설정의 적용 대상이 아니므로 분석 목적 종료 또는 속성 폐기 시 삭제합니다.
              </li>
              <li>
                접속 로그·IP: 통신비밀보호법상 전기통신사업자의 통신사실확인자료 보관 의무가
                적용되는 경우 해당 법정기간(인터넷 로그·접속지 추적자료는 3개월)
              </li>
              <li>법령상 보존 의무가 적용되는 표시·광고 기록: 6개월</li>
              <li>법령상 보존 의무가 적용되는 계약·청약철회 및 대금결제·재화 공급 기록: 5년</li>
              <li>법령상 보존 의무가 적용되는 소비자 불만·분쟁처리 기록: 3년</li>
            </ul>
            <p className="text-neutral-500">
              위 전자상거래 기록은 플랫폼 결제 등 해당 거래 기능이 활성화되어 실제 기록이 생성된
              경우에만 적용합니다.
            </p>
          </Section>

          <Section id="third-party-provision" title="4. 서비스 내 공개 및 개인정보의 제3자 제공">
            <p>
              운영자는 아래와 같이 이용자가 공개하거나 대화·거래 기능을 이용해 상대방에게 전달되는
              경우를 제외하고 개인정보를 외부에 제공하지 않습니다. 그 밖에는 이용자의 별도 동의,
              법령상 근거 또는 수사기관의 적법한 요청이 있는 경우에만 제공합니다.
            </p>
            <ul className="list-disc space-y-3 pl-5">
              <li>
                <strong>공개 서비스 이용자:</strong> 이용자가 등록한 매물·커뮤니티 글·댓글·사진,
                판매자·작성자 계정 식별자와 반응 수가 매물·커뮤니티 게시 및 검색·거래 상대 탐색을
                위해 공개됩니다. 공개·이용 기간은 게시물 삭제 또는 운영상 게시 중단 때까지입니다.
                공개를 원하지 않으면 게시하지 않을 수 있으며, 이 경우 매물 판매·게시 기능을 이용할
                수 없습니다.
              </li>
              <li>
                <strong>대화방 참여자:</strong> 참여자 계정 식별자, 이용자가 보낸 메시지, 방
                제목·참여자 정보가 대화 제공과 거래 협의를 위해 같은 방의 참여자에게 전달됩니다.
                참여자는 대화·거래·분쟁 처리에 필요한 동안만 이를 이용하고 목적 종료 후 지체 없이
                삭제해야 합니다. 다만 법령상 보존 의무 또는 진행 중인 분쟁에 필요한 경우에는 해당
                기간 동안 분리 보관할 수 있습니다. 동의하지 않아도 가입할 수 있으나 방 생성·초대와
                메시지 기능은 제한됩니다.
              </li>
              <li>
                <strong>거래 기능 이용에 필요한 당사자 정보:</strong> 이용자가 요청한 거래의 계약
                이행·배송·분쟁 대응을 위해 거래 상대방에게 당사자 계정 식별자, 매물·주문 식별자,
                상품·금액·주문 상태·결제수단, 분쟁 사유·상세, 배송사·운송장·배송 상태와 변경 이력이
                제공됩니다. 이 정보는 아래 선택 동의의 대상인 전체 전화번호와 구분됩니다. 거래
                기능을 이용하지 않으면 이 정보는 새로 제공되지 않지만 해당 거래 기능도 이용할 수
                없습니다.
              </li>
              <li>
                <strong>별도 선택 동의에 따른 거래 상대방(구매자 또는 판매자) 연락처 제공:</strong>{" "}
                거래 당사자가 연락처 공개를 명시적으로 요청하면, 정보주체의 현재 동의와 조회자의
                현재 동의를 모두 확인한 뒤 정보주체의 전체 전화번호를 제공합니다. 목적은 거래
                협의·배송 진행 및 분쟁 대응을 위한 연락입니다. 상대방은 목적 종료 후 지체 없이
                삭제해야 하며, 법령상 보존 의무 또는 진행 중인 분쟁에 필요한 경우에만 해당 기간 동안
                분리 보관할 수 있습니다. 동의하지 않아도 가입할 수 있으나 채팅과 거래 상대방 연락처
                조회는 제한됩니다. 주소나 카드번호·유효기간·CVC는 상대방에게 제공하지 않으며,
                마케팅·재판매 등 목적 외 이용은 금지됩니다.
              </li>
            </ul>
            <p className="text-neutral-500">
              현재 결제 기능이 비활성화된 동안에는 새로운 플랫폼 주문을 받지 않으며, 이용자 간 직접
              거래 대화만 제공합니다.
            </p>
          </Section>

          <Section title="5. 개인정보 처리 위탁">
            <ul className="list-disc space-y-1 pl-5">
              <li>
                Google Cloud Platform — 서울 리전의 서버 호스팅·데이터 저장 및 백업 인프라 제공
              </li>
              <li>
                Google LLC(Gmail) — 회원가입 인증번호·비밀번호 재설정·회원 탈퇴 본인확인 메일 발송
              </li>
              <li>
                이용 분석에 동의한 경우 Google LLC(Google Analytics·Google Tag Manager) — 방문 통계
                수집·처리와 분석 태그 전달. Tag Manager에는 Google Analytics 페이지 조회 태그만 두며
                광고·리마케팅·사용자 식별 태그는 사용하지 않습니다.
              </li>
              <li>
                Discord Inc. — 장애·계정·주문·문의 상태 등 운영 알림 전달. 이메일, 문의·채팅 원문,
                비밀번호와 결제 비밀값은 전송하지 않습니다.
              </li>
              <li>
                전화번호 인증 기능 활성 시 솔라피㈜(SOLAPI/CoolSMS) — 휴대전화번호와 일회성
                인증번호를 카카오 알림톡으로 발송
              </li>
              <li>
                결제 기능 활성 시 PortOne 및 결제 화면에 표시된 결제대행·결제수단 사업자 — 결제
                사전등록·승인·취소·환불과 결제 원장 조회
              </li>
              <li>
                MinIO는 Google Cloud 서버 안에서 운영자가 직접 관리하며 별도 외부 수탁자에게
                이미지를 전달하지 않습니다. 이용자가 업로드한 이미지는 서비스 화면에서 조회될 수
                있습니다.
              </li>
            </ul>
          </Section>

          <Section title="6. 개인정보의 국외 처리 위탁">
            <p>
              운영자는 서비스 계약 체결·이행에 필요한 이메일 발송과 운영 알림, 이용자가 선택한 방문
              분석을 위해 개인정보 보호법 제28조의8에 따라 아래와 같이 개인정보를 국외에서 처리
              위탁·보관합니다. 같은 조 제2항의 고지 항목에 맞춰 이전 항목, 국가, 시기·방법, 이전받는
              자, 이용 목적·보유기간과 거부 방법·효과를 안내합니다.
            </p>
            <div className="space-y-4">
              <section className="rounded-xl border border-neutral-200 bg-neutral-50 p-4">
                <h3 className="font-semibold text-neutral-900">Google LLC(Gmail)</h3>
                <dl className="mt-3 grid grid-cols-[max-content_minmax(0,1fr)] gap-x-3 gap-y-2">
                  <dt className="font-semibold text-neutral-800">이전 항목</dt>
                  <dd>
                    수신 이메일 주소, 이메일 인증번호·비밀번호 재설정 또는 회원 탈퇴 본인확인 안내
                  </dd>
                  <dt className="font-semibold text-neutral-800">이전 국가</dt>
                  <dd>
                    미국을 포함해 Google이 서버를 운영하는 전 세계 국가. 실제 처리 위치는 서비스
                    가용성과 운영 상황에 따라 달라질 수 있습니다.
                  </dd>
                  <dt className="font-semibold text-neutral-800">시기·방법</dt>
                  <dd>
                    회원가입 인증·비밀번호 재설정 또는 회원 탈퇴 본인확인 메일 발송 시 TLS로
                    암호화해 전송
                  </dd>
                  <dt className="font-semibold text-neutral-800">수령자·연락처</dt>
                  <dd>
                    Google LLC —{" "}
                    <a
                      href="https://policies.google.com/privacy"
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-brand-600 underline"
                    >
                      Google 개인정보처리방침 및 문의
                    </a>
                  </dd>
                  <dt className="font-semibold text-neutral-800">목적</dt>
                  <dd>회원가입 인증·비밀번호 재설정·회원 탈퇴 본인확인 안내 발송</dd>
                  <dt className="font-semibold text-neutral-800">보유기간</dt>
                  <dd>
                    발송 목적 달성 후 운영자가 운영 Gmail 계정의 발송 내역을 삭제하거나 해당 계정을
                    종료할 때까지. 삭제 이후의 복제본은 Google의 삭제·백업 정책 또는 법령상 필요한
                    기간 동안 제한적으로 보관될 수 있습니다.
                  </dd>
                  <dt className="font-semibold text-neutral-800">거부 방법·효과</dt>
                  <dd>
                    이전 전에 {BUSINESS_INFO.contactEmail}로 거부 의사를 알릴 수 있습니다. 거부하면
                    이메일 회원가입·이메일 인증과 이메일을 이용한 비밀번호 재설정·회원 탈퇴 본인확인
                    기능을 이용할 수 없습니다.
                  </dd>
                </dl>
              </section>
              <section
                id="analytics"
                className="scroll-mt-24 rounded-xl border border-neutral-200 bg-neutral-50 p-4"
              >
                <h3 className="font-semibold text-neutral-900">
                  Google LLC(Google Analytics·Google Tag Manager)
                </h3>
                <dl className="mt-3 grid grid-cols-[max-content_minmax(0,1fr)] gap-x-3 gap-y-2">
                  <dt className="font-semibold text-neutral-800">이전 항목</dt>
                  <dd>
                    식별자를 경로 템플릿으로 치환한 화면 유형·이전 페이지 출처, 방문 시각·세션,
                    브라우저·기기·운영체제·언어·화면 정보, 수집 시점의 IP와 대략적 지역, _ga 계열의
                    무작위 클라이언트 식별자. URL 쿼리·해시와 계정·거래·콘텐츠 식별자, 사용자 작성
                    제목, 계정 ID·이메일·전화번호와 이용자가 입력한 원문은 보내지 않습니다.
                  </dd>
                  <dt className="font-semibold text-neutral-800">이전 국가</dt>
                  <dd>
                    미국을 포함해 Google 또는 그 하위처리자가 시설을 운영하는 국가. 실제 수집·처리
                    위치는 이용자 위치와 서비스 가용성에 따라 달라질 수 있습니다.
                  </dd>
                  <dt className="font-semibold text-neutral-800">시기·방법</dt>
                  <dd>
                    이용자가 화면에서 &quot;분석 허용&quot;을 선택한 이후 페이지를 방문할 때 HTTPS로
                    전송. 동의 전에는 Google 분석·태그 스크립트와 네트워크 요청을 전혀 시작하지
                    않습니다.
                  </dd>
                  <dt className="font-semibold text-neutral-800">수령자·연락처</dt>
                  <dd>
                    Google LLC —{" "}
                    <a
                      href="https://policies.google.com/privacy"
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-brand-600 underline"
                    >
                      Google 분석 서비스 개인정보처리방침 및 문의
                    </a>
                  </dd>
                  <dt className="font-semibold text-neutral-800">목적</dt>
                  <dd>페이지 방문 통계 작성, 콘텐츠 이용 현황 파악과 서비스 품질 개선</dd>
                  <dt className="font-semibold text-neutral-800">보유기간</dt>
                  <dd>
                    _ga 계열 쿠키는 최대 90일, 사용자·이벤트 단위 데이터는 2개월. 이미 생성된 표준
                    집계 보고서는 분석 목적 종료 또는 Analytics 속성 폐기 시까지 남을 수 있습니다.
                  </dd>
                  <dt className="font-semibold text-neutral-800">거부·철회 방법과 효과</dt>
                  <dd>
                    최초 안내에서 거부하거나 푸터의 &quot;분석 설정&quot;에서 언제든 철회·초기화할
                    수 있습니다. 거부·철회해도 회원가입·거래 대화·커뮤니티 등 서비스 기능에는 제한이
                    없습니다. 철회하면 알려진 _ga 계열 쿠키를 삭제하고 이후 수집을 중단하지만, 철회
                    전에 수집된 데이터는 위 보유기간이 끝날 때까지 남을 수 있습니다.
                  </dd>
                </dl>
              </section>
              <section className="rounded-xl border border-neutral-200 bg-neutral-50 p-4">
                <h3 className="font-semibold text-neutral-900">Discord Inc.</h3>
                <dl className="mt-3 grid grid-cols-[max-content_minmax(0,1fr)] gap-x-3 gap-y-2">
                  <dt className="font-semibold text-neutral-800">이전 항목</dt>
                  <dd>
                    계정·주문의 내부 식별자, 문의와 연결되지 않는 무작위 알림 이벤트 ID, 이벤트
                    종류·상태, 오류 참조와 요청 경로. 이메일, 문의·채팅 원문, 비밀번호와 결제
                    비밀값은 전송하지 않습니다.
                  </dd>
                  <dt className="font-semibold text-neutral-800">이전 국가</dt>
                  <dd>
                    미국. 다만 Discord 공개 정책상 이용자와 서비스 제공자의 위치 등 운영 요인에 따라
                    다른 국가의 서버·장비에도 저장될 수 있어 실제 처리 국가는 달라질 수 있습니다.
                  </dd>
                  <dt className="font-semibold text-neutral-800">시기·방법</dt>
                  <dd>장애·계정·주문·문의 관련 운영 이벤트 발생 시 HTTPS 웹훅으로 전송</dd>
                  <dt className="font-semibold text-neutral-800">수령자·연락처</dt>
                  <dd>
                    Discord Inc. —{" "}
                    <a href="mailto:privacy@discord.com" className="text-brand-600 underline">
                      privacy@discord.com
                    </a>
                  </dd>
                  <dt className="font-semibold text-neutral-800">목적</dt>
                  <dd>서비스 장애 감지와 계정·주문·운영 문의 상태 확인 및 대응</dd>
                  <dt className="font-semibold text-neutral-800">보유기간</dt>
                  <dd>
                    운영자가 알림 메시지·채널을 삭제하거나 운영 채널을 종료할 때까지. 삭제 이후의
                    복제본은 Discord의 삭제·백업 정책 또는 법령상 필요한 기간 동안 제한적으로 보관될
                    수 있습니다.
                  </dd>
                  <dt className="font-semibold text-neutral-800">거부 방법·효과</dt>
                  <dd>
                    이전 전에 {BUSINESS_INFO.contactEmail}로 거부 의사를 알릴 수 있습니다. 거부하면
                    운영 알림이 필요한 계정·주문·문의 기능의 이용이 제한될 수 있습니다.
                  </dd>
                </dl>
              </section>
            </div>
            <ul className="list-disc pl-5">
              <li>
                결제 기능을 열기 전에는 PortOne·결제대행사의 실제 처리 위치, 이전 항목·시기·방법,
                수령자 명칭·연락처, 이용 목적·보유기간과 거부 방법·효과를 결제 화면과 본 방침에
                확정해 고지합니다.
              </li>
            </ul>
          </Section>

          <Section title="7. 문의 처리 보조 시스템(AI 기능 고지)">
            <ul className="list-disc space-y-1 pl-5">
              <li>
                운영 문의 접수 시 문의방 식별자, 이용자가 선택한 카테고리, 문의 제목·최초 본문과
                언어 코드를 Google Cloud 서버 내부에서 자체 운영하는 LangGraph 기반 결정론적 규칙
                엔진 <code>rules-v1</code>에 전달합니다. 엔진은 추천 카테고리·우선순위·검토
                신호·관리자용 요약과 답변 초안을 만듭니다.
              </li>
              <li>
                문의 제목·본문은 문의방과 메시지의 원본으로 저장하고 분석 결과 저장소에는 복제하지
                않습니다. 생성된 결과와 엔진 버전은 문의방 ID에 연결해 저장하며, 제3조의 문의 기록
                보유기간 동안 보관한 뒤 문의 기록과 함께 파기합니다.
              </li>
              <li>
                현재 외부 AI 모델이나 외부 AI 사업자에게 문의 내용을 전송하지 않으며, 문의 원문을
                애플리케이션 로그나 Discord 운영 알림에 포함하지 않습니다. 입력이나 생성 결과를 모델
                학습·개선에 사용하지 않으며 그러한 처리 경로도 구현되어 있지 않습니다. 따라서 현재
                학습 활용을 위한 별도 거부 설정은 없습니다. Discord에는 문의 원문 대신 문의와
                연결되지 않는 무작위 알림 이벤트 ID·유형·상태·관리자 경로만 전송합니다.
              </li>
              <li>
                보조 시스템은 답변을 자동 발송하거나 문의를 자동 종결하지 않습니다. 모든 결과는 담당
                관리자가 확인·수정하고 별도 전송 동작을 해야 이용자에게 전달됩니다. 잘못되거나
                부적절한 분류·초안 또는 답변에 대해서는 같은 운영 문의방에 다시 메시지를 보내
                재검토를 요청하거나 {BUSINESS_INFO.contactEmail} 및 제9조 권리 요청 경로로 신고·이의
                제기·처리정지를 요청할 수 있습니다.
              </li>
              <li>
                향후 외부 AI 사업자를 이용할 경우 처리업체, 이전 국가·리전, 보유 기간과 거부 방법을
                사전에 본 방침에 고지하고 필요한 동의를 받습니다.
              </li>
            </ul>
          </Section>

          <Section title="8. 개인정보 파기 절차 및 방법">
            <ul className="list-disc space-y-1 pl-5">
              <li>
                보유기간 경과, 처리 목적 달성 또는 탈퇴·삭제 요청 접수 시 법령상 보존 대상과 진행
                중인 거래·분쟁 기록을 먼저 확인합니다.
              </li>
              <li>
                회원 탈퇴 요청은 로그인된 계정에서 이메일 일회성 코드, 현재 이메일과 정확한 확인
                문구를 함께 검증합니다. 접수와 동시에 계정을 비활성화하고 모든 세션을 폐기하며, 일반
                관리자 정지 해제로는 복구할 수 없습니다.
              </li>
              <li>
                관리자는 요청 ID를 다시 입력하고 보존 검토를 명시적으로 확인해야 파기를 실행할 수
                있습니다. 진행 주문·미완료 정산·처리 중 신고·문의·공개 콘텐츠·미디어·그룹 소유권
                또는 명시적 보존 중지가 하나라도 남으면 시스템이 파기를 거부합니다.
              </li>
              <li>
                계속 보존해야 하는 정보는 다른 정보와 분리해 해당 목적에만 사용하고, 보존기간이
                끝나면 지체 없이 파기합니다.
              </li>
              <li>
                운영 데이터베이스에서는 대상 기록을 삭제하거나 비식별 처리합니다. 접근이 제한된
                백업·스냅샷 사본은 운영 복구 목적으로만 보관하다가 별도 순환 정책이 만료되면
                파기하며, 백업을 복원하는 경우 완료된 삭제 기록을 다시 적용합니다. 종이 문서가
                예외적으로 생성된 경우 분쇄하거나 소각합니다.
              </li>
            </ul>
          </Section>

          <Section title="9. 이용자 권리">
            <ul className="list-disc space-y-1 pl-5">
              <li>개인정보 조회·수정·삭제 요청 가능</li>
              <li>
                회원 탈퇴는 로그인 후 프로필의 계정 보안 화면에서 요청할 수 있습니다. 본인확인 뒤
                즉시 로그아웃되며 처리 요청 ID가 발급됩니다.
              </li>
              <li>만 14세 미만 아동의 회원가입을 받지 않습니다</li>
              <li>
                로그인한 계정의 대화 화면에서 본인 확인이 연결된 요청을 접수할 수 있습니다. 운영팀은
                10일 안의 첫 처리 안내를 목표로 하며, 법령상 보존이 필요한 기록은 보존 사유와 기간을
                별도로 안내합니다.
              </li>
              <li>
                이메일 접수는{" "}
                <a
                  href={`mailto:${BUSINESS_INFO.contactEmail}`}
                  className="text-brand-600 underline"
                >
                  {BUSINESS_INFO.contactEmail}
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

          <Section title="10. 개인정보의 안전성 확보 조치">
            <ul className="list-disc space-y-1 pl-5">
              <li>비밀번호 BCrypt 해시 저장, 평문 미보관</li>
              <li>불투명 세션 토큰 기반 인증, HTTPS 전 구간 암호화</li>
              <li>관리자 전용 접근 통제(RBAC)</li>
            </ul>
          </Section>

          <Section title="11. 쿠키 및 브라우저 저장소">
            <p>
              로그인 상태 유지를 위해 세션 토큰을 HttpOnly·SameSite 쿠키에 저장하고, 소셜 로그인
              중에는 요청 위조 방지를 위한 일회성 OAuth 상태 쿠키를 사용합니다. 이 쿠키들은 페이지의
              JavaScript에서 읽을 수 없습니다. 화면 복원을 위한 계정 ID·권한·온보딩 상태, 이용자가
              직접 입력한 최근 거래 연락처·카드 결제 이름, 배너·관리자 화면 설정은 로컬 저장소에,
              결제 시도 여부는 탭의 세션 저장소에 보관합니다. 브라우저 설정에서 이를 지우거나 거부할
              수 있으나 로그인·입력값 기억 등 관련 기능이 제한될 수 있습니다. 회원 탈퇴 요청이
              접수되면 요청을 접수한 브라우저에서 화면 복원용 계정 정보, 최근 거래 연락처·카드 결제
              이름과 해당 탭의 결제 시도 표식을 삭제합니다.
            </p>
            <p>
              Google 분석 ID가 설정된 빌드에서도 명시적으로 분석을 허용하기 전에는 Google 스크립트를
              내려받거나 쿠키를 만들지 않고, 쿠키 없는 측정 요청도 보내지 않습니다. 허용하면 _ga와
              _ga_&lt;식별자&gt; 쿠키를 최대 90일 동안 사용하며, 선택 상태와 시각은 이 브라우저의
              <code> gole.analytics-consent.v1</code> 로컬 저장소에만 기록합니다. 푸터의 분석
              설정에서 거부·철회·초기화할 수 있습니다. 분석 ID가 없으면 동의 UI·저장소 접근·Google
              네트워크 요청이 모두 비활성화됩니다.
            </p>
          </Section>

          <Section title="12. 개인정보 보호책임자">
            <address className="not-italic">
              <p>상호: {BUSINESS_INFO.name}</p>
              <p>개인정보 보호책임자: {BUSINESS_INFO.representative}</p>
              <p>주소: {BUSINESS_INFO.address}</p>
              <p>
                대표전화:{" "}
                <a href={`tel:${BUSINESS_INFO.phone}`} className="underline">
                  {BUSINESS_INFO.phone}
                </a>
              </p>
              <p>
                사업자 이메일:{" "}
                <a href={`mailto:${BUSINESS_INFO.contactEmail}`} className="underline">
                  {BUSINESS_INFO.contactEmail}
                </a>
              </p>
              <p>
                개발자 이메일:{" "}
                <a href={`mailto:${BUSINESS_INFO.developerEmail}`} className="underline">
                  {BUSINESS_INFO.developerEmail}
                </a>
              </p>
            </address>
          </Section>

          <Section title="13. 방침 변경 고지">
            <p>
              본 방침은 법령 변경 또는 서비스 정책 변경에 따라 수정될 수 있으며, 변경 시 본 페이지를
              통해 공지합니다.
            </p>
          </Section>
        </div>
      </article>
    </div>
  );
}

function Section({
  id,
  title,
  children,
}: {
  readonly id?: string;
  readonly title: string;
  readonly children: React.ReactNode;
}) {
  return (
    <section id={id} className="scroll-mt-24">
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
