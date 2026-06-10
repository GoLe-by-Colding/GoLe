import { Container, Skeleton } from "@shared/ui";

export default function Loading() {
  return (
    <Container width="xl">
      <div className="flex flex-col gap-20 pt-14 pb-24">
        {/* Hero skeleton */}
        <div className="rounded-3xl border border-neutral-100 bg-neutral-50 px-10 py-20">
          <div className="flex flex-col gap-4">
            <Skeleton className="h-6 w-36 rounded-full" />
            <Skeleton className="h-14 w-80 rounded-xl" />
            <Skeleton className="h-6 w-64" />
            <div className="flex gap-3 pt-2">
              <Skeleton className="h-12 w-36 rounded-xl" />
              <Skeleton className="h-12 w-36 rounded-xl" />
            </div>
          </div>
        </div>
        {/* Trending skeleton */}
        <div className="flex flex-col gap-4">
          <Skeleton className="h-7 w-36" />
          <div className="overflow-hidden rounded-2xl border border-neutral-100">
            {Array.from({ length: 4 }).map((_, i) => (
              <div
                key={i}
                className="flex items-center gap-4 border-t border-neutral-100 px-5 py-4 first:border-t-0"
              >
                <Skeleton circle className="h-8 w-8" />
                <Skeleton className="h-10 w-10 rounded-xl" />
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
