import { ASSEMBLED_MARK_VIEW_BOX, AssembledMark } from "../assembled-mark";
import { BrickSpout } from "./assembled-parts";
import type { MascotArt } from "./types";

/** 원본의 세 층 + 말랑 스터드 + 옆모습의 관절 꼬리. 2026-10-26 사용자 선택을 조합했다. */
function SideBrickAssembledArt({ spout }: { readonly spout: boolean }) {
  return (
    <>
      {spout ? <BrickSpout x={108} /> : null}
      <AssembledMark themed />
    </>
  );
}

export const sideBrickAssembled: MascotArt = {
  viewBox: ASSEMBLED_MARK_VIEW_BOX,
  spoutViewBox: [0, -50, 256, 200],
  hasSpout: true,
  themable: true,
  Art: SideBrickAssembledArt,
};
