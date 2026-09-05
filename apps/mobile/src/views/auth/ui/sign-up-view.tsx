import { useRouter } from "expo-router";
import { ScrollView, StyleSheet } from "react-native";
import { SignUpForm } from "@/features/sign-up";
import { space } from "@/shared/theme";
import { Screen, Text } from "@/shared/ui";

/** 회원가입 화면. 가입 후 인증 단계로 이어진다. */
export function SignUpView() {
  const router = useRouter();

  return (
    <Screen>
      <ScrollView
        contentContainerStyle={styles.content}
        keyboardShouldPersistTaps="handled"
        keyboardDismissMode="on-drag"
      >
        <Text variant="title">가입하기</Text>
        <SignUpForm
          onRegistered={(email) => router.replace({ pathname: "/verify-email", params: { email } })}
        />
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  content: { gap: space[6], paddingBottom: space[10] },
});
