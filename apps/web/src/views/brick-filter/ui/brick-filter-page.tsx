"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import {
  createBrickJob,
  fetchBrickJob,
  fetchBrickJobs,
  fetchBrickQuota,
  fetchBrickResult,
  type BrickJob,
  type BrickMode,
  type BrickQuota,
} from "@gole/core/brick-filter";

export function BrickFilterPage() {
  const { session } = useSession();
  return (
    <section className="mx-auto max-w-5xl space-y-6 px-4 py-10">
      <header className="space-y-3">
        <p className="text-sm font-medium text-brand-600">사진으로 만드는 작은 상상</p>
        <h1 className="text-3xl">브릭 필터</h1>
        <p className="text-neutral-600">
          인물은 미니피겨로, 사물은 브릭 모델로 바꿔 보세요. 회원당 두 모드 합산 하루 3회 이용할 수
          있어요.
        </p>
      </header>
      {session ? (
        <Editor key={`${session.accountId}:${session.sessionToken}`} />
      ) : (
        <div className="rounded-xl border border-neutral-200 bg-white p-6">
          <p className="mb-4">로그인 후 나만의 브릭 이미지를 만들어 보세요.</p>
          <Link className="text-brand-700 underline" href="/login?returnTo=%2Fbrick-filter">
            로그인하기
          </Link>
        </div>
      )}
    </section>
  );
}
function Editor() {
  const [quota, setQuota] = useState<BrickQuota | null>(null);
  const [mode, setMode] = useState<BrickMode>("MINIFIGURE");
  const [file, setFile] = useState<File | null>(null);
  const [source, setSource] = useState("");
  const [job, setJob] = useState<BrickJob | null>(null);
  const [recent, setRecent] = useState<BrickJob[]>([]);
  const [result, setResult] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [consent, setConsent] = useState(false);
  const [pendingId, setPendingId] = useState<string | null>(null);
  const alive = useRef(true);
  const inFlight = useRef(false);
  const refreshVersion = useRef(0);
  const [canResend, setCanResend] = useState(false);
  const [authExpired, setAuthExpired] = useState(false);
  const pending = busy || job?.status === "RESERVED" || pendingId !== null;
  const showError = useCallback((error: unknown, fallback: string) => {
    if (!alive.current) return;
    if (error instanceof ApiError && error.status === 401) {
      ++refreshVersion.current;
      setAuthExpired(true);
      setMessage("로그인이 만료되었습니다. 다시 로그인해 주세요.");
    } else setMessage(fallback);
  }, []);
  const refresh = useCallback(async () => {
    const version = ++refreshVersion.current;
    const [q, rows] = await Promise.all([fetchBrickQuota(), fetchBrickJobs()]);
    if (alive.current && version === refreshVersion.current) {
      setQuota(q);
      setRecent(rows);
      setAuthExpired(false);
    }
  }, []);
  useEffect(() => {
    alive.current = true;
    void refresh().catch((error: unknown) =>
      showError(error, "이용 정보를 불러오지 못했습니다. 횟수 다시 확인을 눌러 주세요."),
    );
    return () => {
      alive.current = false;
    };
  }, [refresh, showError]);
  useEffect(
    () => () => {
      if (source) URL.revokeObjectURL(source);
    },
    [source],
  );
  useEffect(() => {
    if (job?.status !== "SUCCEEDED") return;
    let active = true;
    let url = "";
    void fetchBrickResult(job.id)
      .then((blob) => {
        url = URL.createObjectURL(blob);
        if (active) setResult(url);
        else URL.revokeObjectURL(url);
      })
      .catch((error: unknown) => {
        if (active)
          showError(
            error,
            "결과를 불러오지 못했거나 보관 기간이 지났습니다. 아래 작업에서 다시 열어 주세요.",
          );
      });
    return () => {
      active = false;
      if (url) URL.revokeObjectURL(url);
    };
  }, [job, showError]);
  useEffect(() => {
    if (job?.status !== "RESERVED" || authExpired) return;
    let active = true;
    let timer: ReturnType<typeof setTimeout>;
    const id = job.id;
    async function poll() {
      try {
        const next = await fetchBrickJob(id);
        if (!active) return;
        setJob((current) =>
          current?.id === next.id && current.status !== "RESERVED" ? current : next,
        );
        if (next.status !== "RESERVED") {
          void refresh().catch((error: unknown) =>
            showError(error, "작업 상태를 확인했습니다. 이용 횟수는 다시 확인해 주세요."),
          );
          return;
        }
      } catch (error) {
        if (!active) return;
        showError(error, "연결을 확인하고 요청 상태를 다시 확인해 주세요.");
        if (error instanceof ApiError && [401, 403, 404].includes(error.status)) return;
      }
      if (active) timer = setTimeout(() => void poll(), 2500);
    }
    timer = setTimeout(() => void poll(), 2500);
    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [job?.id, job?.status, authExpired, refresh, showError]);
  async function submit(reuse?: string) {
    if (!file || !quota || inFlight.current || authExpired || !consent) return;
    if (
      reuse
        ? reuse !== pendingId || !canResend
        : pending || !quota.enabled || !quota.retryAvailable || quota.remaining <= 0
    )
      return;
    inFlight.current = true;
    setCanResend(false);
    const id = reuse ?? `${quota.day}_${crypto.randomUUID()}`;
    setBusy(true);
    setPendingId(id);
    setMessage("");
    setResult("");
    try {
      const next = await createBrickJob(id, mode, file);
      if (alive.current) {
        setJob(next);
        setPendingId(null);
        void refresh().catch((error: unknown) =>
          showError(error, "작업 상태를 확인했습니다. 이용 횟수는 다시 확인해 주세요."),
        );
      }
    } catch (error) {
      if (alive.current) {
        if (error instanceof ApiError && [400, 401, 403, 409, 413, 429].includes(error.status)) {
          setPendingId(null);
          showError(error, error.message);
          void refresh().catch(() => {});
        } else
          setMessage("응답을 확인하지 못했습니다. 새로 생성하지 말고 요청 상태를 확인해 주세요.");
      }
    } finally {
      inFlight.current = false;
      if (alive.current) setBusy(false);
    }
  }
  async function check() {
    const id = pendingId ?? job?.id;
    if (!id || inFlight.current) return;
    inFlight.current = true;
    setCanResend(false);
    setBusy(true);
    try {
      const next = await fetchBrickJob(id);
      if (alive.current) {
        setJob((current) =>
          current?.id === next.id && current.status !== "RESERVED" ? current : next,
        );
        setPendingId(null);
        setMessage("");
        void refresh().catch((error: unknown) =>
          showError(error, "작업 상태를 확인했습니다. 이용 횟수는 다시 확인해 주세요."),
        );
      }
    } catch (error) {
      if (alive.current) {
        if (error instanceof ApiError && error.status === 404) {
          setCanResend(pendingId !== null);
          setMessage("아직 요청을 찾지 못했습니다. 같은 요청으로 다시 전송할 수 있습니다.");
        } else showError(error, "아직 상태를 확인할 수 없습니다. 잠시 후 다시 확인해 주세요.");
      }
    } finally {
      inFlight.current = false;
      if (alive.current) setBusy(false);
    }
  }
  const button =
    "rounded-lg border border-neutral-300 bg-white px-4 py-3 text-sm font-medium disabled:opacity-40";
  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-brand-50 p-4">
        <p aria-live="polite">
          {quota
            ? `오늘 남은 횟수 ${quota.remaining} / ${quota.limit} · 서울 자정 초기화`
            : "이용 횟수 확인 중"}
        </p>
        <button
          className={button}
          disabled={busy}
          onClick={() =>
            void refresh().catch((error: unknown) =>
              showError(error, "이용 정보를 불러오지 못했습니다. 다시 확인해 주세요."),
            )
          }
        >
          횟수 다시 확인
        </button>
      </div>
      {quota && !quota.enabled && (
        <p role="status" className="rounded-lg bg-neutral-100 p-4">
          브릭 필터를 준비 중입니다. 준비가 끝나면 이곳에서 이용할 수 있어요.
        </p>
      )}
      {quota && !quota.retryAvailable && (
        <p role="status">반복 요청 제한에 도달했습니다. 내일 다시 이용해 주세요.</p>
      )}
      <div className="grid gap-6 md:grid-cols-2">
        <section className="space-y-4 rounded-xl border border-neutral-200 bg-white p-5">
          <h2 className="text-lg">1. 모드와 사진 선택</h2>
          <fieldset disabled={pending} className="grid grid-cols-2 gap-2">
            <legend className="sr-only">변환 모드</legend>
            {(
              [
                ["MINIFIGURE", "인물 미니피겨"],
                ["BRICK_OBJECT", "사물 브릭"],
              ] as const
            ).map(([value, label]) => (
              <label
                key={value}
                className={`cursor-pointer rounded-lg border p-3 text-sm ${mode === value ? "border-brand-600 bg-brand-50" : "border-neutral-200"}`}
              >
                <input
                  type="radio"
                  name="mode"
                  className="mr-2"
                  value={value}
                  checked={mode === value}
                  onChange={() => setMode(value)}
                />
                {label}
              </label>
            ))}
          </fieldset>
          <label className="block space-y-2 text-sm">
            <span>PNG/JPEG/HEIF · 4MB 이하 · 1200만 화소 이하</span>
            <input
              className="block w-full rounded-lg border border-neutral-300 p-3"
              type="file"
              accept="image/png,image/jpeg,image/heic,image/heif,.heic,.heif"
              disabled={pending}
              onChange={(e) => {
                const selected = e.target.files?.[0];
                if (!selected) return;
                if (
                  !(
                    ["image/png", "image/jpeg", "image/heic", "image/heif"].includes(
                      selected.type,
                    ) || /\.hei[cf]$/i.test(selected.name)
                  ) ||
                  selected.size > 4 * 1024 * 1024
                ) {
                  setFile(null);
                  setSource("");
                  setConsent(false);
                  setMessage("PNG/JPEG/HEIF 4MB 이하 사진을 선택해 주세요.");
                  e.target.value = "";
                  return;
                }
                setFile(selected);
                setSource(
                  /\.hei[cf]$/i.test(selected.name) || selected.type.includes("hei")
                    ? ""
                    : URL.createObjectURL(selected),
                );
                setMessage("");
                setJob(null);
                setResult("");
                setConsent(false);
              }}
            />
          </label>
          {source && (
            <Image
              src={source}
              unoptimized
              alt="선택한 원본 사진"
              width={1024}
              height={1024}
              className="max-h-80 w-full rounded-lg object-contain"
            />
          )}
          <p className="text-xs text-neutral-500">
            선택한 사진은 이미지 생성을 위해 OpenAI로 전송합니다. 서비스에 저장한 원본은 처리 후
            삭제하고, 결과는 본인만 24시간 동안 열 수 있습니다.
          </p>
          <label className="flex items-start gap-2 text-sm">
            <input
              type="checkbox"
              checked={consent}
              disabled={pending}
              onChange={(e) => setConsent(e.target.checked)}
            />
            이용 권한이 있는 사진이며 이미지 생성 전송에 동의합니다.
          </label>
          <button
            className="w-full rounded-lg bg-brand-600 px-4 py-3 font-medium text-white disabled:opacity-40"
            disabled={
              authExpired ||
              !quota?.enabled ||
              !quota.retryAvailable ||
              quota.remaining === 0 ||
              !file ||
              !consent ||
              pending
            }
            onClick={() => void submit()}
          >
            브릭 이미지 만들기
          </button>
        </section>
        <section
          className="space-y-4 rounded-xl border border-neutral-200 bg-white p-5"
          aria-busy={pending}
        >
          <h2 className="text-lg">2. 나만의 브릭 이미지</h2>
          {busy || job?.status === "RESERVED" ? (
            <p role="status">
              사진을 확인하고 브릭 이미지를 만들고 있어요. 최대 몇 분이 걸릴 수 있습니다.
            </p>
          ) : null}
          {job?.status === "FAILED" && (
            <p role="status">
              이미지를 만들지 못했습니다. 예약 횟수를 돌려드렸어요. 사진과 모드를 확인하고 다시
              생성해 주세요.
            </p>
          )}
          {message && (
            <p role="status" className="rounded-lg bg-neutral-100 p-3 text-sm">
              {message}
            </p>
          )}
          {result ? (
            <>
              <Image
                src={result}
                unoptimized
                alt="생성된 브릭 이미지"
                width={1024}
                height={1024}
                className="w-full rounded-lg"
              />
              <a
                className="inline-block text-brand-700 underline"
                href={result}
                download="gole-brick.png"
              >
                이미지 저장
              </a>
            </>
          ) : (
            !pending && (
              <div className="flex min-h-48 items-center justify-center rounded-lg bg-neutral-50 p-5 text-sm text-neutral-500">
                완성된 이미지가 여기에 표시됩니다.
              </div>
            )
          )}
          {(pendingId || job?.status === "RESERVED") && (
            <button className={button} disabled={busy} onClick={() => void check()}>
              요청 상태 다시 확인
            </button>
          )}
          {pendingId && canResend && (
            <button
              className={button}
              disabled={busy || authExpired}
              onClick={() => void submit(pendingId)}
            >
              같은 요청 다시 전송
            </button>
          )}
          <Link
            className="block text-sm text-brand-700 underline"
            href="/login?returnTo=%2Fbrick-filter"
          >
            로그인 다시 하기
          </Link>
        </section>
      </div>
      {recent.length > 0 && (
        <section className="space-y-3">
          <h2 className="text-lg">오늘의 작업</h2>
          <div className="flex flex-wrap gap-2">
            {recent.map((row) => (
              <button
                key={row.id}
                className={button}
                disabled={pending}
                onClick={() => {
                  setResult("");
                  setJob({ ...row });
                  setMessage("");
                }}
              >
                {row.mode === "MINIFIGURE" ? "미니피겨" : "사물 브릭"} ·{" "}
                {row.status === "SUCCEEDED"
                  ? "결과 열기"
                  : row.status === "FAILED"
                    ? "실패"
                    : "진행 중"}
              </button>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
