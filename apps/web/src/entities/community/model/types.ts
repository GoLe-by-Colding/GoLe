/**
 * 커뮤니티 도메인 타입. 백엔드 CommunityDtos와 대응.
 */
export type PostType = "general" | "moc";

export interface Post {
  readonly id: string;
  readonly authorId: string;
  readonly content: string;
  readonly imageUrls: readonly string[];
  readonly type: PostType;
  readonly likeCount: number;
  readonly createdAt: string;
}

export interface Comment {
  readonly id: string;
  readonly authorId: string;
  readonly content: string;
  readonly createdAt: string;
}
