import {
  searchListings,
  type ItemCondition,
  type Listing,
  type ListingCategory,
  type ListingSort,
  type SearchListingsParams,
} from "@entities/listing";
import { ListingFilterBar, type ListingFilterValues } from "@features/listing-filter";
import { Container, Heading, Text } from "@shared/ui";
import { ListingGrid } from "@widgets/listing-grid";

export interface SearchPageProps {
  readonly query?: string | undefined;
  readonly condition?: string | undefined;
  readonly category?: string | undefined;
  readonly minPrice?: string | undefined;
  readonly maxPrice?: string | undefined;
  readonly sort?: string | undefined;
}

const CONDITIONS: readonly ItemCondition[] = ["new_sealed", "used_complete", "used_incomplete"];
const CATEGORIES: readonly ListingCategory[] = ["set", "parts", "minifig", "moc"];
const SORTS: readonly ListingSort[] = ["newest", "price_asc", "price_desc"];

function parseCondition(value: string | undefined): ItemCondition | undefined {
  return CONDITIONS.find((c) => c === value);
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

async function loadListings(params: SearchListingsParams): Promise<readonly Listing[]> {
  try {
    return await searchListings(params);
  } catch {
    return [];
  }
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

  const listings = await loadListings(params);

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
            지금 거래 가능한 레고{" "}
            <span className="font-bold text-brand-600">{listings.length}</span>개
          </Text>
        </div>
        <div className="sticky top-16 z-30 -mx-4 bg-neutral-50 px-4 py-2 sm:-mx-2 sm:px-2">
          <ListingFilterBar initial={initial} />
        </div>
        <ListingGrid
          key={JSON.stringify(params)}
          listings={listings}
          emptyMessage="조건에 맞는 상품이 없습니다."
        />
      </div>
    </Container>
  );
}
