import { Container, Skeleton } from "@shared/ui";

export default function Loading() {
  return (
    <Container width="xl">
      <div className="flex flex-col gap-20 pt-14 pb-24">
        {/* Hero skeleton */}
        {/* 320px 에서 w-80 막대가 화면 밖으로 61px 나가 로딩 중에 가로 스크롤이 생겼다. */}
        <div className="rounded-lg border border-neutral-100 bg-neutral-50 px-5 py-20 sm:px-10">
          <div className="flex flex-col gap-4">
            <Skeleton className="h-6 w-36 max-w-full rounded-full" />
            <Skeleton className="h-14 w-80 max-w-full rounded-md" />
            <Skeleton className="h-6 w-64 max-w-full" />
            <div className="flex gap-3 pt-2">
              <Skeleton className="h-12 w-36 min-w-0 rounded-md" />
              <Skeleton className="h-12 w-36 min-w-0 rounded-md" />
            </div>
          </div>
        </div>
        {/* Trending skeleton */}
        <div className="flex flex-col gap-4">
          <Skeleton className="h-7 w-36" />
          <div className="overflow-hidden rounded-lg border border-neutral-100">
            {Array.from({ length: 4 }).map((_, i) => (
              <div
                key={i}
                className="flex items-center gap-4 border-t border-neutral-100 px-5 py-4 first:border-t-0"
              >
                <Skeleton circle className="h-8 w-8" />
                <Skeleton className="h-10 w-10 rounded-md" />
                <div className="flex flex-1 flex-col gap-1.5">
                  <Skeleton className="h-4 w-32" />
                  <Skeleton className="h-3 w-24" />
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </Container>
  );
}
