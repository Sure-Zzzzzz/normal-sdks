package io.github.surezzzzzz.sdk.sensitive.keyword.support;

import io.github.surezzzzzz.sdk.sensitive.keyword.configuration.SmartKeywordSensitiveProperties;
import io.github.surezzzzzz.sdk.sensitive.keyword.constant.SensitiveOrgType;
import io.github.surezzzzzz.sdk.sensitive.keyword.extractor.MetaInfo;

import java.util.Set;

/**
 * 脱敏策略辅助工具类
 * 提供各策略共享的工具方法
 *
 * @author surezzzzzz
 */
public final class MaskStrategyHelper {

    private MaskStrategyHelper() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
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
        if (str == null || count <= 0) {
            return "";
        }

        StringBuilder result = new StringBuilder(str.length() * count);
        for (int i = 0; i < count; i++) {
            result.append(str);
        }
        return result.toString();
    }

    /**
     * 识别敏感机构类型
     * <p>
     * 检测优先级：
     * 1. 优先基于元信息中的行业字段进行匹配（更准确）
     * 2. 如果元信息无效，则回退到关键词全文匹配
     * 3. 检测顺序：政府 > 金融 > 教育（按敏感度降序）
     * </p>
     *
     * @param keyword     待检测的关键词
     * @param metaInfo    元信息（包含已提取的行业信息）
     * @param keywordSets 关键词集合配置
     * @return 敏感机构类型，未匹配返回 NONE
     */
    public static SensitiveOrgType detectSensitiveOrgType(String keyword,
                                                          MetaInfo metaInfo,
                                                          SmartKeywordSensitiveProperties.KeywordSets keywordSets) {
        if (isInvalidInput(keyword, keywordSets)) {
            return SensitiveOrgType.NONE;
        }

        // 策略1：优先使用元信息中的行业字段进行匹配
        if (hasValidIndustry(metaInfo)) {
            SensitiveOrgType typeByIndustry = detectByIndustry(metaInfo.getIndustry(), keywordSets);
            if (typeByIndustry != SensitiveOrgType.NONE) {
                return typeByIndustry;
            }
        }

        // 策略2：回退到关键词全文匹配
        return detectByKeyword(keyword, keywordSets);
    }

    /**
     * 验证输入是否有效
     */
    private static boolean isInvalidInput(String keyword, SmartKeywordSensitiveProperties.KeywordSets keywordSets) {
        return keyword == null || keyword.isEmpty() || keywordSets == null;
    }

    /**
     * 检查元信息是否包含有效的行业信息
     */
    private static boolean hasValidIndustry(MetaInfo metaInfo) {
        return metaInfo != null && metaInfo.getIndustry() != null && !metaInfo.getIndustry().isEmpty();
    }

    /**
     * 基于元信息中的行业字段检测机构类型
     * 检测优先级：政府 > 金融 > 教育
     *
     * @param industry    行业信息
     * @param keywordSets 关键词集合
     * @return 匹配的机构类型
     */
    private static SensitiveOrgType detectByIndustry(String industry,
                                                     SmartKeywordSensitiveProperties.KeywordSets keywordSets) {
        // 按敏感度从高到低检测
        if (containsAnyKeyword(industry, keywordSets.getGovernmentKeywords())) {
            return SensitiveOrgType.GOVERNMENT;
        }

        if (containsAnyKeyword(industry, keywordSets.getFinancialKeywords())) {
            return SensitiveOrgType.FINANCIAL;
        }

        if (containsAnyKeyword(industry, keywordSets.getEducationKeywords())) {
            return SensitiveOrgType.EDUCATION;
        }

        return SensitiveOrgType.NONE;
    }

    /**
     * 基于关键词全文检测机构类型
     * 检测优先级：金融 > 政府 > 教育
     * （全文匹配时金融关键词优先级更高，避免误判）
     *
     * @param keyword     关键词
     * @param keywordSets 关键词集合
     * @return 匹配的机构类型
     */
    private static SensitiveOrgType detectByKeyword(String keyword,
                                                    SmartKeywordSensitiveProperties.KeywordSets keywordSets) {
        // 金融机构优先（避免"银行路"被误判为金融）
        if (containsAnyKeyword(keyword, keywordSets.getFinancialKeywords())) {
            return SensitiveOrgType.FINANCIAL;
        }

        if (containsAnyKeyword(keyword, keywordSets.getGovernmentKeywords())) {
            return SensitiveOrgType.GOVERNMENT;
        }

        if (containsAnyKeyword(keyword, keywordSets.getEducationKeywords())) {
            return SensitiveOrgType.EDUCATION;
        }

        return SensitiveOrgType.NONE;
    }

    /**
     * 检查文本是否包含关键词集合中的任意一个
     *
     * @param text     待检查的文本
     * @param keywords 关键词集合
     * @return 是否包含任意关键词
     */
    private static boolean containsAnyKeyword(String text, Set<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return false;
        }

        return keywords.stream().anyMatch(text::contains);
    }
}
