import { BrickSpout, HingedTail, RoundedStud } from "./assembled-parts";
import type { MascotArt } from "./types";

/** 낮은 플레이트를 길게 쌓은 몸과 열린 꼬리. 조립형보다 수평의 비례를 강조한다. */
function SideBrickPlateArt({ spout }: { readonly spout: boolean }) {
  return (
    <>
      {spout ? <BrickSpout x={116} /> : null}
      <HingedTail x={233} y={76} scale={0.95} tilt={-8} />
      <RoundedStud x={45} y={5} width={34} />
      <RoundedStud x={99} y={5} width={34} gold />
      <RoundedStud x={153} y={5} width={34} />
      <path
        d="M102 104L137 115C145 118 141 130 133 128L98 117C90 114 94 102 102 104Z"
        fill="#1D4ED8"
        className="fill-[var(--gole-mark-body,var(--color-brand-600))]"
      />
      <path
        d="M4 56V49C4 34 17 26 32 26H201C211 26 218 33 218 43V56Z"
        fill="#6082F7"
        className="fill-[var(--gole-mark-top,var(--gole-mark-body,var(--color-brand-400)))]"
      />
      <path
        d="M4 56H218V87H4Z"
        fill="#3B5CF2"
        className="fill-[var(--gole-mark-body,var(--color-brand-500))]"
      />
      <path
        d="M4 87H218V93C218 104 209 112 198 112H25C12 112 4 104 4 93Z"
        fill="#1D4ED8"
        className="fill-[var(--gole-mark-body,var(--color-brand-600))]"
      />
      <path
        d="M5 56H217M5 87H217M123 57V86M77 88V111"
        stroke="#1A3FC0"
        strokeWidth="1.5"
        opacity="0.22"
        className="stroke-[var(--gole-mark-face,var(--color-brand-700))]"
      />
      <circle
        cx="102"
        cy="111"
        r="4"
        stroke="#1A3FC0"
        strokeWidth="2.5"
        className="stroke-[var(--gole-mark-face,var(--color-brand-700))]"
      />
      <circle
        cx="33"
        cy="70"
        r="6.5"
        fill="#131E4F"
        className="fill-[var(--gole-mark-face,var(--color-brand-950))]"
      />
      <circle
        cx="35"
        cy="68"
        r="1.8"
        fill="#FFFFFF"
        className="fill-[var(--gole-mark-glint,var(--color-white))]"
      />
      <path
        d="M15 82Q21 88 29 83"
        stroke="#131E4F"
        strokeWidth="3"
        strokeLinecap="round"
        className="stroke-[var(--gole-mark-face,var(--color-brand-950))]"
      />
    </>
  );
}

export const sideBrickPlate: MascotArt = {
  viewBox: [0, 0, 284, 134],
  spoutViewBox: [0, -50, 284, 184],
  hasSpout: true,
  themable: true,
  Art: SideBrickPlateArt,
};
