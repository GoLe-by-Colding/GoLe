import { useState } from "react";
import { StyleSheet, View } from "react-native";
import { ApiError } from "@gole/core";
import { signIn } from "@gole/core/user";
import { saveSession } from "@/shared/api";
import { space } from "@/shared/theme";
import { Button, Text, TextField } from "@/shared/ui";

export interface SignInFormProps {
  /** 인증이 안 끝난 계정이면 인증 화면으로 보낸다. 이메일을 넘겨 다시 입력하지 않게 한다. */
  readonly onNeedsVerification: (email: string) => void;
  readonly onSignedIn: () => void;
}

export function SignInForm({ onNeedsVerification, onSignedIn }: SignInFormProps) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(): Promise<void> {
    setError(null);
    setSubmitting(true);
    try {
      const session = await signIn(email.trim(), password);
      await saveSession(session);
      onSignedIn();
    } catch (cause) {
      // 이메일 인증 전 로그인은 실패가 아니라 다음 단계다. 오류로 보여주면 사용자가 막힌다.
      if (cause instanceof ApiError && cause.code === "ACCOUNT_NOT_VERIFIED") {
        onNeedsVerification(email.trim());
        return;
      }
      setError(cause instanceof ApiError ? cause.message : "로그인하지 못했습니다.");
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
        value={password}
        onChangeText={setPassword}
        secureTextEntry
        autoCapitalize="none"
        autoComplete="current-password"
        textContentType="password"
        onSubmitEditing={() => void handleSubmit()}
      />
      <Button
        label="로그인"
        onPress={() => void handleSubmit()}
        loading={submitting}
        disabled={email.trim().length === 0 || password.length === 0}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  form: { gap: space[4] },
  error: { color: "#dc2626" },
});
