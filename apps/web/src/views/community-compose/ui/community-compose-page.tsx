"use client";

import { useRouter } from "next/navigation";
import { CreatePostForm } from "@features/create-post";
import { useSession } from "@entities/user";
import { Container, Heading, LinkButton, Text } from "@shared/ui";

export function CommunityComposePage() {
  const router = useRouter();
  const { session } = useSession();

  return (
    <Container width="sm">
      <div className="flex flex-col gap-6 pt-10 pb-16">
        <Heading level={1}>글쓰기</Heading>
        {session ? (
          <CreatePostForm
            authorId={session.accountId}
            onCreated={(id) => router.push(`/community/${id}`)}
          />
        ) : (
          <div className="flex flex-col items-start gap-4 rounded-lg border border-neutral-200 bg-white p-6">
            <Text tone="secondary">글을 쓰려면 로그인이 필요합니다.</Text>
            <LinkButton href="/login">로그인하러 가기</LinkButton>
          </div>
        )}
      </div>
    </Container>
  );
}
