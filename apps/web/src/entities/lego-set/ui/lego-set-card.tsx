import Link from "next/link";
import { Badge, Card, MediaImage } from "@shared/ui";
import { thumbnailUrl } from "@shared/lib";
import type { LegoSet } from "@gole/core/lego-set";
import { isRetired } from "@gole/core/lego-set";
import { OfficialLegoLink } from "./official-lego-link";

export interface LegoSetCardProps {
  readonly set: LegoSet;
}

/**
 * 카탈로그 세트 카드.
 *
 * <p>위계는 이미지 → 테마 → 이름 → 부가정보 순으로 한 줄씩 내려간다. 테마 뱃지를 이름과 같은
 * 줄에 두면 둘이 가로로 자리를 다투면서 이름이 짧게 잘리고, 그리드에서 카드마다 줄바꿈 위치가
 * 달라져 시선이 흔들린다. 상태(단종)만 이미지 위 칩으로 올린다 — 이름과 겨루지 않으면서도
 * 목록을 훑을 때 먼저 걸려야 하는 정보라서다.
 */
export function LegoSetCard({ set }: LegoSetCardProps) {
  const detailHref = `/sets/${encodeURIComponent(set.setNumber)}`;
  const retired = isRetired(set);

  return (
    <Card interactive padded={false} className="relative flex flex-col" data-testid="lego-set-card">
      <div className="relative aspect-square overflow-hidden bg-neutral-50">
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
        {retired ? (
          <span className="absolute left-3 top-3">
            <Badge tone="danger" data-testid="retired-badge">
              단종
            </Badge>
          </span>
        ) : null}
      </div>

      <div className="flex flex-col gap-1.5 p-4">
        <span className="text-xs font-medium tracking-wide text-neutral-500">{set.theme}</span>
        <Link
          href={detailHref}
          aria-label={`${set.name} 세트 상세 보기`}
          className="line-clamp-2 text-[0.9375rem] font-semibold leading-snug text-neutral-900 after:absolute after:inset-0 after:content-[''] focus-visible:outline-none focus-visible:after:ring-2 focus-visible:after:ring-brand-500 focus-visible:after:ring-offset-2"
        >
          {set.name}
        </Link>
        <dl className="m-0 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-neutral-500">
          <div className="inline-flex gap-1">
            <dt className="sr-only">세트 번호</dt>
            <dd className="font-mono">#{set.setNumber}</dd>
          </div>
          <span aria-hidden="true">·</span>
          <div className="inline-flex gap-1">
            <dt className="sr-only">피스</dt>
            <dd>{set.pieceCount.toLocaleString()}피스</dd>
          </div>
          <span aria-hidden="true">·</span>
          <div className="inline-flex gap-1">
            <dt className="sr-only">출시</dt>
            <dd>{set.releaseYear}</dd>
          </div>
        </dl>
        <OfficialLegoLink
          setNumber={set.setNumber}
          label="제조사 공식 페이지"
          className="relative z-10 mt-1.5 inline-flex w-fit items-center gap-1 text-xs text-neutral-500 hover:text-brand-600 hover:underline"
        />
      </div>
    </Card>
  );
}
