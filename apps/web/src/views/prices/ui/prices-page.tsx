import { fetchFeaturedLegoSets, fetchLegoSetByNumber, type LegoSet } from "@entities/lego-set";
import {
  fetchPriceChart,
  fetchPriceValuation,
  type PricePoint,
  type PriceValuation,
} from "@entities/pricing";
import { Container, Heading, Text } from "@shared/ui";
import { PriceExplorer, type PriceBoardItem } from "@widgets/price-explorer";

async function loadBoard(initialSetNumber?: string): Promise<readonly PriceBoardItem[]> {
  let sets: readonly LegoSet[] = [];
  try {
    sets = await fetchFeaturedLegoSets();
  } catch {
    return [];
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

  return Promise.all(
    sets.map(async (set): Promise<PriceBoardItem> => {
      let points: readonly PricePoint[] = [];
      let valuation: PriceValuation | null = null;
      try {
        [points, valuation] = await Promise.all([
          fetchPriceChart(set.setNumber),
          fetchPriceValuation(set.setNumber),
        ]);
      } catch {
        // 개별 세트 실패는 빈 데이터로 처리한다.
      }
      return {
        setNumber: set.setNumber,
        name: set.name,
        theme: set.theme,
        imageUrl: set.imageUrl,
        points,
        valuation,
      };
    }),
  );
}

export interface PricesPageProps {
  readonly initialSetNumber?: string | undefined;
}

export async function PricesPage({ initialSetNumber }: PricesPageProps) {
  const board = await loadBoard(initialSetNumber);

  return (
    <Container width="xl">
      <div className="flex flex-col gap-6 pt-8 pb-16">
        <div className="flex flex-col gap-1">
          <Heading level={1}>시세</Heading>
          <Text tone="secondary">
            체결가 기반 시세 추이와 상태별 감가 · 즉시판매/즉시구매 추정가
          </Text>
        </div>

        {board.length === 0 ? (
          <Text tone="muted">시세 데이터를 불러오지 못했습니다.</Text>
        ) : (
          <PriceExplorer items={board} initialSetNumber={initialSetNumber} />
        )}
      </div>
    </Container>
  );
}
