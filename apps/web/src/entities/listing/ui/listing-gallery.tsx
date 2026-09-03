"use client";

import { useState } from "react";
import { thumbnailUrl } from "@shared/lib";

export interface ListingGalleryProps {
  readonly photos: readonly string[];
  readonly alt: string;
}

const PLACEHOLDER = "https://placehold.co/800x600?text=BRICK";

/**
 * 매물 사진 갤러리. 대표 이미지 + 썸네일 스트립(클릭 시 전환). 사진이 1장이면 썸네일은 숨긴다.
 */
export function ListingGallery({ photos, alt }: ListingGalleryProps) {
  const list = photos.length > 0 ? photos : [PLACEHOLDER];
  const [active, setActive] = useState(0);
  const main = list[Math.min(active, list.length - 1)]!;

  return (
    <div className="flex flex-col gap-3">
      <div className="overflow-hidden rounded-lg border border-neutral-200 bg-neutral-50">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img className="w-full aspect-[4/3] object-cover" src={thumbnailUrl(main, 800)} alt={alt} />
      </div>
      {list.length > 1 ? (
        <ul className="flex flex-wrap gap-2">
          {list.map((p, i) => (
            <li key={`${p}-${i}`}>
              <button
                type="button"
                onClick={() => setActive(i)}
                aria-label={`사진 ${i + 1}`}
                aria-current={i === active}
                className={`block overflow-hidden rounded-lg border-2 transition-colors ${
                  i === active ? "border-brand-500" : "border-transparent hover:border-neutral-300"
                }`}
              >
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img src={thumbnailUrl(p, 160)} alt="" className="h-16 w-16 object-cover" />
              </button>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}
