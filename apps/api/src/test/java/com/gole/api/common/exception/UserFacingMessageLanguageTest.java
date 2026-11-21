package com.gole.api.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 웹·앱은 오류 응답의 {@code message}를 그대로 보여 준다. 그래서 사용자 화면에 닿는 컨텍스트의 오류 메시지는 한국어여야 한다.
 *
 * <p>{@code super("CODE", "...")}·{@code new XxxException("CODE", "...")}의 메시지가 영문으로만 시작하면 실패한다.
 * 컨텍스트를 옮길 때마다 {@link #USER_FACING_CONTEXTS}에 더한다.
 */
class UserFacingMessageLanguageTest {

    private static final List<String> USER_FACING_CONTEXTS = List.of("account");

    private static final Pattern MESSAGE =
            Pattern.compile("(?:super|new \\w*Exception)\\(\\s*\"([A-Z_]+)\",\\s*\"([^\"]*)\"");
    private static final Pattern HANGUL = Pattern.compile("[가-힣]");

    @Test
    void userFacingContextsDoNotSendEnglishOnlyErrorMessages() throws IOException {
        List<String> english = new ArrayList<>();
        for (String context : USER_FACING_CONTEXTS) {
            try (Stream<Path> files = Files.walk(Path.of("src/main/java/com/gole/api", context))) {
                for (Path file :
                        files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    Matcher matcher = MESSAGE.matcher(Files.readString(file));
                    while (matcher.find()) {
                        String message = matcher.group(2);
                        if (!message.isBlank() && !HANGUL.matcher(message).find()) {
                            english.add(file.getFileName() + " " + matcher.group(1) + " \"" + message + "\"");
                        }
                    }
                }
            }
        }
        assertThat(english).as("사용자에게 보이는 영문 오류 메시지").isEmpty();
    }
}
