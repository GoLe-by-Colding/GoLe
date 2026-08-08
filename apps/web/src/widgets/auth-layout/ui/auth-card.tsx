import type { ReactNode } from "react";
import { BackButton, Card, Heading, Logo, Text } from "@shared/ui";

export interface AuthCardProps {
  readonly title: string;
  readonly subtitle?: string;
  readonly children: ReactNode;
  readonly footer?: ReactNode;
}

export function AuthCard({ title, subtitle, children, footer }: AuthCardProps) {
  return (
    <div className="grid min-h-dvh place-items-center bg-brand-50 px-5 py-8">
      <Card padded={false} className="w-full max-w-[420px] p-8 flex flex-col gap-6">
        <BackButton fallbackHref="/" />
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
