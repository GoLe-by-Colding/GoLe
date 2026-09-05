"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { fetchSellerShop, type ListingSummary } from "@entities/discovery";
import {
  fetchSellerRating,
  fetchSellerReviews,
  replyToReview,
  type Review,
  type SellerRating,
} from "@entities/review";
import { FollowButton } from "@features/follow-seller";
import { ReportButton } from "@features/report-content";
import { useSession } from "@entities/user";
import { fetchLaunchConfig } from "@entities/launch";
import { formatKrw } from "@shared/lib";
import {
  Badge,
  Button,
  Card,
  Container,
  Heading,
  LinkButton,
  StarIcon,
  Text,
  Textarea,
} from "@shared/ui";

export interface SellerShopPageProps {
  readonly sellerId: string;
}

export function SellerShopPage({ sellerId }: SellerShopPageProps) {
  const { session } = useSession();
  const [listings, setListings] = useState<readonly ListingSummary[]>([]);
  const [rating, setRating] = useState<SellerRating | null>(null);
  const [reviews, setReviews] = useState<readonly Review[]>([]);
  const [reviewsOpen, setReviewsOpen] = useState(false);

  useEffect(() => {
    let active = true;
    void (async () => {
      try {
        const [shop, launch] = await Promise.all([fetchSellerShop(sellerId), fetchLaunchConfig()]);
        const [ratingSummary, reviewList] = launch.features.reviews
          ? await Promise.all([fetchSellerRating(sellerId), fetchSellerReviews(sellerId)])
          : ([null, []] as const);
        if (active) {
          setListings(shop);
          setRating(ratingSummary);
          setReviews(reviewList);
          setReviewsOpen(launch.features.reviews);
        }
      } catch {
        /* ignore */
      }
    })();
    return () => {
      active = false;
    };
  }, [sellerId]);

  return (
    <Container width="xl">
      <div className="flex flex-col gap-6 pt-8 pb-16">
        <div className="flex items-center justify-between gap-4">
          <div className="flex flex-col gap-1">
            <Heading level={1}>{sellerId.slice(0, 8)} 님의 샵</Heading>
            <div className="flex items-center gap-3">
              <Text tone="secondary">판매 중인 상품 {listings.length}개</Text>
              {reviewsOpen && rating !== null && rating.count > 0 ? (
                <Badge tone="warning" data-testid="seller-rating">
                  <span
                    className="inline-flex items-center gap-1"
                    aria-label={`평점 ${rating.average.toFixed(1)}점, 후기 ${rating.count}개`}
                  >
                    <StarIcon className="h-3.5 w-3.5" filled />
                    <span aria-hidden="true">
                      {rating.average.toFixed(1)} ({rating.count})
                    </span>
                  </span>
                </Badge>
              ) : reviewsOpen ? (
                <Text tone="muted">후기 없음</Text>
              ) : null}
            </div>
          </div>
          <div className="flex items-center gap-2">
            {session?.accountId !== sellerId ? (
              <LinkButton
                href={`/chat?direct=${encodeURIComponent(sellerId)}`}
                size="sm"
                variant="secondary"
              >
                1:1 대화
              </LinkButton>
            ) : null}
            {session?.accountId !== sellerId ? <FollowButton sellerId={sellerId} /> : null}
          </div>
        </div>

        {listings.length === 0 ? (
          <Text tone="muted">판매 중인 상품이 없습니다.</Text>
        ) : (
          <div className="grid gap-5 [grid-template-columns:repeat(auto-fill,minmax(200px,1fr))]">
            {listings.map((l) => (
              <Link key={l.id} href={`/listings/${l.id}`}>
                <Card interactive padded className="flex flex-col gap-2">
                  <span className="text-sm font-semibold text-neutral-900 line-clamp-1">
                    {l.title}
                  </span>
                  <span className="text-lg font-bold">{formatKrw(l.price)}</span>
                  {l.catalogSetNumber !== null ? (
                    <span className="font-mono text-xs text-neutral-500">
                      #{l.catalogSetNumber}
                    </span>
                  ) : null}
                </Card>
              </Link>
            ))}
          </div>
        )}

        {reviewsOpen ? (
          <section className="flex flex-col gap-3 pt-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <Heading level={2}>거래 후기</Heading>
              <Link
                href="/review-policy"
                className="text-xs font-semibold text-brand-700 underline underline-offset-2"
              >
                후기 작성·게시·평점·삭제 기준
              </Link>
            </div>
            {reviews.length === 0 ? (
              <Text tone="muted">아직 등록된 후기가 없습니다.</Text>
            ) : (
              <ul className="flex flex-col gap-3">
                {reviews.map((r) => (
                  <li key={r.id}>
                    <Card padded className="flex flex-col gap-2">
                      <div className="flex items-center justify-between">
                        <span
                          className="inline-flex items-center gap-0.5 text-warning"
                          aria-label={`5점 만점에 ${r.rating}점`}
                        >
                          {Array.from({ length: 5 }, (_, index) => (
                            <StarIcon
                              key={index}
                              className={`h-4 w-4 ${index < r.rating ? "" : "text-neutral-300"}`}
                              filled={index < r.rating}
                            />
                          ))}
                        </span>
                        <span className="text-xs text-neutral-400">
                          {new Date(r.createdAt).toLocaleDateString("ko-KR")}
                        </span>
                      </div>
                      <Text>{r.content}</Text>
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <span className="font-mono text-xs text-neutral-400">
                          {r.reviewerId.slice(0, 8)}
                        </span>
                        {session?.accountId !== r.reviewerId ? (
                          <ReportButton targetType="REVIEW" targetId={r.id} />
                        ) : null}
                      </div>
                      {typeof r.reply === "string" ? (
                        <div className="rounded-lg bg-neutral-50 px-4 py-3">
                          <p className="text-xs font-semibold text-neutral-500">판매자 답글</p>
                          <Text className="mt-1" size="sm">
                            {r.reply}
                          </Text>
                        </div>
                      ) : null}
                      {session?.accountId === sellerId ? (
                        <SellerReplyEditor
                          review={r}
                          onUpdated={(updated) =>
                            setReviews((current) =>
                              current.map((item) => (item.id === updated.id ? updated : item)),
                            )
                          }
                        />
                      ) : null}
                    </Card>
                  </li>
                ))}
              </ul>
            )}
          </section>
        ) : null}
      </div>
    </Container>
  );
}

interface SellerReplyEditorProps {
  readonly review: Review;
  readonly onUpdated: (review: Review) => void;
}

function SellerReplyEditor({ review, onUpdated }: SellerReplyEditorProps) {
  const hasReply = typeof review.reply === "string";
  const [editing, setEditing] = useState(false);
  const [content, setContent] = useState(review.reply ?? "");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    const value = content.trim();
    if (value.length === 0 || busy) {
      setError("답글 내용을 입력해 주세요.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const updated = await replyToReview(review.id, value);
      onUpdated(updated);
      setContent(updated.reply ?? "");
      setEditing(false);
    } catch {
      setError("답글을 저장하지 못했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setBusy(false);
    }
  }

  if (!editing) {
    return (
      <div>
        <Button size="sm" variant="ghost" onClick={() => setEditing(true)}>
          {hasReply ? "답글 수정" : "답글 남기기"}
        </Button>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-2 rounded-lg border border-neutral-200 p-3">
      <Textarea
        value={content}
        maxLength={1000}
        rows={3}
        aria-label="판매자 답글"
        placeholder="거래 후기에 정중하게 답해 주세요"
        invalid={error !== null}
        disabled={busy}
        onChange={(event) => setContent(event.target.value)}
      />
      {error !== null ? <p className="text-xs text-danger">{error}</p> : null}
      <div className="flex justify-end gap-2">
        <Button
          size="sm"
          variant="ghost"
          disabled={busy}
          onClick={() => {
            setContent(review.reply ?? "");
            setError(null);
            setEditing(false);
          }}
        >
          취소
        </Button>
        <Button size="sm" disabled={busy} onClick={() => void submit()}>
          {busy ? "저장 중" : "답글 저장"}
        </Button>
      </div>
    </div>
  );
}
