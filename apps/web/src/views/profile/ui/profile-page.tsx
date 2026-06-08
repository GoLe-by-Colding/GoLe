"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { fetchMe, useSession, type Me } from "@entities/user";
import { Button, Card, Container, Heading, LinkButton, Text } from "@shared/ui";

/**
 * 마이페이지: 현재 로그인 사용자의 이메일/권한을 보여주고 로그아웃을 제공한다.
 */
export function ProfilePage() {
  const router = useRouter();
  const { session, signOut } = useSession();
  const token = session?.sessionToken ?? null;
  const [me, setMe] = useState<Me | null>(null);
  const [error, setError] = useState<string | undefined>(undefined);

  useEffect(() => {
    if (token === null) {
      return;
    }
    const controller = new AbortController();
    const run = async (): Promise<void> => {
      try {
        const result = await fetchMe(token);
        if (!controller.signal.aborted) {
          setMe(result);
        }
      } catch {
        if (!controller.signal.aborted) {
          setError("내 정보를 불러오지 못했습니다. 다시 로그인해 주세요.");
        }
      }
    };
    void run();
    return () => controller.abort();
  }, [token]);

  function handleSignOut() {
    signOut();
    router.push("/");
  }

  return (
    <Container width="sm">
      <div className="flex flex-col gap-6 pt-10 pb-16">
        <Heading level={1}>내 정보</Heading>

        {session === null ? (
          <Card>
            <div className="flex flex-col items-start gap-4 p-6">
              <Text tone="secondary">로그인이 필요합니다.</Text>
              <LinkButton href="/login">로그인하러 가기</LinkButton>
            </div>
          </Card>
        ) : (
          <Card>
            <div className="flex flex-col gap-4 p-6">
              {error !== undefined ? (
                <p className="rounded-md bg-danger-soft p-3 text-sm text-danger" role="alert">
                  {error}
                </p>
              ) : null}
              <Field label="이메일" value={me?.email ?? "불러오는 중..."} />
              <Field
                label="권한"
                value={(me?.role ?? session.role) === "ADMIN" ? "관리자" : "일반 회원"}
              />
              <Field label="계정 ID" value={session.accountId} mono />
              <Button variant="secondary" size="lg" onClick={handleSignOut}>
                로그아웃
              </Button>
            </div>
          </Card>
        )}
      </div>
    </Container>
  );
}

function Field({
  label,
  value,
  mono = false,
}: {
  readonly label: string;
  readonly value: string;
  readonly mono?: boolean;
}) {
  return (
    <div className="flex flex-col gap-1">
      <Text tone="muted" size="sm">
        {label}
      </Text>
      <p className={mono ? "font-mono text-sm text-neutral-700" : "text-neutral-900"}>{value}</p>
    </div>
  );
}
