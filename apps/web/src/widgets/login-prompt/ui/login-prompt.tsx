import { loginHrefWithReturnTo } from "@shared/lib";
import { EmptyState, LinkButton } from "@shared/ui";

/** 로그인 상태를 판단하지 않는 안내 패널. 화면이 목적·복귀 경로만 넘기고 표현은 한곳에서 관리한다. */
export function LoginPrompt({
  title,
  description,
  returnTo,
  details,
  label = "로그인하고 시작하기",
}: {
  readonly title: string;
  readonly description: string;
  readonly returnTo: string;
  readonly details?: readonly string[];
  readonly label?: string;
}) {
  return (
    <EmptyState
      title={title}
      description={description}
      {...(details === undefined ? {} : { details })}
      action={
        <div className="flex flex-wrap gap-2">
          <LinkButton href={loginHrefWithReturnTo(returnTo)}>{label}</LinkButton>
          <LinkButton href="/search" variant="secondary">
            상품 둘러보기
          </LinkButton>
        </div>
      }
    />
  );
}
