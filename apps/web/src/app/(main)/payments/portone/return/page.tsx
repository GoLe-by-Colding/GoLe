import type { Metadata } from "next";
import { PaymentReturnPage } from "@views/payment-return";

export const metadata: Metadata = {
  title: "결제 결과 확인",
  robots: { index: false, follow: false },
};

interface PageProps {
  readonly searchParams: Promise<{
    readonly paymentId?: string;
    readonly code?: string;
    readonly message?: string;
    readonly pgMessage?: string;
  }>;
}

export default async function Page({ searchParams }: PageProps) {
  const params = await searchParams;
  return (
    <PaymentReturnPage
      paymentId={params.paymentId}
      code={params.code}
      message={params.message}
      pgMessage={params.pgMessage}
    />
  );
}
