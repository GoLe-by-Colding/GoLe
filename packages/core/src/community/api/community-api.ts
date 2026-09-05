import { apiRequest } from "../../runtime";
import type { Comment, Post, PostFeedCursor, PostFeedPage, PostType } from "../model/types";

const BASE = "/api/v1/community/posts";

export interface FetchFeedOptions {
  readonly signal?: AbortSignal;
  readonly headers?: Readonly<Record<string, string>>;
  readonly limit?: number;
}

export function fetchFeed(options: FetchFeedOptions = {}): Promise<readonly Post[]> {
  const query = options.limit === undefined ? "" : `?limit=${encodeURIComponent(options.limit)}`;
  return apiRequest<readonly Post[]>(`${BASE}${query}`, {
    cache: "no-store",
    ...(options.signal === undefined ? {} : { signal: options.signal }),
    ...(options.headers === undefined ? {} : { headers: options.headers }),
  });
}

export interface FetchFeedPageOptions {
  readonly signal?: AbortSignal;
  readonly headers?: Readonly<Record<string, string>>;
  readonly limit?: number;
  readonly cursor?: PostFeedCursor;
  readonly topic?: PostType;
  readonly query?: string;
}

export function fetchFeedPage(options: FetchFeedPageOptions = {}): Promise<PostFeedPage> {
  const params = new URLSearchParams();
  if (options.limit !== undefined) params.set("limit", String(options.limit));
  if (options.cursor !== undefined) {
    params.set("beforeCreatedAt", options.cursor.beforeCreatedAt);
    params.set("beforeId", options.cursor.beforeId);
  }
  if (options.topic !== undefined) params.set("topic", options.topic);
  if (options.query !== undefined && options.query.trim().length > 0) {
    params.set("q", options.query.trim());
  }
  const query = params.size === 0 ? "" : `?${params.toString()}`;
  return apiRequest<PostFeedPage>(`${BASE}/page${query}`, {
    cache: "no-store",
    ...(options.signal === undefined ? {} : { signal: options.signal }),
    ...(options.headers === undefined ? {} : { headers: options.headers }),
  });
}

/** 전체 피드의 첫 페이지를 클라이언트에서 자르지 않고 서버가 팔로우 관계로 직접 조회한다. */
export function fetchFollowingFeed(signal?: AbortSignal): Promise<readonly Post[]> {
  return apiRequest<readonly Post[]>("/api/v1/community/feed/following?limit=100", {
    cache: "no-store",
    ...(signal === undefined ? {} : { signal }),
  });
}

export function fetchPost(postId: string, signal?: AbortSignal): Promise<Post> {
  return apiRequest<Post>(`${BASE}/${postId}`, {
    cache: "no-store",
    ...(signal === undefined ? {} : { signal }),
  });
}

export function fetchComments(postId: string, signal?: AbortSignal): Promise<readonly Comment[]> {
  return apiRequest<readonly Comment[]>(`${BASE}/${postId}/comments`, {
    cache: "no-store",
    ...(signal === undefined ? {} : { signal }),
  });
}

export interface PublishPostInput {
  readonly authorId: string;
  readonly content: string;
  readonly imageUrls: readonly string[];
  readonly topic: import("../model/types").PostType;
}

export function publishPost(input: PublishPostInput): Promise<Post> {
  return apiRequest<Post>(BASE, { method: "POST", body: input });
}

export function likePost(postId: string): Promise<void> {
  return apiRequest<void>(`${BASE}/${postId}/likes`, {
    method: "POST",
  });
}

export function unlikePost(postId: string): Promise<void> {
  return apiRequest<void>(`${BASE}/${postId}/likes`, { method: "DELETE" });
}

export function commentOnPost(postId: string, authorId: string, content: string): Promise<Comment> {
  return apiRequest<Comment>(`${BASE}/${postId}/comments`, {
    method: "POST",
    body: { authorId, content },
  });
}

export function deleteComment(postId: string, commentId: string): Promise<void> {
  return apiRequest<void>(
    `${BASE}/${encodeURIComponent(postId)}/comments/${encodeURIComponent(commentId)}`,
    { method: "DELETE" },
  );
}

export interface EditPostInput {
  readonly requesterId: string;
  readonly content: string;
  readonly imageUrls: readonly string[];
}

/** 게시글 본문/이미지 수정(작성자 본인). */
export function editPost(postId: string, input: EditPostInput): Promise<Post> {
  return apiRequest<Post>(`${BASE}/${postId}`, { method: "PUT", body: input });
}

/** 게시글 삭제(작성자 본인). */
export function deletePost(postId: string, requesterId: string): Promise<void> {
  return apiRequest<void>(`${BASE}/${postId}?requesterId=${encodeURIComponent(requesterId)}`, {
    method: "DELETE",
  });
}
