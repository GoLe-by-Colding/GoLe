const patterns = [
  [
    [2, 4],
    [3, 3],
    [4, 3],
    [3, 5],
  ],
  [
    [4, 2],
    [3, 2],
    [2, 3],
    [2, 5],
  ],
  [
    [3, 3],
    [2, 4],
    [2, 3],
    [1, 6],
  ],
] as const;
const palettes = [
  ["#F3F5F7", "#AFBBD0", "#C6CEDC", "#DEE2EA"],
  ["#F2F6F7", "#A8C2CC", "#C0D3DB", "#DEE9ED"],
  ["#F4F3F8", "#B3B7D0", "#CCD0E0", "#E5E7F0"],
] as const;

/** 카탈로그 시드의 낮은 채도 브릭 패턴을 네트워크 없는 공통 대체 이미지로 재사용한다. */
export function BrickPlaceholder({ seed }: { readonly seed: string }) {
  let hash = 0;
  for (const character of seed) hash = (Math.imul(hash, 31) + character.charCodeAt(0)) >>> 0;
  const variant = hash % patterns.length;
  const pattern = patterns[variant]!;
  const [background, shadow, plane, stud] = palettes[variant]!;
  return (
    <svg
      viewBox="0 0 1200 1200"
      aria-hidden="true"
      focusable="false"
      className="absolute inset-0 h-full w-full"
      data-brick-placeholder={variant}
    >
      <rect width="1200" height="1200" fill={background} />
      {pattern.map(([start, count], row) => {
        const x = 152 + start * 96;
        const y = 390 + row * 96;
        return (
          <g key={row}>
            <rect x={x} y={y} width={count * 96} height="112" rx="14" fill={shadow} />
            <rect x={x} y={y} width={count * 96} height="96" rx="14" fill={plane} />
            {Array.from({ length: count }, (_, column) => (
              <circle key={column} cx={x + 48 + column * 96} cy={y + 18} r="20" fill={stud} />
            ))}
          </g>
        );
      })}
    </svg>
  );
}
