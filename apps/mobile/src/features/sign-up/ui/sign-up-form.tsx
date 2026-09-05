import { useState } from "react";
import { Pressable, StyleSheet, View } from "react-native";
import { ApiError } from "@gole/core";
import {
  fetchCurrentSignupPolicy,
  registerAccount,
  type CurrentSignupPolicy,
  type SignupPolicyAcceptance,
} from "@gole/core/user";
import { useAsync } from "@/shared/lib";
import { radius, space, useTheme } from "@/shared/theme";
import { Button, ErrorState, LoadingState, Text, TextField } from "@/shared/ui";

export interface SignUpFormProps {
  /** 가입 성공 시 인증 단계로 넘긴다. */
  readonly onRegistered: (email: string) => void;
}

export function SignUpForm({ onRegistered }: SignUpFormProps) {
  const policy = useAsync<CurrentSignupPolicy>((signal) => fetchCurrentSignupPolicy(signal), []);

  if (policy.loading) {
    return <LoadingState label="약관을 불러오는 중" />;
  }
  if (policy.error !== null || policy.data === null) {
    // 약관 버전을 모른 채 가입시키면 서버가 거부한다. 여기서 멈추는 편이 정직하다.
    return (
      <ErrorState message={policy.error ?? "약관을 불러오지 못했습니다."} onRetry={policy.reload} />
    );
  }
  return <Form policy={policy.data} onRegistered={onRegistered} />;
}

function Form({
  policy,
  onRegistered,
}: {
  readonly policy: CurrentSignupPolicy;
  readonly onRegistered: (email: string) => void;
}) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [termsAccepted, setTermsAccepted] = useState(false);
  const [privacyAcknowledged, setPrivacyAcknowledged] = useState(false);
  const [minimumAgeConfirmed, setMinimumAgeConfirmed] = useState(false);
  const [thirdPartyProvisionAccepted, setThirdPartyProvisionAccepted] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const acceptance: SignupPolicyAcceptance = {
    termsVersion: policy.termsVersion,
    privacyVersion: policy.privacyVersion,
    thirdPartyProvisionVersion: policy.thirdPartyProvisionVersion,
    termsAccepted,
    privacyAcknowledged,
    thirdPartyProvisionAccepted,
    minimumAgeConfirmed,
  };
  const ready =
    email.trim().length > 0 &&
    password.length >= 8 &&
    termsAccepted &&
    privacyAcknowledged &&
    minimumAgeConfirmed;

  async function handleSubmit(): Promise<void> {
    setError(null);
    setSubmitting(true);
    try {
      await registerAccount(email.trim(), password, acceptance);
      onRegistered(email.trim());
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "가입하지 못했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <View style={styles.form}>
      {error === null ? null : (
        <Text variant="caption" style={styles.error} accessibilityRole="alert">
          {error}
        </Text>
      )}
      <TextField
        label="이메일"
        value={email}
        onChangeText={setEmail}
        placeholder="you@example.com"
        autoCapitalize="none"
        autoComplete="email"
        keyboardType="email-address"
        textContentType="emailAddress"
      />
      <TextField
        label="비밀번호"
        hint="8자 이상"
        value={password}
        onChangeText={setPassword}
        secureTextEntry
        autoCapitalize="none"
        autoComplete="new-password"
        textContentType="newPassword"
      />
      <View style={styles.checks}>
        <Check
          label={`이용약관에 동의합니다 (${policy.termsVersion})`}
          checked={termsAccepted}
          onToggle={() => setTermsAccepted((v) => !v)}
        />
        <Check
          label={`개인정보 처리방침을 확인했습니다 (${policy.privacyVersion})`}
          checked={privacyAcknowledged}
          onToggle={() => setPrivacyAcknowledged((v) => !v)}
        />
        <Check
          label={`만 ${policy.minimumAge}세 이상입니다`}
          checked={minimumAgeConfirmed}
          onToggle={() => setMinimumAgeConfirmed((v) => !v)}
        />
        {/* 제3자 제공은 선택이다. ready 조건에 넣지 않는다 — 웹과 같은 규칙. */}
        <Check
          label={`개인정보 제3자 제공에 동의합니다 (선택, ${policy.thirdPartyProvisionVersion})`}
          checked={thirdPartyProvisionAccepted}
          onToggle={() => setThirdPartyProvisionAccepted((v) => !v)}
        />
      </View>
      <Button
        label="가입하기"
        onPress={() => void handleSubmit()}
        loading={submitting}
        disabled={!ready}
      />
    </View>
  );
}

function Check({
  label,
  checked,
  onToggle,
}: {
  readonly label: string;
  readonly checked: boolean;
  readonly onToggle: () => void;
}) {
  const colors = useTheme();
  return (
    <Pressable
      accessibilityRole="checkbox"
      accessibilityState={{ checked }}
      accessibilityLabel={label}
      onPress={onToggle}
      style={styles.check}
    >
      <View
        style={[
          styles.box,
          { borderColor: checked ? colors.tint : colors.border },
          checked ? { backgroundColor: colors.tint } : null,
        ]}
      >
        {checked ? <Text style={styles.mark}>✓</Text> : null}
      </View>
      <Text variant="caption" style={styles.checkLabel}>
        {label}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  form: { gap: space[4] },
  checks: { gap: space[3] },
  check: { flexDirection: "row", alignItems: "center", gap: space[3] },
  box: {
    width: 24,
    height: 24,
    borderRadius: radius.sm,
    borderWidth: 1.5,
    alignItems: "center",
    justifyContent: "center",
  },
  mark: { color: "#ffffff", fontSize: 14, fontWeight: "700" },
  checkLabel: { flex: 1 },
  error: { color: "#dc2626" },
});
