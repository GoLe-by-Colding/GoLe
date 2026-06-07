import {
  LegoSetCard,
  fetchFeaturedLegoSets,
  type LegoSet,
} from "@entities/lego-set";
import { Container, Heading, LinkButton, Text } from "@shared/ui";
import styles from "./home-page.module.css";

async function loadFeatured(): Promise<readonly LegoSet[]> {
  try {
    return await fetchFeaturedLegoSets();
  } catch {
    // 백엔드 미기동 등으로 조회 실패 시 빈 상태로 렌더한다.
    return [];
  }
}

export async function HomePage() {
  const featured = await loadFeatured();

  return (
    <Container width="xl">
      <div className={styles.page}>
        <section className={styles.hero}>
          <span className={styles.heroEyebrow}>🧱 레고 마켓플레이스</span>
          <h1 className={styles.heroTitle}>GoLe — 레고 중고거래 플랫폼</h1>
          <p className={styles.heroSubtitle}>
            안전하게 사고팔고, 시세를 확인하고, 컬렉션을 자랑하세요. 검수 기반
            안전거래부터 동네 직거래까지 한곳에서.
          </p>
          <div className={styles.heroActions}>
            <LinkButton href="/search" variant="secondary" size="lg">
              상품 둘러보기
            </LinkButton>
            <LinkButton href="/prices" variant="ghost" size="lg" className={styles.heroGhost}>
              시세 확인하기
            </LinkButton>
          </div>
        </section>

        <section className={styles.section}>
          <div className={styles.sectionHeader}>
            <Heading level={2}>오늘의 추천 세트</Heading>
            <Text tone="secondary" size="sm">
              인기 테마에서 엄선한 세트
            </Text>
          </div>
          {featured.length > 0 ? (
            <div className={styles.grid}>
              {featured.map((set) => (
                <LegoSetCard key={set.setNumber} set={set} />
              ))}
            </div>
          ) : (
            <CardEmpty />
          )}
        </section>
      </div>
    </Container>
  );
}

function CardEmpty() {
  return (
    <Text tone="muted">
      표시할 세트가 없습니다. 백엔드(API)가 실행 중인지 확인해 주세요.
    </Text>
  );
}
