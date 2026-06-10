"use client";

import { useCallback, useEffect, useState } from "react";
import {
  fetchComments,
  fetchPost,
  type Comment,
  type Post,
} from "@entities/community";
import { CommentForm } from "@features/comment-post";
import { LikeButton } from "@features/like-post";
import { Badge, Card, Container, Heading, Skeleton, Text } from "@shared/ui";

export interface CommunityPostPageProps {
  readonly postId: string;
}

export function CommunityPostPage({ postId }: CommunityPostPageProps) {
  const [post, setPost] = useState<Post | null>(null);
  const [comments, setComments] = useState<readonly Comment[]>([]);
  const [missing, setMissing] = useState(false);

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
          <Skeleton className="aspect-square w-full rounded-2xl" />
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-3/4" />
        </div>
      </Container>
    );
  }

  const cover = post.imageUrls[0];

  return (
    <Container width="sm">
      <div className="flex flex-col gap-5 pt-8 pb-16">
        <div className="flex items-center justify-between">
          <Heading level={2}>{post.authorId.slice(0, 8)}</Heading>
          {post.type === "moc" ? <Badge tone="brand">MOC</Badge> : null}
        </div>
        <Card padded={false} className="overflow-hidden">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            className="w-full object-cover bg-neutral-100"
            src={cover ?? "https://placehold.co/800x800?text=GoLe"}
            alt=""
          />
        </Card>
        <p className="whitespace-pre-wrap leading-relaxed text-neutral-800">{post.content}</p>
        <LikeButton postId={post.id} initialLikeCount={post.likeCount} />

        <div className="flex flex-col gap-3 border-t border-neutral-200 pt-4">
          <Text weight="semibold">댓글 {comments.length}</Text>
          <ul className="flex flex-col gap-2">
            {comments.map((c) => (
              <li key={c.id} className="text-sm">
                <span className="font-semibold text-neutral-900">{c.authorId.slice(0, 8)}</span>{" "}
                <span className="text-neutral-700">{c.content}</span>
              </li>
            ))}
          </ul>
          <CommentForm postId={post.id} onAdded={() => void loadComments()} />
        </div>
      </div>
    </Container>
  );
}
