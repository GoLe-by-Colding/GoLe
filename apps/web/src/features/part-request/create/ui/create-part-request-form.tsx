"use client";

import { type FormEvent, useRef, useState } from "react";
import {
  createPartRequest,
  PART_REQUEST_ERROR,
  PART_REQUEST_RULES,
  validatePartRequest,
  type PartRequest,
  type WantedPart,
} from "@entities/part-request";
import { ApiError, isOnboardingRequiredError } from "@shared/api";
import { Button, Field, Input, Textarea } from "@shared/ui";

export interface CreatePartRequestFormProps {
  /** `/parts/new?set=` 로 들어오면 미리 채운다. 비워 둘 수 있다. */
  readonly initialSetNumber?: string;
  readonly onCreated: (request: PartRequest) => void;
}

interface Row {
  readonly key: number;
  readonly partNumber: string;
  readonly colorName: string;
  readonly quantity: string;
}

/** 부품 번호 · 색상 · 수량 · 삭제. 좁은 화면에서도 한 줄로 둔다 — 줄이 접히면 어느 칸이 어느 부품인지 헷갈린다. */
const ROW_GRID = "grid grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)_4.5rem_2.75rem] gap-2";

function emptyRow(key: number): Row {
  return { key, partNumber: "", colorName: "", quantity: "1" };
}

/** 빈 칸은 NaN이 되어 수량 검사에서 걸린다 — 0으로 바꿔 통과시키지 않는다. */
function toWantedPart(row: Row): WantedPart {
  const quantity = row.quantity.trim() === "" ? Number.NaN : Number(row.quantity);
  return { partNumber: row.partNumber, colorName: row.colorName, quantity };
}

function submitErrorMessage(cause: unknown): {
  readonly field?: "setNumber";
  readonly message: string;
} {
  if (cause instanceof ApiError) {
    if (cause.code === PART_REQUEST_ERROR.SET_NOT_FOUND) {
      return {
        field: "setNumber",
        message: "카탈로그에 없는 세트 번호예요. 번호를 확인하거나 비워 두세요.",
      };
    }
    if (cause.code === PART_REQUEST_ERROR.LIMIT_EXCEEDED) {
      return {
        message: "열린 요청이 이미 10건이에요. 해결된 요청을 마감한 뒤 다시 올려 주세요.",
      };
    }
    return { message: cause.message };
  }
  return { message: "요청을 올리지 못했어요. 잠시 후 다시 시도해 주세요." };
}

/**
 * 부족 부품 요청 작성 폼. (wanted-parts W1·W2)
 *
 * 서버 규칙(W1)을 코어 `validatePartRequest`로 먼저 검사해 어느 칸이 틀렸는지 바로 보여준다.
 * 서버가 최종 판정하므로 여기서 통과해도 서버 오류는 그대로 보여준다.
 */
export function CreatePartRequestForm({
  initialSetNumber = "",
  onCreated,
}: CreatePartRequestFormProps) {
  const [setNumber, setSetNumber] = useState(initialSetNumber);
  const [rows, setRows] = useState<readonly Row[]>(() => [emptyRow(0)]);
  const [note, setNote] = useState("");
  const [showErrors, setShowErrors] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [setNumberError, setSetNumberError] = useState<string | null>(null);
  const nextKey = useRef(1);
  const submitLock = useRef(false);

  const input = { setNumber, items: rows.map(toWantedPart), note };
  const validation = validatePartRequest(input);
  const canAddRow = rows.length < PART_REQUEST_RULES.maxItems;

  function updateRow(key: number, patch: Partial<Omit<Row, "key">>) {
    setRows((current) => current.map((row) => (row.key === key ? { ...row, ...patch } : row)));
  }

  function addRow() {
    if (!canAddRow) return;
    const key = nextKey.current;
    nextKey.current += 1;
    setRows((current) => [...current, emptyRow(key)]);
  }

  function removeRow(key: number) {
    setRows((current) =>
      current.length <= 1 ? current : current.filter((row) => row.key !== key),
    );
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitLock.current) return;
    setShowErrors(true);
    setError(null);
    setSetNumberError(null);
    if (!validation.valid) {
      setError("빨간 칸을 확인해 주세요.");
      return;
    }
    submitLock.current = true;
    setSubmitting(true);
    try {
      const created = await createPartRequest(input);
      onCreated(created);
    } catch (cause) {
      submitLock.current = false;
      setSubmitting(false);
      // 온보딩 미완료는 공용 래퍼가 온보딩 화면으로 보낸다. 여기서 오류를 덧붙이지 않는다.
      if (isOnboardingRequiredError(cause)) return;
      const { field, message } = submitErrorMessage(cause);
      if (field === "setNumber") setSetNumberError(message);
      else setError(message);
    }
  }

  const setNumberMessage = setNumberError ?? (showErrors ? validation.setNumber : undefined);
  const noteMessage = showErrors ? validation.note : undefined;

  return (
    <form className="flex flex-col gap-6" onSubmit={handleSubmit} noValidate>
      {error !== null ? (
        <p role="alert" className="rounded-md bg-danger-soft p-3 text-sm text-danger">
          {error}
        </p>
      ) : null}

      <Field
        label="세트 번호 (선택)"
        hint="세트를 적으면 그 세트를 가진 회원에게 알림이 가요."
        error={setNumberMessage}
      >
        {({ inputId, describedBy }) => (
          <Input
            id={inputId}
            value={setNumber}
            placeholder="예: 10307"
            inputMode="text"
            autoComplete="off"
            maxLength={PART_REQUEST_RULES.maxSetNumberLength}
            aria-describedby={describedBy}
            invalid={setNumberMessage !== undefined}
            onChange={(event) => {
              setSetNumber(event.target.value);
              setSetNumberError(null);
            }}
          />
        )}
      </Field>

      <div role="group" aria-labelledby="part-rows-label" className="flex flex-col gap-3">
        <div className="flex items-baseline justify-between gap-2">
          <span id="part-rows-label" className="text-sm font-medium text-neutral-600">
            찾는 부품
          </span>
          <span className="text-xs text-neutral-400">
            {rows.length}/{PART_REQUEST_RULES.maxItems}
          </span>
        </div>
        <div aria-hidden="true" className={`${ROW_GRID} px-1 text-xs text-neutral-500`}>
          <span>부품 번호</span>
          <span>색상</span>
          <span>수량</span>
          <span />
        </div>
        <ol className="flex flex-col gap-3">
          {rows.map((row, index) => {
            const errors = showErrors ? validation.items[index] : undefined;
            const position = index + 1;
            const messages = [errors?.partNumber, errors?.colorName, errors?.quantity].filter(
              (message): message is string => message !== undefined,
            );
            const errorId = `part-row-${row.key}-error`;
            const describedBy = messages.length > 0 ? errorId : undefined;
            return (
              <li key={row.key} className="flex flex-col gap-1.5" data-testid="part-row">
                <div className={ROW_GRID}>
                  <Input
                    aria-label={`${position}번째 부품 번호`}
                    value={row.partNumber}
                    placeholder="3062b"
                    autoComplete="off"
                    autoCapitalize="none"
                    spellCheck={false}
                    className="px-2 font-mono"
                    invalid={errors?.partNumber !== undefined}
                    aria-describedby={describedBy}
                    onChange={(event) => updateRow(row.key, { partNumber: event.target.value })}
                  />
                  <Input
                    aria-label={`${position}번째 부품 색상`}
                    value={row.colorName}
                    placeholder="검정"
                    autoComplete="off"
                    className="px-2"
                    maxLength={PART_REQUEST_RULES.maxColorNameLength}
                    invalid={errors?.colorName !== undefined}
                    aria-describedby={describedBy}
                    onChange={(event) => updateRow(row.key, { colorName: event.target.value })}
                  />
                  <Input
                    aria-label={`${position}번째 부품 수량`}
                    type="number"
                    inputMode="numeric"
                    min={PART_REQUEST_RULES.minQuantity}
                    max={PART_REQUEST_RULES.maxQuantity}
                    step={1}
                    className="px-2 tabular-nums"
                    value={row.quantity}
                    invalid={errors?.quantity !== undefined}
                    aria-describedby={describedBy}
                    onChange={(event) => updateRow(row.key, { quantity: event.target.value })}
                  />
                  <Button
                    variant="ghost"
                    className="h-11 px-0 text-lg"
                    disabled={rows.length <= 1}
                    aria-label={`${position}번째 부품 줄 삭제`}
                    onClick={() => removeRow(row.key)}
                  >
                    <span aria-hidden="true">×</span>
                  </Button>
                </div>
                {messages.length > 0 ? (
                  <p id={errorId} className="text-xs text-danger">
                    {messages.join(" ")}
                  </p>
                ) : null}
              </li>
            );
          })}
        </ol>
        {showErrors && validation.itemsError !== undefined ? (
          <p className="text-xs text-danger">{validation.itemsError}</p>
        ) : null}
        <Button
          variant="secondary"
          size="sm"
          className="self-start"
          disabled={!canAddRow}
          onClick={addRow}
        >
          + 부품 줄 추가
        </Button>
      </div>

      <Field
        label="메모 (선택)"
        hint={`${note.trim().length}/${PART_REQUEST_RULES.maxNoteLength} · 직거래 지역, 대체 색상 허용 여부 등을 적어 주세요.`}
        error={noteMessage}
      >
        {({ inputId, describedBy }) => (
          <Textarea
            id={inputId}
            value={note}
            rows={4}
            aria-describedby={describedBy}
            invalid={noteMessage !== undefined}
            placeholder="예: 판교 직거래 가능해요. 짙은 회색도 괜찮아요."
            onChange={(event) => setNote(event.target.value)}
          />
        )}
      </Field>

      <Button type="submit" size="lg" fullWidth disabled={submitting}>
        {submitting ? "올리는 중…" : "부품 요청 올리기"}
      </Button>
    </form>
  );
}
