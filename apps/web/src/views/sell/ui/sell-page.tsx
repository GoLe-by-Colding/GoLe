"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { CreateListingForm } from "@features/create-listing";
import { fetchLaunchConfig, type LaunchConfig } from "@entities/launch";
import { fetchOnboardingStatus, useSession } from "@entities/user";
import { isPaymentRuntimeAvailable } from "@shared/config";
import { loginHrefWithReturnTo } from "@shared/lib";
import { Container, Heading, LinkButton, Text } from "@shared/ui";

export function SellPage() {
  const router = useRouter();
  const { session } = useSession();
  const [launch, setLaunch] = useState<LaunchConfig | null>(null);
  const [phoneCheck, setPhoneCheck] = useState<{
    readonly accountId: string;
    readonly state: "verified" | "unverified" | "unavailable";
    readonly stepAvailable: boolean;
  } | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    void fetchLaunchConfig(controller.signal).then((launch) => {
      if (!controller.signal.aborted) {
        setLaunch(launch);
      }
    });
    return () => controller.abort();
  }, []);

  useEffect(() => {
    if (launch?.sellerIdentityVerificationReady !== true || session === null) {
      return;
    }
    const controller = new AbortController();
    void fetchOnboardingStatus(controller.signal)
      .then((status) => {
        if (controller.signal.aborted) return;
        setPhoneCheck({
          accountId: session.accountId,
          state: status.phoneCompleted ? "verified" : "unverified",
          stepAvailable: status.phoneVerificationRequired,
        });
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setPhoneCheck({
            accountId: session.accountId,
            state: "unavailable",
            stepAvailable: false,
          });
        }
      });
    return () => controller.abort();
  }, [launch?.sellerIdentityVerificationReady, session]);

  // 세션이 바뀐 직후 이전 계정의 확인 결과를 잠깐이라도 재사용하지 않는다. 새 결과가
  // 도착할 때까지 계산된 checking 상태로 닫아 두면 effect 안의 동기 setState도 필요 없다.
  const phoneState =
    session !== null && phoneCheck?.accountId === session.accountId ? phoneCheck.state : "checking";
  const phoneStepAvailable =
    session !== null && phoneCheck?.accountId === session.accountId && phoneCheck.stepAvailable;

  const paymentsOpen =
    launch?.features.payments === true &&
    launch.sellerIdentityVerificationReady &&
    isPaymentRuntimeAvailable();

  return (
    <Container width="sm">
      <div className="flex flex-col gap-6 pt-10 pb-16">
        <div className="flex flex-col gap-1">
          <Heading level={1}>상품 등록</Heading>
          <Text tone="secondary">판매할 브릭 상품 정보를 입력하세요.</Text>
        </div>

        {launch === null ? (
          <SellerAccessPanel
            title="판매 준비 상태를 확인하고 있어요"
            body="잠시만 기다려 주세요. 준비 상태를 확인할 수 없으면 신규 등록은 자동으로 열리지 않습니다."
          />
        ) : !launch.sellerIdentityVerificationReady ? (
          <SellerAccessPanel
            title="신규 상품 등록 준비 중"
            body="판매자 신원확인 절차가 아직 준비되지 않아 신규 상품 등록과 새 거래 연결을 잠시 받지 않습니다. 기존 상품 탐색·커뮤니티·운영 문의는 계속 이용할 수 있습니다."
            actions={
              <>
                <LinkButton href="/search">상품 둘러보기</LinkButton>
                <LinkButton href="/community" variant="secondary">
                  커뮤니티 보기
                </LinkButton>
                <LinkButton href="/chat?compose=support&category=PRODUCT_FEEDBACK" variant="ghost">
                  운영 문의
                </LinkButton>
              </>
            }
          />
        ) : !session ? (
          <SellerAccessPanel
            title="로그인이 필요해요"
            body="상품을 등록하려면 로그인한 뒤 판매자 본인확인을 완료해야 합니다."
            actions={<LinkButton href={loginHrefWithReturnTo("/sell")}>로그인하러 가기</LinkButton>}
          />
        ) : phoneState === "checking" ? (
          <SellerAccessPanel
            title="판매자 본인확인을 확인하고 있어요"
            body="인증된 전화번호가 확인된 계정만 신규 상품을 등록할 수 있습니다."
          />
        ) : phoneState === "unavailable" ? (
          <SellerAccessPanel
            title="판매자 본인확인을 확인할 수 없어요"
            body="확인 상태를 추측해서 판매 기능을 열지 않습니다. 잠시 후 다시 접속하거나 운영팀에 문의해 주세요."
            actions={
              <LinkButton href="/chat?compose=support&category=GENERAL">운영 문의</LinkButton>
            }
          />
        ) : phoneState === "unverified" ? (
          <SellerAccessPanel
            title="판매자 전화번호 확인이 필요해요"
            body={
              phoneStepAvailable
                ? "온보딩에서 전화번호 인증을 완료하면 상품 등록을 시작할 수 있습니다."
                : "현재 전화번호 인증 절차를 준비 중이라 신규 판매자로 등록할 수 없습니다. 운영팀에 준비 상태를 문의해 주세요."
            }
            actions={
              <LinkButton
                href={phoneStepAvailable ? "/onboarding" : "/chat?compose=support&category=GENERAL"}
              >
                {phoneStepAvailable ? "본인확인 진행하기" : "운영 문의"}
              </LinkButton>
            }
          />
        ) : (
          <CreateListingForm
            sellerId={session.accountId}
            paymentsOpen={paymentsOpen}
            onCreated={(id) => router.push(`/listings/${id}`)}
          />
        )}
      </div>
    </Container>
  );
}

function SellerAccessPanel({
  title,
  body,
  actions,
}: {
  readonly title: string;
  readonly body: string;
  readonly actions?: React.ReactNode;
}) {
  return (
    <div className="flex flex-col items-start gap-4 rounded-lg border border-neutral-200 bg-white p-6">
      <div className="flex flex-col gap-1.5">
        <Text weight="semibold">{title}</Text>
        <Text tone="secondary" className="max-w-2xl leading-relaxed">
          {body}
        </Text>
      </div>
      {actions === undefined ? null : <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}
