import type { PartRequestStatus } from "@gole/core/parts";
import { Badge } from "@shared/ui";

export function PartRequestStatusBadge({ status }: { readonly status: PartRequestStatus }) {
  return status === "open" ? <Badge tone="success">찾는 중</Badge> : <Badge>마감</Badge>;
}
