"use client";

import { type FormEvent, useState } from "react";
import { setNickname, validateNickname } from "@entities/user";
import { ApiError } from "@shared/api";
import { Button, Field, Input } from "@shared/ui";

export interface SetNicknameFormProps {
  /** 저장이 끝나면 호출된다. 값은 이 시점에 이미 서버에 영속화돼 있다(D1). */
  readonly onCompleted: () => void;
}

/** 온보딩 1단계 — 닉네임 설정(R3). 중복 여부는 서버만 알 수 있어 응답 메시지를 그대로 보여준다. */
export function SetNicknameForm({ onCompleted }: SetNicknameFormProps) {
  const [nickname, setNicknameValue] = useState("");
  const [error, setError] = useState<string | undefined>(undefined);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const invalid = validateNickname(nickname);
    if (invalid !== null) {
      setError(invalid);
      return;
    }
    setError(undefined);
    setSubmitting(true);
    try {
      await setNickname(nickname.trim());
      onCompleted();
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "닉네임 설정 중 오류가 발생했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
      {error ? (
        <p className="p-3 rounded-md bg-danger-soft text-danger text-sm" role="alert">
          {error}
        </p>
      ) : null}
      <Field label="닉네임" hint="2~12자의 한글·영문·숫자만 사용할 수 있어요.">
        {({ inputId, describedBy }) => (
          <Input
            id={inputId}
            autoComplete="nickname"
            value={nickname}
            placeholder="브릭러버"
            maxLength={12}
            aria-describedby={describedBy}
            onChange={(e) => setNicknameValue(e.target.value)}
            required
          />
        )}
      </Field>
      <Button type="submit" size="lg" fullWidth disabled={submitting}>
        {submitting ? "저장 중..." : "다음"}
      </Button>
    </form>
  );
}
