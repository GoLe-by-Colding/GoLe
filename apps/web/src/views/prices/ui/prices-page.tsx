import { fetchFeaturedLegoSets, type LegoSet } from "@entities/lego-set";
import {
  fetchPriceChart,
  fetchPriceValuation,
  type PricePoint,
  type PriceValuation,
} from "@entities/pricing";
import { Container, Heading, Text } from "@shared/ui";
import { PriceExplorer, type PriceBoardItem } from "@widgets/price-explorer";

async function loadBoard(): Promise<readonly PriceBoardItem[]> {
  let sets: readonly LegoSet[] = [];
  try {
    sets = await fetchFeaturedLegoSets();
  } catch {
    return [];
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
  const board = await loadBoard();

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
