import { LinkButton, Logo } from "@shared/ui";

export default function NotFound() {
  return (
    <main className="grid min-h-dvh place-items-center bg-[radial-gradient(1200px_500px_at_50%_-10%,var(--color-brand-50),transparent)] px-6">
      <div className="flex flex-col items-center gap-6 text-center">
        <Logo size={48} showWordmark={false} />
        <p className="text-7xl font-extrabold tracking-tight text-brand-600">404</p>
        <div className="flex flex-col gap-2">
          <h1 className="text-2xl font-bold text-neutral-900">페이지를 찾을 수 없어요</h1>
          <p className="max-w-[40ch] text-neutral-500">
            주소가 바뀌었거나 삭제된 페이지일 수 있어요. 홈에서 다시 둘러보세요.
          </p>
        </div>
        <div className="mt-2 flex flex-wrap justify-center gap-3">
          <LinkButton href="/">홈으로</LinkButton>
          <LinkButton href="/search" variant="secondary">
            상품 둘러보기
          </LinkButton>
        </div>
      </div>
    </main>
  );
}
