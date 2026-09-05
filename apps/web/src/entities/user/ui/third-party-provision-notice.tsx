import Link from "next/link";

export interface ThirdPartyProvisionNoticeProps {
  readonly compact?: boolean;
}

/** 가입 화면과 JIT 동의창이 함께 쓰는 단일 제3자 제공 안내 원문. */
export function ThirdPartyProvisionNotice({ compact = false }: ThirdPartyProvisionNoticeProps) {
  return (
    <div
      data-testid="third-party-provision-notice"
      className={
        compact
          ? "flex flex-col gap-2 text-xs leading-relaxed text-neutral-600"
          : "flex flex-col gap-3 text-sm leading-relaxed text-neutral-700"
      }
    >
      <div className="flex flex-col gap-3">
        <section className="rounded-lg border border-neutral-200 bg-white/70 p-3">
          <h3 className="font-semibold text-neutral-900">제공받는 자: 대화방 참여자</h3>
          <dl className="mt-2 grid grid-cols-[max-content_minmax(0,1fr)] gap-x-3 gap-y-1">
            <dt className="font-semibold text-neutral-800">목적</dt>
            <dd>대화 제공 및 거래 협의</dd>
            <dt className="font-semibold text-neutral-800">항목</dt>
            <dd>계정 ID, 메시지, 방 제목과 참여자 정보</dd>
          </dl>
        </section>
        <section className="rounded-lg border border-neutral-200 bg-white/70 p-3">
          <h3 className="font-semibold text-neutral-900">
            제공받는 자: 거래 상대방(구매자 또는 판매자)
          </h3>
          <dl className="mt-2 grid grid-cols-[max-content_minmax(0,1fr)] gap-x-3 gap-y-1">
            <dt className="font-semibold text-neutral-800">목적</dt>
            <dd>거래 협의·배송 진행 및 분쟁 대응을 위한 연락</dd>
            <dt className="font-semibold text-neutral-800">항목</dt>
            <dd>정보주체의 전체 전화번호</dd>
          </dl>
        </section>
      </div>
      <dl className="grid grid-cols-[max-content_minmax(0,1fr)] gap-x-3 gap-y-2">
        <dt className="font-semibold text-neutral-900">보유·이용 기간</dt>
        <dd>
          제공 목적 종료 후 지체 없이 삭제. 단, 법령상 보존 의무 또는 진행 중인 분쟁에 필요한 경우
          해당 기간 동안 분리 보관
        </dd>
        <dt className="font-semibold text-neutral-900">동의 거부 영향</dt>
        <dd>동의하지 않아도 가입할 수 있으나, 채팅과 거래 상대방 연락처 조회가 제한됩니다.</dd>
      </dl>
      <p>
        자세한 내용은{" "}
        <Link href="/privacy#third-party-provision" target="_blank" className="underline">
          개인정보처리방침의 제3자 제공 안내
        </Link>
        에서 확인할 수 있습니다.
      </p>
    </div>
  );
}
