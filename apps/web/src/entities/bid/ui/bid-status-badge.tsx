import { bidStatusLabel, type BidStatus } from "@gole/core/bid";
import { Badge, type BadgeTone } from "@shared/ui";

const TONE: Record<BidStatus, BadgeTone> = {
  active: "brand",
  filled: "success",
  canceled: "neutral",
  expired: "neutral",
};

export function BidStatusBadge({ status }: { readonly status: BidStatus }) {
  return <Badge tone={TONE[status]}>{bidStatusLabel(status)}</Badge>;
}
