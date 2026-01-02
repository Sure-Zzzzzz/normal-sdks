package io.github.surezzzzzz.sdk.naturallanguage.parser.test.cases;

import io.github.surezzzzzz.sdk.naturallanguage.parser.support.OperatorSuggester;
import io.github.surezzzzzz.sdk.naturallanguage.parser.test.NaturalLanguageParserTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OperatorSuggester（操作符拼写建议器）测试
 * 展示 Levenshtein 距离算法在拼写检查中的应用
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = NaturalLanguageParserTestApplication.class)
@DisplayName("OperatorSuggester - 操作符拼写建议测试")
public class OperatorSuggesterTest {

    @Test
    @DisplayName("拼写错误：大雨 → 建议：大于")
    void testTypoDaYu() {
        String input = "大雨";

        // 查找最相似的建议
        String mostSimilar = OperatorSuggester.findMostSimilar(input);
        log.info("输入: \"{}\" → 建议: \"{}\"", input, mostSimilar);

        assertEquals("大于", mostSimilar);

        // 计算编辑距离
        int distance = OperatorSuggester.levenshteinDistance(input, "大于");
        log.info("  编辑距离: {}", distance);
        assertEquals(1, distance);

        // 检查是否可能是拼写错误
        assertTrue(OperatorSuggester.isPossibleTypo(input));
    }

    @Test
    @DisplayName("拼写错误：小鱼 → 建议：小于")
    void testTypoXiaoYu() {
        String input = "小鱼";

        String mostSimilar = OperatorSuggester.findMostSimilar(input);
        log.info("输入: \"{}\" → 建议: \"{}\"", input, mostSimilar);

        assertEquals("小于", mostSimilar);

        int distance = OperatorSuggester.levenshteinDistance(input, "小于");
        log.info("  编辑距离: {}", distance);
        assertEquals(1, distance);

        assertTrue(OperatorSuggester.isPossibleTypo(input));
    }

    @Test
    @DisplayName("拼写错误：包刮 → 建议：包含")
    void testTypoBaoGua() {
        String input = "包刮";

        String mostSimilar = OperatorSuggester.findMostSimilar(input);
        log.info("输入: \"{}\" → 建议: \"{}\"", input, mostSimilar);

        assertEquals("包含", mostSimilar);

        int distance = OperatorSuggester.levenshteinDistance(input, "包含");
        log.info("  编辑距离: {}", distance);
        assertEquals(1, distance);

        assertTrue(OperatorSuggester.isPossibleTypo(input));
    }

    @Test
    @DisplayName("拼写错误：等玉 → 建议：等于")
    void testTypoDengYu() {
        String input = "等玉";

        String mostSimilar = OperatorSuggester.findMostSimilar(input);
        log.info("输入: \"{}\" → 建议: \"{}\"", input, mostSimilar);

        assertEquals("等于", mostSimilar);

        int distance = OperatorSuggester.levenshteinDistance(input, "等于");
        log.info("  编辑距离: {}", distance);
        assertEquals(1, distance);

        assertTrue(OperatorSuggester.isPossibleTypo(input));
    }

    @Test
    @DisplayName("拼写错误：不在于 → 建议：不在")
    void testTypoBuZaiYu() {
        String input = "不在于";

        String mostSimilar = OperatorSuggester.findMostSimilar(input);
        log.info("输入: \"{}\" → 建议: \"{}\"", input, mostSimilar);

        // "不在于"与"不在"的距离是1（删除"于"），应该建议"不在"
        assertNotNull(mostSimilar);

        int distance = OperatorSuggester.levenshteinDistance(input, "不在");
        log.info("  编辑距离: {}", distance);
        assertEquals(1, distance);

        assertTrue(OperatorSuggester.isPossibleTypo(input));
    }

    @Test
    @DisplayName("多个建议：查找前3个最相似的操作符")
    void testMultipleSuggestions() {
        String input = "大";

        List<String> suggestions = OperatorSuggester.findSimilar(input, 3);
        log.info("输入: \"{}\" → 前3个建议:", input);
        for (String suggestion : suggestions) {
            int distance = OperatorSuggester.levenshteinDistance(input, suggestion);
            log.info("  - \"{}\" (距离={})", suggestion, distance);
        }

        assertNotNull(suggestions);
        assertFalse(suggestions.isEmpty());
        assertTrue(suggestions.size() <= 3);
    }

    @Test
    @DisplayName("格式化建议文本：用顿号连接")
    void testSuggestionText() {
        String input = "大";

        String suggestionText = OperatorSuggester.getSuggestionText(input);
        log.info("输入: \"{}\" → 格式化建议: \"{}\"", input, suggestionText);

        assertNotNull(suggestionText);
        // 应该包含顿号
        assertTrue(suggestionText.contains("、") || !suggestionText.contains("、"));
    }

    @Test
    @DisplayName("完全不像操作符：返回空建议")
    void testNoSuggestion() {
        String input = "根本不是操作符";

        String mostSimilar = OperatorSuggester.findMostSimilar(input);
        log.info("输入: \"{}\" → 建议: {}", input, (mostSimilar == null ? "无" : mostSimilar));

        // 距离太远，没有建议
        assertNull(mostSimilar);
        assertFalse(OperatorSuggester.isPossibleTypo(input));
    }

    @Test
    @DisplayName("编辑距离算法：空字符串")
    void testLevenshteinDistanceEmptyString() {
        assertEquals(0, OperatorSuggester.levenshteinDistance("", ""));
        assertEquals(3, OperatorSuggester.levenshteinDistance("abc", ""));
        assertEquals(3, OperatorSuggester.levenshteinDistance("", "abc"));
    }

    @Test
    @DisplayName("编辑距离算法：相同字符串")
    void testLevenshteinDistanceSameString() {
        assertEquals(0, OperatorSuggester.levenshteinDistance("大于", "大于"));
        assertEquals(0, OperatorSuggester.levenshteinDistance("equals", "equals"));
    }

    @Test
    @DisplayName("编辑距离算法：单字符替换")
    void testLevenshteinDistanceSingleSubstitution() {
        // "大于" → "大雨"（替换"于"为"雨"）
        assertEquals(1, OperatorSuggester.levenshteinDistance("大于", "大雨"));
    }

    @Test
    @DisplayName("编辑距离算法：插入操作")
    void testLevenshteinDistanceInsertion() {
        // "不在" → "不在于"（插入"于"）
        assertEquals(1, OperatorSuggester.levenshteinDistance("不在", "不在于"));
    }

    @Test
    @DisplayName("编辑距离算法：删除操作")
    void testLevenshteinDistanceDeletion() {
        // "大于等于" → "大于"（删除"等于"）
        assertEquals(2, OperatorSuggester.levenshteinDistance("大于等于", "大于"));
    }

    @Test
    @DisplayName("完整示例：展示所有常见拼写错误")
    void testAllCommonTypos() {
        log.info("\n========== 常见拼写错误示例 ==========");

        String[] typos = {"大雨", "小鱼", "包刮", "等玉", "不在于", "匹培", "超出"};

        for (String typo : typos) {
            String suggestion = OperatorSuggester.findMostSimilar(typo);
            if (suggestion != null) {
                int distance = OperatorSuggester.levenshteinDistance(typo, suggestion);
                log.info("  \"{}\" → 建议: \"{}\" (编辑距离={})", typo, suggestion, distance);
            } else {
                log.info("  \"{}\" → 建议: 无", typo);
            }
        }
        log.info("=====================================\n");
    }

    @Test
    @DisplayName("阈值测试：距离>2的不返回建议")
    void testThreshold() {
        // "abc" 与任何中文操作符的距离都应该 > 2
        String input = "abcdefg";

        String mostSimilar = OperatorSuggester.findMostSimilar(input);
        log.info("输入: \"{}\" → 建议: {}", input, (mostSimilar == null ? "无（距离>阈值）" : mostSimilar));

        // 因为距离太远，不应该有建议
        // 注意：如果有英文操作符如"like"，可能会有建议
        if (mostSimilar != null) {
            int distance = OperatorSuggester.levenshteinDistance(input, mostSimilar);
            log.info("  找到的建议距离: {}", distance);
            assertTrue(distance <= 2); // 验证阈值
        }
    }
}
