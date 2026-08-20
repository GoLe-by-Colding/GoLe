package com.gole.api.media.adapter.out.classpath;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClasspathBundledMediaAdapterTest {

    private final ClasspathBundledMediaAdapter adapter = new ClasspathBundledMediaAdapter();

    @Test
    void get_loadsKnownCatalogSvgFromApplicationBundle() {
        var result = adapter.get("catalog/10294.svg");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().contentType()).isEqualTo("image/svg+xml");
        assertThat(new String(result.orElseThrow().content())).contains("<svg");
    }

    @Test
    void get_rejectsTraversalAndUnknownPaths() {
        assertThat(adapter.get("../application.yml")).isEmpty();
        assertThat(adapter.get("images/user-upload.svg")).isEmpty();
        assertThat(adapter.get("catalog/missing.svg")).isEmpty();
    }
}
