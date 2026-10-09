"use client";

import { type ReactNode, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { EditListingForm } from "@features/manage-listing";
import { fetchLaunchConfig, type LaunchConfig } from "@entities/launch";
import { fetchListingById, type Listing } from "@entities/listing";
import { useSession } from "@entities/user";
import { isApiNotFoundError } from "@shared/api";
import { isPaymentRuntimeAvailable } from "@shared/config";
import { loginHrefWithReturnTo } from "@shared/lib";
import { Button, Container, Heading, LinkButton, Skeleton, Text } from "@shared/ui";

type ListingLoad =
  | { readonly status: "loading" }
  | { readonly status: "ready"; readonly listing: Listing }
  | { readonly status: "missing" }
  | { readonly status: "failed" };

export interface ListingEditPageProps {
  readonly listingId: string;
}

/**
 * 매물 수정 화면. 본인 매물인지는 브라우저 세션으로만 알 수 있어 클라이언트에서 그린다.
 * 서버도 소유자·상태를 다시 검사하므로(403·409) 여기서 거르는 것은 안내를 위한 것이다.
 */
export function ListingEditPage({ listingId }: ListingEditPageProps) {
  const router = useRouter();
  const { session } = useSession();
  const [load, setLoad] = useState<ListingLoad>({ status: "loading" });
  const [reloadKey, setReloadKey] = useState(0);
  const [launch, setLaunch] = useState<LaunchConfig | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    fetchListingById(listingId, controller.signal)
      .then((listing) => {
        if (!controller.signal.aborted) setLoad({ status: "ready", listing });
      })
      .catch((cause: unknown) => {
        if (controller.signal.aborted) return;
        setLoad({ status: isApiNotFoundError(cause) ? "missing" : "failed" });
      });
    return () => controller.abort();
  }, [listingId, reloadKey]);

  useEffect(() => {
    const controller = new AbortController();
    void fetchLaunchConfig(controller.signal).then((config) => {
      if (!controller.signal.aborted) setLaunch(config);
    });
    return () => controller.abort();
  }, []);

  const paymentsOpen =
    launch?.features.payments === true &&
    launch.sellerIdentityVerificationReady &&
    isPaymentRuntimeAvailable();
  const detailHref = `/listings/${encodeURIComponent(listingId)}`;

  function retry() {
    setLoad({ status: "loading" });
    setReloadKey((current) => current + 1);
  }

  let body: ReactNode;
  if (load.status === "loading") {
    body = (
      <div className="flex flex-col gap-4" aria-busy="true">
        <Skeleton className="h-20 w-full rounded-lg" />
        <Skeleton className="h-11 w-full rounded-md" />
        <Skeleton className="h-28 w-full rounded-md" />
        <Skeleton className="h-11 w-1/2 rounded-md" />
      </div>
    );
  } else if (load.status === "missing") {
    body = (
      <EditNotice
        title="매물을 찾을 수 없어요"
        body="삭제됐거나 주소가 잘못된 매물이에요."
        actions={<LinkButton href="/profile">내 매물로 가기</LinkButton>}
      />
    );
  } else if (load.status === "failed") {
    body = (
      <EditNotice
        title="매물을 불러오지 못했어요"
        body="잠시 후 다시 시도해 주세요."
        actions={<Button onClick={retry}>다시 시도</Button>}
      />
    );
  } else if (session === null) {
    body = (
      <EditNotice
        title="로그인이 필요해요"
        body="매물을 수정하려면 판매자 계정으로 로그인해 주세요."
        actions={
          <LinkButton href={loginHrefWithReturnTo(`${detailHref}/edit`)}>
            로그인하러 가기
          </LinkButton>
        }
      />
    );
  } else if (session.accountId !== load.listing.sellerId) {
    body = (
      <EditNotice
        title="내 매물만 수정할 수 있어요"
        body="다른 판매자의 매물은 수정할 수 없어요. 궁금한 점은 판매자에게 문의해 주세요."
        actions={<LinkButton href={detailHref}>매물 보기</LinkButton>}
      />
    );
  } else if (load.listing.status !== "active") {
    body = (
      <EditNotice
        title={
          load.listing.status === "reserved"
            ? "거래가 진행 중인 매물은 수정할 수 없어요"
            : "판매가 끝난 매물은 수정할 수 없어요"
        }
        body={
          load.listing.status === "reserved"
            ? "주문이 취소되면 다시 수정할 수 있어요."
            : "판매 완료되었거나 판매를 중지한 매물이에요."
        }
        actions={<LinkButton href={detailHref}>매물 보기</LinkButton>}
      />
    );
  } else {
    body = (
      <EditListingForm
        key={load.listing.id}
        listing={load.listing}
        paymentsOpen={paymentsOpen}
        onSaved={(updated) => router.push(`/listings/${encodeURIComponent(updated.id)}`)}
      />
    );
  }

  return (
    <Container width="sm">
      <div className="flex flex-col gap-6 pt-10 pb-16">
        <div className="flex flex-col gap-1">
          <Heading level={1}>매물 수정</Heading>
          <Text tone="secondary">
            가격·상태·사진을 고칠 수 있어요. 찜과 댓글, 채팅은 그대로 이어져요.
          </Text>
        </div>
        {body}
      </div>
    </Container>
  );
}

function EditNotice({
  title,
  body,
  actions,
}: {
  readonly title: string;
  readonly body: string;
  readonly actions?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-start gap-4 rounded-lg border border-neutral-200 bg-white p-6">
      <div className="flex flex-col gap-1.5">
        <Text weight="semibold">{title}</Text>
        <Text tone="secondary" className="max-w-2xl leading-relaxed">
          {body}
        </Text>
      </div>
      {actions === undefined ? null : <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}
