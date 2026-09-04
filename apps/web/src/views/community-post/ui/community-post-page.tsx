"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import {
  fetchComments,
  fetchPost,
  deleteComment,
  POST_TOPIC_LABEL,
  type Comment,
  type Post,
} from "@entities/community";
import { CommentForm } from "@features/comment-post";
import { LikeButton } from "@features/like-post";
import { ReportButton } from "@features/report-content";
import { PostAuthorActions } from "@features/manage-post";
import { useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import {
  Badge,
  Button,
  Card,
  Container,
  EmptyState,
  Heading,
  LinkButton,
  Skeleton,
  Text,
} from "@shared/ui";

export interface CommunityPostPageProps {
  readonly postId: string;
}

export function CommunityPostPage({ postId }: CommunityPostPageProps) {
  const { session } = useSession();
  const [post, setPost] = useState<Post | null>(null);
  const [comments, setComments] = useState<readonly Comment[]>([]);
  const [postStatus, setPostStatus] = useState<"loading" | "ready" | "missing" | "error">(
    "loading",
  );
  const [postAttempt, setPostAttempt] = useState(0);
  const [commentsError, setCommentsError] = useState(false);
  const [deletingCommentId, setDeletingCommentId] = useState<string | null>(null);
  const [commentActionError, setCommentActionError] = useState<string | null>(null);

  const loadComments = useCallback(async () => {
    setCommentsError(false);
    try {
      setComments(await fetchComments(postId));
    } catch {
      setCommentsError(true);
    }
  }, [postId]);

  useEffect(() => {
    let active = true;
    const timer = window.setTimeout(() => {
      if (!active) return;
      setPost(null);
      setPostStatus("loading");
      setComments([]);
      setCommentsError(false);
      setCommentActionError(null);
    }, 0);
    void (async () => {
      try {
        const data = await fetchPost(postId);
        if (active) {
          setPost(data);
          setPostStatus("ready");
        }
        await loadComments();
      } catch (cause) {
        if (active) {
          setPostStatus(cause instanceof ApiError && cause.status === 404 ? "missing" : "error");
        }
      }
    })();
    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [postId, postAttempt, loadComments]);

  if (postStatus === "missing") {
    return (
      <Container width="sm">
        <div className="pt-10 pb-16">
          <EmptyState
            variant="inline"
            title="게시글을 찾을 수 없어요"
            description="삭제되었거나 공개되지 않은 글일 수 있어요. 커뮤니티의 다른 이야기를 둘러보세요."
            action={<LinkButton href="/community">커뮤니티로 돌아가기</LinkButton>}
          />
        </div>
      </Container>
    );
  }

  if (postStatus === "error") {
    return (
      <Container width="sm">
        <div className="pt-10 pb-16">
          <EmptyState
            variant="inline"
            title="게시글을 불러오지 못했어요"
            description="연결이 잠시 지연되고 있어요. 다시 시도하거나 커뮤니티 목록으로 이동하세요."
            action={
              <div className="flex flex-wrap justify-center gap-2">
                <Button size="sm" onClick={() => setPostAttempt((attempt) => attempt + 1)}>
                  다시 시도
                </Button>
                <LinkButton href="/community" size="sm" variant="secondary">
                  커뮤니티 목록
                </LinkButton>
              </div>
            }
          />
        </div>
      </Container>
    );
  }

  if (post === null || postStatus === "loading") {
    return (
      <Container width="sm">
        <div className="flex flex-col gap-5 pt-8 pb-16">
          <div className="flex items-center gap-3">
            <Skeleton circle className="h-9 w-9" />
            <Skeleton className="h-5 w-32" />
          </div>
          <Skeleton className="aspect-square w-full rounded-lg" />
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-3/4" />
        </div>
      </Container>
    );
  }

  const cover = post.imageUrls[0];

  async function handleDeleteComment(comment: Comment): Promise<void> {
    if (!window.confirm("이 댓글을 삭제할까요? 화면에서 즉시 숨겨집니다.")) return;
    setDeletingCommentId(comment.id);
    setCommentActionError(null);
    try {
      await deleteComment(postId, comment.id);
      setComments((current) => current.filter((candidate) => candidate.id !== comment.id));
    } catch {
      setCommentActionError("댓글을 삭제하지 못했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setDeletingCommentId(null);
    }
  }

  return (
    <Container width="sm">
      <div className="flex flex-col gap-5 pt-8 pb-16">
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <Heading level={2}>{post.authorId.slice(0, 8)}</Heading>
            {session?.accountId !== post.authorId ? (
              <LinkButton
                href={`/chat?direct=${encodeURIComponent(post.authorId)}`}
                size="sm"
                variant="secondary"
              >
                1:1 대화
              </LinkButton>
            ) : null}
          </div>
          <Badge tone={post.type === "moc" || post.type === "easter_egg" ? "brand" : "neutral"}>
            {POST_TOPIC_LABEL[post.type]}
          </Badge>
        </div>
        <PostAuthorActions post={post} onUpdated={setPost} />
        {cover !== undefined ? (
          <Card padded={false} className="overflow-hidden">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img className="w-full object-cover bg-neutral-100" src={cover} alt="" />
          </Card>
        ) : null}
        <p className="whitespace-pre-wrap leading-relaxed text-neutral-800">{post.content}</p>
        {post.imageUrls.length > 0 ? (
          <p className="rounded-lg bg-neutral-50 px-3 py-2 text-xs leading-relaxed text-neutral-500">
            게시 이미지는 작성자가 직접 등록한 콘텐츠입니다. 권리 침해가 의심되면 신고해 주세요.{" "}
            <Link href="/terms" className="font-semibold text-brand-700 hover:underline">
              콘텐츠 운영 원칙
            </Link>
          </p>
        ) : null}
        <div className="flex items-center justify-between">
          <LikeButton
            postId={post.id}
            initialLikeCount={post.likeCount}
            initialLiked={post.likedByViewer}
          />
          <ReportButton targetType="POST" targetId={post.id} />
        </div>

        <div className="flex flex-col gap-3 border-t border-neutral-200 pt-4">
          <Text weight="semibold">댓글 {comments.length}</Text>
          <ul className="flex flex-col gap-2">
            {comments.map((c) => (
              <li
                id={`comment-${c.id}`}
                key={c.id}
                className="flex items-start justify-between gap-3 rounded-lg px-2 py-1.5 text-sm hover:bg-neutral-50"
              >
                <p className="min-w-0 leading-relaxed">
                  <span className="font-semibold text-neutral-900">{c.authorId.slice(0, 8)}</span>{" "}
                  <span className="break-words text-neutral-700">{c.content}</span>
                </p>
                <div className="flex shrink-0 items-center gap-2">
                  {session?.accountId === c.authorId ? (
                    <button
                      type="button"
                      onClick={() => void handleDeleteComment(c)}
                      disabled={deletingCommentId === c.id}
                      className="rounded px-1.5 py-1 text-xs font-semibold text-neutral-500 transition-colors hover:bg-neutral-100 hover:text-neutral-800 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      {deletingCommentId === c.id ? "삭제 중…" : "삭제"}
                    </button>
                  ) : (
                    <ReportButton targetType="COMMENT" targetId={c.id} parentId={post.id} compact />
                  )}
                </div>
              </li>
            ))}
          </ul>
          {commentsError ? (
            <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-warning/30 bg-warning-soft px-3 py-2">
              <Text size="sm" tone="secondary" role="alert">
                댓글을 불러오지 못했어요. 작성 기능은 그대로 이용할 수 있어요.
              </Text>
              <Button size="sm" variant="secondary" onClick={() => void loadComments()}>
                댓글 다시 확인
              </Button>
            </div>
          ) : comments.length === 0 ? (
            <Text size="sm" tone="muted">
              아직 댓글이 없어요. 첫 의견을 남겨보세요.
            </Text>
          ) : null}
          {commentActionError === null ? null : (
            <Text role="alert" size="sm" tone="secondary">
              {commentActionError}
            </Text>
          )}
          <CommentForm postId={post.id} onAdded={() => void loadComments()} />
        </div>
      </div>
    </Container>
  );
}
