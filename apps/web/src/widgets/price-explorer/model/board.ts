import type { PricePoint, PriceSnapshot } from "@entities/pricing";

export interface PriceBoardItem {
  readonly setNumber: string;
  readonly name: string;
  readonly theme: string;
  readonly imageUrl: string | null;
  /** 미개봉 체결 시계열(오름차순). null은 조회 실패, []는 실제 빈 데이터다. */
  readonly points: readonly PricePoint[] | null;
  /** null은 조회 실패다. EMPTY 상태와 섞지 않는다. */
  readonly snapshot: PriceSnapshot | null;
}

/** 서버 렌더에서 실패한 세트를 화면에서 다시 불러온 결과. */
export type SnapshotRecovery =
  | { readonly status: "loading" }
  | { readonly status: "failed" }
  | { readonly status: "ready"; readonly snapshot: PriceSnapshot };

/**
 * 스냅샷 관측은 최신순이다. 같은 원자적 응답을 뒤집어 차트에 써서 헤드라인과
 * 시계열이 서로 다른 요청 시점·표본을 가리키지 않게 한다.
 */
export function priceBoardPoints(snapshot: PriceSnapshot): readonly PricePoint[] {
  return [...snapshot.observations].reverse();
}

/** 조회에 실패했던 항목만 다시 불러온 스냅샷으로 채운다. 이미 받은 스냅샷은 덮지 않는다. */
export function withRecoveredSnapshots(
  items: readonly PriceBoardItem[],
  recoveries: ReadonlyMap<string, SnapshotRecovery>,
): readonly PriceBoardItem[] {
  if (recoveries.size === 0) return items;
  return items.map((item) => {
    const recovery = recoveries.get(item.setNumber);
    if (item.snapshot !== null || recovery?.status !== "ready") return item;
    return { ...item, snapshot: recovery.snapshot, points: priceBoardPoints(recovery.snapshot) };
  });
}
