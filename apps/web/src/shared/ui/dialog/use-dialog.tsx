"use client";

import {
  useCallback,
  useEffect,
  useId,
  useMemo,
  useRef,
  useState,
  type FormEvent,
  type ReactNode,
} from "react";
import { createPortal } from "react-dom";
import { cn } from "@shared/lib";
import { Button } from "../button";

export interface ConfirmDialogOptions {
  readonly title: string;
  /** 본문. 문자열의 줄바꿈(`\n`)은 그대로 줄을 바꾼다. */
  readonly description?: ReactNode;
  readonly confirmLabel?: string;
  readonly cancelLabel?: string;
  /** `danger`는 되돌리기 어려운 조치다. 확인 버튼을 위험 색으로, 첫 초점을 취소에 둔다. */
  readonly tone?: "default" | "danger";
}

export interface AlertDialogOptions {
  readonly title: string;
  readonly description?: ReactNode;
  readonly confirmLabel?: string;
}

export interface DialogChoice {
  readonly value: string;
  readonly label: string;
  readonly description?: string;
}

export interface PromptDialogOptions extends ConfirmDialogOptions {
  /** 입력란(또는 선택지 묶음)의 이름. */
  readonly label: string;
  readonly defaultValue?: string;
  readonly placeholder?: string;
  /** 있으면 자유 입력 대신 이 중 하나를 고른다. */
  readonly choices?: readonly DialogChoice[];
  /** 문구를 돌려주면 그 이유를 보이고 닫지 않는다. */
  readonly validate?: (value: string) => string | null;
}

type DialogRequest =
  | { readonly kind: "alert"; readonly options: AlertDialogOptions; readonly settle: () => void }
  | {
      readonly kind: "confirm";
      readonly options: ConfirmDialogOptions;
      readonly settle: (value: boolean) => void;
    }
  | {
      readonly kind: "prompt";
      readonly options: PromptDialogOptions;
      readonly settle: (value: string | null) => void;
    };

export interface DialogController {
  /** 확인하면 `true`, 취소·Escape·바깥 클릭이면 `false`. */
  readonly confirm: (options: ConfirmDialogOptions) => Promise<boolean>;
  /** 입력·선택한 값. 취소면 `null`. */
  readonly prompt: (options: PromptDialogOptions) => Promise<string | null>;
  /** 닫히면 끝난다. */
  readonly alert: (options: AlertDialogOptions) => Promise<void>;
  /** 화면에 한 번 그린다. 열린 요청이 없으면 `null`. */
  readonly element: ReactNode;
}

/**
 * 브라우저 기본 대화상자(`alert`·`confirm`·`prompt`)를 대신하는 앱 공통 모달. (browser-dialogs R1)
 *
 * <p>화면마다 훅 하나를 두고 `element`를 그린다. 한 번에 하나만 열리며, 열린 동안 온 요청과 화면이 사라질 때
 * 열려 있던 요청은 취소로 끝난다 — `await`가 매달리거나 같은 조치가 두 번 확인되지 않는다.
 */
export function useDialog(): DialogController {
  // 요청마다 번호를 붙여 모달 본체의 입력값·오류가 새로 시작하게 한다.
  const [visible, setVisible] = useState<{
    readonly id: number;
    readonly request: DialogRequest;
  } | null>(null);
  const openRef = useRef<DialogRequest | null>(null);
  const sequenceRef = useRef(0);
  // 처음 연 버튼. 모달이 이어서 열려도(요청 ID → 사유) 마지막에 닫힐 때 거기로 초점을 돌린다.
  const returnFocusRef = useRef<HTMLElement | null>(null);

  const close = useCallback((value: boolean | string | null) => {
    const current = openRef.current;
    if (current === null) return;
    openRef.current = null;
    setVisible(null);
    window.requestAnimationFrame(() => {
      if (openRef.current !== null) return;
      const target = returnFocusRef.current;
      returnFocusRef.current = null;
      if (target?.isConnected === true) target.focus();
    });
    if (current.kind === "alert") current.settle();
    else if (current.kind === "confirm") current.settle(value === true);
    else current.settle(typeof value === "string" ? value : null);
  }, []);

  useEffect(() => () => close(null), [close]);

  const open = useCallback((next: DialogRequest): boolean => {
    if (openRef.current !== null) return false;
    if (returnFocusRef.current === null && document.activeElement instanceof HTMLElement) {
      returnFocusRef.current = document.activeElement;
    }
    openRef.current = next;
    sequenceRef.current += 1;
    setVisible({ id: sequenceRef.current, request: next });
    return true;
  }, []);

  const confirm = useCallback(
    (options: ConfirmDialogOptions) =>
      new Promise<boolean>((resolve) => {
        if (!open({ kind: "confirm", options, settle: resolve })) resolve(false);
      }),
    [open],
  );
  const prompt = useCallback(
    (options: PromptDialogOptions) =>
      new Promise<string | null>((resolve) => {
        if (!open({ kind: "prompt", options, settle: resolve })) resolve(null);
      }),
    [open],
  );
  const alert = useCallback(
    (options: AlertDialogOptions) =>
      new Promise<void>((resolve) => {
        if (!open({ kind: "alert", options, settle: resolve })) resolve();
      }),
    [open],
  );

  const element =
    visible === null
      ? null
      : createPortal(
          <DialogView key={visible.id} request={visible.request} onClose={close} />,
          document.body,
        );

  return useMemo(() => ({ confirm, prompt, alert, element }), [confirm, prompt, alert, element]);
}

const FOCUSABLE =
  'button:not([disabled]), [href], input:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

function DialogView({
  request,
  onClose,
}: {
  readonly request: DialogRequest;
  readonly onClose: (value: boolean | string | null) => void;
}) {
  const titleId = useId();
  const descriptionId = useId();
  const inputId = useId();
  const errorId = useId();
  const dialogRef = useRef<HTMLFormElement>(null);
  const options = request.options;
  const prompt = request.kind === "prompt" ? request.options : null;
  const [value, setValue] = useState(prompt?.defaultValue ?? "");
  const [error, setError] = useState<string | null>(null);
  const danger = request.kind !== "alert" && request.options.tone === "danger";
  const cancelValue = request.kind === "prompt" ? null : false;

  // 첫 초점은 `data-autofocus`(입력란·고른 선택지·안전한 버튼)에 둔다. 열린 동안 뒤 화면은 스크롤되지 않는다.
  // 닫힌 뒤 초점 복귀는 훅이 맡는다.
  useEffect(() => {
    // 이미 다른 화면이 잠가 두었으면 그 잠금은 그대로 둔다.
    const body = document.body.classList;
    const lockedBefore = body.contains("overflow-hidden");
    body.add("overflow-hidden");
    const dialog = dialogRef.current;
    (dialog?.querySelector<HTMLElement>("[data-autofocus]") ?? dialog)?.focus();
    return () => {
      if (!lockedBefore) body.remove("overflow-hidden");
    };
  }, []);

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        onClose(cancelValue);
        return;
      }
      if (event.key !== "Tab") return;
      const dialog = dialogRef.current;
      if (dialog === null) return;
      // 라디오 묶음은 Tab 순서에서 고른 하나만 한 칸이다. 고른 것이 있으면 나머지는 세지 않는다 — 안 그러면
      // 첫 칸이 아닌 기본 선택에서 Shift+Tab 할 때 가두기가 걸리지 않아 초점이 뒤 화면으로 나간다.
      const focusable = Array.from(dialog.querySelectorAll<HTMLElement>(FOCUSABLE)).filter(
        (element) =>
          !(
            element instanceof HTMLInputElement &&
            element.type === "radio" &&
            !element.checked &&
            dialog.querySelector(
              `input[type="radio"][name="${CSS.escape(element.name)}"]:checked`,
            ) !== null
          ),
      );
      const first = focusable.at(0);
      const last = focusable.at(-1);
      if (first === undefined || last === undefined) {
        event.preventDefault();
        dialog.focus();
      } else if (
        event.shiftKey &&
        (document.activeElement === first || !dialog.contains(document.activeElement))
      ) {
        event.preventDefault();
        last.focus();
      } else if (
        !event.shiftKey &&
        (document.activeElement === last || !dialog.contains(document.activeElement))
      ) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [cancelValue, onClose]);

  function submit(event: FormEvent) {
    event.preventDefault();
    if (request.kind === "alert") return onClose(null);
    if (request.kind === "confirm") return onClose(true);
    const choices = request.options.choices;
    if (choices !== undefined && !choices.some((choice) => choice.value === value)) {
      setError("하나를 골라 주세요.");
      return;
    }
    const problem = request.options.validate?.(value) ?? null;
    if (problem !== null) {
      setError(problem);
      return;
    }
    onClose(value);
  }

  const description = options.description;
  const confirmLabel = options.confirmLabel ?? "확인";
  // 선택지에서는 고른 값(없으면 첫 선택지)에 첫 초점을 둔다.
  const focusedChoice = prompt?.choices?.some((choice) => choice.value === value)
    ? value
    : prompt?.choices?.[0]?.value;

  return (
    <div
      className="fixed inset-0 z-[80] grid place-items-center overflow-y-auto bg-neutral-950/55 px-4 py-8"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose(cancelValue);
      }}
    >
      <form
        ref={dialogRef}
        role={request.kind === "prompt" ? "dialog" : "alertdialog"}
        aria-modal="true"
        aria-labelledby={titleId}
        {...(description === undefined ? {} : { "aria-describedby": descriptionId })}
        tabIndex={-1}
        noValidate
        onSubmit={submit}
        className="flex max-h-full w-full max-w-md min-w-0 flex-col gap-4 overflow-y-auto rounded-2xl bg-white p-5 shadow-2xl outline-none sm:p-6"
      >
        <h2 id={titleId} className="text-lg font-bold break-keep text-neutral-900">
          {options.title}
        </h2>
        {description === undefined ? null : (
          <div
            id={descriptionId}
            className="text-sm leading-relaxed break-keep whitespace-pre-line text-neutral-600"
          >
            {description}
          </div>
        )}

        {prompt === null ? null : prompt.choices === undefined ? (
          <div className="flex flex-col gap-1.5">
            <label htmlFor={inputId} className="text-sm font-medium text-neutral-700">
              {prompt.label}
            </label>
            <input
              id={inputId}
              data-autofocus
              value={value}
              placeholder={prompt.placeholder}
              autoComplete="off"
              spellCheck={false}
              aria-invalid={error !== null}
              {...(error === null ? {} : { "aria-errormessage": errorId })}
              onChange={(event) => {
                setValue(event.target.value);
                setError(null);
              }}
              className="h-11 w-full min-w-0 rounded-md border border-neutral-300 bg-white px-3 text-sm text-neutral-900 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-200"
            />
          </div>
        ) : (
          <fieldset className="flex min-w-0 flex-col gap-2">
            <legend className="mb-1 text-sm font-medium text-neutral-700">{prompt.label}</legend>
            {prompt.choices.map((choice) => (
              <label
                key={choice.value}
                className={cn(
                  "flex cursor-pointer items-start gap-3 rounded-lg border p-3 text-sm",
                  value === choice.value ? "border-brand-400 bg-brand-50" : "border-neutral-200",
                )}
              >
                <input
                  type="radio"
                  name={inputId}
                  value={choice.value}
                  checked={value === choice.value}
                  {...(choice.value === focusedChoice ? { "data-autofocus": true } : {})}
                  onChange={() => {
                    setValue(choice.value);
                    setError(null);
                  }}
                  className="mt-0.5 h-4 w-4 shrink-0 accent-brand-600"
                />
                <span className="min-w-0">
                  <span className="block font-semibold break-all text-neutral-900">
                    {choice.label}
                  </span>
                  {choice.description === undefined ? null : (
                    <span className="block text-xs break-keep text-neutral-500">
                      {choice.description}
                    </span>
                  )}
                </span>
              </label>
            ))}
          </fieldset>
        )}

        {error === null ? null : (
          <p id={errorId} role="alert" className="text-sm text-danger">
            {error}
          </p>
        )}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          {request.kind === "alert" ? null : (
            <Button
              type="button"
              variant="secondary"
              {...(danger && prompt === null ? { "data-autofocus": true } : {})}
              onClick={() => onClose(cancelValue)}
            >
              {request.options.cancelLabel ?? "취소"}
            </Button>
          )}
          <Button
            type="submit"
            variant={danger ? "danger" : "primary"}
            {...(!danger && prompt === null ? { "data-autofocus": true } : {})}
          >
            {confirmLabel}
          </Button>
        </div>
      </form>
    </div>
  );
}
