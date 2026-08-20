package com.gole.api.media.adapter.out.classpath;

import com.gole.api.media.application.port.out.BundledMediaPort;
import com.gole.api.media.application.port.out.ObjectStoragePort.StoredObject;
import java.io.IOException;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** 빌드에 포함된 IP-safe SVG를 객체 스토리지 장애와 무관하게 제공한다. */
@Component
public class ClasspathBundledMediaAdapter implements BundledMediaPort {

    private static final String RESOURCE_PREFIX = "seed-media/";
    private static final String SVG_CONTENT_TYPE = "image/svg+xml";

    @Override
    public Optional<StoredObject> get(String key) {
        if (!isAllowedKey(key)) {
            return Optional.empty();
        }

        ClassPathResource resource = new ClassPathResource(RESOURCE_PREFIX + key);
        if (!resource.exists() || !resource.isReadable()) {
            return Optional.empty();
        }
        try (var input = resource.getInputStream()) {
            return Optional.of(new StoredObject(input.readAllBytes(), SVG_CONTENT_TYPE));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    private static boolean isAllowedKey(String key) {
        if (key == null || key.isBlank() || key.contains("..") || key.startsWith("/")) {
            return false;
        }
        return key.endsWith(".svg") && (key.startsWith("catalog/") || key.startsWith("community/"));
    }
}
