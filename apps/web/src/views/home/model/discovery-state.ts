/** 빈 배열은 정상 응답이며 null만 조회 실패를 나타낸다. */
export async function loadHomeCollection<T>(
  load: () => Promise<readonly T[]>,
): Promise<readonly T[] | null> {
  try {
    return await load();
  } catch {
    return null;
  }
}

export function homeIntroduction(sellerTradingOpen: boolean) {
  return sellerTradingOpen
    ? {
        label: "브릭을 좋아하는 사람들의 마켓",
        description: "갖고 싶던 세트를 찾고, 소중히 모은 브릭을 새로운 주인에게 전해 주세요.",
      }
    : {
        label: "브릭을 좋아하는 사람들의 공간",
        description: "관심 있는 세트를 둘러보고, 컬렉션을 모으며 브릭 이야기를 나눠 보세요.",
      };
}
