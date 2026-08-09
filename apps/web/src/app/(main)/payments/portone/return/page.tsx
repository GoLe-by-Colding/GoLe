import { PaymentReturnPage } from "@views/payment-return";

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
