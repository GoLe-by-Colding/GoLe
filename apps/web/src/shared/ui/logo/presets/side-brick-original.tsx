import type { MascotArt } from "./types";

/*
 * 원본 브릭 고래 — `9f581c70`(2026-08-30)의 고래 마크. 다시 그리지 않고 그때의 SVG 를 그대로 옮겼다.
 * 원본: `git show 9f581c70:apps/web/src/shared/ui/logo/logo.tsx`
 */
function SideBrickOriginalArt({ spout }: { readonly spout: boolean }) {
  return (
    <>
      {spout ? (
        <g aria-hidden="true">
          {/* 물줄기 — 가운데 스터드 뒤에서 시작해 위로 뻗으며 브릭이 솟는 경로를 안내한다. */}
          <path
            d="M96 10C90-6 84-14 75-21M100 8C102-9 108-21 118-29M104 11C116-2 130-8 144-8"
            stroke="#93AEFB"
            strokeWidth="4"
            strokeLinecap="round"
            opacity="0.75"
            transform="translate(0 -7)"
            className="gole-spout-stream"
          />
          {/* 1x2 브릭 세 개. 스터드 두 개가 얹힌 폭이 곧 1x2다. */}
          <g className="gole-spout-brick gole-spout-brick-a">
            <rect x="60" y="-30" width="34" height="17" rx="3" fill="#F7BE2C" />
            <rect x="66" y="-35" width="9" height="6" rx="2" fill="#F7BE2C" />
            <rect x="79" y="-35" width="9" height="6" rx="2" fill="#F7BE2C" />
          </g>
          <g className="gole-spout-brick gole-spout-brick-b">
            <rect x="114" y="-42" width="34" height="17" rx="3" fill="#FF7A72" />
            <rect x="120" y="-47" width="9" height="6" rx="2" fill="#FF7A72" />
            <rect x="133" y="-47" width="9" height="6" rx="2" fill="#FF7A72" />
          </g>
          <g className="gole-spout-brick gole-spout-brick-c">
            <rect x="154" y="-20" width="34" height="17" rx="3" fill="#6082F7" />
            <rect x="160" y="-25" width="9" height="6" rx="2" fill="#6082F7" />
            <rect x="173" y="-25" width="9" height="6" rx="2" fill="#6082F7" />
          </g>
        </g>
      ) : null}

      {/* ── 꼬리: 몸 뒤로 빠져나온 꼬리자루에 힌지 핀으로 물린 두 장의 플레이트 ── */}
      <polygon points="204,74 252,38 268,56 222,88" fill="#1D4ED8" />
      <polygon points="204,90 262,102 254,126 214,102" fill="#1D4ED8" />
      <path d="M188 66h18a5 5 0 0 1 5 5v22a5 5 0 0 1-5 5h-18V66Z" fill="#3B5CF2" />
      <circle cx="206" cy="82" r="8.5" fill="#1A3FC0" />
      <circle cx="206" cy="82" r="3.4" fill="#6082F7" />

      {/* ── 스파인 플레이트: 몸통 뒤에 먼저 그려 베이스는 감추고 스터드만
          등 표면 위로 솟게 한다. 분수 물줄기는 가운데 스터드 뒤에서 시작한다. ── */}
      <path
        d="M54 28V18a6 6 0 0 1 6-6h6V8a6 6 0 0 1 6-6h10a6 6 0 0 1 6 6v4h12V8a6 6 0 0 1 6-6h10a6 6 0 0 1 6 6v4h12V8a6 6 0 0 1 6-6h10a6 6 0 0 1 6 6v4h6a6 6 0 0 1 6 6v10H54Z"
        fill="#93AEFB"
        transform="translate(0 -7)"
      />

      {/* ── 몸통: 같은 폭(x8~196)으로 쌓인 큰 브릭 면 세 장(3단 면광) ── */}
      {/* 윗면 — 위로 확장해 스파인 베이스를 덮고, 스터드가 몸에서 난 것처럼 연결한다. */}
      <path d="M8 64V48C8 28 21 16 40 16H180C189 16 196 23 196 32V64H8Z" fill="#6082F7" />
      {/* 정면 — 눈과 입이 놓이는 기본 면. 곧은 사각이어야 브릭으로 읽힌다. */}
      <path d="M8 64h188v36H8V64Z" fill="#3B5CF2" />
      {/* 아랫면 — 그늘진 배. 바닥 모서리만 굴려 몸이 아래로 좁아진다. */}
      <path d="M8 100h188v6c0 11-9 18-20 18H30c-13 0-22-9-22-20v-4Z" fill="#1D4ED8" />
      {/* 몰드 사출 하이라이트 — 이마 모서리의 짧은 광택과 면 경계선만. */}
      <path
        d="M16 44C17 33 25 25 36 23"
        stroke="#FFFFFF"
        strokeWidth="3"
        strokeLinecap="round"
        opacity="0.14"
      />
      <path
        d="M14 65h174"
        stroke="#FFFFFF"
        strokeWidth="2.5"
        strokeLinecap="round"
        opacity="0.08"
      />

      {/* ── 가슴지느러미: 배 밑에 힌지 핀으로 물린 플레이트 한 장 ── */}
      <polygon points="100,126 148,132 142,146 98,138" fill="#1D4ED8" />
      <circle cx="107" cy="128" r="7" fill="#1A3FC0" />
      <circle cx="107" cy="128" r="3" fill="#6082F7" />

      {/* ── 얼굴: 작은 눈 하나와 짧은 입 ── */}
      <circle cx="40" cy="76" r="5.6" fill="#091A3A" />
      <circle cx="42" cy="73.6" r="1.6" fill="#FFFFFF" />
      <path d="M22 90c5 4 11 4.6 17 2" stroke="#091A3A" strokeWidth="2.4" strokeLinecap="round" />
    </>
  );
}

export const sideBrickOriginal: MascotArt = {
  viewBox: [0, 0, 274, 146],
  spoutViewBox: [0, -48, 274, 194],
  hasSpout: true,
  themable: false,
  Art: SideBrickOriginalArt,
};
