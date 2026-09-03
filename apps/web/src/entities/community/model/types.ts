/**
 * 커뮤니티 도메인 타입. 백엔드 CommunityDtos와 대응.
 */
export type PostType =
  | "general"
  | "showcase"
  | "moc"
  | "review"
  | "question"
  | "tip"
  | "easter_egg";

/** 토픽 메타(노출 순서·라벨). 작성 셀렉트/피드 탭/배지에서 공통 사용. */
export const POST_TOPICS: ReadonlyArray<{ readonly key: PostType; readonly label: string }> = [
  { key: "general", label: "자유" },
  { key: "showcase", label: "자랑" },
  { key: "moc", label: "창작(MOC)" },
  { key: "review", label: "리뷰" },
  { key: "question", label: "질문" },
  { key: "tip", label: "팁" },
  { key: "easter_egg", label: "이스터에그" },
];

export const POST_TOPIC_LABEL: Record<PostType, string> = {
  general: "자유",
  showcase: "자랑",
  moc: "창작(MOC)",
  review: "리뷰",
  question: "질문",
  tip: "팁",
  easter_egg: "이스터에그",
};

export interface Post {
  readonly id: string;
  readonly authorId: string;
  readonly content: string;
  readonly imageUrls: readonly string[];
  readonly type: PostType;
  readonly likeCount: number;
  readonly likedByViewer: boolean;
  readonly createdAt: string;
}

export interface PostFeedCursor {
  readonly beforeCreatedAt: string;
  readonly beforeId: string;
}

export interface PostFeedPage {
  readonly items: readonly Post[];
  readonly nextCursor: PostFeedCursor | null;
}

export interface Comment {
  readonly id: string;
  readonly authorId: string;
  readonly content: string;
  readonly createdAt: string;
}
