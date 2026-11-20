import { fetchFeaturedLegoSets, fetchLegoSetByNumber, type LegoSet } from "@entities/lego-set";
import { fetchPriceSnapshot, type PricePoint, type PriceSnapshot } from "@entities/pricing";
import { isApiNotFoundError } from "@shared/api";
import { PageLayout, PageHeader, EmptyState, LinkButton } from "@shared/ui";
import { PriceExplorer, type PriceBoardItem } from "@widgets/price-explorer";

/** 딥링크로 요청한 세트를 보드에 올리지 못한 이유. 올렸거나 요청이 없으면 null 이다. */
type RequestedSetIssue = "not-found" | "unavailable";

type PriceBoardLoadResult =
  | {
      readonly status: "ready";
      readonly items: readonly PriceBoardItem[];
      readonly requestedSetIssue: RequestedSetIssue | null;
    }
  | { readonly status: "failed"; readonly items: readonly [] };

async function loadBoard(initialSetNumber?: string): Promise<PriceBoardLoadResult> {
  let sets: readonly LegoSet[] = [];
  try {
    sets = await fetchFeaturedLegoSets();
  } catch {
    return { status: "failed", items: [] };
  }

  // 딥링크로 들어온 세트가 추천 목록에 없을 수 있다. 홈의 "지금 뜨는 세트"는 거래량 기준이라
  // 추천(featured) 여부와 무관하기 때문이다. 못 올리면 PriceExplorer가 다른 세트를 대신 보여주므로,
  // 사용자가 자기가 누른 세트의 시세로 착각하지 않게 그 이유를 화면에 알린다.
  let requestedSetIssue: RequestedSetIssue | null = null;
  if (initialSetNumber !== undefined && !sets.some((s) => s.setNumber === initialSetNumber)) {
    try {
      sets = [await fetchLegoSetByNumber(initialSetNumber), ...sets];
    } catch (cause) {
      requestedSetIssue = isApiNotFoundError(cause) ? "not-found" : "unavailable";
    }
  }

  return {
    status: "ready",
    requestedSetIssue,
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
    <PageLayout width="xl">
      <PageHeader
        title="시세"
        description="미개봉 체결가 추이와 상태별 추정 시세 · 빠른 판매/구매 추정"
      />

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
        <>
          {board.requestedSetIssue !== null && initialSetNumber !== undefined && (
            <RequestedSetNotice
              setNumber={initialSetNumber}
              issue={board.requestedSetIssue}
              retryHref={retryHref}
            />
          )}
          <PriceExplorer
            key={initialSetNumber ?? "default"}
            items={board.items}
            initialSetNumber={initialSetNumber}
          />
        </>
      )}
    </PageLayout>
  );
}

function RequestedSetNotice({
  setNumber,
  issue,
  retryHref,
}: {
  readonly setNumber: string;
  readonly issue: RequestedSetIssue;
  readonly retryHref: string;
}) {
  if (issue === "not-found") {
    return (
      <p
        role="status"
        className="mb-4 rounded-lg bg-neutral-50 px-4 py-3 text-sm break-keep text-neutral-700"
      >
        {setNumber} 세트를 카탈로그에서 찾지 못했어요. 아래는 추천 세트의 시세예요.
      </p>
    );
  }
  return (
    <div
      role="alert"
      className="mb-4 flex flex-wrap items-center justify-between gap-2 rounded-lg bg-danger-soft px-4 py-3 text-sm break-keep text-danger"
    >
      <span>{setNumber} 세트 정보를 불러오지 못했어요. 아래는 추천 세트의 시세예요.</span>
      <LinkButton href={retryHref} size="sm" variant="secondary">
        다시 확인
      </LinkButton>
    </div>
  );
}
