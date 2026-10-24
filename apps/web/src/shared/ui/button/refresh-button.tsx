"use client";

import { useTransition } from "react";
import { useRouter } from "next/navigation";
import { Button, type ButtonProps } from "./button";

/** 같은 주소 링크는 조회를 다시 하지 않을 수 있으므로 서버 컴포넌트를 명시적으로 갱신한다. */
export function RefreshButton({
  children = "다시 불러오기",
  ...props
}: Omit<ButtonProps, "onClick" | "loading" | "children"> & {
  readonly children?: ButtonProps["children"];
}) {
  const router = useRouter();
  const [pending, startTransition] = useTransition();
  return (
    <Button
      {...props}
      loading={pending}
      loadingLabel="불러오는 중"
      onClick={() => startTransition(() => router.refresh())}
    >
      {children}
    </Button>
  );
}
