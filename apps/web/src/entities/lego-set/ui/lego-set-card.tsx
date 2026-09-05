import Link from "next/link";
import { Badge, Card, MediaImage } from "@shared/ui";
import { thumbnailUrl } from "@shared/lib";
import type { LegoSet } from "@gole/core/lego-set";
import { isRetired } from "@gole/core/lego-set";
import { OfficialLegoLink } from "./official-lego-link";

export interface LegoSetCardProps {
  readonly set: LegoSet;
}

export function LegoSetCard({ set }: LegoSetCardProps) {
  const detailHref = `/sets/${encodeURIComponent(set.setNumber)}`;

  return (
    <Card interactive padded={false} className="relative flex flex-col" data-testid="lego-set-card">
      <div className="aspect-[4/3] overflow-hidden bg-neutral-50">
        <MediaImage
          src={set.imageUrl === null ? null : thumbnailUrl(set.imageUrl, 480)}
          alt={set.name}
          loading="lazy"
          className="h-full w-full object-cover"
          fallback={
            <>
              <span className="text-sm tracking-[0.2em]">SET</span>
              <span className="font-mono text-xs font-medium">#{set.setNumber}</span>
            </>
          }
          fallbackClassName="flex-col gap-2 font-bold"
        />
      </div>
      <div className="flex flex-col gap-2 p-4">
        <div className="flex items-start justify-between gap-2">
          <Link
            href={detailHref}
            aria-label={`${set.name} 세트 상세 보기`}
            className="text-base font-semibold leading-tight text-neutral-900 after:absolute after:inset-0 after:content-[''] focus-visible:outline-none focus-visible:after:ring-2 focus-visible:after:ring-brand-500 focus-visible:after:ring-offset-2"
          >
            {set.name}
          </Link>
          {isRetired(set) ? (
            <Badge tone="danger" data-testid="retired-badge">
              단종
            </Badge>
          ) : (
            <Badge tone="brand">{set.theme}</Badge>
          )}
        </div>
        <span className="font-mono text-xs text-neutral-500">#{set.setNumber}</span>
        <dl className="flex flex-wrap gap-x-3 gap-y-1 m-0 text-sm text-neutral-600">
          <div className="inline-flex gap-1">
            <dt className="text-neutral-500">피스</dt>
            <dd>{set.pieceCount.toLocaleString()}</dd>
          </div>
          <div className="inline-flex gap-1">
            <dt className="text-neutral-500">출시</dt>
            <dd>{set.releaseYear}</dd>
          </div>
        </dl>
        <OfficialLegoLink
          setNumber={set.setNumber}
          label="제조사 공식 페이지"
          className="relative z-10 mt-1 inline-flex w-fit items-center gap-1 text-xs font-medium text-brand-600 hover:text-brand-700 hover:underline"
        />
      </div>
    </Card>
  );
}
