import {
  searchListings,
  parseItemCondition,
  type ItemCondition,
  type Listing,
  type ListingCategory,
  type ListingSort,
  type SearchListingsParams,
} from "@entities/listing";
import { ListingFilterBar, type ListingFilterValues } from "@features/listing-filter";
import { Container, EmptyState, Heading, LinkButton, Text } from "@shared/ui";
import { ListingGrid } from "@widgets/listing-grid";

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

function parsePrice(value: string | undefined): number | undefined {
  if (value === undefined || value.trim() === "") {
    return undefined;
  }
  const n = Number(value);
  return Number.isFinite(n) && n >= 0 ? n : undefined;
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

function searchHref(params: SearchListingsParams): string {
  const query = new URLSearchParams();
  if (params.query) query.set("query", params.query);
  if (params.condition) query.set("condition", params.condition);
  if (params.category) query.set("category", params.category);
  if (params.minPrice !== undefined) query.set("minPrice", String(params.minPrice));
  if (params.maxPrice !== undefined) query.set("maxPrice", String(params.maxPrice));
  if (params.sort !== undefined && params.sort !== "newest") query.set("sort", params.sort);
  const suffix = query.toString();
  return suffix.length === 0 ? "/search" : `/search?${suffix}`;
}

export async function SearchPage(props: SearchPageProps) {
  const condition = parseCondition(props.condition);
  const category = parseCategory(props.category);
  const sort = parseSort(props.sort);
  const minPrice = parsePrice(props.minPrice);
  const maxPrice = parsePrice(props.maxPrice);

  const params: SearchListingsParams = {
    ...(props.query ? { query: props.query } : {}),
    ...(condition ? { condition } : {}),
    ...(category ? { category } : {}),
    ...(minPrice !== undefined ? { minPrice } : {}),
    ...(maxPrice !== undefined ? { maxPrice } : {}),
    sort,
  };

  const result = await loadListings(params);
  const { listings } = result;
  const hasFilters =
    (props.query?.trim().length ?? 0) > 0 ||
    condition !== undefined ||
    category !== undefined ||
    minPrice !== undefined ||
    maxPrice !== undefined ||
    sort !== "newest";

  const initial: ListingFilterValues = {
    query: props.query ?? "",
    condition: condition ?? "",
    category: category ?? "",
    minPrice: props.minPrice ?? "",
    maxPrice: props.maxPrice ?? "",
    sort,
  };

  return (
    <Container width="xl">
      <div className="flex flex-col gap-6 pt-8 pb-16">
        <div className="flex flex-col gap-1">
          <Heading level={1}>상품 탐색</Heading>
          <Text tone="secondary">
            지금 거래 가능한 브릭 상품{" "}
            <span className="font-bold text-brand-600">{listings.length}</span>개
          </Text>
        </div>
        <div className="sticky top-16 z-10 -mx-4 bg-neutral-50 px-4 py-2 sm:-mx-2 sm:px-2">
          <ListingFilterBar initial={initial} />
        </div>
        {result.status === "failed" ? (
          <EmptyState
            variant="inline"
            title="상품 목록을 불러오지 못했어요"
            description="목록 연결이 잠시 지연되고 있어요. 다시 확인하거나 공개된 브릭 이야기를 먼저 둘러보세요."
            action={
              <div className="flex flex-wrap justify-center gap-2">
                <LinkButton href={searchHref(params)} size="sm" variant="secondary">
                  다시 확인
                </LinkButton>
                <LinkButton href="/community" size="sm" variant="ghost">
                  커뮤니티 보기
                </LinkButton>
              </div>
            }
          />
        ) : listings.length === 0 ? (
          <EmptyState
            variant="inline"
            title={hasFilters ? "조건에 맞는 상품이 없어요" : "아직 공개된 상품이 없어요"}
            description={
              hasFilters
                ? "필터를 초기화하거나 커뮤니티에서 다른 브릭 이야기를 찾아보세요."
                : "판매자 확인 절차가 준비되는 동안 시세와 커뮤니티 콘텐츠를 먼저 이용할 수 있어요."
            }
            action={
              <div className="flex flex-wrap justify-center gap-2">
                {hasFilters ? (
                  <LinkButton href="/search" size="sm" variant="secondary">
                    필터 초기화
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
          <ListingGrid key={JSON.stringify(params)} listings={listings} />
        )}
      </div>
    </Container>
  );
}
