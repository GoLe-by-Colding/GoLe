"use client";

import { useRouter } from "next/navigation";
import { type FormEvent, useState } from "react";
import { changePassword, clearSession, useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import {
  BackButton,
  Button,
  Card,
  Container,
  Field,
  Heading,
  Input,
  LinkButton,
  Text,
} from "@shared/ui";

export function AccountSecurityPage() {
  const router = useRouter();
  const { session } = useSession();
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (session === null) {
    return (
      <Container width="sm">
        <div className="flex flex-col items-start gap-4 py-12">
          <Heading level={1}>계정 보안</Heading>
          <Text tone="secondary">비밀번호를 바꾸려면 로그인이 필요합니다.</Text>
          <LinkButton href="/login?returnTo=%2Fprofile%2Fsecurity">로그인하러 가기</LinkButton>
        </div>
      </Container>
    );
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    if (newPassword !== confirmation) {
      setError("새 비밀번호가 서로 일치하지 않습니다.");
      return;
    }
    setSubmitting(true);
    try {
      await changePassword(currentPassword, newPassword);
      clearSession();
      router.replace("/login?passwordChanged=1&returnTo=%2Fprofile");
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "비밀번호를 변경하지 못했어요.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Container width="sm">
      <div className="flex flex-col gap-6 py-10 pb-16">
        <BackButton fallbackHref="/profile" />
        <div className="flex flex-col gap-2">
          <Heading level={1}>계정 보안</Heading>
          <Text tone="secondary">변경하면 현재 기기를 포함한 모든 기기에서 로그아웃됩니다.</Text>
        </div>
        <Card padded>
          <form className="flex flex-col gap-4" onSubmit={submit} noValidate>
            {error ? (
              <p role="alert" className="rounded-md bg-danger-soft p-3 text-sm text-danger">
                {error}
              </p>
            ) : null}
            <Field label="현재 비밀번호">
              {({ inputId, describedBy }) => (
                <Input
                  id={inputId}
                  type="password"
                  autoComplete="current-password"
                  value={currentPassword}
                  aria-describedby={describedBy}
                  onChange={(event) => setCurrentPassword(event.target.value)}
                  required
                />
              )}
            </Field>
            <Field label="새 비밀번호" hint="8자 이상, UTF-8 기준 72바이트 이하">
              {({ inputId, describedBy }) => (
                <Input
                  id={inputId}
                  type="password"
                  autoComplete="new-password"
                  value={newPassword}
                  aria-describedby={describedBy}
                  onChange={(event) => setNewPassword(event.target.value)}
                  required
                />
              )}
            </Field>
            <Field label="새 비밀번호 확인">
              {({ inputId, describedBy }) => (
                <Input
                  id={inputId}
                  type="password"
                  autoComplete="new-password"
                  value={confirmation}
                  aria-describedby={describedBy}
                  onChange={(event) => setConfirmation(event.target.value)}
                  required
                />
              )}
            </Field>
            <Button
              type="submit"
              size="lg"
              fullWidth
              disabled={submitting || currentPassword.length === 0 || newPassword.length < 8}
            >
              {submitting ? "변경 중…" : "비밀번호 변경"}
            </Button>
          </form>
        </Card>
      </div>
    </Container>
  );
}
