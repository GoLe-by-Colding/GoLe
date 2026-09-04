import { fetchFeedPage, type PostFeedPage } from "@entities/community";
import { serverSessionHeaders } from "@shared/api/server-session-headers";
import { Container, EmptyState, Heading, LinkButton, Text } from "@shared/ui";
import { CommunityFeed } from "./community-feed";

const INITIAL_FEED_ROWS = 6;

type CommunityLoadResult =
  | { readonly status: "ready"; readonly page: PostFeedPage }
  | { readonly status: "failed"; readonly page: PostFeedPage };

async function loadFeed(): Promise<CommunityLoadResult> {
  try {
    return {
      status: "ready",
      page: await fetchFeedPage({
        headers: await serverSessionHeaders(),
        limit: INITIAL_FEED_ROWS,
      }),
    };
  } catch {
    return { status: "failed", page: { items: [], nextCursor: null } };
  }
}

export async function CommunityPage() {
  const result = await loadFeed();

  return (
    <Container width="xl">
      <div className="flex flex-col gap-6 pt-8 pb-16">
        <div className="flex items-center justify-between gap-4">
          <div className="flex flex-col gap-1">
            <Heading level={1}>커뮤니티</Heading>
            <Text tone="secondary">
              자랑·리뷰·질문·팁·창작(MOC)·이스터에그까지, 브릭 이야기를 나눠요
            </Text>
          </div>
          <LinkButton href="/community/new">글쓰기</LinkButton>
        </div>

        {result.status === "failed" ? (
          <EmptyState
            variant="inline"
            title="커뮤니티 글을 불러오지 못했어요"
            description="연결이 잠시 지연되고 있어요. 다시 확인하거나 새 글 작성 화면으로 이동할 수 있어요."
            action={
              <div className="flex flex-wrap justify-center gap-2">
                <LinkButton href="/community" size="sm" variant="secondary">
                  다시 확인
                </LinkButton>
                <LinkButton href="/community/new" size="sm" variant="ghost">
                  새 글 작성
                </LinkButton>
              </div>
            }
          />
        ) : result.page.items.length === 0 ? (
          <EmptyState
            eyebrow="첫 브릭 이야기"
            title="아직 올라온 글이 없어요"
            description="조립 기록, 창작품, 보관 팁이나 궁금한 점을 가장 먼저 나눠보세요."
            details={["직접 촬영한 작품 자랑", "세트·부품 질문과 보관 팁"]}
            action={
              <div className="flex flex-wrap gap-2">
                <LinkButton href="/community/new">첫 글 쓰기</LinkButton>
                <LinkButton href="/prices" variant="secondary">
                  시세 둘러보기
                </LinkButton>
              </div>
            }
          />
        ) : (
          <CommunityFeed initialPage={result.page} pageSize={INITIAL_FEED_ROWS} />
        )}
      </div>
    </Container>
  );
}
