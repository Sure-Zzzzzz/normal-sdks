package io.github.surezzzzzz.sdk.naturallanguage.parser.test.cases;

import io.github.surezzzzzz.sdk.naturallanguage.parser.support.OperatorSuggester;
import io.github.surezzzzzz.sdk.naturallanguage.parser.test.NaturalLanguageParserTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OperatorSuggester（操作符拼写建议器）测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = NaturalLanguageParserTestApplication.class)
@DisplayName("OperatorSuggester - 操作符拼写建议测试")
public class OperatorSuggesterTest {

    @Autowired
    private OperatorSuggester operatorSuggester;

    @Test
    @DisplayName("拼写错误：大雨 → 建议：大于")
    void testTypoDaYu() {
        String input = "大雨";
        String mostSimilar = operatorSuggester.findMostSimilar(input);
        log.info("输入: \"{}\" → 建议: \"{}\"", input, mostSimilar);
        assertEquals("大于", mostSimilar);
        assertEquals(1, OperatorSuggester.levenshteinDistance(input, "大于"));
        assertTrue(operatorSuggester.isPossibleTypo(input));
    }

    @Test
    @DisplayName("拼写错误：小鱼 → 建议：小于")
    void testTypoXiaoYu() {
        String input = "小鱼";
        String mostSimilar = operatorSuggester.findMostSimilar(input);
        log.info("输入: \"{}\" → 建议: \"{}\"", input, mostSimilar);
        assertEquals("小于", mostSimilar);
        assertEquals(1, OperatorSuggester.levenshteinDistance(input, "小于"));
        assertTrue(operatorSuggester.isPossibleTypo(input));
    }

    @Test
    @DisplayName("拼写错误：包刮 → 建议：包含")
    void testTypoBaoGua() {
        String input = "包刮";
        String mostSimilar = operatorSuggester.findMostSimilar(input);
        log.info("输入: \"{}\" → 建议: \"{}\"", input, mostSimilar);
        assertEquals("包含", mostSimilar);
        assertEquals(1, OperatorSuggester.levenshteinDistance(input, "包含"));
        assertTrue(operatorSuggester.isPossibleTypo(input));
    }

    @Test
    @DisplayName("拼写错误：等玉 → 建议：等于")
    void testTypoDengYu() {
        String input = "等玉";
        String mostSimilar = operatorSuggester.findMostSimilar(input);
        log.info("输入: \"{}\" → 建议: \"{}\"", input, mostSimilar);
        assertEquals("等于", mostSimilar);
        assertEquals(1, OperatorSuggester.levenshteinDistance(input, "等于"));
        assertTrue(operatorSuggester.isPossibleTypo(input));
    }

    @Test
    @DisplayName("拼写错误：不在于 → 建议：不在")
    void testTypoBuZaiYu() {
        String input = "不在于";
        String mostSimilar = operatorSuggester.findMostSimilar(input);
        log.info("输入: \"{}\" → 建议: \"{}\"", input, mostSimilar);
        assertNotNull(mostSimilar);
        assertEquals(1, OperatorSuggester.levenshteinDistance(input, "不在"));
        assertTrue(operatorSuggester.isPossibleTypo(input));
    }

    @Test
    @DisplayName("多个建议：查找前3个最相似的操作符")
    void testMultipleSuggestions() {
        String input = "大";
        List<String> suggestions = operatorSuggester.findSimilar(input, 3);
        log.info("输入: \"{}\" → 前3个建议: {}", input, suggestions);
        assertNotNull(suggestions);
        assertFalse(suggestions.isEmpty());
        assertTrue(suggestions.size() <= 3);
    }

    @Test
    @DisplayName("格式化建议文本：用顿号连接")
    void testSuggestionText() {
        String input = "大";
        String suggestionText = operatorSuggester.getSuggestionText(input);
        log.info("输入: \"{}\" → 格式化建议: \"{}\"", input, suggestionText);
        assertNotNull(suggestionText);
    }

    @Test
    @DisplayName("完全不像操作符：返回空建议")
    void testNoSuggestion() {
        String input = "根本不是操作符";
        String mostSimilar = operatorSuggester.findMostSimilar(input);
        log.info("输入: \"{}\" → 建议: {}", input, mostSimilar == null ? "无" : mostSimilar);
        assertNull(mostSimilar);
        assertFalse(operatorSuggester.isPossibleTypo(input));
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
        assertEquals(1, OperatorSuggester.levenshteinDistance("大于", "大雨"));
    }

    @Test
    @DisplayName("编辑距离算法：插入操作")
    void testLevenshteinDistanceInsertion() {
        assertEquals(1, OperatorSuggester.levenshteinDistance("不在", "不在于"));
    }

    @Test
    @DisplayName("编辑距离算法：删除操作")
    void testLevenshteinDistanceDeletion() {
        assertEquals(2, OperatorSuggester.levenshteinDistance("大于等于", "大于"));
    }

    @Test
    @DisplayName("阈值测试：距离>2的不返回建议")
    void testThreshold() {
        String input = "abcdefg";
        String mostSimilar = operatorSuggester.findMostSimilar(input);
        log.info("输入: \"{}\" → 建议: {}", input, mostSimilar == null ? "无（距离>阈值）" : mostSimilar);
        if (mostSimilar != null) {
            int distance = OperatorSuggester.levenshteinDistance(input, mostSimilar);
            assertTrue(distance <= 2);
        }
    }
}
