import { fetchFeed, type Post } from "@entities/community";
import { Container, Heading, LinkButton, Text } from "@shared/ui";
import { PostCard } from "@widgets/post-card";

async function loadFeed(): Promise<readonly Post[]> {
  try {
    return await fetchFeed();
  } catch {
    return [];
  }
}

export async function CommunityPage() {
  const posts = await loadFeed();

  return (
    <Container width="xl">
      <div className="flex flex-col gap-6 pt-8 pb-16">
        <div className="flex items-center justify-between gap-4">
          <div className="flex flex-col gap-1">
            <Heading level={1}>커뮤니티</Heading>
            <Text tone="secondary">레고 자랑과 MOC를 공유하는 공간</Text>
          </div>
          <LinkButton href="/community/new">글쓰기</LinkButton>
        </div>

        {posts.length === 0 ? (
          <Text tone="muted">아직 게시글이 없습니다. 첫 글을 남겨보세요!</Text>
        ) : (
          <div className="grid gap-5 [grid-template-columns:repeat(auto-fill,minmax(240px,1fr))]">
            {posts.map((post) => (
              <PostCard key={post.id} post={post} />
            ))}
          </div>
        )}
      </div>
    </Container>
  );
}
