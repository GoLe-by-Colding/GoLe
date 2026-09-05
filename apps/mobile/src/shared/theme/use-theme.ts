import { useColorScheme } from "react-native";
import { themes, type ColorSchemeName, type ThemeColors } from "./theme";

/** 시스템 색 구성을 따른다. `null`·`unspecified`는 라이트로 본다. */
export function useTheme(): ThemeColors {
  const scheme = useColorScheme();
  const key: ColorSchemeName = scheme === "dark" ? "dark" : "light";
  return themes[key];
}
