import type { Metadata } from "next";
import { AdminOrdersView } from "@views/admin";

export const metadata: Metadata = { title: "주문" };

const STATUSES = new Set([
  "PAYMENT_PENDING",
  "PAYMENT_REVIEW",
  "FUNDS_HELD",
  "REFUND_PENDING",
  "COMPLETED",
  "REFUNDED",
  "PAYMENT_FAILED",
]);

export default async function Page({
  searchParams,
}: {
  readonly searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const requested = (await searchParams).status;
  const status = typeof requested === "string" && STATUSES.has(requested) ? requested : "";
  return <AdminOrdersView initialStatus={status} />;
}
