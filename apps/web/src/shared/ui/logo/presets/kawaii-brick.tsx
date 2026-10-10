import type { MascotArt } from "./types";

/*
 * 카와이 브릭 고래 — `d29b52d9`(2026-06-14)의 고래 마크. 다시 그리지 않고 그때의 SVG 를 그대로 옮겼다.
 * 원본: `git show d29b52d9:apps/web/src/shared/ui/logo/logo.tsx`
 */
function KawaiiBrickArt() {
  return (
    <>
      {/* 등 위 스터드 3개 — 가운데 골드가 시그니처. 몸통보다 먼저 그려 하단이 등에 묻힌다. */}
      <rect x="9.5" y="10.5" width="5" height="3.2" rx="1.3" fill="#1D4ED8" />
      <rect x="15.55" y="10.5" width="5" height="3.2" rx="1.3" fill="#EAB308" />
      <rect x="21.6" y="10.5" width="5" height="3.2" rx="1.3" fill="#1D4ED8" />

      {/* 몸통 — 평평한 브릭 등(직선) + 클래식 가로 고래 플루크 */}
      <path
        d="M9 13
           L26.3 13
           C28.4 13 29.4 14.9 29.8 17.4
           C30.8 16 32.6 14.8 35 14.4
           C36.4 14.2 37.2 15.6 36.4 16.8
           C35.4 18.3 34.2 19.4 32.8 20.1
           C34.3 20.9 35.6 22.1 36.6 23.7
           C37.4 24.9 36.5 26.3 35.1 26
           C32.7 25.5 30.8 24.2 29.7 22.4
           C28.9 25.6 26.4 28.8 21.8 28.8
           L13 28.8
           C7.5 28.8 3.7 25.2 3.7 20.6
           C3.7 16.5 5.7 13.4 9 13
           Z"
        fill="#1D4ED8"
      />

      {/* 카와이 얼굴 — 큰 눈 + 하이라이트 */}
      <circle cx="9.6" cy="19.6" r="2.1" fill="#ffffff" />
      <circle cx="10.4" cy="18.9" r="0.62" fill="#1D4ED8" opacity="0.4" />
      {/* 볼터치 */}
      <circle cx="6.9" cy="22" r="1.5" fill="#ffffff" opacity="0.16" />
      {/* 미소 */}
      <path
        d="M6 22.6 Q8.3 24.6 11 23.2"
        stroke="#ffffff"
        strokeWidth="1.1"
        strokeLinecap="round"
        fill="none"
        opacity="0.75"
      />
    </>
  );
}

export const kawaiiBrick: MascotArt = {
  viewBox: [0, 0, 40, 40],
  hasSpout: false,
  themable: false,
  Art: KawaiiBrickArt,
};
