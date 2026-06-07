import type { ReactNode } from "react";
import { useId } from "react";
import styles from "./field.module.css";

export interface FieldProps {
  readonly label: string;
  readonly hint?: string;
  readonly error?: string | undefined;
  /** label/error를 input과 연결하기 위해 id를 주입받는 render prop. */
  readonly children: (ids: { readonly inputId: string; readonly describedBy: string | undefined }) => ReactNode;
}

export function Field({ label, hint, error, children }: FieldProps) {
  const inputId = useId();
  const messageId = useId();
  const hasMessage = Boolean(error) || Boolean(hint);

  return (
    <div className={styles.field}>
      <label className={styles.label} htmlFor={inputId}>
        {label}
      </label>
      {children({ inputId, describedBy: hasMessage ? messageId : undefined })}
      {error ? (
        <span id={messageId} className={styles.error} role="alert">
          {error}
        </span>
      ) : hint ? (
        <span id={messageId} className={styles.hint}>
          {hint}
        </span>
      ) : null}
    </div>
  );
}
