import { BrickSpout, HingedTail, RoundedStud } from "./assembled-parts";
import type { MascotArt } from "./types";

/** 원본의 세 층 + 말랑 스터드 + 옆모습의 관절 꼬리. 2026-10-26 사용자 선택을 조합했다. */
function SideBrickAssembledArt({ spout }: { readonly spout: boolean }) {
  return (
    <>
      {spout ? <BrickSpout x={108} /> : null}
      <HingedTail x={209} y={80} />
      <RoundedStud x={44} />
      <RoundedStud x={92} gold />
      <RoundedStud x={140} />
      <path
        d="M96 121L128 132C136 135 131 148 123 145L91 134C83 131 88 118 96 121Z"
        fill="#1D4ED8"
        className="fill-[var(--gole-mark-body,var(--color-brand-600))]"
      />
      <path
        d="M8 66V51C8 31 21 18 41 18H176C186 18 194 26 194 36V66Z"
        fill="#6082F7"
        className="fill-[var(--gole-mark-top,var(--gole-mark-body,var(--color-brand-400)))]"
      />
      <path
        d="M8 66H194V103H8Z"
        fill="#3B5CF2"
        className="fill-[var(--gole-mark-body,var(--color-brand-500))]"
      />
      <path
        d="M8 103H194V108C194 122 184 130 171 130H32C18 130 8 120 8 107Z"
        fill="#1D4ED8"
        className="fill-[var(--gole-mark-body,var(--color-brand-600))]"
      />
      <path
        d="M9 66H193M9 103H193"
        stroke="#1A3FC0"
        strokeWidth="1.5"
        opacity="0.12"
        className="stroke-[var(--gole-mark-face,var(--color-brand-700))]"
      />
      <circle
        cx="96"
        cy="127"
        r="4.5"
        stroke="#1A3FC0"
        strokeWidth="2.5"
        className="stroke-[var(--gole-mark-face,var(--color-brand-700))]"
      />
      <circle
        cx="42"
        cy="79"
        r="7"
        fill="#131E4F"
        className="fill-[var(--gole-mark-face,var(--color-brand-950))]"
      />
      <circle
        cx="44"
        cy="76.5"
        r="2"
        fill="#FFFFFF"
        className="fill-[var(--gole-mark-glint,var(--color-white))]"
      />
      <path
        d="M21 92Q28 99 37 93"
        stroke="#131E4F"
        strokeWidth="3.5"
        strokeLinecap="round"
        className="stroke-[var(--gole-mark-face,var(--color-brand-950))]"
      />
    </>
  );
}

export const sideBrickAssembled: MascotArt = {
  viewBox: [0, 0, 256, 150],
  spoutViewBox: [0, -50, 256, 200],
  hasSpout: true,
  themable: true,
  Art: SideBrickAssembledArt,
};
