import { LinkButton, type ButtonSize } from "@shared/ui";

export interface EditListingLinkProps {
  readonly listingId: string;
  readonly size?: ButtonSize;
}

/** 매물 수정 화면 경로. 상세·내 매물 목록이 같은 주소를 쓰도록 한곳에 둔다. */
export function listingEditHref(listingId: string): string {
  return `/listings/${encodeURIComponent(listingId)}/edit`;
}

export function EditListingLink({ listingId, size = "sm" }: EditListingLinkProps) {
  return (
    <LinkButton href={listingEditHref(listingId)} size={size} variant="secondary">
      수정
    </LinkButton>
  );
}
