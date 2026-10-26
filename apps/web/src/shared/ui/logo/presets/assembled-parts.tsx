/** 조립 고래 세 후보가 공유하는 둥근 스터드·관절 꼬리·정지된 브릭 분수. */
export function RoundedStud({
  x,
  y = 0,
  width = 32,
  gold = false,
}: {
  readonly x: number;
  readonly y?: number;
  readonly width?: number;
  readonly gold?: boolean;
}) {
  return (
    <rect
      x={x}
      y={y}
      width={width}
      height="26"
      rx="6"
      fill={gold ? "#EAB308" : "#6082F7"}
      className={
        gold
          ? "fill-[var(--gole-mark-accent,var(--color-accent-500))]"
          : "fill-[var(--gole-mark-top,var(--gole-mark-body,var(--color-brand-400)))]"
      }
    />
  );
}

/** 옆모습 브릭 고래의 두 둥근 꼬리판을 관절 중심으로 옮긴다. */
export function HingedTail({
  x,
  y,
  scale = 1,
  tilt = 0,
}: {
  readonly x: number;
  readonly y: number;
  readonly scale?: number;
  readonly tilt?: number;
}) {
  return (
    <g transform={`translate(${x} ${y}) rotate(${tilt}) scale(${scale}) translate(-192 -69)`}>
      <path
        d="M192.9 44.1L214.1 22.9C218.8 18.2 226.4 18.2 231.1 22.9C235.8 27.6 235.8 35.2 231.1 39.9L209.9 61.1C205.2 65.8 197.6 65.8 192.9 61.1C188.2 56.4 188.2 48.8 192.9 44.1ZM208.6 76.8L226.7 85.3C232.2 87.8 234.6 94.4 232 99.9C229.5 105.4 222.9 107.8 217.4 105.2L199.3 96.7C193.8 94.2 191.4 87.6 194 82.1C196.5 76.6 203.1 74.2 208.6 76.8Z"
        fill="#1D4ED8"
        className="fill-[var(--gole-mark-body,var(--color-brand-600))]"
      />
      <rect
        x="171"
        y="52"
        width="30"
        height="34"
        rx="8"
        fill="#3B5CF2"
        className="fill-[var(--gole-mark-body,var(--color-brand-500))]"
      />
      <circle
        cx="192"
        cy="69"
        r="7"
        stroke="#1A3FC0"
        strokeWidth="3"
        className="stroke-[var(--gole-mark-face,var(--color-brand-700))]"
      />
    </g>
  );
}

export function BrickSpout({ x }: { readonly x: number }) {
  return (
    <g transform={`translate(${x} 0)`}>
      <path
        d="M-3 3Q-10-13-28-19M0 2Q0-22 7-31M3 4Q17-10 34-10"
        strokeWidth="3"
        strokeLinecap="round"
        className="stroke-brand-300"
      />
      <path d="M-41-27h5v-5h5v5h6v-5h5v5h5v13h-26Z" className="fill-brand-300" />
      <path d="M-4-41h5v-5h5v5h6v-5h5v5h5v13H-4Z" className="fill-accent-500" />
      <path d="M29-18h5v-5h5v5h6v-5h5v5h5v13H29Z" className="fill-brand-400" />
    </g>
  );
}
