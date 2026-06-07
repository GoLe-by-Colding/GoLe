import { LegoSetCard, type LegoSet } from "@entities/lego-set";

// 데모용 정적 데이터. 실제로는 entities/lego-set api를 통해 서버에서 조회한다.
const FEATURED_SET: LegoSet = {
  setNumber: "10307",
  name: "Eiffel Tower",
  theme: "Icons",
  pieceCount: 10001,
  releaseYear: 2022,
  retirementStatus: "active",
  imageUrl: null,
};

export function HomePage() {
  return (
    <main style={{ padding: "2rem", maxWidth: 720, margin: "0 auto" }}>
      <h1>GoLe — 레고 중고거래 플랫폼</h1>
      <p>안전하게 사고팔고, 시세를 확인하고, 컬렉션을 자랑하세요.</p>
      <section>
        <h2>오늘의 추천 세트</h2>
        <LegoSetCard set={FEATURED_SET} />
      </section>
    </main>
  );
}
