import Link from "next/link";
import type { Post } from "@entities/community";
import { LikeButton } from "@features/like-post";
import { Card } from "@shared/ui";
import { thumbnailUrl } from "@shared/lib";

export interface PostCardProps {
  readonly post: Post;
}

export function PostCard({ post }: PostCardProps) {
  const cover = post.imageUrls[0];

  return (
    <Card padded={false} className="flex flex-col">
      <Link href={`/community/${post.id}`} className="relative block overflow-hidden">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          className="img-zoom w-full aspect-square object-cover bg-neutral-100"
          src={cover === undefined ? "https://placehold.co/600x600?text=GoLe" : thumbnailUrl(cover, 480)}
          alt=""
          loading="lazy"
        />
        {post.type === "moc" ? (
          <span className="absolute right-3 top-3 rounded-full bg-accent-500 px-2.5 py-1 text-xs font-bold text-white shadow-sm">
            MOC
          </span>
        ) : null}
      </Link>
      <div className="flex flex-col gap-2 p-4">
        <div className="flex items-center gap-2">
          <span className="grid h-7 w-7 shrink-0 place-items-center rounded-full bg-brand-50 text-xs font-bold text-brand-700">
            {post.authorId.slice(0, 1).toUpperCase()}
          </span>
          <span className="truncate text-sm font-semibold text-neutral-900">
            {post.authorId.slice(0, 8)}
          </span>
        </div>
        <Link href={`/community/${post.id}`} className="text-sm text-neutral-700 line-clamp-2">
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
