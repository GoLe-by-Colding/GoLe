import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { fetchPost, POST_TOPIC_LABEL, type Post } from "@entities/community";
import { CommunityPostPage } from "@views/community-post";
import { isApiNotFoundError } from "@shared/api";
import { env } from "@shared/config";
import { JsonLd } from "@shared/ui";
import { absoluteUrl, breadcrumbJsonLd } from "@shared/lib";

interface PageParams {
  readonly params: Promise<{ readonly id: string }>;
}

async function loadPost(id: string): Promise<Post | null> {
  try {
    return await fetchPost(id);
  } catch (cause) {
    if (isApiNotFoundError(cause)) return null;
    throw cause;
  }
}

/** 게시글에는 별도 제목 필드가 없다. 본문 첫 줄을 제목처럼 쓴다. */
function postHeadline(post: Post): string {
  const firstLine = post.content.split("\n").find((line) => line.trim().length > 0) ?? "";
  const trimmed = firstLine.trim();
  if (trimmed.length === 0) {
    return `${POST_TOPIC_LABEL[post.type]} 게시글`;
  }
  return trimmed.length > 60 ? `${trimmed.slice(0, 60)}…` : trimmed;
}

/** 커뮤니티 게시글 동적 메타데이터. (SEO 스펙 R3.3) */
export async function generateMetadata({ params }: PageParams): Promise<Metadata> {
  const { id } = await params;
  const post = await loadPost(id);
  if (post === null) {
    return {
      title: "커뮤니티",
      description: "GoLe 브릭 커뮤니티",
      alternates: { canonical: `/community/${id}` },
      robots: { index: false, follow: false },
    };
  }

  const title = `${postHeadline(post)} · ${POST_TOPIC_LABEL[post.type]}`;
  const description = post.content.replace(/\s+/g, " ").slice(0, 150);
  const cover = post.imageUrls[0];

  return {
    title,
    description,
    alternates: { canonical: `/community/${id}` },
    openGraph: {
      title,
      description,
      url: `/community/${id}`,
      type: "article",
      publishedTime: post.createdAt,
      ...(cover === undefined ? {} : { images: [{ url: absoluteUrl(cover, env.siteUrl) }] }),
    },
  };
}

export default async function Page({ params }: PageParams) {
  const { id } = await params;
  const post = await loadPost(id);
  if (post === null) {
    notFound();
  }

  return (
    <>
      <CommunityPostPage postId={id} />
      <JsonLd
        data={{
          "@context": "https://schema.org",
          "@type": "Article",
          "@id": `${env.siteUrl}/community/${post.id}#article`,
          headline: postHeadline(post),
          articleSection: POST_TOPIC_LABEL[post.type],
          datePublished: post.createdAt,
          url: `${env.siteUrl}/community/${post.id}`,
          author: { "@type": "Person", name: post.authorId },
          publisher: { "@id": `${env.siteUrl}/#organization` },
          ...(post.imageUrls.length === 0
            ? {}
            : { image: post.imageUrls.map((u) => absoluteUrl(u, env.siteUrl)) }),
          interactionStatistic: {
            "@type": "InteractionCounter",
            interactionType: "https://schema.org/LikeAction",
            userInteractionCount: post.likeCount,
          },
        }}
      />
      <JsonLd
        data={breadcrumbJsonLd(
          [
            { name: "홈", path: "/" },
            { name: "커뮤니티", path: "/community" },
            { name: postHeadline(post), path: `/community/${post.id}` },
          ],
          env.siteUrl,
        )}
      />
    </>
  );
}
