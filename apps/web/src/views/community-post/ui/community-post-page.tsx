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
import { Badge, Card, Container, Heading, LinkButton, Skeleton, Text } from "@shared/ui";

export interface CommunityPostPageProps {
  readonly postId: string;
}

export function CommunityPostPage({ postId }: CommunityPostPageProps) {
  const { session } = useSession();
  const [post, setPost] = useState<Post | null>(null);
  const [comments, setComments] = useState<readonly Comment[]>([]);
  const [missing, setMissing] = useState(false);
  const [deletingCommentId, setDeletingCommentId] = useState<string | null>(null);
  const [commentActionError, setCommentActionError] = useState<string | null>(null);

  const loadComments = useCallback(async () => {
    try {
      setComments(await fetchComments(postId));
    } catch {
      setComments([]);
    }
  }, [postId]);

  useEffect(() => {
    let active = true;
    void (async () => {
      try {
        const data = await fetchPost(postId);
        if (active) {
          setPost(data);
        }
        await loadComments();
      } catch {
        if (active) {
          setMissing(true);
        }
      }
    })();
    return () => {
      active = false;
    };
  }, [postId, loadComments]);

  if (missing) {
    return (
      <Container width="sm">
        <div className="pt-10">
          <Text tone="muted">게시글을 찾을 수 없습니다.</Text>
        </div>
      </Container>
    );
  }

  if (post === null) {
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
