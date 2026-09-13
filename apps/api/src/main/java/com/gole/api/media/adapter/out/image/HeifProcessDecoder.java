package com.gole.api.media.adapter.out.image;

import com.gole.api.media.domain.exception.InvalidImageException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Native code is isolated from the JVM with bounded resources and no inherited credentials. */
final class HeifProcessDecoder {
    private static final Semaphore SLOTS = new Semaphore(2);
    private final String python;
    private final Duration timeout;

    HeifProcessDecoder(String python, Duration timeout) {
        this.python = python;
        this.timeout = timeout;
    }

    byte[] decode(byte[] source, int width, int height, long pixels, long maxBytes) {
        if (!SLOTS.tryAcquire()) throw new InvalidImageException("이미지 변환이 혼잡합니다. 잠시 후 다시 시도해 주세요.");
        Path directory = null;
        Process process = null;
        try {
            directory = Files.createTempDirectory("gole-heif-");
            Path script = directory.resolve("decode.py");
            try (var resource = getClass().getResourceAsStream("/media/heif_decode.py")) {
                if (resource == null) throw unsupported();
                Files.copy(resource, script);
            }
            Path input = directory.resolve("input.heic");
            Path output = directory.resolve("output.jpg");
            Files.write(input, source);
            ProcessBuilder builder = new ProcessBuilder(
                    python,
                    "-I",
                    script.toString(),
                    input.toString(),
                    output.toString(),
                    Integer.toString(width),
                    Integer.toString(height),
                    Long.toString(pixels),
                    Long.toString(maxBytes));
            builder.environment().clear();
            builder.environment().put("PATH", "/usr/bin:/bin");
            builder.environment().put("OMP_NUM_THREADS", "1");
            builder.directory(directory.toFile());
            builder.redirectError(ProcessBuilder.Redirect.DISCARD).redirectOutput(ProcessBuilder.Redirect.DISCARD);
            try {
                process = builder.start();
            } catch (IOException absent) {
                throw unsupported();
            }
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new InvalidImageException("이미지 변환 시간이 초과됐습니다. JPEG/PNG로 변환해 다시 올려주세요.");
            }
            if (process.exitValue() == 69) throw unsupported();
            if (process.exitValue() != 0 || !Files.isRegularFile(output) || Files.size(output) > maxBytes) {
                throw new InvalidImageException("HEIC/HEIF 사진을 안전하게 변환할 수 없습니다. JPEG/PNG로 변환해 다시 올려주세요.");
            }
            return Files.readAllBytes(output);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new InvalidImageException("이미지 변환이 중단됐습니다. 다시 시도해 주세요.");
        } catch (IOException error) {
            throw new InvalidImageException("이미지 변환에 실패했습니다. JPEG/PNG로 변환해 다시 올려주세요.");
        } finally {
            if (process != null && process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                try {
                    process.waitFor(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (directory != null) {
                for (String name : new String[] {"input.heic", "output.jpg", "decode.py"}) {
                    try {
                        Files.deleteIfExists(directory.resolve(name));
                    } catch (IOException ignored) {
                    }
                }
                try {
                    Files.deleteIfExists(directory);
                } catch (IOException ignored) {
                }
            }
            SLOTS.release();
        }
    }

    private static InvalidImageException unsupported() {
        return new InvalidImageException("서버에서 HEIC/HEIF 변환을 지원하지 않습니다. JPEG/PNG로 변환해 다시 올려주세요.");
    }
}
