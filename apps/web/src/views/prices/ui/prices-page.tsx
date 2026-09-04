import { fetchFeaturedLegoSets, fetchLegoSetByNumber, type LegoSet } from "@entities/lego-set";
import { fetchPriceSnapshot, type PricePoint, type PriceSnapshot } from "@entities/pricing";
import { Container, EmptyState, Heading, LinkButton, Text } from "@shared/ui";
import { PriceExplorer, type PriceBoardItem } from "@widgets/price-explorer";

type PriceBoardLoadResult =
  | { readonly status: "ready"; readonly items: readonly PriceBoardItem[] }
  | { readonly status: "failed"; readonly items: readonly [] };

async function loadBoard(initialSetNumber?: string): Promise<PriceBoardLoadResult> {
  let sets: readonly LegoSet[] = [];
  try {
    sets = await fetchFeaturedLegoSets();
  } catch {
    return { status: "failed", items: [] };
  }

  // 딥링크로 들어온 세트가 추천 목록에 없을 수 있다. 홈의 "지금 뜨는 세트"는 거래량 기준이라
  // 추천(featured) 여부와 무관하기 때문이다. 목록에 없으면 PriceExplorer가 조용히 다른 세트를
  // 대신 보여주는데, 사용자는 자기가 누른 세트의 시세를 본다고 착각하게 된다.
  if (initialSetNumber !== undefined && !sets.some((s) => s.setNumber === initialSetNumber)) {
    try {
      sets = [await fetchLegoSetByNumber(initialSetNumber), ...sets];
    } catch {
      // 없는 세트 번호로 들어온 경우. 추천 목록만 보여준다.
    }
  }

  return {
    status: "ready",
    items: await Promise.all(
      sets.map(async (set): Promise<PriceBoardItem> => {
        const snapshot: PriceSnapshot | null = await fetchPriceSnapshot(set.setNumber).catch(
          (): null => null,
        );
        // snapshot 관측은 최신순이다. 같은 원자적 응답을 뒤집어 차트에 써서 헤드라인과
        // 시계열이 서로 다른 요청 시점·표본을 가리키지 않게 한다.
        const points: readonly PricePoint[] | null =
          snapshot === null ? null : [...snapshot.observations].reverse();
        return {
          setNumber: set.setNumber,
          name: set.name,
          theme: set.theme,
          imageUrl: set.imageUrl,
          points,
          snapshot,
        };
      }),
    ),
  };
}

export interface PricesPageProps {
  readonly initialSetNumber?: string | undefined;
}

export async function PricesPage({ initialSetNumber }: PricesPageProps) {
  const board = await loadBoard(initialSetNumber);
  const retryHref =
    initialSetNumber === undefined
      ? "/prices"
      : `/prices?set=${encodeURIComponent(initialSetNumber)}`;

  return (
    <Container width="xl">
      <div className="flex flex-col gap-6 pt-8 pb-16">
        <div className="flex flex-col gap-1">
          <Heading level={1}>시세</Heading>
          <Text tone="secondary">
            체결가 기반 시세 추이와 상태별 감가 · 즉시판매/즉시구매 추정가
          </Text>
        </div>

        {board.status === "failed" ? (
          <EmptyState
            variant="inline"
            title="시세판을 불러오지 못했어요"
            description="카탈로그 연결이 잠시 지연되고 있어요. 다시 확인하거나 다른 공개 콘텐츠를 둘러보세요."
            action={
              <div className="flex flex-wrap justify-center gap-2">
                <LinkButton href={retryHref} size="sm" variant="secondary">
                  다시 확인
                </LinkButton>
                <LinkButton href="/community" size="sm" variant="ghost">
                  커뮤니티 보기
                </LinkButton>
              </div>
            }
          />
        ) : board.items.length === 0 ? (
          <EmptyState
            variant="inline"
            title="아직 공개된 시세 세트가 없어요"
            description="카탈로그가 준비되는 동안 브릭 이야기와 공개된 상품을 먼저 둘러볼 수 있어요."
            action={
              <div className="flex flex-wrap justify-center gap-2">
                <LinkButton href="/community" size="sm">
                  커뮤니티 보기
                </LinkButton>
                <LinkButton href="/search" size="sm" variant="secondary">
                  상품 탐색
                </LinkButton>
              </div>
            }
          />
        ) : (
          <PriceExplorer
            key={initialSetNumber ?? "default"}
            items={board.items}
            initialSetNumber={initialSetNumber}
          />
        )}
      </div>
    </Container>
  );
}
