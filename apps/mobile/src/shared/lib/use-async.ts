import { useCallback, useEffect, useState } from "react";

export interface AsyncState<T> {
  readonly data: T | null;
  readonly loading: boolean;
  readonly error: string | null;
  readonly reload: () => void;
}

interface Settled<T> {
  /** 이 결과가 몇 번째 시도의 것인지. 현재 시도와 다르면 아직 로딩 중이다. */
  readonly attempt: number;
  readonly data: T | null;
  readonly error: string | null;
}

/**
 * 화면 진입 시 한 번 부르고 재시도할 수 있게 하는 최소 훅.
 *
 * <b>loading을 상태로 두지 않고 파생한다.</b> effect 본문에서 `setLoading(true)`를 부르면
 * 연쇄 렌더가 생기고 React Compiler가 이를 오류로 막는다. 대신 "요청한 시도 번호"와 "결과가
 * 담고 있는 시도 번호"를 비교하면 같은 정보를 렌더 중에 얻을 수 있다.
 *
 * 취소를 `AbortSignal`로 넘기는 이유는 코어의 모든 조회 함수가 그것을 받기 때문이다 —
 * 화면을 빠르게 벗어났을 때 늦게 도착한 응답이 사라진 화면의 상태를 건드리지 않는다.
 */
export function useAsync<T>(
  load: (signal: AbortSignal) => Promise<T>,
  deps: readonly unknown[],
): AsyncState<T> {
  const [attempt, setAttempt] = useState(0);
  const [settled, setSettled] = useState<Settled<T>>({ attempt: -1, data: null, error: null });

  const reload = useCallback(() => {
    setAttempt((n) => n + 1);
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;

    load(controller.signal)
      .then((data) => {
        if (active) {
          setSettled({ attempt, data, error: null });
        }
      })
      .catch((cause: unknown) => {
        if (!active || controller.signal.aborted) {
          return;
        }
        setSettled({
          attempt,
          data: null,
          error: cause instanceof Error ? cause.message : "불러오지 못했습니다.",
        });
      });

    return () => {
      active = false;
      controller.abort();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, attempt]);

  return {
    data: settled.data,
    loading: settled.attempt !== attempt,
    error: settled.error,
    reload,
  };
}
