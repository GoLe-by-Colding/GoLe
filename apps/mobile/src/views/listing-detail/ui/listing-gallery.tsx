import { useState } from "react";
import { Dimensions, Pressable, ScrollView, StyleSheet, View } from "react-native";
import { radius, space, useTheme } from "@/shared/theme";
import { MediaImage } from "@/shared/ui";

const { width: SCREEN } = Dimensions.get("window");

export interface ListingGalleryProps {
  readonly photoUrls: readonly string[];
}

/** 매물 사진. 가로 스와이프 + 썸네일 선택. 사진이 없으면 빈 자리 하나를 보여준다. */
export function ListingGallery({ photoUrls }: ListingGalleryProps) {
  const colors = useTheme();
  const [index, setIndex] = useState(0);
  const photos = photoUrls.length > 0 ? photoUrls : [""];
  const current = photos[index] ?? "";

  return (
    <View style={styles.wrap}>
      <MediaImage uri={current} width={900} style={styles.main} contentFit="contain" />
      {photos.length > 1 ? (
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.strip}
        >
          {photos.map((url, i) => (
            <Pressable
              key={`${url}-${i}`}
              accessibilityRole="button"
              accessibilityLabel={`사진 ${i + 1}`}
              accessibilityState={{ selected: i === index }}
              onPress={() => setIndex(i)}
            >
              <MediaImage
                uri={url}
                width={160}
                style={[styles.thumb, { borderColor: i === index ? colors.tint : "transparent" }]}
              />
            </Pressable>
          ))}
        </ScrollView>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: space[3] },
  main: { width: SCREEN, height: SCREEN * 0.75, borderRadius: 0 },
  strip: { gap: space[2], paddingHorizontal: space[4] },
  thumb: { width: 56, height: 56, borderRadius: radius.md, borderWidth: 2 },
});
