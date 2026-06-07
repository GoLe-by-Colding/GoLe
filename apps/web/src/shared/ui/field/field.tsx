import type { ReactNode } from "react";
import { useId } from "react";

export interface FieldProps {
  readonly label: string;
  readonly hint?: string;
  readonly error?: string | undefined;
  /** label/error를 input과 연결하기 위해 id를 주입받는 render prop. */
  readonly children: (ids: {
    readonly inputId: string;
    readonly describedBy: string | undefined;
  }) => ReactNode;
}

export function Field({ label, hint, error, children }: FieldProps) {
  const inputId = useId();
  const messageId = useId();
  const hasMessage = Boolean(error) || Boolean(hint);

  return (
    <div className="flex flex-col gap-2">
      <label className="text-sm font-medium text-neutral-600" htmlFor={inputId}>
        {label}
      </label>
      {children({ inputId, describedBy: hasMessage ? messageId : undefined })}
      {error ? (
        <span id={messageId} className="text-xs text-danger" role="alert">
          {error}
        </span>
      ) : hint ? (
        <span id={messageId} className="text-xs text-neutral-500">
          {hint}
        </span>
      ) : null}
    </div>
  );
}
