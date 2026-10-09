import type { LaunchConfig } from "@entities/launch";
import { isPaymentRuntimeAvailable } from "@shared/config";

/**
 * 이 매물에 플랫폼 결제로 새 주문을 낼 수 있는지 — 구매 버튼을 그릴지의 판정이다.
 *
 * 매물 상세가 쓰던 조건을 그대로 옮겨 왔다: 판매자 신원확인 준비 + 결제 기능 + 결제 단계(Stage 2 이상) +
 * 이 웹 빌드의 결제 런타임(`NEXT_PUBLIC_PAYMENT_MODE`·PortOne 키). 채팅의 "제안가로 구매하기"도
 * 같은 판정으로 열고 닫아야 해서 한 곳에 둔다. 하나라도 아니면 직거래 단계로 보고 결제 동선을 감춘다.
 */
export function isPurchaseOpen(launch: LaunchConfig): boolean {
  return (
    launch.sellerIdentityVerificationReady &&
    launch.features.payments &&
    launch.stage >= 2 &&
    isPaymentRuntimeAvailable()
  );
}
