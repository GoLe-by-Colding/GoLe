import Link from "next/link";
import type { Post } from "@entities/community";
import { LikeButton } from "@features/like-post";
import { Badge, Card } from "@shared/ui";
import { thumbnailUrl } from "@shared/lib";

export interface PostCardProps {
  readonly post: Post;
}

export function PostCard({ post }: PostCardProps) {
  const cover = post.imageUrls[0];

  return (
    <Card padded={false} className="flex flex-col">
      <Link href={`/community/${post.id}`}>
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          className="w-full aspect-square object-cover bg-neutral-100"
          src={cover === undefined ? "https://placehold.co/600x600?text=GoLe" : thumbnailUrl(cover, 480)}
          alt=""
          loading="lazy"
        />
      </Link>
      <div className="flex flex-col gap-2 p-4">
        <div className="flex items-center justify-between gap-2">
          <span className="text-sm font-semibold text-neutral-900">
            {post.authorId.slice(0, 8)}
          </span>
          {post.type === "moc" ? <Badge tone="brand">MOC</Badge> : null}
        </div>
        <Link href={`/community/${post.id}`} className="text-sm text-neutral-700 line-clamp-2">
          {post.content}
        </Link>
        <div className="flex items-center justify-between pt-1">
          <LikeButton postId={post.id} initialLikeCount={post.likeCount} />
          <Link
            href={`/community/${post.id}`}
            className="text-sm text-neutral-400 hover:text-neutral-700"
          >
            자세히
          </Link>
        </div>
      </div>
    </Card>
  );
}
