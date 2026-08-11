import Link from "next/link";
import { POST_TOPIC_LABEL, type Post } from "@entities/community";
import { LikeButton } from "@features/like-post";
import { Card, MediaImage } from "@shared/ui";
import { thumbnailUrl } from "@shared/lib";

export interface PostCardProps {
  readonly post: Post;
}

export function PostCard({ post }: PostCardProps) {
  const cover = post.imageUrls[0];
  const topicLabel = POST_TOPIC_LABEL[post.type];
  const accentTopic = post.type === "moc" || post.type === "easter_egg";

  return (
    <Card padded={false} className="flex flex-col">
      {cover !== undefined ? (
        <Link href={`/community/${post.id}`} className="relative block overflow-hidden">
          <MediaImage
            className="aspect-square w-full bg-neutral-100 object-cover"
            src={thumbnailUrl(cover, 480)}
            alt=""
            loading="lazy"
            fallback="이미지 준비 중"
          />
          <span
            className={`absolute right-3 top-3 rounded-md border px-2.5 py-1 text-xs font-bold ${
              accentTopic
                ? "border-accent-500 bg-accent-500 text-white"
                : "border-neutral-200 bg-white text-neutral-800"
            }`}
          >
            {topicLabel}
          </span>
        </Link>
      ) : null}
      <div className="flex flex-1 flex-col gap-2 p-4">
        <div className="flex items-center justify-between gap-2">
          <div className="flex min-w-0 items-center gap-2">
            <span className="grid h-7 w-7 shrink-0 place-items-center rounded-full bg-brand-50 text-xs font-bold text-brand-700">
              {post.authorId.slice(0, 1).toUpperCase()}
            </span>
            <span className="truncate text-sm font-semibold text-neutral-900">
              {post.authorId.slice(0, 8)}
            </span>
          </div>
          {cover === undefined ? (
            <span
              className={`shrink-0 rounded-md px-2 py-0.5 text-xs font-bold ${
                accentTopic ? "bg-accent-100 text-accent-700" : "bg-neutral-100 text-neutral-600"
              }`}
            >
              {topicLabel}
            </span>
          ) : null}
        </div>
        <Link
          href={`/community/${post.id}`}
          className={`text-sm text-neutral-700 ${cover === undefined ? "line-clamp-4" : "line-clamp-2"}`}
        >
          {post.content}
        </Link>
        <div className="mt-auto flex items-center justify-between border-t border-neutral-100 pt-3">
          <LikeButton postId={post.id} initialLikeCount={post.likeCount} />
          <Link
            href={`/community/${post.id}`}
            className="text-sm font-medium text-neutral-400 hover:text-brand-600"
          >
            자세히
          </Link>
        </div>
      </div>
    </Card>
  );
}
