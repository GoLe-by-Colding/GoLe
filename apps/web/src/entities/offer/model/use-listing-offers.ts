"use client";

import { useCallback, useEffect, useId, useRef, useState } from "react";
import { fetchListingOffers, type PriceOffer } from "@gole/core/offer";

export interface UseListingOffersOptions {
  /** `false`면 요청하지 않는다(비로그인·본인 매물 등). 기본 `true`. */
  readonly enabled?: boolean;
  /** 화면이 보이는 동안 이 간격(ms)으로 다시 읽는다. 0이면 폴링하지 않는다. */
  readonly pollMs?: number;
  /** 값이 바뀌면 즉시 다시 읽는다. 대화방의 마지막 메시지 id처럼 "무언가 일어났다"는 신호를 넘긴다. */
  readonly refreshKey?: string | number | null;
}

export interface UseListingOffersResult {
  /** 아직 한 번도 읽지 못했으면 `null`. 읽기에 실패해도 마지막으로 읽은 목록은 남긴다. */
  readonly offers: readonly PriceOffer[] | null;
  /** 마지막 읽기가 실패했는지. */
  readonly failed: boolean;
  readonly reload: () => void;
  /** 수락·거절·철회·제안 응답을 다음 읽기 전에 바로 반영한다. */
  readonly apply: (offer: PriceOffer) => void;
}

interface Snapshot {
  readonly listingId: string;
  readonly offers: readonly PriceOffer[] | null;
  readonly failed: boolean;
}

interface ChangedDetail {
  readonly listingId: string;
  readonly source: string;
}

/**
 * 같은 화면에서 한 매물의 제안을 읽는 곳이 여럿일 때(매물 상세의 구매 버튼과 인라인 채팅 배너) 서로 맞추는 신호.
 * 한쪽이 바뀐 상태를 보면 다른 쪽이 다시 읽는다 — 배너는 수락을 봤는데 구매 버튼은 정가로 남아 있으면 안 된다.
 */
const CHANGED_EVENT = "gole:listing-offers-changed";

function announce(listingId: string, source: string): void {
  window.dispatchEvent(
    new CustomEvent<ChangedDetail>(CHANGED_EVENT, { detail: { listingId, source } }),
  );
}

/** 상태 변화만 본다. 같은 목록을 다시 읽은 것은 변화가 아니다. */
function signature(offers: readonly PriceOffer[]): string {
  return offers.map((offer) => `${offer.id}:${offer.status}:${offer.expiresAt}`).join("|");
}

function merge(offers: readonly PriceOffer[] | null, next: PriceOffer): readonly PriceOffer[] {
  const rest = (offers ?? []).filter((offer) => offer.id !== next.id);
  return [next, ...rest].toSorted(
    (left, right) => Date.parse(right.createdAt) - Date.parse(left.createdAt),
  );
}

/**
 * 매물의 가격 제안(`GET /api/v1/offers?listingId=`). 판매자면 그 매물의 모든 제안, 아니면 내 제안만
 * 온다 — 채팅 제안과 입찰 체결 제안(방 없음)이 함께 온다(price-offer O14).
 *
 * 세션이 필요한 조회다. 로그인하지 않았으면 `enabled: false`로 두어 401이 화면 세션을 지우지 않게 한다.
 */
export function useListingOffers(
  listingId: string,
  { enabled = true, pollMs = 0, refreshKey = null }: UseListingOffersOptions = {},
): UseListingOffersResult {
  const instanceId = useId();
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null);
  const [nonce, setNonce] = useState(0);
  // 마지막으로 본 상태. 다른 곳에 변화를 알릴지 판단한다.
  const seenRef = useRef<{ readonly listingId: string; readonly signature: string } | null>(null);

  useEffect(() => {
    if (!enabled) return;
    let active = true;
    let inFlight: AbortController | null = null;

    const load = (force: boolean) => {
      // 폴링은 보이는 동안만 돈다. 첫 읽기와 명시적 갱신은 화면 상태와 무관하게 한다.
      if (!force && document.visibilityState !== "visible") return;
      inFlight?.abort();
      const controller = new AbortController();
      inFlight = controller;
      fetchListingOffers(listingId, controller.signal).then(
        (offers) => {
          if (!active || controller.signal.aborted) return;
          const next = signature(offers);
          const seen = seenRef.current;
          seenRef.current = { listingId, signature: next };
          if (seen !== null && seen.listingId === listingId && seen.signature !== next) {
            announce(listingId, instanceId);
          }
          setSnapshot({ listingId, offers, failed: false });
        },
        () => {
          if (!active || controller.signal.aborted) return;
          setSnapshot((current) => ({
            listingId,
            offers: current?.listingId === listingId ? current.offers : null,
            failed: true,
          }));
        },
      );
    };

    const handleChanged = (event: Event) => {
      const detail = (event as CustomEvent<ChangedDetail>).detail;
      if (detail.listingId === listingId && detail.source !== instanceId) load(true);
    };

    load(true);
    const timer = pollMs > 0 ? window.setInterval(() => load(false), pollMs) : undefined;
    window.addEventListener(CHANGED_EVENT, handleChanged);
    return () => {
      active = false;
      inFlight?.abort();
      if (timer !== undefined) window.clearInterval(timer);
      window.removeEventListener(CHANGED_EVENT, handleChanged);
    };
  }, [enabled, instanceId, listingId, pollMs, refreshKey, nonce]);

  const reload = useCallback(() => setNonce((current) => current + 1), []);

  const apply = useCallback(
    (offer: PriceOffer) => {
      setSnapshot((current) => ({
        listingId,
        offers: merge(current?.listingId === listingId ? current.offers : null, offer),
        failed: false,
      }));
      // 이 화면에서 직접 바꾼 상태다. 같은 매물을 보는 다른 곳도 다시 읽게 한다.
      announce(listingId, instanceId);
    },
    [instanceId, listingId],
  );

  const current = enabled && snapshot?.listingId === listingId ? snapshot : null;
  return {
    offers: current?.offers ?? null,
    failed: current?.failed ?? false,
    reload,
    apply,
  };
}
