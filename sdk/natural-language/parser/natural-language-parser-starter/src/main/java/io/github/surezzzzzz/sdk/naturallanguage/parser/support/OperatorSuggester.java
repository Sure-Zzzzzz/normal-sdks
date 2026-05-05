package io.github.surezzzzzz.sdk.naturallanguage.parser.support;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.OperatorType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.keyword.KeywordRegistry;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 操作符拼写建议工具
 * 使用Levenshtein距离算法找到与输入最相似的操作符关键词
 *
 * @author surezzzzzz
 */
public class OperatorSuggester {

    private static final int SIMILARITY_THRESHOLD = 2;
    private static final int MAX_SUGGESTIONS = 3;

    private final Set<String> operatorKeywords;

    public OperatorSuggester(KeywordRegistry keywordRegistry) {
        Set<String> keywords = new HashSet<>();
        for (OperatorType type : OperatorType.values()) {
            keywords.addAll(keywordRegistry.getOperatorKeywords(type));
        }
        this.operatorKeywords = Collections.unmodifiableSet(keywords);
    }

    /**
     * 查找最相似的操作符关键词
     */
    public String findMostSimilar(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        List<String> suggestions = findSimilar(input, 1);
        return suggestions.isEmpty() ? null : suggestions.get(0);
    }

    /**
     * 查找相似的操作符关键词列表
     */
    public List<String> findSimilar(String input, int limit) {
        if (input == null || input.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Integer> distanceMap = new HashMap<>();
        for (String keyword : operatorKeywords) {
            int distance = levenshteinDistance(input, keyword);
            if (distance <= SIMILARITY_THRESHOLD) {
                distanceMap.put(keyword, distance);
            }
        }

        return distanceMap.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(Math.min(limit, MAX_SUGGESTIONS))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * 获取格式化的建议文本
     */
    public String getSuggestionText(String input) {
        List<String> suggestions = findSimilar(input, MAX_SUGGESTIONS);
        if (suggestions.isEmpty()) {
            return null;
        }
        return String.join("、", suggestions);
    }

    /**
     * Levenshtein距离算法
     */
    public static int levenshteinDistance(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return Integer.MAX_VALUE;
        }
        if (s1.equals(s2)) {
            return 0;
        }

        int len1 = s1.length();
        int len2 = s2.length();

        if (len1 == 0) {
            return len2;
        }
        if (len2 == 0) {
            return len1;
        }

        int[][] dp = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= len1; i++) {
            char c1 = s1.charAt(i - 1);
            for (int j = 1; j <= len2; j++) {
                char c2 = s2.charAt(j - 1);
                if (c1 == c2) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = Math.min(
                            Math.min(dp[i - 1][j], dp[i][j - 1]),
                            dp[i - 1][j - 1]
                    ) + 1;
                }
            }
        }

        return dp[len1][len2];
    }

    /**
     * 判断输入是否可能是拼写错误的操作符
     */
    public boolean isPossibleTypo(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        return !findSimilar(input, 1).isEmpty();
    }
}
