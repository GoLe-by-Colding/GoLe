import { cn } from "@shared/lib";

export interface LogoProps {
  /** 고래 마크가 들어가는 정사각형 영역의 픽셀 크기. */
  readonly size?: number;
  readonly showWordmark?: boolean;
  readonly className?: string;
  readonly accentClassName?: string;
  /** 히어로처럼 큰 장면에서만 브릭 분수를 표시한다. */
  readonly spout?: boolean;
}

/**
 * GoLe의 Figma 기준 마크(Whale/Core · node 8:31).
 * 한쪽 눈, 둥근 이마와 볼, 짧은 입선, 세 개의 브릭 스터드가 식별 요소다.
 */
export function Logo({
  size = 32,
  showWordmark = true,
  className,
  accentClassName = "text-brand-600",
  spout = false,
}: LogoProps) {
  return (
    <span
      className={cn("inline-flex items-center gap-1.5 font-extrabold tracking-tight", className)}
    >
      <svg
        width={size}
        height={size}
        viewBox={spout ? "0 -48 274 194" : "0 0 274 146"}
        fill="none"
        aria-hidden="true"
        className="shrink-0 overflow-visible"
      >
        {spout ? (
          <g aria-hidden="true">
            <path
              d="M152 27C142 11 132 4 120-1M154 26C155 7 161-6 171-15M157 28C171 14 186 9 201 10"
              stroke="#74B9FF"
              strokeWidth="4"
              strokeLinecap="round"
              opacity="0.82"
            />
            <g className="gole-spout-brick gole-spout-brick-a">
              <rect x="107" y="-14" width="31" height="18" rx="4" fill="#F7BE2C" />
              <rect x="112" y="-19" width="8" height="6" rx="3" fill="#F7BE2C" />
              <rect x="125" y="-19" width="8" height="6" rx="3" fill="#F7BE2C" />
            </g>
            <g className="gole-spout-brick gole-spout-brick-b">
              <rect x="159" y="-28" width="29" height="18" rx="4" fill="#FF7A72" />
              <rect x="164" y="-33" width="7" height="6" rx="3" fill="#FF7A72" />
              <rect x="176" y="-33" width="7" height="6" rx="3" fill="#FF7A72" />
            </g>
            <g className="gole-spout-brick gole-spout-brick-c">
              <rect x="202" y="-7" width="27" height="17" rx="4" fill="#74B9FF" />
              <rect x="207" y="-12" width="7" height="6" rx="3" fill="#74B9FF" />
              <rect x="218" y="-12" width="7" height="6" rx="3" fill="#74B9FF" />
            </g>
          </g>
        ) : null}

        <path
          d="M231.823 78.263C246.823 64.263 263.823 65.263 273.823 73.263C266.823 82.263 256.823 88.263 246.823 90.263C257.823 93.263 266.823 101.263 272.823 112.263C258.823 116.263 244.823 108.263 231.823 98.263V78.263Z"
          fill="#1C54C7"
        />
        <path
          d="M76.823 0.263C36.823-2.737 5.823 20.263 0.823 51.263C-4.177 81.263 13.823 111.263 48.823 125.263C65.823 132.263 83.823 136.263 105.823 136.263H183.823C211.823 135.263 231.823 119.263 237.823 95.263C243.823 71.263 234.823 51.263 215.823 40.263C193.823 27.263 168.823 33.263 144.823 27.263C117.823 20.263 97.823 3.263 76.823 0.263Z"
          fill="#2C72E2"
        />
        <path
          d="M11.823 100.263C29.823 123.263 61.823 130.263 103.823 130.263H183.823C206.823 128.263 223.823 116.263 231.823 99.263C212.823 111.263 190.823 116.263 163.823 116.263H87.823C53.823 116.263 28.823 111.263 11.823 100.263Z"
          fill="#DCEBFF"
        />
        <path
          d="M141.823 123.263C150.823 126.263 158.823 134.263 161.823 145.263C152.823 147.263 143.823 141.263 138.823 132.263C135.823 127.263 137.823 123.263 141.823 123.263Z"
          fill="#1C54C7"
        />
        <path
          d="M109.823 19.263C132.823 19.263 157.823 23.263 195.823 29.263L194.823 37.263C159.823 31.263 134.823 27.263 110.823 27.263Z"
          fill="#1C54C7"
          opacity="0.45"
        />
        <rect x="116.823" y="11.263" width="19" height="13" rx="4" fill="#2C72E2" />
        <rect x="142.823" y="8.263" width="24" height="18" rx="5" fill="#F7BE2C" />
        <rect x="173.823" y="16.263" width="19" height="12" rx="4" fill="#2C72E2" />
        <ellipse cx="39.823" cy="66.263" rx="5.8" ry="6.9" fill="#091A3A" />
        <circle cx="41.823" cy="63.563" r="1.5" fill="white" />
        <path
          d="M13.823 91.263C17.823 93.463 21.823 93.463 25.823 91.263"
          stroke="#091A3A"
          strokeWidth="1.7"
          strokeLinecap="round"
        />
      </svg>
      {showWordmark ? (
        <span>
          Go<span className={accentClassName}>Le</span>
        </span>
      ) : null}
    </span>
  );
}
