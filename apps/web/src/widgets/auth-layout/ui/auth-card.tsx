import type { ReactNode } from "react";
import { Card, Heading, Text } from "@shared/ui";
import styles from "./auth-card.module.css";

export interface AuthCardProps {
  readonly title: string;
  readonly subtitle?: string;
  readonly children: ReactNode;
  readonly footer?: ReactNode;
}

export function AuthCard({ title, subtitle, children, footer }: AuthCardProps) {
  return (
    <div className={styles.wrapper}>
      <Card padded={false} className={styles.card}>
        <div className={styles.header}>
          <span className={styles.brand}>🧱 GoLe</span>
          <Heading level={2}>{title}</Heading>
          {subtitle ? <Text tone="secondary">{subtitle}</Text> : null}
        </div>
        {children}
        {footer ? <div className={styles.footer}>{footer}</div> : null}
      </Card>
    </div>
  );
}
