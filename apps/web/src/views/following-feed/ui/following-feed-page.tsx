"use client";

import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import Link from "next/link";
import { fetchFeed, fetchFollowingFeed, type Post } from "@entities/community";
import { fetchFollowing, fetchPersonalizedFeed, type ListingSummary } from "@entities/discovery";
import { conditionLabel, fetchActiveListings, formatPriceKrw } from "@entities/listing";
import { useSession } from "@entities/user";
import {
  Badge,
  Button,
  Card,
  Container,
  EmptyState,
  Heading,
  LinkButton,
  MediaImage,
  MessageCircleIcon,
  Text,
} from "@shared/ui";
import { thumbnailUrl } from "@shared/lib";
import { PostCard } from "@widgets/post-card";

type FeedTab = "all" | "listings" | "posts";

interface FeedState {
  readonly accountId: string | null;
  readonly status: "idle" | "loading" | "ready" | "error";
  readonly followedSellerIds: readonly string[];
  readonly listings: readonly ListingSummary[];
  readonly posts: readonly Post[];
  readonly suggestedListings: readonly ListingSummary[];
  readonly suggestedPosts: readonly Post[];
  readonly failedSources: readonly string[];
}

const INITIAL_STATE: FeedState = {
  accountId: null,
  status: "idle",
  followedSellerIds: [],
  listings: [],
  posts: [],
  suggestedListings: [],
  suggestedPosts: [],
  failedSources: [],
};

export function FollowingFeedPage() {
  const { session } = useSession();
  const accountId = session?.accountId ?? null;
  const [tab, setTab] = useState<FeedTab>("all");
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<FeedState>(INITIAL_STATE);

  const load = useCallback(async (targetAccountId: string, signal: AbortSignal) => {
    const [
      followingResult,
      listingResult,
      postResult,
      suggestedListingResult,
      suggestedPostResult,
    ] = await Promise.allSettled([
      fetchFollowing(targetAccountId, signal),
      fetchPersonalizedFeed(targetAccountId, signal, 36),
      fetchFollowingFeed(signal),
      fetchActiveListings(signal),
      fetchFeed({ signal, limit: 6 }),
    ]);
    if (signal.aborted) return;

    const failedSources = [
      ...(followingResult.status === "rejected" ? ["팔로우 목록"] : []),
      ...(listingResult.status === "rejected" ? ["새 매물"] : []),
      ...(postResult.status === "rejected" ? ["새 글"] : []),
      ...(suggestedListingResult.status === "rejected" ? ["추천 매물"] : []),
      ...(suggestedPostResult.status === "rejected" ? ["추천 이야기"] : []),
    ];
    setState({
      accountId: targetAccountId,
      status: failedSources.length === 5 ? "error" : "ready",
      followedSellerIds: followingResult.status === "fulfilled" ? followingResult.value : [],
      listings: listingResult.status === "fulfilled" ? listingResult.value : [],
      posts: postResult.status === "fulfilled" ? postResult.value : [],
      suggestedListings:
        suggestedListingResult.status === "fulfilled"
          ? suggestedListingResult.value.slice(0, 6)
          : [],
      suggestedPosts: suggestedPostResult.status === "fulfilled" ? suggestedPostResult.value : [],
      failedSources,
    });
  }, []);

  useEffect(() => {
    if (accountId === null) return;
    const controller = new AbortController();
    void Promise.resolve().then(() => load(accountId, controller.signal));
    return () => controller.abort();
  }, [accountId, attempt, load]);

  const people = useMemo(() => {
    const ids = new Set(state.followedSellerIds);
    const listings = state.listings.length > 0 ? state.listings : state.suggestedListings;
    const posts = state.posts.length > 0 ? state.posts : state.suggestedPosts;
    listings.forEach((listing) => ids.add(listing.sellerId));
    posts.forEach((post) => ids.add(post.authorId));
    return [...ids].slice(0, 12);
  }, [
    state.followedSellerIds,
    state.listings,
    state.posts,
    state.suggestedListings,
    state.suggestedPosts,
  ]);

  const visibleState =
    state.accountId === accountId
      ? state
      : { ...INITIAL_STATE, accountId, status: "loading" as const };

  if (accountId === null) {
    return (
      <Container width="xl">
        <div className="flex flex-col gap-7 pt-10 pb-16 sm:pt-14">
          <PageTitle />
          <EmptyState
            eyebrow="나를 위한 새 소식"
            title="로그인하면 팔로잉 피드가 열려요"
            description="관심 있는 빌더와 판매자를 팔로우하면 새 글, 새 매물, 대화 진입점을 한곳에 모아드려요."
            details={["새 매물 놓치지 않기", "빌더와 바로 대화하기"]}
            action={
              <LinkButton href={`/login?returnTo=${encodeURIComponent("/feed")}`}>
                로그인하고 피드 보기
              </LinkButton>
            }
          />
        </div>
      </Container>
    );
  }

  const shownListings =
    visibleState.listings.length > 0 ? visibleState.listings : visibleState.suggestedListings;
  const shownPosts =
    visibleState.posts.length > 0 ? visibleState.posts : visibleState.suggestedPosts;
  const showingSuggestedListings =
    visibleState.listings.length === 0 && visibleState.suggestedListings.length > 0;
  const showingSuggestedPosts =
    visibleState.posts.length === 0 && visibleState.suggestedPosts.length > 0;
  const hasContent = shownListings.length > 0 || shownPosts.length > 0;

  return (
    <Container width="xl">
      <div className="flex flex-col gap-7 pt-8 pb-16 sm:pt-10">
        <div className="flex flex-col justify-between gap-5 sm:flex-row sm:items-end">
          <PageTitle />
          <Button variant="secondary" size="sm" onClick={() => setAttempt((value) => value + 1)}>
            새로고침
          </Button>
        </div>

        <section className="grid grid-cols-3 overflow-hidden rounded-xl border border-brand-100 bg-brand-50/50">
          <FeedStat label="팔로잉" value={`${visibleState.followedSellerIds.length}명`} />
          <FeedStat label="새 매물" value={`${visibleState.listings.length}개`} />
          <FeedStat label="새 이야기" value={`${visibleState.posts.length}개`} />
        </section>

        {people.length > 0 ? (
          <section className="flex flex-col gap-3" aria-labelledby="following-people-title">
            <div className="flex items-center justify-between gap-3">
              <Heading level={2} id="following-people-title" className="text-xl">
                {visibleState.followedSellerIds.length > 0
                  ? "이어지는 사람들"
                  : "먼저 만나볼 사람들"}
              </Heading>
              <Text size="sm" tone="muted">
                {visibleState.followedSellerIds.length > 0
                  ? "프로필이나 대화로 바로 이동"
                  : "관심 가는 빌더와 판매자를 발견해 보세요"}
              </Text>
            </div>
            <div className="flex gap-2 overflow-x-auto pb-1">
              {people.map((personId) => (
                <div
                  key={personId}
                  className="flex shrink-0 items-center gap-2 rounded-full border border-neutral-200 bg-white py-1.5 pr-2 pl-1.5"
                >
                  <Link
                    href={`/shops/${encodeURIComponent(personId)}`}
                    className="inline-flex items-center gap-2 rounded-full outline-none focus-visible:ring-2 focus-visible:ring-brand-400"
                  >
                    <span className="grid h-8 w-8 place-items-center rounded-full bg-brand-100 text-xs font-bold text-brand-700">
                      {personId.slice(0, 1).toUpperCase()}
                    </span>
                    <span className="max-w-28 truncate text-sm font-semibold text-neutral-800">
                      {personId.slice(0, 12)}
                    </span>
                  </Link>
                  {personId !== accountId ? (
                    <Link
                      href={`/chat?direct=${encodeURIComponent(personId)}`}
                      aria-label={`${personId.slice(0, 12)} 님과 대화`}
                      className="grid h-8 w-8 place-items-center rounded-full text-brand-600 transition-colors hover:bg-brand-50"
                    >
                      <MessageCircleIcon className="h-4 w-4" />
                    </Link>
                  ) : null}
                </div>
              ))}
            </div>
          </section>
        ) : null}

        <div
          className="flex gap-1 rounded-lg bg-neutral-100 p-1"
          role="tablist"
          aria-label="피드 종류"
        >
          {(
            [
              ["all", "모아보기"],
              ["listings", "새 매물"],
              ["posts", "새 이야기"],
            ] as const
          ).map(([value, label]) => (
            <button
              key={value}
              type="button"
              role="tab"
              aria-selected={tab === value}
              onClick={() => setTab(value)}
              className={`flex-1 rounded-md px-3 py-2 text-sm font-semibold transition-colors ${
                tab === value
                  ? "bg-white text-neutral-900 shadow-sm"
                  : "text-neutral-500 hover:text-neutral-800"
              }`}
            >
              {label}
            </button>
          ))}
        </div>

        {visibleState.status === "loading" && !hasContent ? (
          <FeedSkeleton />
        ) : visibleState.status === "error" ? (
          <EmptyState
            variant="inline"
            title="팔로잉 피드를 불러오지 못했어요"
            description="연결을 확인한 뒤 다시 시도해 주세요."
            action={<Button onClick={() => setAttempt((value) => value + 1)}>다시 시도</Button>}
          />
        ) : !hasContent ? (
          <EmptyState
            eyebrow="첫 팔로우부터 시작"
            title="아직 모아볼 새 소식이 없어요"
            description="커뮤니티 글의 작성자나 판매자 샵에서 팔로우를 누르면 이곳에 새 글과 매물이 시간순으로 쌓여요."
            details={["관심 판매자 새 매물", "빌더의 새 글과 작품"]}
            action={
              <div className="flex flex-wrap gap-2">
                <LinkButton href="/community">빌더 둘러보기</LinkButton>
                <LinkButton href="/search" variant="secondary">
                  매물 탐색하기
                </LinkButton>
              </div>
            }
          />
        ) : (
          <>
            {visibleState.failedSources.length > 0 ? (
              <p className="rounded-lg border border-warning/30 bg-warning-soft px-4 py-3 text-sm text-neutral-700">
                {visibleState.failedSources.join(" · ")}은 잠시 불러오지 못했어요. 나머지 소식은
                계속 볼 수 있어요.
              </p>
            ) : null}

            {showingSuggestedListings || showingSuggestedPosts ? (
              <p className="rounded-xl border border-brand-100 bg-brand-50 px-4 py-3 text-sm leading-relaxed text-brand-900">
                아직 팔로우 소식이 비어 있어 최근 활동을 먼저 보여드려요. 관심 가는 사람을
                팔로우하면 다음 방문부터 내 피드로 바뀝니다.
              </p>
            ) : null}

            {(tab === "all" || tab === "listings") && shownListings.length > 0 ? (
              <FeedSection
                title={
                  showingSuggestedListings ? "처음 만나볼 새 매물" : "팔로우한 판매자의 새 매물"
                }
                description={
                  showingSuggestedListings
                    ? "최근 등록된 매물에서 관심 판매자를 찾아보세요."
                    : "최근 등록 순으로 보여드려요."
                }
              >
                <div className="grid gap-5 [grid-template-columns:repeat(auto-fill,minmax(220px,1fr))]">
                  {shownListings.map((listing) => (
                    <FollowingListingCard key={listing.id} listing={listing} />
                  ))}
                </div>
              </FeedSection>
            ) : tab === "listings" ? (
              <EmptyState
                variant="inline"
                title="새 매물이 아직 없어요"
                description="팔로우한 판매자가 상품을 등록하면 가장 먼저 이곳에 보여드려요."
              />
            ) : null}

            {(tab === "all" || tab === "posts") && shownPosts.length > 0 ? (
              <FeedSection
                title={showingSuggestedPosts ? "지금 둘러볼 이야기" : "팔로우한 빌더의 새 이야기"}
                description={
                  showingSuggestedPosts
                    ? "새 작품과 팁을 보고 마음에 드는 빌더를 찾아보세요."
                    : "작품과 팁을 이어서 만나보세요."
                }
              >
                <div className="grid gap-5 [grid-template-columns:repeat(auto-fill,minmax(240px,1fr))]">
                  {shownPosts.map((post) => (
                    <PostCard key={post.id} post={post} />
                  ))}
                </div>
              </FeedSection>
            ) : tab === "posts" ? (
              <EmptyState
                variant="inline"
                title="새 이야기가 아직 없어요"
                description="팔로우한 빌더가 글을 남기면 이곳에서 바로 이어볼 수 있어요."
              />
            ) : null}
          </>
        )}
      </div>
    </Container>
  );
}

function PageTitle() {
  return (
    <div className="flex flex-col gap-1.5">
      <span className="self-start border-l-2 border-accent-400 pl-3 text-sm font-semibold text-brand-700">
        내가 고른 사람들의 지금
      </span>
      <Heading level={1}>팔로잉 피드</Heading>
      <Text tone="secondary">새 글을 보고, 매물을 발견하고, 바로 대화까지 이어가세요.</Text>
    </div>
  );
}

function FeedStat({ label, value }: { readonly label: string; readonly value: string }) {
  return (
    <div className="flex min-w-0 flex-col gap-1.5 border-r border-brand-100 px-3 py-4 last:border-r-0 sm:flex-row sm:items-baseline sm:justify-between sm:gap-3 sm:px-5">
      <span className="text-xs font-semibold text-brand-600">{label}</span>
      <strong className="truncate text-base font-extrabold tracking-tight text-brand-950 sm:text-lg">
        {value}
      </strong>
    </div>
  );
}

function FeedSection({
  title,
  description,
  children,
}: {
  readonly title: string;
  readonly description: string;
  readonly children: ReactNode;
}) {
  return (
    <section className="flex flex-col gap-4">
      <div className="flex flex-col gap-1">
        <Heading level={2} className="text-2xl">
          {title}
        </Heading>
        <Text size="sm" tone="muted">
          {description}
        </Text>
      </div>
      {children}
    </section>
  );
}

function FollowingListingCard({ listing }: { readonly listing: ListingSummary }) {
  const cover = listing.photoUrls[0];
  return (
    <Card
      interactive
      padded={false}
      className="flex h-full flex-col"
      data-testid="following-listing-card"
    >
      <Link href={`/listings/${listing.id}`} className="block overflow-hidden">
        <MediaImage
          className="aspect-[4/3] w-full bg-neutral-100 object-cover transition-transform duration-300 motion-safe:group-hover:scale-[1.02]"
          src={cover === undefined ? null : thumbnailUrl(cover, 480)}
          alt={listing.title}
          loading="lazy"
          fallback="이미지 준비 중"
        />
      </Link>
      <div className="flex flex-1 flex-col gap-3 p-4">
        <div className="flex items-center justify-between gap-2">
          <Link
            href={`/shops/${encodeURIComponent(listing.sellerId)}`}
            className="min-w-0 truncate text-sm font-semibold text-brand-700 hover:text-brand-800"
          >
            {listing.sellerId.slice(0, 12)}
          </Link>
          <Badge tone="neutral">{conditionLabel(listing.condition)}</Badge>
        </div>
        <Link
          href={`/listings/${listing.id}`}
          className="line-clamp-2 font-semibold text-neutral-900"
        >
          {listing.title}
        </Link>
        <div className="mt-auto flex items-end justify-between gap-3 border-t border-neutral-100 pt-3">
          <div className="flex flex-col gap-0.5">
            <strong className="text-lg font-extrabold tracking-tight text-neutral-900">
              {formatPriceKrw(listing.price)}
            </strong>
            <span className="text-xs text-neutral-400">
              {new Date(listing.createdAt).toLocaleDateString("ko-KR")}
            </span>
          </div>
          <Link
            href={`/listings/${encodeURIComponent(listing.id)}?chat=1`}
            className="text-sm font-semibold text-brand-600 hover:text-brand-700"
          >
            대화
          </Link>
        </div>
      </div>
    </Card>
  );
}

function FeedSkeleton() {
  return (
    <div
      className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3"
      aria-busy="true"
      aria-label="피드 불러오는 중"
    >
      {Array.from({ length: 3 }, (_, index) => (
        <Card key={index} className="flex flex-col gap-4 p-4">
          <div className="aspect-[4/3] animate-pulse rounded-md bg-neutral-100" />
          <div className="h-5 w-2/3 animate-pulse rounded bg-neutral-100" />
          <div className="h-4 w-1/2 animate-pulse rounded bg-neutral-100" />
        </Card>
      ))}
    </div>
  );
}
