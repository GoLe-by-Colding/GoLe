import { useState, type ReactNode } from "react";
import type { MascotArtwork } from "@gole/core/mascot";
import { Badge, Button, Logo } from "@shared/ui";

export interface MascotCandidate {
  readonly artwork: MascotArtwork;
  readonly name: string;
  readonly detail: string;
  readonly source: "preset" | "upload";
}

/** 선택은 로컬 미리보기다. 실제 적용본은 서버에서 읽은 artwork로만 그린다. */
export function MascotPreviewPanel({
  candidate,
  description,
  current,
  children,
}: {
  readonly candidate: MascotCandidate;
  readonly description: string;
  readonly current: MascotArtwork;
  readonly children: ReactNode;
}) {
  const [scene, setScene] = useState<"mark" | "hero">("mark");
  const active = candidate.artwork.id === current.id;
  const artwork = candidate.artwork;
  return (
    <div className="min-w-0 space-y-5">
      <div className="flex min-w-0 flex-col items-start justify-between gap-3 sm:flex-row">
        <div className="w-full min-w-0 space-y-2 sm:flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <p className="text-sm font-medium text-neutral-600">선택한 마스코트</p>
            <Badge tone={active ? "success" : "brand"}>{active ? "현재 적용본" : "적용 전"}</Badge>
          </div>
          <h3
            id="mascot-preview-title"
            tabIndex={-1}
            className="scroll-mt-24 rounded text-xl font-bold [overflow-wrap:anywhere] focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-brand-600"
          >
            {candidate.name}
          </h3>
        </div>
        {children}
      </div>

      <p role="status" className="text-sm break-keep text-neutral-600">
        {active
          ? "지금 사이트에 보이는 마스코트입니다. 다른 후보와 비교해 보세요."
          : "아직 사이트에 적용하지 않은 미리보기입니다."}
      </p>

      <div className="grid min-w-0 gap-5 xl:grid-cols-[minmax(0,1fr)_minmax(240px,0.8fr)]">
        <div className="min-w-0 space-y-3">
          <div role="group" aria-label="큰 그림 보기" className="flex flex-wrap gap-2">
            <Button
              variant={scene === "mark" ? "primary" : "secondary"}
              size="sm"
              aria-pressed={scene === "mark"}
              onClick={() => setScene("mark")}
            >
              일반 로고
            </Button>
            <Button
              variant={scene === "hero" ? "primary" : "secondary"}
              size="sm"
              aria-pressed={scene === "hero"}
              onClick={() => setScene("hero")}
            >
              홈 히어로
            </Button>
          </div>
          <figure aria-label="선택한 마스코트 큰 미리보기" className="min-w-0">
            <div className="flex h-48 items-center justify-center rounded-xl bg-neutral-50 px-4 sm:h-64">
              <Logo
                mascot={artwork}
                size={360}
                maxHeight={200}
                showWordmark={false}
                spout={scene === "hero"}
                className="max-w-full [&_img]:h-auto [&_img]:max-w-full [&_svg]:h-auto [&_svg]:max-w-full"
              />
            </div>
            <figcaption className="mt-3 text-sm leading-relaxed break-keep text-neutral-600 [overflow-wrap:anywhere]">
              {description || "올린 그림을 밝은 배경과 어두운 배경, 작은 크기로 확인하세요."}
            </figcaption>
          </figure>
          {scene === "hero" ? (
            <p className="text-xs break-keep text-neutral-500">
              브릭 분수는 해당 그림에 있는 경우에만 보입니다.
            </p>
          ) : null}
          <details className="text-xs text-neutral-500">
            <summary className="w-fit cursor-pointer rounded py-2 hover:text-neutral-900 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-600">
              {candidate.source === "preset" ? "이 그림의 출처" : "업로드 정보"}
            </summary>
            <p className="pt-1 [overflow-wrap:anywhere]">{candidate.detail}</p>
          </details>
        </div>

        <div className="min-w-0 space-y-4">
          <figure
            aria-label="현재 적용 중인 마스코트"
            className="flex min-w-0 items-center gap-4 border-b border-neutral-200 pb-4"
          >
            <div className="flex w-20 shrink-0 justify-center">
              <Logo mascot={current} size={80} maxHeight={56} showWordmark={false} />
            </div>
            <figcaption className="min-w-0 space-y-1">
              <p className="text-xs text-neutral-500">현재 사이트에 적용 중</p>
              <p className="text-sm font-semibold [overflow-wrap:anywhere]">{current.name}</p>
            </figcaption>
          </figure>

          <div className="grid min-w-0 gap-4 sm:grid-cols-2 xl:grid-cols-1">
            <PreviewRow label="헤더">
              <div className="flex h-16 items-center border-y border-neutral-200 bg-white px-3">
                <Logo mascot={artwork} size={40} className="text-2xl text-neutral-900" />
              </div>
            </PreviewRow>
            <PreviewRow label="푸터 (어두운 배경)">
              <div className="flex h-16 items-center rounded-lg bg-brand-950 px-3">
                <Logo
                  mascot={artwork}
                  size={30}
                  tone="inverse"
                  className="text-lg text-white"
                  accentClassName="text-accent-400"
                />
              </div>
            </PreviewRow>
            <PreviewRow label="작은 크기 (16 · 24 · 48px)">
              <div className="flex min-h-16 items-end gap-6 py-2">
                {[16, 24, 48].map((size) => (
                  <span key={size} className="flex flex-col items-center gap-2">
                    <Logo mascot={artwork} size={size} showWordmark={false} />
                    <span className="text-xs text-neutral-500 tabular-nums">{size}px</span>
                  </span>
                ))}
              </div>
            </PreviewRow>
          </div>
        </div>
      </div>
    </div>
  );
}

function PreviewRow({ label, children }: { readonly label: string; readonly children: ReactNode }) {
  return (
    <figure className="min-w-0 space-y-2">
      <figcaption className="text-xs font-medium text-neutral-600">{label}</figcaption>
      {children}
    </figure>
  );
}
