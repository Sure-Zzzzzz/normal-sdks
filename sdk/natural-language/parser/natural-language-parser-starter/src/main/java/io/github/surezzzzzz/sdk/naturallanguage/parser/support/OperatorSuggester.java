package io.github.surezzzzzz.sdk.naturallanguage.parser.support;

import io.github.surezzzzzz.sdk.naturallanguage.parser.keyword.OperatorKeywords;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 操作符拼写建议工具
 * 使用Levenshtein距离算法找到与输入最相似的操作符关键词
 * <p>
 * 用途：
 * 1. 检测用户输入的拼写错误
 * 2. 提供相似的正确操作符建议
 * <p>
 * 线程安全：无状态，线程安全
 *
 * @author surezzzzzz
 */
public class OperatorSuggester {

    /**
     * 相似度阈值：Levenshtein距离小于等于此值时才认为相似
     * 例如："大雨" vs "大于" 距离为1，可以建议
     */
    private static final int SIMILARITY_THRESHOLD = 2;

    /**
     * 最多返回的建议数量
     */
    private static final int MAX_SUGGESTIONS = 3;

    /**
     * 操作符关键词缓存（从OperatorKeywords获取）
     */
    private static final Set<String> OPERATOR_KEYWORDS;

    static {
        OPERATOR_KEYWORDS = OperatorKeywords.getAllKeywords().keySet();
    }

    /**
     * 查找最相似的操作符关键词
     *
     * @param input 用户输入的文本
     * @return 最相似的操作符关键词，如果没有找到则返回null
     */
    public static String findMostSimilar(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }

        List<String> suggestions = findSimilar(input, 1);
        return suggestions.isEmpty() ? null : suggestions.get(0);
    }

    /**
     * 查找相似的操作符关键词列表
     *
     * @param input 用户输入的文本
     * @param limit 返回的最大建议数量
     * @return 相似的操作符关键词列表，按相似度从高到低排序
     */
    public static List<String> findSimilar(String input, int limit) {
        if (input == null || input.isEmpty()) {
            return Collections.emptyList();
        }

        // 计算每个关键词与输入的距离
        Map<String, Integer> distanceMap = new HashMap<>();
        for (String keyword : OPERATOR_KEYWORDS) {
            int distance = levenshteinDistance(input, keyword);
            // 只保留距离在阈值内的
            if (distance <= SIMILARITY_THRESHOLD) {
                distanceMap.put(keyword, distance);
            }
        }

        // 按距离排序，返回前N个
        return distanceMap.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(Math.min(limit, MAX_SUGGESTIONS))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * 获取格式化的建议文本
     *
     * @param input 用户输入的文本
     * @return 格式化的建议文本，例如："大于、超过、多于"
     */
    public static String getSuggestionText(String input) {
        List<String> suggestions = findSimilar(input, MAX_SUGGESTIONS);
        if (suggestions.isEmpty()) {
            return null;
        }

        // 用顿号连接多个建议
        return String.join("、", suggestions);
    }

    /**
     * Levenshtein距离算法（编辑距离）
     * 计算从字符串s1转换为s2所需的最少编辑操作次数
     * <p>
     * 编辑操作包括：
     * 1. 插入一个字符
     * 2. 删除一个字符
     * 3. 替换一个字符
     *
     * @param s1 第一个字符串
     * @param s2 第二个字符串
     * @return 编辑距离
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

        // 空字符串特殊处理
        if (len1 == 0) {
            return len2;
        }
        if (len2 == 0) {
            return len1;
        }

        // 动态规划数组
        // dp[i][j] 表示 s1[0..i-1] 转换为 s2[0..j-1] 的最小编辑距离
        int[][] dp = new int[len1 + 1][len2 + 1];

        // 初始化第一行和第一列
        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }

        // 动态规划填表
        for (int i = 1; i <= len1; i++) {
            char c1 = s1.charAt(i - 1);
            for (int j = 1; j <= len2; j++) {
                char c2 = s2.charAt(j - 1);

                if (c1 == c2) {
                    // 字符相同，不需要编辑
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    // 取三种操作的最小值：
                    // 1. dp[i-1][j] + 1   : 删除s1[i-1]
                    // 2. dp[i][j-1] + 1   : 插入s2[j-1]
                    // 3. dp[i-1][j-1] + 1 : 替换s1[i-1]为s2[j-1]
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
     *
     * @param input 用户输入的文本
     * @return true表示可能是拼写错误，false表示完全不像操作符
     */
    public static boolean isPossibleTypo(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }

        // 如果能找到相似的操作符，就认为可能是拼写错误
        return !findSimilar(input, 1).isEmpty();
    }

    private OperatorSuggester() {
        // 私有构造函数，防止实例化
    }
}
