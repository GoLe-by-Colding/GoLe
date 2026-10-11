import type { MascotArt } from "./types";

/*
 * 통통 고래 — `11070a52`(2026-06-13)의 고래 마크. 다시 그리지 않고 그때의 SVG 를 그대로 옮겼다.
 * 원본: `git show 11070a52:apps/web/src/shared/ui/logo/logo.tsx`
 */
function ChubbyArt() {
  return (
    <>
      {/* 정수리 스터드 3개 — 가운데 골드가 시그니처. 몸통보다 먼저 그려 하단이 머리에 묻힌다. */}
      <rect x="7.6" y="5" width="3.2" height="3" rx="1.2" fill="#1D4ED8" />
      <rect x="11.5" y="4.6" width="3.2" height="3" rx="1.2" fill="#EAB308" />
      <rect x="15.4" y="5" width="3.2" height="3" rx="1.2" fill="#1D4ED8" />

      {/* 몸통 — 볼록한 둥근 이마 돔 → 통통한 배 → 위로 올라간 플루크 */}
      <path
        d="M8 7.4
           C5 8.2 3 11.4 2.8 16
           C2.7 17.8 2.9 19.2 3.4 20.8
           C5 26.2 9.8 30.2 15.6 30.2
           C21.4 30.2 25.8 26.4 27 21.9
           C28.2 23.7 30.1 25 32.5 25.5
           C33.9 25.8 34.8 24.4 34 23.2
           C33 21.6 31.7 20.4 30.2 19.6
           C31.7 18.9 33 17.8 34 16.3
           C34.8 15.1 33.9 13.7 32.5 14
           C30.1 14.5 28.2 15.8 27 17.6
           C25.9 13.4 22.6 8.4 16.8 7.4
           C13.8 6.9 10.8 6.6 8 7.4
           Z"
        fill="#1D4ED8"
      />

      {/* 큰 눈 — 네거티브 스페이스 */}
      <circle cx="9" cy="17.6" r="2" fill="#ffffff" />
      {/* 미소 */}
      <path
        d="M5.2 21 Q7.3 23 10 21.8"
        stroke="#ffffff"
        strokeWidth="1.2"
        strokeLinecap="round"
        fill="none"
        opacity="0.65"
      />
    </>
  );
}

export const chubby: MascotArt = {
  viewBox: [0, 0, 40, 40],
  hasSpout: false,
  themable: false,
  Art: ChubbyArt,
};
