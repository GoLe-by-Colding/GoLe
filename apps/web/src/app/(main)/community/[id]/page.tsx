import { CommunityPostPage } from "@views/community-post";

export default async function Page({
  params,
}: {
  readonly params: Promise<{ readonly id: string }>;
}) {
  const { id } = await params;
  return <CommunityPostPage postId={id} />;
}
