import { Container, Skeleton } from "@shared/ui";

export default function Loading() {
  return (
    <Container width="lg">
      <div className="grid grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)] gap-10 pt-8 pb-16 max-[820px]:grid-cols-1 max-[820px]:gap-6">
        <div className="flex flex-col gap-3">
          <Skeleton className="aspect-[4/3] w-full rounded-lg" />
          <div className="flex gap-2">
            {Array.from({ length: 3 }).map((_, i) => (
              <Skeleton key={i} className="h-16 w-16 rounded-lg" />
            ))}
          </div>
        </div>
        <div className="flex flex-col gap-4">
          <div className="flex gap-2">
            <Skeleton className="h-6 w-16 rounded-full" />
            <Skeleton className="h-6 w-20 rounded-full" />
          </div>
          <Skeleton className="h-8 w-4/5" />
          <Skeleton className="h-9 w-1/3" />
          <Skeleton className="h-24 w-full rounded-lg" />
          <div className="flex gap-3">
            <Skeleton className="h-12 flex-1 rounded-md" />
            <Skeleton className="h-12 flex-1 rounded-md" />
          </div>
        </div>
      </div>
    </Container>
  );
}
