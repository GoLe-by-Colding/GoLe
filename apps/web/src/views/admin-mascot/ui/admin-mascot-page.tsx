"use client";

import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type FormEvent,
  type ReactNode,
} from "react";
import {
  deleteMascotAsset,
  fetchMascotEditor,
  fetchMascotHistory,
  isMascotPresetKey,
  MASCOT_PRESETS,
  MASCOT_PUBLISHED_EVENT,
  mascotPresetMeta,
  publishMascot,
  registerMascotAsset,
  uploadArtwork,
  type MascotArtwork,
  type MascotEditor,
  type MascotSelection,
} from "@gole/core/mascot";
import { ApiError, uploadImage } from "@shared/api";
import { cn } from "@shared/lib";
import { Badge, Button, Field, Input, Logo, Textarea } from "@shared/ui";

/** 업로드 상한 — 미디어 서버 기본값(5MB)과 같다. */
const MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
const ACCEPTED_TYPES = ["image/png", "image/jpeg"];
const MIN_SIDE = 16;
const MAX_SIDE = 4096;

interface Candidate {
  readonly artwork: MascotArtwork;
  readonly name: string;
  readonly detail: string;
  readonly source: "preset" | "upload";
}

type Notice = { readonly tone: "info" | "error"; readonly text: string } | null;

function errorText(error: unknown, fallback: string): string {
  return error instanceof ApiError && error.message.length > 0 ? error.message : fallback;
}

function formatTime(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : date.toLocaleString("ko-KR", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
      });
}

/**
 * 운영자 콘솔 · 마스코트. (mascot-assets)
 *
 * <p>기본 제공 고래 12벌과 업로드한 그림 중 하나를 골라 헤더·푸터·히어로 크기로 미리 보고, 사유와 확인을 거쳐
 * 사이트에 적용한다. 적용은 리비전을 하나씩 쌓으므로 이력에서 언제든 예전 마스코트로 돌아갈 수 있다.
 */
export function AdminMascotPage() {
  const [editor, setEditor] = useState<MascotEditor | null>(null);
  const [history, setHistory] = useState<readonly MascotSelection[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [reason, setReason] = useState("");
  const [reviewed, setReviewed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [conflict, setConflict] = useState(false);
  const [notice, setNotice] = useState<Notice>(null);
  const previewRef = useRef<HTMLElement>(null);

  const load = useCallback(async (keepSelection: boolean) => {
    const [next, rows] = await Promise.all([fetchMascotEditor(), fetchMascotHistory()]);
    setEditor(next);
    setHistory(rows);
    setConflict(false);
    setSelectedId((previous) =>
      keepSelection && previous !== null ? previous : next.active.asset.id,
    );
  }, []);

  useEffect(() => {
    let alive = true;
    void Promise.all([fetchMascotEditor(), fetchMascotHistory()])
      .then(([next, rows]) => {
        if (!alive) return;
        setEditor(next);
        setHistory(rows);
        setSelectedId(next.active.asset.id);
      })
      .catch(() => {
        if (alive)
          setNotice({
            tone: "error",
            text: "마스코트 설정을 불러오지 못했습니다. 다시 불러오기를 눌러 주세요.",
          });
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

  function choose(id: string) {
    setSelectedId(id);
    setReviewed(false);
    // 한 열 배치에서는 미리보기가 목록 아래에 있어, 고른 결과가 화면 밖에서 바뀐다. 거기로 데려간다.
    if (window.matchMedia("(max-width: 1279px)").matches) {
      const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
      previewRef.current?.scrollIntoView({ behavior: reduce ? "auto" : "smooth", block: "start" });
    }
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
        text: `적용했습니다 · revision ${next.revision} · ${next.assetName}. 다른 방문자에게는 1분 안에 보입니다.`,
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
    if (
      !window.confirm(
        `"${candidate.name}"을(를) 지울까요? 이미지 파일도 함께 폐기되며 되돌릴 수 없습니다.`,
      )
    )
      return;
    setBusy(true);
    try {
      await deleteMascotAsset(candidate.artwork.id);
      if (selectedId === candidate.artwork.id) setSelectedId(activeId);
      await load(true);
      setNotice({ tone: "info", text: `"${candidate.name}"을(를) 지웠습니다.` });
    } catch (error) {
      setNotice({ tone: "error", text: errorText(error, "지우지 못했습니다.") });
    } finally {
      setBusy(false);
    }
  }

  const presetCandidates = candidates.filter((candidate) => candidate.source === "preset");
  const uploadCandidates = candidates.filter((candidate) => candidate.source === "upload");

  return (
    <section className="min-w-0 space-y-8" aria-busy={busy}>
      <header className="space-y-2">
        <p className="text-sm font-medium text-brand-700">사이트 디자인</p>
        <h2 className="text-2xl">마스코트</h2>
        <p className="text-sm text-neutral-600">
          헤더·푸터·홈 히어로·빈 화면의 고래를 고릅니다. 지금까지 그린 고래와 새로 올린 그림 중
          하나를 미리 보고 적용하세요. 배포 없이 바뀌고, 이력에서 언제든 되돌릴 수 있습니다.
        </p>
        <p className="text-xs text-neutral-500">
          {editor === null
            ? "설정 불러오는 중"
            : `revision ${editor.current.revision} · 지금 적용: ${editor.active.asset.name}`}{" "}
          · 파비콘·앱 아이콘·공유 이미지는 빌드 산출물이라 여기서 바뀌지 않습니다.
        </p>
      </header>

      <div className="flex flex-wrap gap-2">
        <Button variant="secondary" size="sm" disabled={busy} onClick={() => void reload()}>
          다시 불러오기
        </Button>
      </div>

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

      <div className="grid min-w-0 gap-6 xl:grid-cols-[minmax(0,1fr)_minmax(300px,360px)]">
        <div className="min-w-0 space-y-6">
          <CandidateGrid
            title="기본 제공 고래"
            description="git 이력에 남은 고래 마크를 다시 그리지 않고 그대로 옮겼습니다."
            candidates={presetCandidates}
            selectedId={selectedId}
            activeId={activeId}
            busy={busy}
            onChoose={choose}
          />
          <CandidateGrid
            title="업로드한 마스코트"
            description={
              uploadCandidates.length === 0
                ? "아직 올린 그림이 없습니다. 아래에서 새 마스코트를 올릴 수 있습니다."
                : "적용 중인 그림은 지울 수 없습니다."
            }
            candidates={uploadCandidates}
            selectedId={selectedId}
            activeId={activeId}
            busy={busy}
            onChoose={choose}
            onRemove={(candidate) => void remove(candidate)}
          />
        </div>

        <aside
          ref={previewRef}
          className="min-w-0 scroll-mt-20 space-y-4 xl:sticky xl:top-24 xl:self-start"
        >
          <PreviewPanel
            candidate={selected}
            description={selectedDescription}
            active={selectedIsActive}
          />
          <div className="space-y-3 rounded-xl border border-neutral-200 bg-white p-4">
            <h3 className="font-semibold">사이트에 적용</h3>
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
          </div>
        </aside>
      </div>

      <UploadForm
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

      <HistoryList
        history={history}
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
        onMore={(rows) => setHistory([...history, ...rows])}
        onError={() => setNotice({ tone: "error", text: "이전 이력을 불러오지 못했습니다." })}
      />
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
    <section className="min-w-0 space-y-3">
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
                    className="flex min-w-0 flex-1 flex-col gap-2 p-3 text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-400"
                    onClick={() => onChoose(id)}
                  >
                    <span className="flex h-20 w-full items-center justify-center rounded-lg bg-neutral-50">
                      <Logo
                        mascot={candidate.artwork}
                        size={96}
                        maxHeight={60}
                        showWordmark={false}
                      />
                    </span>
                    <span className="block min-w-0">
                      <span className="line-clamp-2 text-sm font-semibold break-keep text-neutral-900">
                        {candidate.name}
                      </span>
                      <span className="block truncate text-xs text-neutral-500">
                        {candidate.detail}
                      </span>
                    </span>
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
                        className="text-xs font-medium text-danger disabled:text-neutral-400"
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

function PreviewPanel({
  candidate,
  description,
  active,
}: {
  readonly candidate: Candidate | null;
  readonly description: string;
  readonly active: boolean;
}) {
  if (candidate === null) {
    return (
      <div className="rounded-xl border border-dashed border-neutral-300 bg-white p-6 text-center text-sm text-neutral-500">
        왼쪽에서 마스코트를 고르면 여기서 미리 봅니다.
      </div>
    );
  }
  const artwork = candidate.artwork;
  return (
    <div className="space-y-4 rounded-xl border border-neutral-200 bg-white p-4">
      <div className="space-y-1">
        <div className="flex flex-wrap items-center gap-2">
          <h3 className="min-w-0 font-semibold break-keep">{candidate.name}</h3>
          <Badge tone={candidate.source === "preset" ? "brand" : "neutral"}>
            {candidate.source === "preset" ? "기본 제공" : "업로드"}
          </Badge>
          {active ? <Badge tone="success">적용 중</Badge> : null}
        </div>
        {description.length > 0 ? (
          <p className="text-sm break-keep text-neutral-600">{description}</p>
        ) : null}
      </div>
      <PreviewRow label="헤더">
        <div className="flex h-14 items-center rounded-lg border border-neutral-200 bg-white px-4">
          <Logo mascot={artwork} size={32} className="text-xl text-neutral-900" />
        </div>
      </PreviewRow>
      <PreviewRow label="푸터 (어두운 배경)">
        <div className="flex h-14 items-center rounded-lg bg-brand-950 px-4">
          <Logo
            mascot={artwork}
            size={30}
            tone="inverse"
            className="text-lg text-white"
            accentClassName="text-accent-400"
          />
        </div>
      </PreviewRow>
      <PreviewRow label="홈 히어로">
        <div className="flex h-52 items-end justify-center overflow-hidden rounded-lg bg-brand-50 pb-6">
          <Logo
            mascot={artwork}
            size={200}
            maxHeight={140}
            showWordmark={false}
            spout
            className="gole-mascot-float"
          />
        </div>
      </PreviewRow>
      <PreviewRow label="작은 크기 (16 · 24 · 48px)">
        <div className="flex items-end gap-4 rounded-lg border border-neutral-200 bg-white px-4 py-3">
          <Logo mascot={artwork} size={16} showWordmark={false} />
          <Logo mascot={artwork} size={24} showWordmark={false} />
          <Logo mascot={artwork} size={48} showWordmark={false} />
        </div>
      </PreviewRow>
    </div>
  );
}

function PreviewRow({ label, children }: { readonly label: string; readonly children: ReactNode }) {
  return (
    <figure className="min-w-0 space-y-1.5">
      <figcaption className="text-xs font-medium text-neutral-500">{label}</figcaption>
      {children}
    </figure>
  );
}

interface PickedImage {
  readonly file: File;
  readonly url: string;
  readonly width: number;
  readonly height: number;
}

function readImage(file: File): Promise<PickedImage> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const image = new Image();
    image.onload = () =>
      resolve({ file, url, width: image.naturalWidth, height: image.naturalHeight });
    image.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error("decode"));
    };
    image.src = url;
  });
}

function checkFile(file: File): string | null {
  if (!ACCEPTED_TYPES.includes(file.type)) return "PNG 또는 JPEG 파일만 올릴 수 있습니다.";
  if (file.size > MAX_UPLOAD_BYTES) return "5MB 이하 파일만 올릴 수 있습니다.";
  return null;
}

function checkSize(image: PickedImage): string | null {
  return image.width < MIN_SIDE ||
    image.height < MIN_SIDE ||
    image.width > MAX_SIDE ||
    image.height > MAX_SIDE
    ? `가로·세로 ${MIN_SIDE}~${MAX_SIDE}px 이미지만 올릴 수 있습니다.`
    : null;
}

function UploadForm({
  disabled,
  onUploaded,
  onError,
  setBusy,
}: {
  readonly disabled: boolean;
  readonly onUploaded: (assetId: string, name: string) => Promise<void>;
  readonly onError: (text: string) => void;
  readonly setBusy: (busy: boolean) => void;
}) {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [image, setImage] = useState<PickedImage | null>(null);
  const [darkImage, setDarkImage] = useState<PickedImage | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [formKey, setFormKey] = useState(0);

  // 고른 파일의 미리보기 주소는 바뀌거나 화면을 떠날 때 돌려준다.
  useEffect(() => {
    return () => {
      if (image !== null) URL.revokeObjectURL(image.url);
    };
  }, [image]);
  useEffect(() => {
    return () => {
      if (darkImage !== null) URL.revokeObjectURL(darkImage.url);
    };
  }, [darkImage]);

  async function pick(file: File | undefined, target: "main" | "dark") {
    const set = target === "main" ? setImage : setDarkImage;
    setFileError(null);
    if (file === undefined) {
      set(null);
      return;
    }
    const fileProblem = checkFile(file);
    if (fileProblem !== null) {
      set(null);
      setFileError(fileProblem);
      return;
    }
    try {
      const picked = await readImage(file);
      const sizeProblem = checkSize(picked);
      if (sizeProblem !== null) {
        URL.revokeObjectURL(picked.url);
        set(null);
        setFileError(sizeProblem);
        return;
      }
      set(picked);
    } catch {
      set(null);
      setFileError("이미지를 읽지 못했습니다. 다른 파일을 골라 주세요.");
    }
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (disabled || image === null || name.trim() === "") return;
    setBusy(true);
    try {
      const main = await uploadImage(image.file);
      const dark = darkImage === null ? null : await uploadImage(darkImage.file);
      const asset = await registerMascotAsset({
        name: name.trim(),
        description: description.trim(),
        imageKey: main.key,
        darkImageKey: dark?.key ?? null,
        width: image.width,
        height: image.height,
      });
      setName("");
      setDescription("");
      setImage(null);
      setDarkImage(null);
      setFormKey((key) => key + 1);
      await onUploaded(asset.id, asset.name);
    } catch (error) {
      onError(errorText(error, "올리지 못했습니다. 파일과 연결 상태를 확인해 주세요."));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="min-w-0 space-y-3">
      <div className="space-y-1">
        <h3 className="font-semibold">새 마스코트 올리기</h3>
        <p className="text-sm break-keep text-neutral-600">
          PNG·JPEG, 5MB 이하. 배경이 투명한 PNG를 권장합니다. SVG는 보안상 받지 않습니다. 올린
          뒤에도 적용하기 전까지는 사이트에 보이지 않습니다.
        </p>
      </div>
      <form
        key={formKey}
        className="grid min-w-0 gap-4 rounded-xl border border-neutral-200 bg-white p-4 lg:grid-cols-2"
        onSubmit={(event) => void submit(event)}
      >
        <div className="min-w-0 space-y-4">
          <Field label="이름 (필수)" hint="최대 40자">
            {({ inputId, describedBy }) => (
              <Input
                id={inputId}
                aria-describedby={describedBy}
                maxLength={40}
                value={name}
                disabled={disabled}
                onChange={(event) => setName(event.target.value)}
              />
            )}
          </Field>
          <Field label="설명" hint="최대 200자">
            {({ inputId, describedBy }) => (
              <Textarea
                id={inputId}
                aria-describedby={describedBy}
                rows={2}
                maxLength={200}
                value={description}
                disabled={disabled}
                onChange={(event) => setDescription(event.target.value)}
              />
            )}
          </Field>
          <FileField
            label="마스코트 이미지 (필수)"
            disabled={disabled}
            onPick={(file) => void pick(file, "main")}
          />
          <FileField
            label="어두운 배경용 이미지 (선택)"
            hint="푸터처럼 어두운 배경에서 대신 씁니다. 없으면 위 이미지를 그대로 씁니다."
            disabled={disabled}
            onPick={(file) => void pick(file, "dark")}
          />
          {fileError !== null ? (
            <p role="alert" className="text-sm text-danger">
              {fileError}
            </p>
          ) : null}
        </div>
        <div className="min-w-0 space-y-3">
          <p className="text-xs font-medium text-neutral-500">
            {image === null
              ? "이미지를 고르면 여기서 미리 봅니다."
              : `${image.width}×${image.height}px`}
          </p>
          <div className="grid grid-cols-2 gap-3">
            <LocalPreview image={image} dark={false} />
            <LocalPreview image={darkImage ?? image} dark />
          </div>
          <Button
            type="submit"
            fullWidth
            disabled={disabled || image === null || name.trim() === ""}
          >
            올리기
          </Button>
        </div>
      </form>
    </section>
  );
}

function FileField({
  label,
  hint,
  disabled,
  onPick,
}: {
  readonly label: string;
  readonly hint?: string;
  readonly disabled: boolean;
  readonly onPick: (file: File | undefined) => void;
}) {
  return (
    <Field label={label} {...(hint === undefined ? {} : { hint })}>
      {({ inputId, describedBy }) => (
        <input
          id={inputId}
          aria-describedby={describedBy}
          type="file"
          accept={ACCEPTED_TYPES.join(",")}
          disabled={disabled}
          className="block w-full min-w-0 text-sm text-neutral-700 file:mr-3 file:rounded-md file:border file:border-neutral-300 file:bg-white file:px-3 file:py-2 file:text-sm file:font-medium"
          onChange={(event) => onPick(event.target.files?.[0])}
        />
      )}
    </Field>
  );
}

function LocalPreview({
  image,
  dark,
}: {
  readonly image: PickedImage | null;
  readonly dark: boolean;
}) {
  return (
    <div
      className={cn(
        "flex aspect-[4/3] min-w-0 items-center justify-center rounded-lg p-3",
        dark ? "bg-brand-950" : "border border-neutral-200 bg-neutral-50",
      )}
    >
      {image === null ? (
        <span className={cn("text-xs", dark ? "text-brand-200" : "text-neutral-400")}>
          {dark ? "어두운 배경" : "밝은 배경"}
        </span>
      ) : (
        // 아직 서버에 올리지 않은 로컬 파일(blob:) 미리보기다.
        // eslint-disable-next-line @next/next/no-img-element
        <img src={image.url} alt="" className="max-h-full max-w-full object-contain" />
      )}
    </div>
  );
}

function HistoryList({
  history,
  knownIds,
  busy,
  onPick,
  onMore,
  onError,
}: {
  readonly history: readonly MascotSelection[];
  readonly knownIds: ReadonlySet<string>;
  readonly busy: boolean;
  readonly onPick: (row: MascotSelection) => void;
  readonly onMore: (rows: readonly MascotSelection[]) => void;
  readonly onError: () => void;
}) {
  const [loadingMore, setLoadingMore] = useState(false);
  return (
    <section className="min-w-0 space-y-3">
      <div className="space-y-1">
        <h3 className="font-semibold">적용 이력 · 감사 기록</h3>
        <p className="text-sm text-neutral-600">
          되돌리기는 그 마스코트를 미리보기에 불러올 뿐입니다. 확인하고 적용하면 새 리비전으로
          남습니다.
        </p>
      </div>
      {history.length === 0 ? (
        <p className="text-sm text-neutral-500">
          아직 적용 이력이 없습니다. 기본 고래가 쓰이고 있습니다.
        </p>
      ) : (
        <ul className="space-y-2">
          {history.map((row) => {
            const available = knownIds.has(row.assetId);
            return (
              <li
                key={row.revision}
                className="flex min-w-0 flex-wrap items-center justify-between gap-3 rounded-lg border border-neutral-200 bg-white p-4"
              >
                <div className="min-w-0 flex-1 text-sm">
                  <p className="font-semibold break-keep">
                    revision {row.revision} · {row.assetName}
                  </p>
                  <p className="break-all text-neutral-700">{row.reason}</p>
                  <p className="text-xs break-all text-neutral-500">
                    {formatTime(row.publishedAt)} · 관리자 {row.actorId.slice(0, 8)}
                  </p>
                </div>
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={busy || !available}
                  onClick={() => onPick(row)}
                >
                  {available ? "다시 고르기" : "지운 그림"}
                </Button>
              </li>
            );
          })}
        </ul>
      )}
      {history.length >= 25 ? (
        <Button
          variant="secondary"
          size="sm"
          disabled={busy || loadingMore}
          onClick={() => {
            setLoadingMore(true);
            void fetchMascotHistory(history.at(-1)?.revision)
              .then(onMore)
              .catch(onError)
              .finally(() => setLoadingMore(false));
          }}
        >
          이전 이력 더 보기
        </Button>
      ) : null}
    </section>
  );
}
