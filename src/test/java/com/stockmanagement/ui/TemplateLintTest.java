package com.stockmanagement.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 헌법 원칙 III 정적 검사: 화면 템플릿에 인라인 스타일과 하드코딩 색상이 없고, 모든 입력란에 레이블이 있다. 색상 값은
 * 디자인 토큰(tokens.css)에만 둔다.
 */
class TemplateLintTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");
    private static final Path APP_CSS = Path.of("src/main/resources/static/css/app.css");

    private static final Pattern INLINE_STYLE = Pattern.compile("\\s(?:th:)?style\\s*=", Pattern.CASE_INSENSITIVE);
    private static final Pattern COLOR_LITERAL = Pattern.compile(
            "#[0-9a-fA-F]{3}(?:[0-9a-fA-F]{3})?(?:[0-9a-fA-F]{2})?(?![\\w-])|\\b(?:rgba?|hsla?)\\(",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INPUT = Pattern.compile("<input\\b[^>]*>", Pattern.DOTALL);
    private static final Pattern LABEL_BLOCK = Pattern.compile("<label\\b[^>]*>.*?</label>", Pattern.DOTALL);
    private static final Pattern ID = Pattern.compile("\\s(?:th:)?id\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern FOR = Pattern.compile("\\s(?:th:)?for\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern HIDDEN = Pattern.compile("type\\s*=\\s*\"hidden\"");

    @Test
    void templatesHaveNoInlineStyles() {
        assertThat(violations(INLINE_STYLE)).as("인라인 style 속성").isEmpty();
    }

    @Test
    void templatesHaveNoHardcodedColors() {
        assertThat(violations(COLOR_LITERAL)).as("템플릿의 색상 값").isEmpty();
    }

    @Test
    void appCssUsesOnlyTokens() {
        List<String> found = new ArrayList<>();
        Matcher m = COLOR_LITERAL.matcher(read(APP_CSS));
        while (m.find()) {
            found.add(m.group());
        }
        assertThat(found).as("app.css의 색상 값(tokens.css 변수를 쓴다)").isEmpty();
    }

    @Test
    void everyInputHasLabel() {
        List<String> unlabeled = new ArrayList<>();
        for (Path template : templates()) {
            String html = read(template);
            Set<String> labelTargets = new HashSet<>();
            Matcher forMatcher = FOR.matcher(html);
            while (forMatcher.find()) {
                labelTargets.add(forMatcher.group(1));
            }
            List<int[]> labelBlocks = new ArrayList<>();
            Matcher block = LABEL_BLOCK.matcher(html);
            while (block.find()) {
                labelBlocks.add(new int[] {block.start(), block.end()});
            }
            Matcher input = INPUT.matcher(html);
            while (input.find()) {
                String tag = input.group();
                if (HIDDEN.matcher(tag).find() || insideAny(labelBlocks, input.start())) {
                    continue;
                }
                Matcher id = ID.matcher(tag);
                if (!id.find() || !labelTargets.contains(id.group(1))) {
                    unlabeled.add(TEMPLATES.relativize(template) + ": " + tag.replaceAll("\\s+", " "));
                }
            }
        }
        assertThat(unlabeled).as("레이블 없는 입력란").isEmpty();
    }

    @Test
    void lintCatchesViolations() {
        assertThat(INLINE_STYLE.matcher("<div style=\"color:red\">").find()).isTrue();
        assertThat(COLOR_LITERAL.matcher("fill=\"#c62828\"").find()).isTrue();
        assertThat(COLOR_LITERAL
                        .matcher("href=\"#main\" hx-target=\"#add-form\"")
                        .find())
                .isFalse();
    }

    private static boolean insideAny(List<int[]> blocks, int position) {
        return blocks.stream().anyMatch(b -> position > b[0] && position < b[1]);
    }

    private static List<String> violations(Pattern pattern) {
        List<String> found = new ArrayList<>();
        for (Path template : templates()) {
            String html = read(template).replaceAll("(?s)<!--.*?-->", "");
            Matcher m = pattern.matcher(html);
            while (m.find()) {
                found.add(TEMPLATES.relativize(template) + ": " + m.group());
            }
        }
        return found;
    }

    private static List<Path> templates() {
        try (Stream<Path> files = Files.walk(TEMPLATES)) {
            return files.filter(p -> p.toString().endsWith(".html")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
