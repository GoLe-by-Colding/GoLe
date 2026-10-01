"use client";

import { useState } from "react";
import type { AdminPromotionPost } from "@entities/admin";
import { Badge, Button, Heading, MediaImage, Text } from "@shared/ui";
import { PROMOTION_CHANNEL_LABEL, shortCommitSha, shortId } from "../model/labels";

const CATEGORY_LABEL: Readonly<Record<AdminPromotionPost["category"], string>> = {
  FEATURE: "기능 홍보",
  SERVICE: "서비스 홍보",
};

/**
 * 초안을 승인하기 전에 사진과 그 맥락을 함께 보는 패널. 사진마다 어느 화면을 어떻게 찍었는지,
 * 데모 데이터인지를 옆에 붙인다 — 검토자가 "캡션이 이 화면과 맞나"를 판단할 근거다.
 *
 * 체크 항목은 판단을 돕는 참고용이고 저장하지 않는다. 기록은 "평가"에서 한다.
 */
export function PromotionReviewPanel({
  post,
  onClose,
}: {
  readonly post: AdminPromotionPost;
  readonly onClose: () => void;
}) {
  const [index, setIndex] = useState(0);
  const total = post.mediaUrls.length;
  const current = Math.min(index, Math.max(total - 1, 0));
  const url = post.mediaUrls[current];
  const capture = post.captures[current];
  const provenance = post.provenance;

  return (
    <section
      aria-label="홍보 초안 검토 자료"
      className="flex flex-col gap-4 rounded-xl border border-neutral-200 bg-white p-5"
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <Heading level={3}>
          검토 자료 · {PROMOTION_CHANNEL_LABEL[post.channel] ?? post.channel} · {shortId(post.id)}
        </Heading>
        <div className="flex items-center gap-2">
          <Badge tone="brand">{CATEGORY_LABEL[post.category] ?? post.category}</Badge>
          <Button size="sm" variant="secondary" onClick={onClose}>
            닫기
          </Button>
        </div>
      </div>

      <p className="whitespace-pre-wrap break-words text-neutral-800">{post.caption}</p>

      {provenance !== null ? (
        <dl className="grid grid-cols-[6rem_1fr] gap-x-3 gap-y-1 text-sm">
          {post.sourceCommitSha !== null ? (
            <>
              <dt className="text-neutral-500">릴리스</dt>
              <dd className="break-words text-neutral-800">
                {provenance.releaseTitle ?? "제목 없음"}{" "}
                <span className="font-mono text-xs text-neutral-500">
                  ({shortCommitSha(post.sourceCommitSha)})
                </span>
              </dd>
            </>
          ) : null}
          {provenance.rationale !== null ? (
            <>
              <dt className="text-neutral-500">고른 이유</dt>
              <dd className="break-words text-neutral-800">{provenance.rationale}</dd>
            </>
          ) : null}
          {provenance.runUrl !== null ? (
            <>
              <dt className="text-neutral-500">실행 기록</dt>
              <dd>
                <a
                  href={provenance.runUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-brand-700 underline"
                >
                  GitHub Actions 로그 열기
                </a>
              </dd>
            </>
          ) : null}
        </dl>
      ) : (
        <Text size="sm" className="text-neutral-500">
          사람이 직접 쓴 글이라 출처 정보가 없습니다.
        </Text>
      )}

      {url === undefined ? (
        <Text size="sm" className="text-neutral-500">
          첨부된 사진이 없습니다.
        </Text>
      ) : (
        <div className="grid gap-4 md:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
          <div className="flex flex-col gap-2">
            {capture?.originalUrl ? (
              // AI 가 화면을 다시 그렸다 — 글자·숫자·버튼이 바뀌지 않았는지 원본과 나란히 대조한다.
              <div className="grid gap-2 sm:grid-cols-2">
                <figure className="flex flex-col gap-1">
                  <MediaImage
                    src={url}
                    alt={`${capture.label} (다듬은 이미지)`}
                    className="max-h-[420px] w-full rounded-lg border border-neutral-200 object-contain"
                  />
                  <figcaption className="text-xs text-neutral-500">
                    게시될 이미지 (AI로 다듬음)
                  </figcaption>
                </figure>
                <figure className="flex flex-col gap-1">
                  <MediaImage
                    src={capture.originalUrl}
                    alt={`${capture.label} (원본 캡처)`}
                    className="max-h-[420px] w-full rounded-lg border border-neutral-200 object-contain"
                  />
                  <figcaption className="text-xs text-neutral-500">원본 캡처</figcaption>
                </figure>
              </div>
            ) : (
              <MediaImage
                src={url}
                alt={capture?.label ?? `첨부 이미지 ${current + 1}`}
                className="max-h-[480px] w-full rounded-lg border border-neutral-200 object-contain"
              />
            )}
            {total > 1 ? (
              <div className="flex items-center justify-center gap-3">
                <Button
                  size="sm"
                  variant="secondary"
                  disabled={current === 0}
                  onClick={() => setIndex(current - 1)}
                  aria-label="이전 사진"
                >
                  ◀
                </Button>
                <span className="text-sm tabular-nums text-neutral-600">
                  {current + 1} / {total}
                </span>
                <Button
                  size="sm"
                  variant="secondary"
                  disabled={current === total - 1}
                  onClick={() => setIndex(current + 1)}
                  aria-label="다음 사진"
                >
                  ▶
                </Button>
              </div>
            ) : null}
          </div>

          <div className="flex flex-col gap-2 text-sm">
            {capture !== undefined ? (
              <>
                <p className="font-semibold text-neutral-900">
                  {current + 1}. {capture.label}
                </p>
                <p className="text-neutral-600">
                  화면 <span className="font-mono">{capture.route}</span>
                  {capture.actions !== "" ? ` · ${capture.actions}` : ""}
                </p>
                {capture.edit !== null ? (
                  <p className="rounded-md bg-violet-50 px-2 py-1 text-violet-800">
                    AI로 다듬은 이미지입니다. 지시문: {capture.edit}
                  </p>
                ) : null}
                {capture.dataSource === "DEMO" ? (
                  <p className="rounded-md bg-amber-50 px-2 py-1 text-amber-800">
                    데모 데이터로 찍은 화면입니다. 매물·닉네임·가격은 실제가 아닙니다.
                  </p>
                ) : (
                  <p className="rounded-md bg-neutral-50 px-2 py-1 text-neutral-700">
                    운영 사이트에서 찍은 화면입니다. 다른 이용자 정보가 보이는지 확인하세요.
                  </p>
                )}
              </>
            ) : (
              <p className="text-neutral-500">이 사진의 설명표가 없습니다.</p>
            )}
            <fieldset key={current} className="mt-2 flex flex-col gap-1 text-neutral-700">
              <legend className="mb-1 text-xs text-neutral-500">
                확인 (참고용, 저장되지 않음)
              </legend>
              <label className="flex items-center gap-2">
                <input id={`review-match-${post.id}-${current}`} type="checkbox" />
                캡션과 이 화면이 맞다
              </label>
              <label className="flex items-center gap-2">
                <input id={`review-safe-${post.id}-${current}`} type="checkbox" />
                다른 이용자 정보·사실처럼 읽히는 가짜 숫자가 없다
              </label>
              {capture?.originalUrl ? (
                <label className="flex items-center gap-2">
                  <input id={`review-faithful-${post.id}-${current}`} type="checkbox" />
                  원본과 글자·숫자·버튼이 같다
                </label>
              ) : null}
            </fieldset>
          </div>
        </div>
      )}
    </section>
  );
}
