import type { LegoSet } from "../model/types";
import { isRetired } from "../model/types";

export interface LegoSetCardProps {
  readonly set: LegoSet;
}

export function LegoSetCard({ set }: LegoSetCardProps) {
  return (
    <article data-testid="lego-set-card">
      <h3>
        {set.name} <small>#{set.setNumber}</small>
      </h3>
      <dl>
        <dt>Theme</dt>
        <dd>{set.theme}</dd>
        <dt>Pieces</dt>
        <dd>{set.pieceCount.toLocaleString()}</dd>
        <dt>Released</dt>
        <dd>{set.releaseYear}</dd>
      </dl>
      {isRetired(set) ? <span data-testid="retired-badge">단종</span> : null}
    </article>
  );
}
