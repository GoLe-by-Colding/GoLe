import { Image, type ImageContentFit, type ImageStyle } from "expo-image";
import { StyleSheet, View, type StyleProp, type ViewStyle } from "react-native";
import { thumbnailUrl } from "@gole/core";
import { radius, useTheme } from "@/shared/theme";

export interface MediaImageProps {
  readonly uri: string | null | undefined;
  /** 썸네일 폭. 서버가 이 값으로 리사이즈한 이미지를 준다. */
  readonly width: number;
  /** 이미지와 빈 자리에 공통으로 적용된다. 크기·모서리 정도만 넘긴다. */
  readonly style?: StyleProp<ViewStyle & ImageStyle>;
  readonly contentFit?: ImageContentFit;
}

/**
 * 미디어 이미지. 없거나 실패하면 빈 자리를 보여준다.
 *
 * 썸네일 URL 규칙(`thumbnailUrl`)은 웹과 공유한다 — 목록에서 원본을 받으면 목록 하나에
 * 수 MB 가 오간다.
 */
export function MediaImage({ uri, width, style, contentFit = "cover" }: MediaImageProps) {
  const colors = useTheme();

  if (uri === null || uri === undefined || uri.length === 0) {
    return <View style={[styles.placeholder, { backgroundColor: colors.border }, style]} />;
  }
  return (
    <Image
      source={{ uri: thumbnailUrl(uri, width) }}
      style={[styles.placeholder, style]}
      contentFit={contentFit}
      transition={150}
    />
  );
}

const styles = StyleSheet.create({
  placeholder: { borderRadius: radius.md },
});
