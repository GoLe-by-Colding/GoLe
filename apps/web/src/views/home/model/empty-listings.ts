/** 매물이 없어도 판매 준비 여부와 동일한 안내·다음 행동을 제공한다. */
export function emptyListingsContent(sellerTradingOpen: boolean) {
  return sellerTradingOpen
    ? {
        title: "첫 매물을 기다리고 있어요.",
        description: "소중한 브릭을 다음 컬렉터에게 소개해 주세요.",
        href: "/sell",
        action: "판매 시작하기",
      }
    : {
        title: "신규 판매 등록을 준비하고 있어요",
        description:
          "판매자 본인확인 준비가 끝나면 열립니다. 지금은 커뮤니티에서 브릭 이야기를 나눠 보세요.",
        href: "/community",
        action: "브릭 이야기 둘러보기",
      };
}
