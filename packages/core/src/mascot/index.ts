import { apiRequest } from "../runtime";

/**
 * 사이트 마스코트(고래 마크) — 기본 제공 에셋 메타데이터와 공개·관리 API. (mascot-assets)
 *
 * <p>그림은 각 플랫폼이 가진다(웹 `shared/ui/logo/presets/`). 키 목록은 서버 `MascotPresets`와 같아야 한다.
 */
export const MASCOT_PRESET_KEYS = [
  "side-brick",
  "side-brick-original",
  "baby-round",
  "front-stud-head",
  "tail-up",
  "kawaii-brick",
  "chubby",
  "flat-silhouette",
  "blocky",
  "three-studs",
  "fountain",
  "first-sketch",
] as const;

export type MascotPresetKey = (typeof MASCOT_PRESET_KEYS)[number];

export interface MascotPresetMeta {
  readonly key: MascotPresetKey;
  readonly name: string;
  readonly description: string;
  /** 그림을 처음 넣은 커밋과 날짜. 다시 그리지 않고 그때 SVG를 그대로 옮겼다. */
  readonly commit: string;
  readonly date: string;
}

export const DEFAULT_MASCOT_KEY: MascotPresetKey = "side-brick";

export const MASCOT_PRESETS: readonly MascotPresetMeta[] = [
  {
    key: "side-brick",
    name: "옆모습 브릭 고래",
    description:
      "지금 기본 고래. 원본 옆모습을 둥글게 다듬고 가운데 골드 스터드에서 분수가 솟는다.",
    commit: "5cc1f3da",
    date: "2026-10-09",
  },
  {
    key: "side-brick-original",
    name: "원본 브릭 고래",
    description: "3단 브릭 면과 힌지 V 꼬리. 홈 히어로 분수 흐름을 처음 만든 옆모습 원본.",
    commit: "9f581c70",
    date: "2026-08-30",
  },
  {
    key: "baby-round",
    name: "둥근 아기 고래",
    description: "정면을 보는 통통한 아기 고래. 작은 눈과 작은 미소, 정수리 골드 스터드.",
    commit: "daa6cb9a",
    date: "2026-10-09",
  },
  {
    key: "front-stud-head",
    name: "정면 스터드 머리 고래",
    description: "정수리 골드 스터드와 머리 뒤로 솟은 꼬리, 넓은 웃음의 정면 고래.",
    commit: "f403ade0",
    date: "2026-10-09",
  },
  {
    key: "tail-up",
    name: "꼬리 든 고래",
    description: "꼬리를 높이 든 매끈한 옆모습. 등에 스터드 세 개, 가운데만 골드.",
    commit: "6734f0f8",
    date: "2026-10-09",
  },
  {
    key: "kawaii-brick",
    name: "카와이 브릭 고래",
    description: "평평한 브릭 등에 큰 눈·하이라이트·미소를 더한 고래.",
    commit: "d29b52d9",
    date: "2026-06-14",
  },
  {
    key: "chubby",
    name: "통통 고래",
    description: "둥근 몸에 골드 스터드 하나를 얹은 통통한 마스코트.",
    commit: "11070a52",
    date: "2026-06-13",
  },
  {
    key: "flat-silhouette",
    name: "한 획 플랫 고래",
    description: "한 획 실루엣과 시그니처 골드 스터드만 남긴 플랫 고래.",
    commit: "e8b0bcc3",
    date: "2026-06-10",
  },
  {
    key: "blocky",
    name: "블로키 브릭 고래",
    description: "둥근 사각형 브릭 몸통에 스터드 세 개와 골드 분수.",
    commit: "08924228",
    date: "2026-06-10",
  },
  {
    key: "three-studs",
    name: "스터드 세 개 고래",
    description: "납작한 등 위 스터드 세 개, 코스 심과 미소, 위로 뻗은 꼬리.",
    commit: "6fc65282",
    date: "2026-06-10",
  },
  {
    key: "fountain",
    name: "분수 고래",
    description: "몸통·꼬리·지느러미·눈을 다듬고 스터드에서 골드 분수가 솟는 고래.",
    commit: "ca295042",
    date: "2026-06-10",
  },
  {
    key: "first-sketch",
    name: "첫 고래",
    description: "처음 그린 고래 — 실루엣과 스터드 두 개, 골드 물줄기.",
    commit: "e303afb4",
    date: "2026-06-09",
  },
];

export function isMascotPresetKey(value: unknown): value is MascotPresetKey {
  return typeof value === "string" && (MASCOT_PRESET_KEYS as readonly string[]).includes(value);
}

export function mascotPresetMeta(key: MascotPresetKey): MascotPresetMeta {
  return (
    MASCOT_PRESETS.find((preset) => preset.key === key) ?? (MASCOT_PRESETS[0] as MascotPresetMeta)
  );
}

export interface PresetMascot {
  readonly kind: "PRESET";
  readonly id: MascotPresetKey;
  readonly name: string;
}

export interface UploadedMascot {
  readonly kind: "UPLOAD";
  readonly id: string;
  readonly name: string;
  /** 같은 원점 공개 경로(`/api/v1/media/...`). */
  readonly imageUrl: string;
  readonly darkImageUrl: string | null;
  readonly width: number;
  readonly height: number;
}

export type MascotArtwork = PresetMascot | UploadedMascot;

export interface ActiveMascot {
  readonly revision: number;
  readonly asset: MascotArtwork;
}

export const DEFAULT_MASCOT: PresetMascot = Object.freeze({
  kind: "PRESET",
  id: DEFAULT_MASCOT_KEY,
  name: "옆모습 브릭 고래",
});

export const DEFAULT_ACTIVE_MASCOT: ActiveMascot = Object.freeze({
  revision: 0,
  asset: DEFAULT_MASCOT,
});

const MEDIA_PATH = /^\/api\/v1\/media\/images\/[0-9a-f-]{36}\.(?:png|jpg)$/;

function mediaPath(value: unknown): string | null {
  return typeof value === "string" && MEDIA_PATH.test(value) ? value : null;
}

function side(value: unknown): number | null {
  return typeof value === "number" && Number.isInteger(value) && value >= 16 && value <= 4096
    ? value
    : null;
}

/** 서버 응답의 에셋 하나를 읽는다. 모르는 모양이면 null — 호출부가 기본 고래로 떨어진다. */
export function parseMascotArtwork(input: unknown): MascotArtwork | null {
  if (input === null || typeof input !== "object") return null;
  const value = input as Record<string, unknown>;
  const name = typeof value.name === "string" ? value.name.slice(0, 40) : "";
  if (value.kind === "PRESET") {
    return isMascotPresetKey(value.id)
      ? { kind: "PRESET", id: value.id, name: name || mascotPresetMeta(value.id).name }
      : null;
  }
  if (value.kind === "UPLOAD" && typeof value.id === "string" && value.id.length > 0) {
    const imageUrl = mediaPath(value.imageUrl);
    const width = side(value.width);
    const height = side(value.height);
    if (imageUrl === null || width === null || height === null) return null;
    return {
      kind: "UPLOAD",
      id: value.id,
      name,
      imageUrl,
      darkImageUrl: mediaPath(value.darkImageUrl),
      width,
      height,
    };
  }
  return null;
}

/** 공개 응답을 읽는다. 어떤 실패도 기본 고래로 돌아간다(R4.3). */
export function parseActiveMascot(payload: unknown): ActiveMascot {
  if (payload === null || typeof payload !== "object") return DEFAULT_ACTIVE_MASCOT;
  const value = payload as Record<string, unknown>;
  const revision =
    typeof value.revision === "number" && Number.isSafeInteger(value.revision) ? value.revision : 0;
  const asset = parseMascotArtwork(value.asset);
  return asset === null ? DEFAULT_ACTIVE_MASCOT : { revision, asset };
}

const MASCOT_TIMEOUT_MS = 2_500;

/**
 * 사이트가 지금 그릴 마스코트. 서버 컴포넌트는 `revalidate`로 웹 서버 캐시를 쓰고(다른 방문자에게 최대 그 초만큼 늦게 보인다),
 * 브라우저 갱신은 `no-store`로 바로 받는다. 실패하면 기본 고래다.
 */
export async function fetchActiveMascot(
  options: { readonly revalidate?: number } = {},
): Promise<ActiveMascot> {
  try {
    const payload = await apiRequest<unknown>("/api/v1/config/mascot", {
      ...(options.revalidate === undefined
        ? { cache: "no-store" as const }
        : { next: { revalidate: options.revalidate, tags: ["mascot"] } }),
      signal: AbortSignal.timeout(MASCOT_TIMEOUT_MS),
    });
    return parseActiveMascot(payload);
  } catch {
    return DEFAULT_ACTIVE_MASCOT;
  }
}

// ── 관리자 ──────────────────────────────────────────────────────────────

export interface MascotSelection {
  readonly revision: number;
  readonly assetId: string;
  readonly assetName: string;
  readonly actorId: string;
  readonly reason: string;
  readonly action: string;
  readonly publishedAt: string;
}

export interface MascotUpload {
  readonly id: string;
  readonly name: string;
  readonly description: string;
  readonly imageKey: string;
  readonly imageUrl: string;
  readonly darkImageKey: string | null;
  readonly darkImageUrl: string | null;
  readonly width: number;
  readonly height: number;
  readonly createdBy: string;
  readonly createdAt: string;
}

export interface MascotEditor {
  readonly current: MascotSelection;
  readonly active: ActiveMascot;
  readonly uploads: readonly MascotUpload[];
}

/** 업로드 에셋을 미리보기·적용 대상 모양으로 바꾼다. */
export function uploadArtwork(upload: MascotUpload): MascotArtwork {
  return {
    kind: "UPLOAD",
    id: upload.id,
    name: upload.name,
    imageUrl: upload.imageUrl,
    darkImageUrl: upload.darkImageUrl,
    width: upload.width,
    height: upload.height,
  };
}

export async function fetchMascotEditor(): Promise<MascotEditor> {
  const raw = await apiRequest<{
    current: MascotSelection;
    active: unknown;
    uploads: MascotUpload[];
  }>("/api/admin/mascot", { cache: "no-store" });
  return { current: raw.current, active: parseActiveMascot(raw.active), uploads: raw.uploads };
}

export const fetchMascotHistory = (before?: number) =>
  apiRequest<MascotSelection[]>(
    `/api/admin/mascot/history${before === undefined ? "" : `?before=${before}`}`,
    { cache: "no-store" },
  );

export interface RegisterMascotInput {
  readonly name: string;
  readonly description: string;
  readonly imageKey: string;
  readonly darkImageKey: string | null;
  readonly width: number;
  readonly height: number;
}

export const registerMascotAsset = (input: RegisterMascotInput) =>
  apiRequest<MascotUpload>("/api/admin/mascot/assets", { method: "POST", body: input });

export const publishMascot = (expectedRevision: number, assetId: string, reason: string) =>
  apiRequest<MascotSelection>("/api/admin/mascot/publish", {
    method: "POST",
    body: { expectedRevision, assetId, reason },
  });

export const deleteMascotAsset = (assetId: string) =>
  apiRequest<void>(`/api/admin/mascot/assets/${encodeURIComponent(assetId)}`, {
    method: "DELETE",
  });

/** 관리자 화면이 적용에 성공하면 쏘는 이벤트. 같은 탭의 마스코트가 바로 바뀐다. */
export const MASCOT_PUBLISHED_EVENT = "gole:mascot-published";
