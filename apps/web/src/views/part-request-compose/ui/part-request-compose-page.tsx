"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { CreatePartRequestForm } from "@features/part-request/create";
import { partRequestHref, partRequestsHref } from "@entities/part-request";
import { useSession } from "@entities/user";
import { loginHrefWithReturnTo } from "@shared/lib";
import { Container, Heading, LinkButton, Text } from "@shared/ui";

export interface PartRequestComposePageProps {
  /** `/parts/new?set=` 로 들어오면 세트 번호를 미리 채운다(세트 상세·컬렉션 진입). */
  readonly initialSetNumber?: string | undefined;
}

/** 부족 부품 요청 작성 화면. (wanted-parts W1·W2, F1) */
export function PartRequestComposePage({ initialSetNumber = "" }: PartRequestComposePageProps) {
  const router = useRouter();
  const { session } = useSession();
  const setNumber = initialSetNumber.trim();

  return (
    <Container width="sm">
      <div className="flex flex-col gap-6 pt-10 pb-16">
        <nav aria-label="탐색 경로" className="text-sm text-neutral-500">
          <Link href={partRequestsHref({ setNumber })} className="hover:text-brand-600">
            부품 요청
          </Link>
          <span className="mx-2" aria-hidden="true">
            ›
          </span>
          <span className="text-neutral-700">새 요청</span>
        </nav>
        <div className="flex flex-col gap-1">
          <Heading level={1}>부품 요청하기</Heading>
          <Text tone="secondary">
            부품 번호는 설명서 뒤쪽 부품 목록이나 부품 안쪽에 새겨진 숫자를 적어 주세요. 세트를
            적으면 그 세트를 가진 회원에게 알림이 가요.
          </Text>
        </div>
        {session ? (
          <CreatePartRequestForm
            initialSetNumber={setNumber}
            onCreated={(created) => router.push(partRequestHref(created.id))}
          />
        ) : (
          <div className="flex flex-col items-start gap-4 rounded-lg border border-neutral-200 bg-white p-6">
            <Text tone="secondary">부품 요청을 올리려면 로그인이 필요해요.</Text>
            <LinkButton
              href={loginHrefWithReturnTo(partRequestsHref({ setNumber, compose: true }))}
            >
              로그인하러 가기
            </LinkButton>
          </div>
        )}
      </div>
    </Container>
  );
}
