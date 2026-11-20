package com.gole.api.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 웹·앱은 오류 응답의 {@code message}를 그대로 보여 준다. 그래서 사용자 화면에 닿는 컨텍스트의 오류 메시지는 한국어여야 한다.
 *
 * <p>두 형태를 본다. {@code super("CODE", "...")}·{@code new XxxException("CODE", "...")}, 그리고 컨텍스트의
 * {@code domain/exception} 예외를 메시지 하나로 만드는 {@code new XxxException("...")}. 메시지 식 전체에 한글이 없으면 실패한다.
 * 컨텍스트를 옮길 때마다 {@link #USER_FACING_CONTEXTS}에 더한다.
 */
class UserFacingMessageLanguageTest {

    private static final List<String> USER_FACING_CONTEXTS = List.of(
            "account",
            "catalog",
            "collection",
            "community",
            "discovery",
            "listing",
            "media",
            "notification",
            "order",
            "report",
            "review");

    /** 메시지 인자 전체(문자열 이어 붙이기 포함)를 문장 끝까지 잡는다. */
    private static final Pattern CODED_MESSAGE =
            Pattern.compile("(?:super|new \\w*Exception)\\(\\s*\"([A-Z_]+)\",\\s*(\"[^;]*?)\\)\\s*;");

    private static final Pattern SINGLE_MESSAGE =
            Pattern.compile("new (\\w+Exception)\\(\\s*(\"(?![A-Z_]+\",)[^;]*?)\\)\\s*;");

    private static final Pattern HANGUL = Pattern.compile("[가-힣]");

    @Test
    void userFacingContextsDoNotSendEnglishOnlyErrorMessages() throws IOException {
        List<String> english = new ArrayList<>();
        for (String context : USER_FACING_CONTEXTS) {
            Path root = Path.of("src/main/java/com/gole/api", context);
            Set<String> domainExceptions = domainExceptionNames(root);
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file :
                        files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(file);
                    Matcher coded = CODED_MESSAGE.matcher(source);
                    while (coded.find()) {
                        if (!coded.group(2).equals("\"\"")
                                && !HANGUL.matcher(coded.group(2)).find()) {
                            english.add(file.getFileName() + " " + coded.group(1) + " " + coded.group(2));
                        }
                    }
                    Matcher single = SINGLE_MESSAGE.matcher(source);
                    while (single.find()) {
                        if (domainExceptions.contains(single.group(1))
                                && !HANGUL.matcher(single.group(2)).find()) {
                            english.add(file.getFileName() + " " + single.group(1) + " " + single.group(2));
                        }
                    }
                }
            }
        }
        assertThat(english).as("사용자에게 보이는 영문 오류 메시지").isEmpty();
    }

    private static Set<String> domainExceptionNames(Path context) throws IOException {
        Path exceptions = context.resolve("domain/exception");
        if (!Files.isDirectory(exceptions)) {
            return Set.of();
        }
        try (Stream<Path> files = Files.list(exceptions)) {
            return files.map(file -> file.getFileName().toString().replace(".java", ""))
                    .collect(Collectors.toSet());
        }
    }
}
