import { apiRequest } from "@shared/api";
import type { Comment, Post } from "../model/types";

const BASE = "/api/v1/community/posts";

export function fetchFeed(signal?: AbortSignal): Promise<readonly Post[]> {
  return apiRequest<readonly Post[]>(BASE, {
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

export function fetchComments(
  postId: string,
  signal?: AbortSignal,
): Promise<readonly Comment[]> {
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

export function likePost(postId: string, userId: string): Promise<void> {
  return apiRequest<void>(`${BASE}/${postId}/likes`, {
    method: "POST",
    body: { userId },
  });
}

export function commentOnPost(
  postId: string,
  authorId: string,
  content: string,
): Promise<Comment> {
  return apiRequest<Comment>(`${BASE}/${postId}/comments`, {
    method: "POST",
    body: { authorId, content },
  });
}
