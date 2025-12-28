package io.github.surezzzzzz.sdk.sensitive.keyword.extractor;

import io.github.surezzzzzz.sdk.sensitive.keyword.annotation.SmartKeywordSensitiveComponent;
import io.github.surezzzzzz.sdk.sensitive.keyword.constant.BracketType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bracket Content Extractor
 *
 * @author surezzzzzz
 */
@Slf4j
@SmartKeywordSensitiveComponent
public class BracketExtractor {

    /**
     * 括号匹配模式（支持中英文括号混合）
     */
    private static final Pattern BRACKET_PATTERN = Pattern.compile(
        "[" + BracketType.CHINESE.getLeftBracket() + BracketType.ENGLISH.getLeftBracket() + "]" +
        "([^" + BracketType.CHINESE.getRightBracket() + BracketType.ENGLISH.getRightBracket() + "]+)" +
        "[" + BracketType.CHINESE.getRightBracket() + BracketType.ENGLISH.getRightBracket() + "]"
    );

    /**
     * 提取括号内容
     *
     * @param text 文本
     * @return 括号内容（不包含括号本身），未找到返回null
     */
    public String extract(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }

        Matcher matcher = BRACKET_PATTERN.matcher(text);
        if (matcher.find()) {
            String content = matcher.group(1);
            log.debug("Extracted bracket content: {} from text: {}", content, text);
            return content;
        }

        return null;
    }

    /**
     * 提取括号类型（左右括号类型）
     *
     * @param text 文本
     * @return 数组，[0]为左括号类型，[1]为右括号类型，未找到返回null
     */
    public String[] extractBracketTypes(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }

        Matcher matcher = BRACKET_PATTERN.matcher(text);
        if (matcher.find()) {
            String fullMatch = matcher.group(0);
            String leftBracketType = fullMatch.startsWith(BracketType.CHINESE.getLeftBracket()) ? BracketType.CHINESE.name() : BracketType.ENGLISH.name();
            String rightBracketType = fullMatch.endsWith(BracketType.CHINESE.getRightBracket()) ? BracketType.CHINESE.name() : BracketType.ENGLISH.name();
            return new String[]{leftBracketType, rightBracketType};
        }

        return null;
    }

    /**
     * 提取括号内容（包含括号）
     *
     * @param text 文本
     * @return 括号内容（包含括号），未找到返回null
     */
    public String extractWithBracket(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }

        Matcher matcher = BRACKET_PATTERN.matcher(text);
        if (matcher.find()) {
            String content = matcher.group(0);
            log.debug("Extracted bracket content with bracket: {} from text: {}", content, text);
            return content;
        }

        return null;
    }

    /**
     * 移除括号内容
     *
     * @param text 文本
     * @return 移除括号后的文本
     */
    public String removeBracket(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }

        return BRACKET_PATTERN.matcher(text).replaceAll("").trim();
    }

    /**
     * 判断文本是否包含括号
     *
     * @param text 文本
     * @return true表示包含括号
     */
    public boolean hasBracket(String text) {
        return extract(text) != null;
    }
}
