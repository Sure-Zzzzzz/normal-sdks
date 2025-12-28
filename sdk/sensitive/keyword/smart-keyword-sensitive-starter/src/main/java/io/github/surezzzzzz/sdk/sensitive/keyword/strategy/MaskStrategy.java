package io.github.surezzzzzz.sdk.sensitive.keyword.strategy;

import io.github.surezzzzzz.sdk.sensitive.keyword.configuration.SmartKeywordSensitiveProperties;
import io.github.surezzzzzz.sdk.sensitive.keyword.extractor.MetaInfo;

/**
 * Mask Strategy Interface
 *
 * @author surezzzzzz
 */
public interface MaskStrategy {

    /**
     * 脱敏处理
     *
     * @param keyword 关键词
     * @param meta    元信息
     * @param config  策略配置
     * @return 脱敏后的文本
     */
    String mask(String keyword, MetaInfo meta, SmartKeywordSensitiveProperties.RuntimeStrategy config);

    /**
     * Fallback 脱敏
     * 当没有匹配到任何关键词或组织机构时使用
     *
     * @param text   原始文本
     * @param config 策略配置
     * @return 脱敏后的文本
     */
    String maskWithFallback(String text, SmartKeywordSensitiveProperties.RuntimeStrategy config);

    /**
     * 声明是否需要完整元信息提取（包含NLP）
     * 默认返回false，只有需要完整元信息的策略（如ASTERISK）才返回true
     *
     * @return true表示需要完整元信息提取
     */
    default boolean requiresFullMetaExtraction() {
        return false;
    }

    /**
     * 声明是否需要保留率计算和策略降级
     * 默认返回false，只有需要智能降级的策略（如ASTERISK）才返回true
     *
     * @return true表示需要保留率计算
     */
    default boolean requiresRetentionCalculation() {
        return false;
    }

    /**
     * 构建脱敏原因（策略自己负责生成reason）
     * 默认返回简单的 "keyword → masked" 格式
     *
     * @param keyword          待脱敏关键词
     * @param source           识别来源（可选，如RULE/NLP/CONFIG）
     * @param meta             元信息（可能为null）
     * @param originalStrategy 原始策略配置
     * @param actualStrategy   实际使用的策略
     * @param adjustment       保留率调整详情（可能为null）
     * @param masked           脱敏后的结果
     * @return 脱敏原因字符串
     */
    default String buildMaskReason(String keyword, String source, MetaInfo meta,
                                   SmartKeywordSensitiveProperties.RuntimeStrategy originalStrategy,
                                   SmartKeywordSensitiveProperties.RuntimeStrategy actualStrategy,
                                   Object adjustment, String masked) {
        if (source != null) {
            return String.format("%s(%s) → %s", keyword, source, masked);
        }
        return String.format("%s → %s", keyword, masked);
    }
}
