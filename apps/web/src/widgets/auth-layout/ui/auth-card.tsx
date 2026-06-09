import type { ReactNode } from "react";
import { Card, Heading, Logo, Text } from "@shared/ui";

export interface AuthCardProps {
  readonly title: string;
  readonly subtitle?: string;
  readonly children: ReactNode;
  readonly footer?: ReactNode;
}

export function AuthCard({ title, subtitle, children, footer }: AuthCardProps) {
  return (
    <div className="min-h-dvh grid place-items-center px-5 py-8 bg-[radial-gradient(1200px_400px_at_50%_-10%,var(--color-brand-50),transparent)]">
      <Card padded={false} className="w-full max-w-[420px] p-8 flex flex-col gap-6">
        <div className="flex flex-col gap-2 text-center">
          <Logo size={36} className="self-center text-xl text-neutral-900" />
          <Heading level={2}>{title}</Heading>
          {subtitle ? <Text tone="secondary">{subtitle}</Text> : null}
        </div>
        {children}
        {footer ? (
          <div className="text-center text-sm text-neutral-600 [&_a]:font-semibold [&_a]:text-brand-500">
            {footer}
          </div>
        ) : null}
      </Card>
    </div>
  );
}
