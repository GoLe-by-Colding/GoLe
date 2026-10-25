"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  deleteMascotAsset,
  fetchMascotEditor,
  fetchMascotHistory,
  isMascotPresetKey,
  MASCOT_PRESETS,
  MASCOT_PUBLISHED_EVENT,
  mascotPresetMeta,
  publishMascot,
  uploadArtwork,
  type MascotEditor,
  type MascotSelection,
} from "@gole/core/mascot";
import { ApiError } from "@shared/api";
import { cn } from "@shared/lib";
import { Badge, Button, Field, Logo, Textarea, useDialog } from "@shared/ui";
import { errorText, formatTime } from "../lib/mascot-format";
import { MascotHistoryList } from "./mascot-history-list";
import { MascotPreviewPanel, type MascotCandidate as Candidate } from "./mascot-preview-panel";
import { MascotUploadForm } from "./mascot-upload-form";

/** 이력 한 쪽 크기 — 서버 `history(before)`가 한 번에 주는 리비전 수와 같다. */
const HISTORY_PAGE = 25;

type Notice = { readonly tone: "info" | "error"; readonly text: string } | null;

/**
 * 운영자 콘솔 · 마스코트. (mascot-assets)
 *
 * <p>기본 제공 고래와 업로드한 그림 중 하나를 골라 헤더·푸터·히어로 크기로 미리 보고, 사유와 확인을 거쳐
 * 사이트에 적용한다. 적용은 리비전을 하나씩 쌓으므로 이력에서 언제든 예전 마스코트로 돌아갈 수 있다.
 */
export function AdminMascotPage() {
  const [editor, setEditor] = useState<MascotEditor | null>(null);
  const [history, setHistory] = useState<readonly MascotSelection[]>([]);
  const [hasMoreHistory, setHasMoreHistory] = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [reason, setReason] = useState("");
  const [reviewed, setReviewed] = useState(false);
  const [busy, setBusy] = useState(true);
  const [conflict, setConflict] = useState(false);
  const [notice, setNotice] = useState<Notice>(null);
  const dialog = useDialog();
  const previewRef = useRef<HTMLElement>(null);

  const load = useCallback(async (keepSelection: boolean) => {
    const [next, rows] = await Promise.all([fetchMascotEditor(), fetchMascotHistory()]);
    setEditor(next);
    setHistory(rows);
    setHasMoreHistory(rows.length >= HISTORY_PAGE);
    setConflict(false);
    setReviewed(false);
    // 고른 업로드 에셋을 그 사이 누가 지웠으면 적용 중인 에셋으로 돌아간다.
    setSelectedId((previous) =>
      keepSelection &&
      previous !== null &&
      (isMascotPresetKey(previous) || next.uploads.some((upload) => upload.id === previous))
        ? previous
        : next.active.asset.id,
    );
  }, []);

  useEffect(() => {
    let alive = true;
    void Promise.all([fetchMascotEditor(), fetchMascotHistory()])
      .then(([next, rows]) => {
        if (!alive) return;
        setEditor(next);
        setHistory(rows);
        setHasMoreHistory(rows.length >= HISTORY_PAGE);
        setSelectedId(next.active.asset.id);
      })
      .catch(() => {
        if (alive)
          setNotice({
            tone: "error",
            text: "마스코트 설정을 불러오지 못했습니다. 다시 불러오기를 눌러 주세요.",
          });
      })
      .finally(() => {
        if (alive) setBusy(false);
      });
    return () => {
      alive = false;
    };
  }, []);

  const candidates = useMemo<readonly Candidate[]>(() => {
    const presets = MASCOT_PRESETS.map<Candidate>((preset) => ({
      artwork: { kind: "PRESET", id: preset.key, name: preset.name },
      name: preset.name,
      detail: `${preset.date} · ${preset.commit}`,
      source: "preset",
    }));
    const uploads = (editor?.uploads ?? []).map<Candidate>((upload) => ({
      artwork: uploadArtwork(upload),
      name: upload.name,
      detail: `${formatTime(upload.createdAt)} 업로드`,
      source: "upload",
    }));
    return [...presets, ...uploads];
  }, [editor]);

  const activeId = editor?.active.asset.id ?? null;
  const selected = candidates.find((candidate) => candidate.artwork.id === selectedId) ?? null;
  const selectedDescription =
    selected === null
      ? ""
      : selected.source === "preset" && isMascotPresetKey(selected.artwork.id)
        ? mascotPresetMeta(selected.artwork.id).description
        : (editor?.uploads.find((upload) => upload.id === selected.artwork.id)?.description ?? "");
  const selectedIsActive = selected !== null && selected.artwork.id === activeId;

  const selectedIndex = candidates.findIndex((candidate) => candidate.artwork.id === selectedId);

  function choose(id: string, revealPreview = true) {
    if (busy || editor === null) return;
    setSelectedId(id);
    if (id !== selectedId) setReviewed(false);
    if (revealPreview) {
      previewRef.current?.querySelector("h3")?.focus({ preventScroll: true });
      const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
      previewRef.current?.scrollIntoView({ behavior: reduce ? "auto" : "smooth", block: "start" });
    }
  }

  function stepCandidate(offset: number) {
    const next = candidates[(selectedIndex + offset + candidates.length) % candidates.length];
    if (next !== undefined) choose(next.artwork.id, false);
  }

  async function reload() {
    setBusy(true);
    try {
      await load(true);
      setNotice({ tone: "info", text: "최신 값을 불러왔습니다." });
    } catch {
      setNotice({ tone: "error", text: "불러오기에 실패했습니다. 다시 시도해 주세요." });
    } finally {
      setBusy(false);
    }
  }

  async function apply() {
    if (editor === null || selected === null || busy || !reviewed || reason.trim() === "") return;
    setBusy(true);
    try {
      const next = await publishMascot(editor.current.revision, selected.artwork.id, reason);
      setReason("");
      setReviewed(false);
      window.dispatchEvent(new Event(MASCOT_PUBLISHED_EVENT));
      setNotice({
        tone: "info",
        text: `적용했습니다 · 변경 버전 ${next.revision} · ${next.assetName}. 다른 방문자에게는 1분 안에 보입니다.`,
      });
      await load(true).catch(() =>
        setNotice({
          tone: "error",
          text: "적용됐지만 화면을 새로 불러오지 못했습니다. 다시 불러오기를 눌러 주세요.",
        }),
      );
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setConflict(true);
        // 사이트 상태가 바뀌었으니 앞선 확인은 무효다. 최신 값을 보고 다시 확인하게 한다.
        setReviewed(false);
        setNotice({
          tone: "error",
          text: "다른 관리자가 먼저 바꿨습니다. 고른 마스코트는 그대로 두었으니 최신 값을 불러온 뒤 다시 검토해 주세요.",
        });
      } else {
        setNotice({
          tone: "error",
          text: errorText(
            error,
            "적용하지 못했습니다. 최신 값을 불러와 적용 여부를 확인해 주세요.",
          ),
        });
      }
    } finally {
      setBusy(false);
    }
  }

  async function remove(candidate: Candidate) {
    if (busy) return;
    const confirmed = await dialog.confirm({
      title: "마스코트 지우기",
      description: `"${candidate.name}"을(를) 지울까요? 이미지 파일도 함께 폐기되며 되돌릴 수 없습니다.`,
      confirmLabel: "지우기",
      tone: "danger",
    });
    if (!confirmed) return;
    setBusy(true);
    try {
      await deleteMascotAsset(candidate.artwork.id);
      if (selectedId === candidate.artwork.id) {
        setSelectedId(activeId);
        setReviewed(false);
      }
      await load(true);
      setNotice({ tone: "info", text: `"${candidate.name}"을(를) 지웠습니다.` });
    } catch (error) {
      setNotice({ tone: "error", text: errorText(error, "지우지 못했습니다.") });
      // 그 사이 다른 관리자가 적용했거나(409) 이미 지웠으면(404) 목록이 낡았다. 최신 값으로 맞춘다.
      if (error instanceof ApiError && (error.status === 409 || error.status === 404)) {
        await load(true).catch(() => undefined);
      }
    } finally {
      setBusy(false);
    }
  }

  const presetCandidates = candidates.filter((candidate) => candidate.source === "preset");
  const uploadCandidates = candidates.filter((candidate) => candidate.source === "upload");

  return (
    <section className="min-w-0 space-y-8" aria-busy={busy}>
      <header className="space-y-2">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-2xl">마스코트</h2>
          <Button variant="secondary" size="sm" disabled={busy} onClick={() => void reload()}>
            다시 불러오기
          </Button>
        </div>
        <p className="max-w-2xl text-sm leading-relaxed break-keep text-neutral-600">
          고래의 표정과 브릭 모양을 실제 사용 크기로 비교해 보세요.
        </p>
      </header>

      {notice !== null ? (
        <p
          role={notice.tone === "error" ? "alert" : "status"}
          className={cn(
            "rounded-lg border p-3 text-sm break-keep",
            notice.tone === "error"
              ? "border-danger/30 bg-danger-soft text-danger"
              : "border-neutral-200 bg-white text-neutral-700",
          )}
        >
          {notice.text}
        </p>
      ) : null}

      <section
        ref={previewRef}
        aria-label="마스코트 비교"
        className="min-w-0 scroll-mt-24 border-b border-neutral-200 pb-8"
      >
        {selected !== null && editor !== null ? (
          <MascotPreviewPanel
            candidate={selected}
            description={selectedDescription}
            current={editor.active.asset}
          >
            <div className="flex flex-wrap items-center gap-2">
              <p
                className="text-xs text-neutral-500 tabular-nums"
                aria-live="polite"
                aria-atomic="true"
              >
                <span className="sr-only">{selected.name} 미리보기, </span>
                {selectedIndex + 1} / {candidates.length}
              </p>
              <Button
                variant="secondary"
                size="sm"
                disabled={busy || candidates.length < 2}
                onClick={() => stepCandidate(-1)}
              >
                이전 후보
              </Button>
              <Button
                variant="secondary"
                size="sm"
                disabled={busy || candidates.length < 2}
                onClick={() => stepCandidate(1)}
              >
                다음 후보
              </Button>
            </div>
          </MascotPreviewPanel>
        ) : (
          <p role="status" className="rounded-xl bg-neutral-50 p-6 text-sm text-neutral-600">
            {busy
              ? "마스코트 미리보기를 불러오는 중…"
              : "설정을 다시 불러오면 마스코트를 비교할 수 있습니다."}
          </p>
        )}
      </section>

      <div className="grid min-w-0 gap-8 xl:grid-cols-[minmax(0,1fr)_minmax(280px,320px)]">
        <div className="min-w-0 space-y-8">
          <CandidateGrid
            title="기본 제공 고래"
            description="그림을 고르거나 위의 이전·다음 버튼으로 비교해 보세요."
            candidates={presetCandidates}
            selectedId={selectedId}
            activeId={activeId}
            busy={busy || editor === null}
            onChoose={choose}
          />
          <CandidateGrid
            title="업로드한 마스코트"
            description={
              uploadCandidates.length === 0
                ? "아직 올린 그림이 없습니다. 아래에서 새 마스코트를 올릴 수 있습니다."
                : "기본 고래와 같은 크기로 비교할 수 있습니다. 적용 중인 그림은 지울 수 없습니다."
            }
            candidates={uploadCandidates}
            selectedId={selectedId}
            activeId={activeId}
            busy={busy || editor === null}
            onChoose={choose}
            onRemove={(candidate) => void remove(candidate)}
          />
        </div>
        <aside className="min-w-0 xl:sticky xl:top-24 xl:self-start" aria-label="마스코트 적용">
          <div className="space-y-3 rounded-xl border border-neutral-200 bg-white p-4">
            <h3 className="font-semibold">사이트에 적용</h3>
            {editor !== null ? (
              <p className="text-xs text-neutral-500 [overflow-wrap:anywhere]">
                변경 버전 {editor.current.revision} · 지금 적용: {editor.active.asset.name}
              </p>
            ) : null}
            {selectedIsActive ? (
              <p className="text-sm text-neutral-600">
                지금 적용 중인 마스코트입니다. 다른 고래를 고르면 적용할 수 있습니다.
              </p>
            ) : null}
            <Field label="변경 사유 (감사 기록, 필수)" hint="최대 300자">
              {({ inputId, describedBy }) => (
                <Textarea
                  id={inputId}
                  aria-describedby={describedBy}
                  rows={3}
                  maxLength={300}
                  value={reason}
                  disabled={busy}
                  onChange={(event) => setReason(event.target.value)}
                />
              )}
            </Field>
            <label className="flex items-start gap-2 text-sm text-neutral-700">
              <input
                type="checkbox"
                className="mt-0.5 shrink-0"
                checked={reviewed}
                disabled={busy || selected === null || selectedIsActive}
                onChange={(event) => setReviewed(event.target.checked)}
              />
              <span className="min-w-0 break-keep">
                미리보기를 확인했고, 사이트 전체의 고래가 이 그림으로 바뀝니다.
              </span>
            </label>
            <Button
              fullWidth
              disabled={
                editor === null ||
                selected === null ||
                selectedIsActive ||
                !reviewed ||
                reason.trim() === "" ||
                busy ||
                conflict
              }
              onClick={() => void apply()}
            >
              이 마스코트 적용
            </Button>
            {conflict ? (
              <Button variant="secondary" fullWidth disabled={busy} onClick={() => void reload()}>
                최신 값 불러오기
              </Button>
            ) : null}
            <p className="text-xs leading-relaxed break-keep text-neutral-500">
              헤더·푸터·홈·빈 화면에 적용됩니다. 파비콘·앱 아이콘·공유 이미지는 여기서 바뀌지
              않습니다.
            </p>
          </div>
        </aside>
      </div>

      <MascotUploadForm
        disabled={busy || editor === null}
        onUploaded={async (assetId, name) => {
          await load(false).catch(() => undefined);
          setSelectedId(assetId);
          setReviewed(false);
          setNotice({
            tone: "info",
            text: `"${name}"을(를) 올렸습니다. 미리보기를 확인하고 적용하세요.`,
          });
        }}
        onError={(text) => setNotice({ tone: "error", text })}
        setBusy={setBusy}
      />

      <MascotHistoryList
        history={history}
        hasMore={hasMoreHistory}
        knownIds={new Set(candidates.map((candidate) => candidate.artwork.id))}
        busy={busy}
        onPick={(row) => {
          choose(row.assetId);
          setReason(`revision ${row.revision}의 "${row.assetName}"(으)로 되돌림`);
          setNotice({
            tone: "info",
            text: `"${row.assetName}"을(를) 미리보기에 불러왔습니다. 확인 후 적용하세요.`,
          });
        }}
        onMore={(rows) => {
          setHistory((previous) => [...previous, ...rows]);
          setHasMoreHistory(rows.length >= HISTORY_PAGE);
        }}
        onError={() => setNotice({ tone: "error", text: "이전 이력을 불러오지 못했습니다." })}
      />
      {dialog.element}
    </section>
  );
}

function CandidateGrid({
  title,
  description,
  candidates,
  selectedId,
  activeId,
  busy,
  onChoose,
  onRemove,
}: {
  readonly title: string;
  readonly description: string;
  readonly candidates: readonly Candidate[];
  readonly selectedId: string | null;
  readonly activeId: string | null;
  readonly busy: boolean;
  readonly onChoose: (id: string) => void;
  readonly onRemove?: (candidate: Candidate) => void;
}) {
  return (
    <section className="min-w-0 space-y-3" aria-label={title}>
      <div className="space-y-1">
        <h3 className="font-semibold">{title}</h3>
        <p className="text-sm text-neutral-600">{description}</p>
      </div>
      {candidates.length > 0 ? (
        <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 2xl:grid-cols-4">
          {candidates.map((candidate) => {
            const id = candidate.artwork.id;
            const selected = id === selectedId;
            const active = id === activeId;
            return (
              <li key={id} className="min-w-0">
                <div
                  className={cn(
                    "flex h-full min-w-0 flex-col overflow-hidden rounded-xl border bg-white transition-colors",
                    selected
                      ? "border-brand-500 ring-2 ring-brand-200"
                      : "border-neutral-200 hover:border-neutral-300",
                  )}
                >
                  <button
                    type="button"
                    aria-pressed={selected}
                    disabled={busy}
                    aria-label={`${candidate.name}${active ? " · 적용 중" : ""}`}
                    className="flex min-w-0 flex-1 touch-manipulation flex-col gap-2 p-3 text-left hover:bg-neutral-50 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-brand-600 active:bg-brand-50 disabled:cursor-wait disabled:opacity-60"
                    onClick={() => onChoose(id)}
                  >
                    <span className="flex h-20 w-full items-center justify-center rounded-lg bg-neutral-50">
                      <Logo
                        mascot={candidate.artwork}
                        size={96}
                        maxHeight={60}
                        showWordmark={false}
                        className="max-w-full [&_img]:h-auto [&_img]:max-w-full [&_svg]:h-auto [&_svg]:max-w-full"
                      />
                    </span>
                    <span className="block min-w-0">
                      <span className="line-clamp-2 text-sm font-semibold break-keep text-neutral-900">
                        {candidate.name}
                      </span>
                    </span>
                    {selected && !active ? (
                      <Badge tone="brand" className="self-start">
                        미리보기
                      </Badge>
                    ) : null}
                    {active ? (
                      <Badge tone="success" className="self-start">
                        적용 중
                      </Badge>
                    ) : null}
                  </button>
                  {onRemove !== undefined ? (
                    <div className="border-t border-neutral-100 px-3 py-2">
                      <button
                        type="button"
                        className="min-h-11 w-full rounded-md px-1 text-left text-xs font-medium break-keep text-danger hover:bg-danger-soft focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-600 disabled:cursor-not-allowed disabled:text-neutral-400"
                        disabled={busy || active}
                        onClick={() => onRemove(candidate)}
                      >
                        {active ? "적용 중이라 지울 수 없음" : "지우기"}
                      </button>
                    </div>
                  ) : null}
                </div>
              </li>
            );
          })}
        </ul>
      ) : null}
    </section>
  );
}
