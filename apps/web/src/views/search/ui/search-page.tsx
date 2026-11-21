import { RecentSets } from "@features/recent-sets";
import {
  searchListings,
  parseItemCondition,
  type ItemCondition,
  type Listing,
  type ListingCategory,
  type ListingSort,
  type SearchListingsParams,
} from "@entities/listing";
import {
  fetchLegoSetForPage,
  searchLegoSetsForPage,
  setNumberInSearchText,
  type LegoSet,
} from "@entities/lego-set";
import { fetchPriceSnapshotForPage } from "@entities/pricing";
import {
  ListingFilterBar,
  filterPriceError,
  parseFilterPrice,
  type ListingFilterValues,
} from "@features/listing-filter";
import {
  PageLayout,
  PageHeader,
  EmptyState,
  LinkButton,
  RefreshButton,
  AlertCircleIcon,
  SearchIcon,
} from "@shared/ui";
import { buildPriceNotes, ListingGrid, priceNoteSetNumbers } from "@widgets/listing-grid";
import { SearchSetShortcut } from "./search-set-shortcut";

export interface SearchPageProps {
  readonly query?: string | undefined;
  readonly condition?: string | undefined;
  readonly category?: string | undefined;
  readonly minPrice?: string | undefined;
  readonly maxPrice?: string | undefined;
  readonly sort?: string | undefined;
}

const CATEGORIES: readonly ListingCategory[] = ["set", "parts", "minifig", "moc"];
const SORTS: readonly ListingSort[] = ["newest", "price_asc", "price_desc"];

function parseCondition(value: string | undefined): ItemCondition | undefined {
  return parseItemCondition(value);
}

function parseCategory(value: string | undefined): ListingCategory | undefined {
  return CATEGORIES.find((c) => c === value);
}

function parseSort(value: string | undefined): ListingSort {
  return SORTS.find((s) => s === value) ?? "newest";
}

type ListingLoadResult =
  | { readonly status: "ready"; readonly listings: readonly Listing[] }
  | { readonly status: "failed"; readonly listings: readonly [] };

async function loadListings(params: SearchListingsParams): Promise<ListingLoadResult> {
  try {
    return { status: "ready", listings: await searchListings(params) };
  } catch {
    return { status: "failed", listings: [] };
  }
}

/**
 * 목록 카드의 "추정 시세보다 N% 낮음/높음". 세트별 시세는 캐시된 조회(5분)로 함께 읽고, 실패한 세트는 문구 없이 둔다.
 * 시세가 늦거나 없어도 목록 자체는 그대로 보인다.
 */
async function loadPriceNotes(listings: readonly Listing[]): Promise<Record<string, string>> {
  const sets = priceNoteSetNumbers(listings);
  if (sets.length === 0) return {};
  const entries = await Promise.all(
    sets.map(
      async (setNumber) =>
        [setNumber, await fetchPriceSnapshotForPage(setNumber).catch(() => null)] as const,
    ),
  );
  return buildPriceNotes(listings, Object.fromEntries(entries));
}

/** 바로가기로 보일 세트 수. 결과 목록을 밀어내지 않게 적게 둔다. */
const MAX_QUERY_SETS = 3;

/**
 * 검색어에 맞는 카탈로그 세트(바로가기용). 세트 번호면 그 세트 하나, 아니면 이름에 검색어가 든 세트를 최대 3개.
 * 테마만 맞는 세트는 너무 넓어(예: "Icons") 넣지 않는다. 조회가 실패하면 빈 목록 — 매물 결과는 그대로 보인다.
 */
async function loadQuerySets(query: string | undefined): Promise<readonly LegoSet[]> {
  const text = query?.trim() ?? "";
  if (text.length === 0) return [];
  const setNumber = setNumberInSearchText(text);
  try {
    if (setNumber !== null) return [await fetchLegoSetForPage(setNumber)];
    if (text.length < 2) return [];
    const needle = text.toLocaleLowerCase("ko-KR");
    const sets = await searchLegoSetsForPage(text);
    return sets
      .filter((set) => set.name.toLocaleLowerCase("ko-KR").includes(needle))
      .slice(0, MAX_QUERY_SETS);
  } catch {
    return [];
  }
}

export async function SearchPage(props: SearchPageProps) {
  const condition = parseCondition(props.condition);
  const category = parseCategory(props.category);
  const sort = parseSort(props.sort);
  const minPrice = parseFilterPrice(props.minPrice);
  const maxPrice = parseFilterPrice(props.maxPrice);
  const initial: ListingFilterValues = {
    query: props.query?.trim() ?? "",
    condition: condition ?? "",
    category: category ?? "",
    minPrice: props.minPrice ?? "",
    maxPrice: props.maxPrice ?? "",
    sort,
  };
  const priceError = filterPriceError(initial);

  const params: SearchListingsParams = {
    ...(initial.query ? { query: initial.query } : {}),
    ...(condition ? { condition } : {}),
    ...(category ? { category } : {}),
    ...(minPrice !== undefined ? { minPrice } : {}),
    ...(maxPrice !== undefined ? { maxPrice } : {}),
    sort,
  };

  const [result, querySets] =
    priceError === null
      ? await Promise.all([loadListings(params), loadQuerySets(initial.query)])
      : [{ status: "invalid" as const, listings: [] }, []];
  const { listings } = result;
  const priceNotes = await loadPriceNotes(listings);
  const hasFilters =
    (props.query?.trim().length ?? 0) > 0 ||
    condition !== undefined ||
    category !== undefined ||
    minPrice !== undefined ||
    maxPrice !== undefined ||
    sort !== "newest";

  return (
    <PageLayout width="xl">
      <PageHeader title="상품 탐색" description="내 선반에 더하고 싶은 브릭을 찾아보세요." />
      <div>
        {/* URL에서 필터를 빼거나 뒤로 가면 입력 초안도 같은 조건으로 다시 만든다. */}
        <ListingFilterBar key={JSON.stringify(initial)} initial={initial} />
      </div>
      {result.status === "ready" ? (
        <div className="flex items-center gap-4 text-sm text-neutral-600">
          <p className="shrink-0">
            검색 결과{" "}
            <span className="font-bold tabular-nums text-neutral-900">{listings.length}</span>개
          </p>
          <span aria-hidden="true" className="h-px flex-1 bg-neutral-200" />
        </div>
      ) : null}
      {result.status !== "ready" ? null : (
        <SearchSetShortcut sets={querySets} listingCount={listings.length} />
      )}
      {result.status === "invalid" ? (
        <EmptyState
          variant="inline"
          icon={<AlertCircleIcon className="size-7 text-danger" />}
          title="가격 범위를 확인해 주세요"
          description="위의 가격을 수정하면 원하는 조건으로 다시 찾을 수 있어요."
        />
      ) : result.status === "failed" ? (
        <EmptyState
          variant="inline"
          icon={<AlertCircleIcon className="size-7 text-neutral-600" />}
          title="상품 목록을 불러오지 못했어요"
          description="검색 조건은 그대로예요. 잠시 후 다시 불러와 주세요."
          action={
            <div className="flex flex-wrap justify-center gap-2">
              <RefreshButton variant="secondary" />
            </div>
          }
        />
      ) : listings.length === 0 ? (
        <EmptyState
          variant="inline"
          icon={<SearchIcon className="size-7 text-brand-600" />}
          title={hasFilters ? "조건에 맞는 상품이 없어요" : "아직 공개된 상품이 없어요"}
          description={
            hasFilters
              ? "검색어를 짧게 바꾸거나 가격과 상태 조건을 조금 넓혀보세요."
              : "새로운 브릭을 기다리는 동안 세트별 시세를 둘러보세요."
          }
          action={
            <div className="flex flex-wrap justify-center gap-2">
              {hasFilters ? (
                <LinkButton href="/search" size="sm" variant="secondary">
                  전체 상품 보기
                </LinkButton>
              ) : (
                <LinkButton href="/prices" size="sm" variant="secondary">
                  시세 보기
                </LinkButton>
              )}
              <LinkButton href="/community" size="sm" variant="ghost">
                커뮤니티 보기
              </LinkButton>
            </div>
          }
        />
      ) : (
        <ListingGrid key={JSON.stringify(params)} listings={listings} priceNotes={priceNotes} />
      )}
      <RecentSets />
    </PageLayout>
  );
}
