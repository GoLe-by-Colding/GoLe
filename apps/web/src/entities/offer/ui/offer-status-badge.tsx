import { offerStatusLabel, type OfferStatus } from "@gole/core/offer";
import { Badge, type BadgeTone } from "@shared/ui";

const TONE: Record<OfferStatus, BadgeTone> = {
  pending: "warning",
  accepted: "success",
  declined: "danger",
  withdrawn: "neutral",
  expired: "neutral",
};

export function OfferStatusBadge({ status }: { readonly status: OfferStatus }) {
  return <Badge tone={TONE[status]}>{offerStatusLabel(status)}</Badge>;
}
