import { fetchFeed, type Post } from "@entities/community";
import { env } from "@shared/config";
import { Container, Heading, LinkButton, Text } from "@shared/ui";
import { CommunityFeed } from "./community-feed";

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
            <Text tone="secondary">
              자랑·리뷰·질문·팁·창작(MOC)·이스터에그까지, 레고 이야기를 나눠요
            </Text>
          </div>
          <LinkButton href="/community/new">글쓰기</LinkButton>
        </div>

        <section className="relative overflow-hidden rounded-3xl border border-[#5865F2]/20 bg-[#F4F5FF] px-6 py-5 sm:px-8">
          <div
            aria-hidden="true"
            className="absolute -right-10 -top-12 h-40 w-40 rounded-full bg-[#5865F2]/10"
          />
          <div className="relative flex flex-col items-start justify-between gap-5 sm:flex-row sm:items-center">
            <div className="flex items-center gap-4">
              <span className="grid h-12 w-12 shrink-0 place-items-center rounded-2xl bg-[#5865F2] text-white shadow-[0_8px_24px_rgba(88,101,242,0.24)]">
                <svg viewBox="0 0 24 24" aria-hidden="true" className="h-6 w-6 fill-current">
                  <path d="M19.4 5.3A17.1 17.1 0 0 0 15.2 4l-.5 1.1a15.5 15.5 0 0 0-5.4 0L8.8 4a17 17 0 0 0-4.2 1.3C1.9 9.3 1.2 13.2 1.5 17c1.8 1.3 3.5 2.1 5.2 2.6l1.3-1.8a10.4 10.4 0 0 1-2-1l.5-.4c3.8 1.7 7.9 1.7 11.6 0l.6.4c-.7.4-1.4.8-2.1 1l1.3 1.8c1.7-.5 3.5-1.3 5.2-2.6.4-4.4-.7-8.2-3.7-11.7ZM8.7 14.7c-1.1 0-2.1-1.1-2.1-2.5s.9-2.5 2.1-2.5c1.2 0 2.1 1.1 2.1 2.5s-.9 2.5-2.1 2.5Zm6.6 0c-1.2 0-2.1-1.1-2.1-2.5s.9-2.5 2.1-2.5 2.1 1.1 2.1 2.5-.9 2.5-2.1 2.5Z" />
                </svg>
              </span>
              <div>
                <p className="text-xs font-bold uppercase tracking-[0.14em] text-[#5865F2]">
                  GoLe Discord
                </p>
                <h2 className="mt-1 text-lg font-extrabold text-neutral-900">
                  고래방에서 실시간으로 이야기해요
                </h2>
                <p className="mt-1 text-sm text-neutral-600">
                  거래 팁, MOC 자랑, 세트 이야기가 더 빠르게 이어집니다.
                </p>
              </div>
            </div>
            <LinkButton
              href={env.discordInviteUrl}
              target="_blank"
              rel="noreferrer"
              className="shrink-0"
            >
              고래방 입장하기
              <span aria-hidden="true">↗</span>
            </LinkButton>
          </div>
        </section>

        {posts.length === 0 ? (
          <Text tone="muted">아직 게시글이 없습니다. 첫 글을 남겨보세요!</Text>
        ) : (
          <CommunityFeed posts={posts} />
        )}
      </div>
    </Container>
  );
}
