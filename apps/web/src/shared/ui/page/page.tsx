import type { ReactNode } from "react";
import { Container, type ContainerWidth } from "../container";
import { Heading, Text } from "../typography";

export interface PageLayoutProps {
  readonly width?: ContainerWidth;
  readonly children: ReactNode;
}

/** 일반 서비스 화면의 공통 여백. 상세·인증·관리자 셸은 각각의 전용 구성을 유지한다. */
export function PageLayout({ width = "lg", children }: PageLayoutProps) {
  return (
    <Container width={width}>
      <div className="flex min-w-0 flex-col gap-6 pt-8 pb-12 sm:gap-8 sm:pt-12 sm:pb-16">
        {children}
      </div>
    </Container>
  );
}

export interface PageHeaderProps {
  readonly title: string;
  readonly description?: ReactNode;
  readonly eyebrow?: string;
  readonly actions?: ReactNode;
}

/** 페이지 제목은 하나의 h1, 보조 행동은 작은 화면에서 다음 줄로 내려간다. */
export function PageHeader({ title, description, eyebrow, actions }: PageHeaderProps) {
  return (
    <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between sm:gap-6">
      <div className="flex min-w-0 flex-col gap-2">
        {eyebrow === undefined ? null : (
          <span className="text-sm font-semibold text-brand-700">{eyebrow}</span>
        )}
        <Heading level={1}>{title}</Heading>
        {description === undefined ? null : (
          <Text tone="secondary" className="max-w-[64ch] leading-relaxed break-keep">
            {description}
          </Text>
        )}
      </div>
      {actions === undefined ? null : (
        <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div>
      )}
    </header>
  );
}
