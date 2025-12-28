package io.github.surezzzzzz.sdk.sensitive.keyword.support;

import io.github.surezzzzzz.sdk.sensitive.keyword.configuration.SmartKeywordSensitiveProperties;
import io.github.surezzzzzz.sdk.sensitive.keyword.constant.SensitiveOrgType;

import java.util.Set;

/**
 * 脱敏策略辅助工具类
 * 提供各策略共享的工具方法
 *
 * @author surezzzzzz
 */
public class MaskStrategyHelper {

    private MaskStrategyHelper() {
        // 工具类，禁止实例化
    }

    /**
     * 重复字符串
     * Java 8兼容的实现方式
     *
     * @param str   要重复的字符串
     * @param count 重复次数
     * @return 重复后的字符串
     */
    public static String repeat(String str, int count) {
        if (count <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(str.length() * count);
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    /**
     * 识别敏感机构类型
     * 根据关键词内容判断机构类型，用于确定保留率阈值
     *
     * @param keyword     待检测的关键词
     * @param keywordSets 关键词集合配置
     * @return 敏感机构类型
     */
    public static SensitiveOrgType detectSensitiveOrgType(String keyword, SmartKeywordSensitiveProperties.KeywordSets keywordSets) {
        if (keyword == null || keyword.isEmpty() || keywordSets == null) {
            return SensitiveOrgType.NONE;
        }

        // 金融机构关键词
        Set<String> financialKeywords = keywordSets.getFinancialKeywords();
        if (financialKeywords != null) {
            for (String k : financialKeywords) {
                if (keyword.contains(k)) {
                    return SensitiveOrgType.FINANCIAL;
                }
            }
        }

        // 政府机构关键词
        Set<String> governmentKeywords = keywordSets.getGovernmentKeywords();
        if (governmentKeywords != null) {
            for (String k : governmentKeywords) {
                if (keyword.contains(k)) {
                    return SensitiveOrgType.GOVERNMENT;
                }
            }
        }

        // 教育机构关键词
        Set<String> educationKeywords = keywordSets.getEducationKeywords();
        if (educationKeywords != null) {
            for (String k : educationKeywords) {
                if (keyword.contains(k)) {
                    return SensitiveOrgType.EDUCATION;
                }
            }
        }

        return SensitiveOrgType.NONE;
    }
}
