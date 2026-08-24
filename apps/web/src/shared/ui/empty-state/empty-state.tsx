import type { ReactNode } from "react";
import { cn } from "@shared/lib";
import { CheckCircleIcon } from "../icon";
import { Logo } from "../logo";
import { Heading, Text } from "../typography";

export interface EmptyStateProps {
  readonly eyebrow?: string;
  readonly title: string;
  readonly description: string;
  readonly details?: readonly string[];
  readonly action?: ReactNode;
  readonly animated?: boolean;
  readonly className?: string;
}

/**
 * 비어 있음·로그인 필요·첫 시작을 한 가지 브랜드 문법으로 보여주는 상태 패널.
 * Figma `Product/Empty State`의 GoLe V2 마스코트형을 코드로 대응한다.
 */
export function EmptyState({
  eyebrow,
  title,
  description,
  details = [],
  action,
  animated = true,
  className,
}: EmptyStateProps) {
  return (
    <section
      className={cn(
        "relative isolate overflow-hidden rounded-2xl border border-brand-100 bg-[linear-gradient(135deg,var(--color-brand-50),white_58%,var(--color-accent-50))] px-6 py-8 shadow-soft sm:px-9 sm:py-10",
        className,
      )}
    >
      <div
        aria-hidden="true"
        className="absolute -top-10 -right-8 size-44 rounded-full border-[28px] border-white/55"
      />
      <div className="relative grid items-center gap-7 sm:grid-cols-[220px_minmax(0,1fr)] sm:gap-10">
        <div className="flex min-h-40 items-center justify-center rounded-2xl border border-white/80 bg-white/55 px-4 shadow-soft">
          <Logo
            size={190}
            showWordmark={false}
            spout
            className={animated ? "gole-mascot-float" : ""}
          />
        </div>

        <div className="flex min-w-0 flex-col items-start gap-4">
          {eyebrow ? (
            <span className="border-l-2 border-accent-400 pl-3 text-sm font-semibold text-brand-700">
              {eyebrow}
            </span>
          ) : null}
          <div className="flex flex-col gap-2">
            <Heading level={2} className="text-2xl sm:text-3xl">
              {title}
            </Heading>
            <Text tone="secondary" className="max-w-[42ch] leading-relaxed">
              {description}
            </Text>
          </div>
          {details.length > 0 ? (
            <ul className="grid gap-2 text-sm text-neutral-600 sm:grid-cols-2">
              {details.map((detail) => (
                <li key={detail} className="flex items-center gap-2">
                  <CheckCircleIcon className="size-4 shrink-0 text-brand-500" />
                  <span>{detail}</span>
                </li>
              ))}
            </ul>
          ) : null}
          {action ? <div className="pt-1">{action}</div> : null}
        </div>
      </div>
    </section>
  );
}
