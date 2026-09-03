import { fetchFeedPage, type PostFeedPage } from "@entities/community";
import { serverSessionHeaders } from "@shared/api/server-session-headers";
import { Container, Heading, LinkButton, Text } from "@shared/ui";
import { CommunityFeed } from "./community-feed";

const INITIAL_FEED_ROWS = 6;

async function loadFeed(): Promise<PostFeedPage> {
  try {
    return await fetchFeedPage({
      headers: await serverSessionHeaders(),
      limit: INITIAL_FEED_ROWS,
    });
  } catch {
    return { items: [], nextCursor: null };
  }
}

export async function CommunityPage() {
  const page = await loadFeed();

  return (
    <Container width="xl">
      <div className="flex flex-col gap-6 pt-8 pb-16">
        <div className="flex items-center justify-between gap-4">
          <div className="flex flex-col gap-1">
            <Heading level={1}>커뮤니티</Heading>
            <Text tone="secondary">
              자랑·리뷰·질문·팁·창작(MOC)·이스터에그까지, 레고 이야기를 나눠요
            </Text>
          </div>
          <LinkButton href="/community/new">글쓰기</LinkButton>
        </div>

        {page.items.length === 0 ? (
          <Text tone="muted">아직 게시글이 없습니다. 첫 글을 남겨보세요!</Text>
        ) : (
          <CommunityFeed initialPage={page} pageSize={INITIAL_FEED_ROWS} />
        )}
      </div>
    </Container>
  );
}
