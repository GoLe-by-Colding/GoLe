import { createElement } from "react";
import mark from "./assembled-mark.json";

/** 승인된 조립 고래의 정본. 웹 프리셋·OG·아이콘 생성기가 같은 좌표와 색을 읽는다. */
export const ASSEMBLED_MARK_VIEW_BOX = [0, 0, mark.width, mark.height] as const;

/** OG는 정본 색 그대로, 웹 프리셋은 기존 CSS 변수와 브랜드 토큰으로 그린다. */
export function AssembledMark({ themed = false }: { readonly themed?: boolean }) {
  return (
    <g>
      {mark.elements.map(({ id, tag, props }) => {
        const { className, ...attributes } = props;
        return createElement(tag, {
          ...attributes,
          key: id,
          ...(themed ? { className } : {}),
        });
      })}
    </g>
  );
}
