import type { ReactElement } from "react";

/** viewBox 네 값 — minX, minY, width, height(마크 단위). */
export type MascotViewBox = readonly [number, number, number, number];

/**
 * 기본 제공 마스코트 한 벌의 그림. (mascot-assets R1)
 *
 * <p>`Art`는 `<svg>` 안쪽만 그린다. 상자 크기·접근성·폭 계산은 `Logo`가 한 곳에서 맡는다.
 */
export interface MascotArt {
  /** 분수 없이 그릴 때의 viewBox. 화면 높이는 이 비율로 정한다. */
  readonly viewBox: MascotViewBox;
  /** 분수가 상자 위로 솟을 자리를 넓힌 viewBox. 없으면 분수도 같은 상자를 쓴다. */
  readonly spoutViewBox?: MascotViewBox;
  /** 히어로의 브릭 분수 연출을 그릴 수 있는가. 오래된 그림은 물줄기가 그림에 박혀 있어 false다. */
  readonly hasSpout: boolean;
  /**
   * 어두운 배경에서 `--gole-mark-body` CSS 변수로 몸 색만 바꿀 수 있는가. 아니면 색이 SVG 에 박혀 있어
   * `Logo`가 흰 실루엣 필터로 그린다.
   */
  readonly themable: boolean;
  readonly Art: (props: { readonly spout: boolean }) => ReactElement;
}
