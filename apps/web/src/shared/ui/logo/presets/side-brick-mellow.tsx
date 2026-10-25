import type { MascotArt } from "./types";

/**
 * 2026-10-26 말랑 브릭 고래. 옆모습·세 스터드는 유지하고 눈과 꼬리를 간결하게 다듬었다.
 * 시안과 제작 근거: .kiro/specs/mascot-preview-studio/. 기본 마크와 과거 프리셋은 교체하지 않는다.
 */
function SideBrickMellowArt({ spout }: { readonly spout: boolean }) {
  return (
    <>
      {spout ? (
        // 새 후보의 분수는 정지된 브릭 세 개다. 작은 미소와 몸의 윤곽에 시선을 남긴다.
        <g>
          <path
            d="M96 4Q88-16 73-22M97 4Q99-23 109-32M99 5Q115-13 135-14"
            strokeWidth="3"
            strokeLinecap="round"
            className="stroke-brand-300"
          />
          <path d="M60-29h4v-4h5v4h5v-4h5v4h4v13H60Z" className="fill-accent-500" />
          <path d="M102-40h4v-4h5v4h5v-4h5v4h4v13h-23Z" className="fill-brand-400" />
          <path d="M130-22h4v-4h5v4h5v-4h5v4h4v13h-23Z" className="fill-brand-300" />
        </g>
      ) : null}
      <path
        d="M85 0h22a6 6 0 0 1 6 6v18H79V6a6 6 0 0 1 6-6Z"
        fill="#EAB308"
        className="fill-[var(--gole-mark-accent,var(--color-accent-500))]"
      />
      <path
        d="M36 14H158C174 14 186 26 186 42V48Q186 52 190 52H195C205 33 223 23 233 22C242 19 242 34 236 44C229 56 220 62 211 65C224 71 234 81 238 91C242 101 238 105 231 104C213 103 201 91 195 78H187C183 99 169 106 152 106H40C16 106 0 91 0 70V52C0 29 16 14 36 14ZM73 98C84 97 95 103 102 113C109 124 99 127 89 122C80 118 73 111 69 105Q66 99 73 98Z"
        fill="#1D4ED8"
        className="fill-[var(--gole-mark-body,var(--color-brand-600))]"
      />
      <path
        d="M36 14H158C174 14 186 26 186 42H1C5 25 19 14 36 14ZM42 0h20a6 6 0 0 1 6 6v12H36V6a6 6 0 0 1 6-6ZM129 0h20a6 6 0 0 1 6 6v12h-32V6a6 6 0 0 1 6-6Z"
        fill="#6082F7"
        className="fill-[var(--gole-mark-top,var(--gole-mark-body,var(--color-brand-400)))]"
      />
      <circle
        cx="41"
        cy="66"
        r="7.5"
        fill="#131E4F"
        className="fill-[var(--gole-mark-face,var(--color-brand-950))]"
      />
      <circle
        cx="43.5"
        cy="63.5"
        r="2.2"
        fill="#FFFFFF"
        className="fill-[var(--gole-mark-glint,var(--color-white))]"
      />
      <path
        d="M18 79Q25 87 33 79"
        stroke="#131E4F"
        strokeWidth="4"
        strokeLinecap="round"
        className="stroke-[var(--gole-mark-face,var(--color-brand-950))]"
      />
    </>
  );
}

export const sideBrickMellow: MascotArt = {
  viewBox: [0, 0, 242, 125],
  spoutViewBox: [0, -48, 242, 173],
  hasSpout: true,
  themable: true,
  Art: SideBrickMellowArt,
};
