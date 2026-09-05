"use client";

import { useRouter } from "next/navigation";
import { type FormEvent, useEffect, useState } from "react";
import {
  acceptThirdPartyProvisionConsent,
  changePassword,
  clearAccountBrowserStorage,
  clearSession,
  fetchThirdPartyProvisionConsentStatus,
  requestAccountDeletion,
  requestAccountDeletionVerification,
  ThirdPartyProvisionNotice,
  THIRD_PARTY_PROVISION_VERSION_STALE_CODE,
  type ThirdPartyProvisionConsentStatus,
  useSession,
  withdrawThirdPartyProvisionConsent,
} from "@entities/user";
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
  const sessionAccountId = session?.accountId ?? null;
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [consentStatus, setConsentStatus] = useState<ThirdPartyProvisionConsentStatus | null>(null);
  const [consentOwnerId, setConsentOwnerId] = useState<string | null>(null);
  const [consentLoading, setConsentLoading] = useState(true);
  const [consentBusy, setConsentBusy] = useState(false);
  const [consentChecked, setConsentChecked] = useState(false);
  const [consentError, setConsentError] = useState<string | null>(null);
  const [consentNotice, setConsentNotice] = useState<string | null>(null);
  const [deletionEmail, setDeletionEmail] = useState("");
  const [deletionPhrase, setDeletionPhrase] = useState("");
  const [deletionCode, setDeletionCode] = useState("");
  const [deletionCodeSent, setDeletionCodeSent] = useState(false);
  const [deletionBusy, setDeletionBusy] = useState(false);
  const [deletionError, setDeletionError] = useState<string | null>(null);

  useEffect(() => {
    if (sessionAccountId === null) return;
    const controller = new AbortController();
    fetchThirdPartyProvisionConsentStatus(controller.signal)
      .then((status) => {
        if (!controller.signal.aborted) {
          setConsentOwnerId(sessionAccountId);
          setConsentStatus(status);
          setConsentError(null);
        }
      })
      .catch((cause) => {
        if (!controller.signal.aborted) {
          setConsentOwnerId(sessionAccountId);
          setConsentStatus(null);
          setConsentError(
            cause instanceof ApiError ? cause.message : "제3자 제공 동의 상태를 불러오지 못했어요.",
          );
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setConsentLoading(false);
      });
    return () => controller.abort();
  }, [sessionAccountId]);

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
  const currentAccountId = session.accountId;

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

  const currentConsentStatus = consentOwnerId === currentAccountId ? consentStatus : null;
  const currentConsentLoading = consentOwnerId !== currentAccountId || consentLoading;

  async function consentToProvision(): Promise<void> {
    if (currentConsentStatus === null || !consentChecked || consentBusy) return;
    setConsentBusy(true);
    setConsentError(null);
    setConsentNotice(null);
    try {
      const next = await acceptThirdPartyProvisionConsent(
        currentConsentStatus.noticeVersion,
        "ACCOUNT_SETTINGS",
        createConsentRequestId(),
      );
      setConsentStatus(next);
      setConsentChecked(false);
      setConsentNotice("제3자 제공 동의를 기록했습니다.");
    } catch (cause) {
      if (cause instanceof ApiError && cause.code === THIRD_PARTY_PROVISION_VERSION_STALE_CODE) {
        await reloadConsentAfterVersionChange();
      } else {
        setConsentError(
          cause instanceof ApiError ? cause.message : "제3자 제공 동의를 기록하지 못했어요.",
        );
      }
    } finally {
      setConsentBusy(false);
    }
  }

  async function withdrawProvisionConsent(): Promise<void> {
    if (currentConsentStatus === null || consentBusy) return;
    const confirmed = window.confirm(
      "동의를 철회하면 새 대화·메시지 전송·거래 상대방 연락처 조회가 제한됩니다. 과거 대화는 계속 읽을 수 있습니다. 철회할까요?",
    );
    if (!confirmed) return;
    setConsentBusy(true);
    setConsentError(null);
    setConsentNotice(null);
    try {
      const next = await withdrawThirdPartyProvisionConsent(
        currentConsentStatus.noticeVersion,
        createConsentRequestId(),
      );
      setConsentStatus(next);
      setConsentNotice("제3자 제공 동의를 철회했습니다. 과거 대화는 계속 확인할 수 있습니다.");
    } catch (cause) {
      if (cause instanceof ApiError && cause.code === THIRD_PARTY_PROVISION_VERSION_STALE_CODE) {
        await reloadConsentAfterVersionChange();
      } else {
        setConsentError(
          cause instanceof ApiError ? cause.message : "제3자 제공 동의를 철회하지 못했어요.",
        );
      }
    } finally {
      setConsentBusy(false);
    }
  }

  async function reloadConsentAfterVersionChange(): Promise<void> {
    setConsentLoading(true);
    setConsentChecked(false);
    try {
      const current = await fetchThirdPartyProvisionConsentStatus();
      setConsentOwnerId(currentAccountId);
      setConsentStatus(current);
      setConsentError("동의 안내가 변경되었습니다. 최신 내용을 확인한 뒤 다시 선택해 주세요.");
    } catch (cause) {
      setConsentOwnerId(currentAccountId);
      setConsentStatus(null);
      setConsentError(
        cause instanceof ApiError
          ? cause.message
          : "최신 제3자 제공 동의 상태를 불러오지 못했어요.",
      );
    } finally {
      setConsentLoading(false);
    }
  }

  async function sendDeletionCode(): Promise<void> {
    if (deletionBusy) return;
    setDeletionBusy(true);
    setDeletionError(null);
    try {
      await requestAccountDeletionVerification();
      setDeletionCodeSent(true);
    } catch (cause) {
      setDeletionError(
        cause instanceof ApiError ? cause.message : "본인확인 코드를 보내지 못했어요.",
      );
    } finally {
      setDeletionBusy(false);
    }
  }

  async function submitDeletion(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    if (deletionBusy) return;
    const confirmed = window.confirm(
      "탈퇴를 요청하면 즉시 모든 기기에서 로그아웃되고 다시 로그인할 수 없습니다. 진행할까요?",
    );
    if (!confirmed) return;
    setDeletionBusy(true);
    setDeletionError(null);
    try {
      const idempotencyKey = crypto.randomUUID();
      const result = await requestAccountDeletion(
        deletionEmail,
        deletionPhrase,
        deletionCode,
        idempotencyKey,
      );
      clearAccountBrowserStorage();
      window.alert(`회원 탈퇴 요청이 접수되었습니다.\n요청 ID: ${result.requestId}`);
      router.replace("/login?deletionRequested=1");
    } catch (cause) {
      setDeletionError(
        cause instanceof ApiError ? cause.message : "회원 탈퇴를 요청하지 못했어요.",
      );
    } finally {
      setDeletionBusy(false);
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
        <Card padded className="flex flex-col gap-4">
          <div className="flex flex-col gap-1">
            <Heading level={2}>개인정보 제3자 제공 동의</Heading>
            <Text tone="secondary">
              가입과 별개인 선택 동의입니다. 현재 상태를 확인하고 언제든 철회하거나 다시 동의할 수
              있습니다.
            </Text>
          </div>
          {currentConsentLoading ? (
            <p role="status" className="text-sm text-neutral-500">
              동의 상태를 확인하는 중…
            </p>
          ) : currentConsentStatus === null ? (
            <div className="flex flex-col items-start gap-3">
              <p role="alert" className="text-sm text-danger">
                {consentError ?? "동의 상태를 확인할 수 없습니다."}
              </p>
              <Button
                size="sm"
                variant="secondary"
                onClick={() => {
                  setConsentLoading(true);
                  setConsentError(null);
                  fetchThirdPartyProvisionConsentStatus()
                    .then((status) => {
                      setConsentOwnerId(currentAccountId);
                      setConsentStatus(status);
                    })
                    .catch((cause) =>
                      setConsentError(
                        cause instanceof ApiError
                          ? cause.message
                          : "제3자 제공 동의 상태를 불러오지 못했어요.",
                      ),
                    )
                    .finally(() => setConsentLoading(false));
                }}
              >
                다시 시도
              </Button>
            </div>
          ) : (
            <>
              <div className="rounded-xl border border-neutral-200 bg-neutral-50 p-4">
                <ThirdPartyProvisionNotice compact />
                <p className="mt-3 text-xs text-neutral-400">
                  제3자 제공 안내 버전 {currentConsentStatus.noticeVersion}
                </p>
              </div>
              <p
                className={
                  currentConsentStatus.consented
                    ? "rounded-lg bg-success-soft p-3 text-sm text-success"
                    : "rounded-lg bg-neutral-100 p-3 text-sm text-neutral-700"
                }
                role="status"
              >
                현재 상태: {currentConsentStatus.consented ? "동의함" : "동의하지 않음"}
                {currentConsentStatus.lastDecisionAt === null
                  ? ""
                  : ` · ${new Date(currentConsentStatus.lastDecisionAt).toLocaleString("ko-KR")}`}
              </p>
              {consentError ? (
                <p role="alert" className="rounded-md bg-danger-soft p-3 text-sm text-danger">
                  {consentError}
                </p>
              ) : null}
              {consentNotice ? (
                <p role="status" className="rounded-md bg-brand-50 p-3 text-sm text-brand-800">
                  {consentNotice}
                </p>
              ) : null}
              {currentConsentStatus.consented ? (
                <div className="flex flex-col items-start gap-2">
                  <Button
                    size="sm"
                    variant="danger"
                    disabled={consentBusy}
                    onClick={() => void withdrawProvisionConsent()}
                  >
                    {consentBusy ? "처리 중…" : "동의 철회"}
                  </Button>
                  <p className="text-xs leading-relaxed text-neutral-500">
                    철회 후에도 권리 행사와 분쟁 확인을 위해 과거 대화는 읽을 수 있습니다. 새 대화
                    참여·메시지 전송·상대방 연락처 조회는 다시 동의할 때까지 제한됩니다.
                  </p>
                </div>
              ) : (
                <div className="flex flex-col gap-3">
                  <label className="flex cursor-pointer items-start gap-3 text-sm leading-relaxed text-neutral-700">
                    <input
                      type="checkbox"
                      checked={consentChecked}
                      disabled={consentBusy}
                      onChange={(event) => setConsentChecked(event.target.checked)}
                      className="mt-0.5 h-4 w-4 shrink-0 accent-brand-600"
                    />
                    위 개인정보 제3자 제공에 동의합니다. (선택)
                  </label>
                  <Button
                    size="sm"
                    disabled={!consentChecked || consentBusy}
                    onClick={() => void consentToProvision()}
                    className="self-start"
                  >
                    {consentBusy ? "처리 중…" : "동의하기"}
                  </Button>
                </div>
              )}
            </>
          )}
        </Card>
        <Card padded className="flex flex-col gap-4 border-danger/30">
          <div className="flex flex-col gap-1">
            <Heading level={2}>회원 탈퇴</Heading>
            <Text tone="secondary">
              요청 즉시 계정과 모든 세션이 비활성화됩니다. 진행 중 거래·정산·분쟁·신고 또는 법정
              보존 대상이 있으면 해당 기록은 분리 보존되고, 나머지 개인정보는 운영 검토 후
              파기됩니다.
            </Text>
          </div>
          {deletionError ? (
            <p role="alert" className="rounded-md bg-danger-soft p-3 text-sm text-danger">
              {deletionError}
            </p>
          ) : null}
          {!deletionCodeSent ? (
            <Button
              size="sm"
              variant="secondary"
              className="self-start"
              disabled={deletionBusy}
              onClick={() => void sendDeletionCode()}
            >
              {deletionBusy ? "전송 중…" : "탈퇴 본인확인 코드 받기"}
            </Button>
          ) : (
            <form className="flex flex-col gap-4" onSubmit={(event) => void submitDeletion(event)}>
              <p role="status" className="rounded-md bg-neutral-100 p-3 text-sm text-neutral-700">
                현재 계정 이메일로 10분짜리 코드를 보냈습니다.
              </p>
              <Field label="현재 계정 이메일">
                {({ inputId, describedBy }) => (
                  <Input
                    id={inputId}
                    type="email"
                    autoComplete="email"
                    value={deletionEmail}
                    aria-describedby={describedBy}
                    onChange={(event) => setDeletionEmail(event.target.value)}
                    required
                  />
                )}
              </Field>
              <Field label="확인 문구" hint='"회원 탈퇴"를 그대로 입력해 주세요.'>
                {({ inputId, describedBy }) => (
                  <Input
                    id={inputId}
                    value={deletionPhrase}
                    aria-describedby={describedBy}
                    onChange={(event) => setDeletionPhrase(event.target.value)}
                    required
                  />
                )}
              </Field>
              <Field label="이메일 본인확인 코드">
                {({ inputId, describedBy }) => (
                  <Input
                    id={inputId}
                    inputMode="numeric"
                    autoComplete="one-time-code"
                    maxLength={6}
                    value={deletionCode}
                    aria-describedby={describedBy}
                    onChange={(event) => setDeletionCode(event.target.value.replace(/\D/g, ""))}
                    required
                  />
                )}
              </Field>
              <div className="flex flex-wrap gap-2">
                <Button
                  type="submit"
                  size="sm"
                  variant="danger"
                  disabled={
                    deletionBusy ||
                    deletionEmail.length === 0 ||
                    deletionPhrase !== "회원 탈퇴" ||
                    deletionCode.length !== 6
                  }
                >
                  {deletionBusy ? "요청 중…" : "회원 탈퇴 요청"}
                </Button>
                <Button
                  size="sm"
                  variant="ghost"
                  disabled={deletionBusy}
                  onClick={() => void sendDeletionCode()}
                >
                  코드 다시 받기
                </Button>
              </div>
            </form>
          )}
        </Card>
      </div>
    </Container>
  );
}

function createConsentRequestId(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return `account-settings-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}
