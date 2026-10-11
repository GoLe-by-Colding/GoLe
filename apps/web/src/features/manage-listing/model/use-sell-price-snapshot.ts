"use client";

import { useEffect, useState } from "react";
import { fetchPriceSnapshot, type PriceSnapshot } from "@entities/pricing";

/** 세트 번호 입력이 멈춘 뒤 시세를 읽기까지 기다리는 시간. 한 글자마다 요청하지 않는다. */
const SET_NUMBER_SETTLE_MS = 400;
/** 세트 번호로 보기 어려운 입력(두 글자 이하·공백·기호)은 시세를 묻지 않는다. 변형 번호(`10307-1`)는 받는다. */
const SET_NUMBER_PATTERN = /^[0-9A-Za-z][0-9A-Za-z-]{2,19}$/;

export type SellPriceSnapshot =
  | { readonly status: "idle" }
  | { readonly status: "loading"; readonly setNumber: string }
  | { readonly status: "loaded"; readonly setNumber: string; readonly snapshot: PriceSnapshot }
  | { readonly status: "failed"; readonly setNumber: string };

/**
 * 판매 등록·수정 화면이 가격 옆에 둘 세트 시세 스냅숏. 세트 번호가 바뀔 때만 읽고(상태·가격·카테고리 변경은 화면이
 * 같은 스냅숏으로 다시 계산한다), 한 번 읽은 세트는 이 화면에 있는 동안 다시 묻지 않는다. 이전 번호의 늦은 응답은 버린다.
 * 실패는 기억하지 않는다 — 번호를 바꿨다 돌아오면 다시 묻는다.
 */
export function useSellPriceSnapshot(setNumber: string | null): SellPriceSnapshot {
  const target = setNumber !== null && SET_NUMBER_PATTERN.test(setNumber) ? setNumber : null;
  const [snapshots, setSnapshots] = useState<ReadonlyMap<string, PriceSnapshot>>(() => new Map());
  const [failed, setFailed] = useState<string | null>(null);

  useEffect(() => {
    if (target === null || snapshots.has(target)) return;
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      fetchPriceSnapshot(target, controller.signal).then(
        (snapshot) => {
          if (controller.signal.aborted) return;
          setSnapshots((current) => new Map(current).set(target, snapshot));
        },
        () => {
          if (!controller.signal.aborted) setFailed(target);
        },
      );
    }, SET_NUMBER_SETTLE_MS);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [snapshots, target]);

  if (target === null) return { status: "idle" };
  const snapshot = snapshots.get(target);
  if (snapshot !== undefined) return { status: "loaded", setNumber: target, snapshot };
  return failed === target
    ? { status: "failed", setNumber: target }
    : { status: "loading", setNumber: target };
}
