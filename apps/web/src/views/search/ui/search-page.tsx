import { fetchActiveListings, type Listing } from "@entities/listing";
import { Container, Heading, Text } from "@shared/ui";
import { ListingGrid } from "@widgets/listing-grid";
import styles from "./search-page.module.css";

async function loadListings(): Promise<readonly Listing[]> {
  try {
    return await fetchActiveListings();
  } catch {
    return [];
  }
}

export async function SearchPage() {
  const listings = await loadListings();

  return (
    <Container width="xl">
      <div className={styles.page}>
        <div className={styles.header}>
          <Heading level={1}>상품 탐색</Heading>
          <Text tone="secondary">
            지금 거래 가능한 레고 {listings.length}개
          </Text>
        </div>
        <ListingGrid
          listings={listings}
          emptyMessage="아직 등록된 상품이 없습니다. 백엔드(API)가 실행 중인지 확인해 주세요."
        />
      </div>
    </Container>
  );
}
