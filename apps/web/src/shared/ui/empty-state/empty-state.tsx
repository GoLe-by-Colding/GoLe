import type { ReactNode } from "react";
import { cn } from "@shared/lib";
import { CheckCircleIcon } from "../icon";
import { Logo } from "../logo";
import { Heading, Text } from "../typography";

export interface EmptyStateProps {
  /**
   * `panel` — 화면 전체가 비었을 때 쓰는 마스코트형. 로그인 유도·첫 시작처럼 다음 행동이 분명한 자리.
   * `inline` — 목록 한 칸이 비었을 때 쓰는 점선 상자. 주변 UI를 밀어내지 않는다.
   */
  readonly variant?: "panel" | "inline";
  /** `inline`에서 제목 위에 놓을 아이콘. `panel`은 마스코트를 쓰므로 무시된다. */
  readonly icon?: ReactNode;
  readonly eyebrow?: string;
  readonly title: string;
  readonly description?: string;
  readonly details?: readonly string[];
  readonly action?: ReactNode;
  readonly animated?: boolean;
  readonly className?: string;
}

/**
 * 비어 있음·로그인 필요·첫 시작을 한 가지 문법으로 보여주는 상태 컴포넌트.
 * 빈 화면이 화면에서 가장 화려한 요소가 되지 않도록, 배경은 단색이고 장식은 두지 않는다
 * (`.kiro/steering/brand-identity.md` — 그라데이션 배경 금지, 그림자 절제).
 */
export function EmptyState({
  variant = "panel",
  icon,
  eyebrow,
  title,
  description,
  details = [],
  action,
  animated = true,
  className,
}: EmptyStateProps) {
  if (variant === "inline") {
    return (
      <div
        className={cn(
          "flex flex-col items-center gap-2 rounded-xl border border-dashed border-neutral-300 px-6 py-14 text-center",
          className,
        )}
      >
        {icon}
        <Text tone="secondary" weight="medium">
          {title}
        </Text>
        {description ? (
          <Text tone="muted" size="sm">
            {description}
          </Text>
        ) : null}
        {action ? <div className="pt-2">{action}</div> : null}
      </div>
    );
  }

  return (
    <section
      className={cn(
        "rounded-xl border border-neutral-200/70 bg-neutral-50 px-6 py-8 sm:px-9 sm:py-10",
        className,
      )}
    >
      <div className="grid items-center gap-7 sm:grid-cols-[180px_minmax(0,1fr)] sm:gap-10">
        <div className="flex items-center justify-center">
          <Logo
            size={160}
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
            <Heading level={2} className="text-2xl">
              {title}
            </Heading>
            {description ? (
              <Text tone="secondary" className="max-w-[42ch] leading-relaxed">
                {description}
              </Text>
            ) : null}
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
