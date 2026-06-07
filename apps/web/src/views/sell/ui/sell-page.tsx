"use client";

import { useRouter } from "next/navigation";
import { CreateListingForm } from "@features/create-listing";
import { useSession } from "@entities/user";
import { Container, Heading, LinkButton, Text } from "@shared/ui";

export function SellPage() {
  const router = useRouter();
  const { session } = useSession();

  return (
    <Container width="sm">
      <div className="flex flex-col gap-6 pt-10 pb-16">
        <div className="flex flex-col gap-1">
          <Heading level={1}>상품 등록</Heading>
          <Text tone="secondary">판매할 레고 상품 정보를 입력하세요.</Text>
        </div>

        {session ? (
          <CreateListingForm
            sellerId={session.accountId}
            onCreated={(id) => router.push(`/listings/${id}`)}
          />
        ) : (
          <div className="flex flex-col items-start gap-4 rounded-lg border border-neutral-200 bg-white p-6">
            <Text tone="secondary">상품을 등록하려면 로그인이 필요합니다.</Text>
            <LinkButton href="/login">로그인하러 가기</LinkButton>
          </div>
        )}
      </div>
    </Container>
  );
}
