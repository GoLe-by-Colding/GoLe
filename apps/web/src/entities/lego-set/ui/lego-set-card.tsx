import { Badge, Card } from "@shared/ui";
import type { LegoSet } from "../model/types";
import { isRetired } from "../model/types";
import styles from "./lego-set-card.module.css";

export interface LegoSetCardProps {
  readonly set: LegoSet;
}

export function LegoSetCard({ set }: LegoSetCardProps) {
  return (
    <Card interactive padded={false} className={styles.card} data-testid="lego-set-card">
      <div className={styles.thumb} aria-hidden="true">
        {set.imageUrl === null ? "🧱" : null}
      </div>
      <div className={styles.body}>
        <div className={styles.header}>
          <span className={styles.title}>{set.name}</span>
          {isRetired(set) ? (
            <Badge tone="danger" data-testid="retired-badge">
              단종
            </Badge>
          ) : (
            <Badge tone="brand">{set.theme}</Badge>
          )}
        </div>
        <span className={styles.setNumber}>#{set.setNumber}</span>
        <dl className={styles.meta}>
          <div className={styles.metaItem}>
            <dt className={styles.metaLabel}>피스</dt>
            <dd>{set.pieceCount.toLocaleString()}</dd>
          </div>
          <div className={styles.metaItem}>
            <dt className={styles.metaLabel}>출시</dt>
            <dd>{set.releaseYear}</dd>
          </div>
        </dl>
      </div>
    </Card>
  );
}
