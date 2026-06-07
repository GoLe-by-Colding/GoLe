import { fetchFeaturedLegoSets, type LegoSet } from "@entities/lego-set";
import {
  fetchPriceChart,
  fetchPriceStatistics,
  type PricePoint,
  type PriceStatistics,
} from "@entities/pricing";
import { Card, Container, Heading, Text } from "@shared/ui";
import { formatKrw } from "@shared/lib";
import { PriceChart } from "@widgets/price-chart";

interface SetPricing {
  readonly set: LegoSet;
  readonly stats: PriceStatistics | null;
  readonly chart: readonly PricePoint[];
}

async function loadBoard(): Promise<readonly SetPricing[]> {
  let sets: readonly LegoSet[] = [];
  try {
    sets = await fetchFeaturedLegoSets();
  } catch {
    return [];
  }

  return Promise.all(
    sets.map(async (set): Promise<SetPricing> => {
      try {
        const [stats, chart] = await Promise.all([
          fetchPriceStatistics(set.setNumber),
          fetchPriceChart(set.setNumber),
        ]);
        return { set, stats, chart };
      } catch {
        return { set, stats: null, chart: [] };
      }
    }),
  );
}

export async function PricesPage() {
  const board = await loadBoard();

  return (
    <Container width="xl">
      <div className="flex flex-col gap-6 pt-8 pb-16">
        <div className="flex flex-col gap-1">
          <Heading level={1}>시세</Heading>
          <Text tone="secondary">최근 체결가 기준 인기 세트 시세</Text>
        </div>

        {board.length === 0 ? (
          <Text tone="muted">시세 데이터를 불러오지 못했습니다.</Text>
        ) : (
          <div className="grid gap-5 [grid-template-columns:repeat(auto-fill,minmax(280px,1fr))]">
            {board.map(({ set, stats, chart }) => (
              <Card key={set.setNumber} padded className="flex flex-col gap-3">
                <div className="flex items-start justify-between gap-2">
                  <div className="flex flex-col">
                    <span className="font-semibold text-neutral-900">{set.name}</span>
                    <span className="font-mono text-xs text-neutral-500">
                      #{set.setNumber} · {set.theme}
                    </span>
                  </div>
                </div>

                {stats?.hasData && stats.latestPrice !== null ? (
                  <>
                    <span className="text-2xl font-bold tracking-tight">
                      {formatKrw(stats.latestPrice)}
                    </span>
                    <PriceChart points={chart} />
                    <div className="flex gap-4 text-xs text-neutral-500">
                      <span>최고 {formatKrw(stats.highestPrice ?? 0)}</span>
                      <span>최저 {formatKrw(stats.lowestPrice ?? 0)}</span>
                      <span>체결 {stats.transactionCount}건</span>
                    </div>
                  </>
                ) : (
                  <Text tone="muted" size="sm">
                    체결 데이터 없음
                  </Text>
                )}
              </Card>
            ))}
          </div>
        )}
      </div>
    </Container>
  );
}
