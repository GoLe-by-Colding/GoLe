import { Container, Skeleton } from "@shared/ui";

/** 상위 매물 상세의 로딩 화면(갤러리 골격) 대신 폼 골격을 보여 준다. */
export default function Loading() {
  return (
    <Container width="sm">
      <div className="flex flex-col gap-4 pt-10 pb-16">
        <Skeleton className="h-8 w-1/3" />
        <Skeleton className="h-20 w-full rounded-lg" />
        <Skeleton className="h-11 w-full rounded-md" />
        <Skeleton className="h-28 w-full rounded-md" />
      </div>
    </Container>
  );
}
