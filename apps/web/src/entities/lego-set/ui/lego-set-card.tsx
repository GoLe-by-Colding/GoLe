import { Badge, Card, MediaImage } from "@shared/ui";
import { thumbnailUrl } from "@shared/lib";
import type { LegoSet } from "../model/types";
import { isRetired } from "../model/types";

export interface LegoSetCardProps {
  readonly set: LegoSet;
}

export function LegoSetCard({ set }: LegoSetCardProps) {
  return (
    <Card interactive padded={false} className="flex flex-col" data-testid="lego-set-card">
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
          <span className="text-base font-semibold leading-tight text-neutral-900">{set.name}</span>
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
        <a
          href={`https://www.lego.com/ko-kr/search?q=${encodeURIComponent(set.setNumber)}`}
          target="_blank"
          rel="noopener noreferrer nofollow"
          className="mt-1 inline-flex w-fit items-center gap-1 text-xs font-medium text-brand-600 hover:text-brand-700 hover:underline"
        >
          레고 공식 페이지 ↗
        </a>
      </div>
    </Card>
  );
}
