import { Container, Skeleton } from "@shared/ui";

function SetRowSkeleton() {
  return (
    <div className="flex items-center gap-3 border-t border-neutral-100 px-3 py-2.5 first:border-t-0">
      <Skeleton className="h-10 w-10 shrink-0 rounded-md" />
      <div className="flex flex-col gap-1.5">
        <Skeleton className="h-4 w-32" />
        <Skeleton className="h-3 w-20" />
      </div>
    </div>
  );
}

export default function PricesLoading() {
  return (
    <Container width="xl">
      <div
        className="flex flex-col gap-6 pt-8 pb-16"
        aria-label="시세 화면 불러오는 중"
        aria-busy="true"
      >
        <div className="flex flex-col gap-2">
          <Skeleton className="h-10 w-24" />
          <Skeleton className="h-5 w-80 max-w-full" />
        </div>

        <div className="grid gap-6 lg:grid-cols-[300px_1fr]">
          <div className="flex flex-col gap-2">
            <div className="flex items-center justify-between px-1">
              <Skeleton className="h-4 w-20" />
              <Skeleton className="h-7 w-16 rounded-md" />
            </div>
            <div className="overflow-hidden rounded-lg border border-neutral-200 bg-white p-2">
              {Array.from({ length: 7 }).map((_, index) => (
                <SetRowSkeleton key={index} />
              ))}
            </div>
          </div>

          <div className="flex flex-col gap-5 rounded-lg border border-neutral-200 bg-white p-5">
            <div className="flex items-center gap-3">
              <Skeleton className="h-14 w-14 rounded-md" />
              <div className="flex flex-col gap-2">
                <Skeleton className="h-5 w-28" />
                <Skeleton className="h-3 w-36" />
              </div>
            </div>
            <Skeleton className="h-10 w-48" />
            <div className="grid grid-cols-4 gap-3 border-b border-neutral-200 pb-2">
              {Array.from({ length: 4 }).map((_, index) => (
                <Skeleton key={index} className="h-7 w-full" />
              ))}
            </div>
            <Skeleton className="h-[280px] w-full rounded-md" />
            <div className="grid grid-cols-3 gap-3 border-y border-neutral-200 py-3">
              {Array.from({ length: 3 }).map((_, index) => (
                <Skeleton key={index} className="h-10 w-full" />
              ))}
            </div>
          </div>
        </div>
      </div>
    </Container>
  );
}
