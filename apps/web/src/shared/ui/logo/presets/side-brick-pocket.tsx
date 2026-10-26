import { BrickSpout, HingedTail, RoundedStud } from "./assembled-parts";
import type { MascotArt } from "./types";

/** 손에 쥘 수 있는 작은 브릭 장난감처럼 짧고 둥근 몸통과 작은 관절 꼬리를 조합한다. */
function SideBrickPocketArt({ spout }: { readonly spout: boolean }) {
  return (
    <>
      {spout ? <BrickSpout x={84} /> : null}
      <HingedTail x={160} y={82} scale={0.8} tilt={-8} />
      <RoundedStud x={30} y={2} width={28} />
      <RoundedStud x={70} width={28} gold />
      <RoundedStud x={110} y={2} width={28} />
      <path
        d="M77 121L97 133C106 138 99 149 91 144L71 132C63 127 69 116 77 121Z"
        fill="#1D4ED8"
        className="fill-[var(--gole-mark-body,var(--color-brand-600))]"
      />
      <path
        d="M37 20H123C137 20 148 31 148 45V91C148 117 131 135 106 135H51C23 135 6 117 6 91V53C6 34 18 20 37 20Z"
        fill="#3B5CF2"
        className="fill-[var(--gole-mark-body,var(--color-brand-500))]"
      />
      <path
        d="M37 20H123C137 20 148 31 148 45V57H6V53C6 34 18 20 37 20Z"
        fill="#6082F7"
        className="fill-[var(--gole-mark-top,var(--gole-mark-body,var(--color-brand-400)))]"
      />
      <path
        d="M8 106H146C140 125 126 135 106 135H51C29 135 14 125 8 106Z"
        fill="#1D4ED8"
        className="fill-[var(--gole-mark-body,var(--color-brand-600))]"
      />
      <path
        d="M7 57H147M9 106H145"
        stroke="#1A3FC0"
        strokeWidth="1.5"
        opacity="0.12"
        className="stroke-[var(--gole-mark-face,var(--color-brand-700))]"
      />
      <circle
        cx="37"
        cy="77"
        r="8"
        fill="#131E4F"
        className="fill-[var(--gole-mark-face,var(--color-brand-950))]"
      />
      <circle
        cx="39.5"
        cy="74.5"
        r="2.4"
        fill="#FFFFFF"
        className="fill-[var(--gole-mark-glint,var(--color-white))]"
      />
      <path
        d="M17 91Q24 99 32 92"
        stroke="#131E4F"
        strokeWidth="3.5"
        strokeLinecap="round"
        className="stroke-[var(--gole-mark-face,var(--color-brand-950))]"
      />
    </>
  );
}

export const sideBrickPocket: MascotArt = {
  viewBox: [0, 0, 204, 150],
  spoutViewBox: [0, -50, 204, 200],
  hasSpout: true,
  themable: true,
  Art: SideBrickPocketArt,
};
