import { StyleSheet, TextInput, View, type TextInputProps } from "react-native";
import { fontSize, radius, space, useTheme } from "@/shared/theme";
import { Text } from "./text";

export interface TextFieldProps extends Omit<TextInputProps, "style"> {
  readonly label: string;
  readonly hint?: string;
  readonly error?: string;
}

export function TextField({ label, hint, error, ...input }: TextFieldProps) {
  const colors = useTheme();
  const invalid = error !== undefined && error.length > 0;

  return (
    <View style={styles.group}>
      <Text variant="caption" muted>
        {label}
      </Text>
      <TextInput
        accessibilityLabel={label}
        placeholderTextColor={colors.textMuted}
        style={[
          styles.input,
          {
            color: colors.text,
            backgroundColor: colors.surface,
            borderColor: invalid ? "#dc2626" : colors.border,
          },
        ]}
        {...input}
      />
      {invalid ? (
        <Text variant="caption" style={styles.error}>
          {error}
        </Text>
      ) : hint !== undefined ? (
        <Text variant="caption" muted>
          {hint}
        </Text>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  group: { gap: space[1] },
  input: {
    minHeight: 48,
    paddingHorizontal: space[4],
    borderRadius: radius.lg,
    borderWidth: StyleSheet.hairlineWidth,
    fontSize: fontSize.base,
  },
  error: { color: "#dc2626" },
});
