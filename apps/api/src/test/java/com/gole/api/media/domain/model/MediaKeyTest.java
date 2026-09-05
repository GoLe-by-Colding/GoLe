package com.gole.api.media.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MediaKeyTest {

    private static final String KEY = "images/0194f1c0-15ab-4f33-9b1d-34073d9d7738.jpg";

    @Test
    void acceptsOnlyCanonicalGeneratedUserKey() {
        assertThat(MediaKey.isUserKey(KEY)).isTrue();
        assertThat(MediaKey.safePublicPath(KEY)).contains("/api/v1/media/" + KEY);
        assertThat(MediaKey.safeStoredKey("/api/v1/media/" + KEY)).contains(KEY);
    }

    @Test
    void quarantinesExternalTraversalAndDerivativeValuesFromResponses() {
        List<String> untrusted = List.of(
                "https://tracker.example/pixel.gif",
                "//tracker.example/pixel.gif",
                "/api/v1/media/../../secret",
                "derivatives/" + KEY + "/w240",
                "/api/v1/media/images/not-a-uuid.jpg");

        assertThat(untrusted).allSatisfy(value -> {
            assertThat(MediaKey.safePublicPath(value)).isEmpty();
            assertThat(MediaKey.safeStoredKey(value)).isEmpty();
        });
    }

    @Test
    void keepsOnlyPackagedSystemSvgAsBundledMedia() {
        assertThat(MediaKey.safePublicPath("/api/v1/media/catalog/10307.svg"))
                .contains("/api/v1/media/catalog/10307.svg");
        assertThat(MediaKey.safePublicPath("catalog/10307.png")).isEmpty();
        assertThat(MediaKey.safePublicPath("community/../secret.svg")).isEmpty();
    }
}
