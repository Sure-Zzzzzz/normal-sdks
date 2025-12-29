package io.github.surezzzzzz.sdk.sensitive.keyword.strategy;

import io.github.surezzzzzz.sdk.sensitive.keyword.annotation.SmartKeywordSensitiveComponent;
import io.github.surezzzzzz.sdk.sensitive.keyword.configuration.SmartKeywordSensitiveProperties;
import io.github.surezzzzzz.sdk.sensitive.keyword.constant.ErrorCode;
import io.github.surezzzzzz.sdk.sensitive.keyword.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.sensitive.keyword.constant.SmartKeywordSensitiveConstant;
import io.github.surezzzzzz.sdk.sensitive.keyword.exception.MaskException;
import io.github.surezzzzzz.sdk.sensitive.keyword.extractor.MetaInfo;
import io.github.surezzzzzz.sdk.sensitive.keyword.support.MaskReasonHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Placeholder Mask Strategy Implementation
 * 占位符脱敏策略实现
 *
 * @author surezzzzzz
 */
@Slf4j
@SmartKeywordSensitiveComponent
public class PlaceholderMaskStrategy implements MaskStrategy {

    private final SmartKeywordSensitiveProperties properties;

    // 分类到前缀的映射表，避免长if-else链
    private static final Map<String, String> CATEGORY_PREFIX_MAP = new HashMap<>();

    static {
        CATEGORY_PREFIX_MAP.put(SmartKeywordSensitiveConstant.ORG_TYPE_CATEGORY_COMPANY, SmartKeywordSensitiveConstant.ORG_TYPE_PREFIX_COMPANY);
        CATEGORY_PREFIX_MAP.put(SmartKeywordSensitiveConstant.ORG_TYPE_CATEGORY_GOVERNMENT, SmartKeywordSensitiveConstant.ORG_TYPE_PREFIX_ORGANIZATION);
        CATEGORY_PREFIX_MAP.put(SmartKeywordSensitiveConstant.ORG_TYPE_CATEGORY_SCHOOL, SmartKeywordSensitiveConstant.ORG_TYPE_PREFIX_SCHOOL);
        CATEGORY_PREFIX_MAP.put(SmartKeywordSensitiveConstant.ORG_TYPE_CATEGORY_MEDICAL, SmartKeywordSensitiveConstant.ORG_TYPE_PREFIX_MEDICAL);
        CATEGORY_PREFIX_MAP.put(SmartKeywordSensitiveConstant.ORG_TYPE_CATEGORY_FINANCE, SmartKeywordSensitiveConstant.ORG_TYPE_PREFIX_FINANCE);
    }

    @Autowired
    public PlaceholderMaskStrategy(SmartKeywordSensitiveProperties properties) {
        this.properties = properties;
    }

    @Override
    public String mask(String keyword, MetaInfo meta, SmartKeywordSensitiveProperties.RuntimeStrategy config) {
        if (!StringUtils.hasText(keyword)) {
            return keyword;
        }

        if (config == null || !config.isComplete()) {
            throw new MaskException(ErrorCode.CONFIG_STRATEGY_MISSING,
                    String.format(ErrorMessage.CONFIG_STRATEGY_MISSING, keyword));
        }

        return maskWithPlaceholder(meta, config);
    }

    /**
     * 占位符掩码
     * 使用占位符替换敏感信息
     * 优先级：元信息中的orgType > 元信息中的industry > 用户配置的placeholder > 默认占位符
     *
     * @param meta   元信息
     * @param config 策略配置
     * @return 占位符字符串
     */
    private String maskWithPlaceholder(MetaInfo meta, SmartKeywordSensitiveProperties.RuntimeStrategy config) {
        // 优先根据元信息动态生成
        if (meta != null) {
            if (meta.getOrgType() != null) {
                String orgType = meta.getOrgType();
                String prefix = getOrgTypePrefix(orgType);
                return SmartKeywordSensitiveConstant.PLACEHOLDER_LEFT_BRACKET + prefix + SmartKeywordSensitiveConstant.PLACEHOLDER_SEPARATOR + orgType + SmartKeywordSensitiveConstant.PLACEHOLDER_RIGHT_BRACKET;
            }
            if (meta.getIndustry() != null) {
                return SmartKeywordSensitiveConstant.PLACEHOLDER_LEFT_BRACKET + SmartKeywordSensitiveConstant.ORG_TYPE_PREFIX_COMPANY + SmartKeywordSensitiveConstant.PLACEHOLDER_SEPARATOR + meta.getIndustry() + SmartKeywordSensitiveConstant.PLACEHOLDER_RIGHT_BRACKET;
            }
        }

        // 如果元信息没有，使用配置的placeholder或默认占位符
        return wrapPlaceholder(config.getPlaceholder());
    }

    /**
     * 根据组织类型获取合适的前缀
     * 改用Map查询 + fallback机制
     */
    private String getOrgTypePrefix(String orgType) {
        // 1. 优先从ORG_TYPE_CATEGORY_MAP精确查询（O(1)，更准确）
        String category = SmartKeywordSensitiveConstant.ORG_TYPE_CATEGORY_MAP.get(orgType);
        if (category != null) {
            return getCategoryPrefix(category);
        }

        // 2. Fallback：使用contains判断（保证向后兼容，支持用户自定义组织类型）
        // 遍历map，查找orgType中是否包含已知的组织类型关键词
        // 注意：跳过value=null的条目，继续寻找有分类的匹配
        for (String keyword : SmartKeywordSensitiveConstant.ORG_TYPE_CATEGORY_MAP.keySet()) {
            if (orgType.contains(keyword)) {
                category = SmartKeywordSensitiveConstant.ORG_TYPE_CATEGORY_MAP.get(keyword);
                if (category != null) {
                    return getCategoryPrefix(category);
                }
                // value=null表示无分类，跳过继续寻找
            }
        }

        // 3. 都不匹配，返回默认前缀
        return SmartKeywordSensitiveConstant.ORG_TYPE_PREFIX_DEFAULT;
    }

    /**
     * 根据category获取对应的前缀
     *
     * @param category 组织类型分类（企业/政府/学校/医疗/金融）
     * @return 前缀字符串
     */
    private String getCategoryPrefix(String category) {
        return CATEGORY_PREFIX_MAP.getOrDefault(category, SmartKeywordSensitiveConstant.ORG_TYPE_PREFIX_DEFAULT);
    }

    /**
     * 包装占位符文本，自动添加方括号（如需要）
     *
     * @param placeholder 占位符文本
     * @return 包装后的占位符
     */
    private String wrapPlaceholder(String placeholder) {
        if (StringUtils.hasText(placeholder)) {
            // 如果配置中已经包含了方括号，直接使用
            if (placeholder.startsWith(SmartKeywordSensitiveConstant.PLACEHOLDER_LEFT_BRACKET)
                    && placeholder.endsWith(SmartKeywordSensitiveConstant.PLACEHOLDER_RIGHT_BRACKET)) {
                return placeholder;
            }
            return SmartKeywordSensitiveConstant.PLACEHOLDER_LEFT_BRACKET + placeholder + SmartKeywordSensitiveConstant.PLACEHOLDER_RIGHT_BRACKET;
        }
        // 默认占位符
        return SmartKeywordSensitiveConstant.DEFAULT_PLACEHOLDER_FALLBACK;
    }

    /**
     * Fallback 脱敏
     * 当没有匹配到任何关键词或组织机构时使用
     * 对于Placeholder策略，fallback返回默认占位符
     *
     * @param text   原始文本
     * @param config 策略配置
     * @return 脱敏后的文本
     */
    @Override
    public String maskWithFallback(String text, SmartKeywordSensitiveProperties.RuntimeStrategy config) {
        if (!StringUtils.hasText(text)) {
            return text;
        }

        return wrapPlaceholder(config.getPlaceholder());
    }

    /**
     * Placeholder策略需要元信息提取（需要orgType和industry）
     * 虽然不需要完整的智能脱敏逻辑，但需要提取orgType来生成占位符
     */
    @Override
    public boolean requiresFullMetaExtraction() {
        return true;
    }

    /**
     * Placeholder策略不需要保留率计算
     */
    @Override
    public boolean requiresRetentionCalculation() {
        return false;
    }

    /**
     * 构建Placeholder脱敏的原因说明（统一通过MaskReasonHelper生成）
     */
    @Override
    public String buildMaskReason(String keyword, String source, MetaInfo meta,
                                  SmartKeywordSensitiveProperties.RuntimeStrategy originalStrategy,
                                  SmartKeywordSensitiveProperties.RuntimeStrategy actualStrategy,
                                  Object adjustment, String masked) {
        // Placeholder策略统一通过MaskReasonHelper生成reason
        if (adjustment instanceof MaskReasonHelper.RetentionAdjustment) {
            MaskReasonHelper.RetentionAdjustment reasonAdjustment =
                    (MaskReasonHelper.RetentionAdjustment) adjustment;
            return MaskReasonHelper.buildSingleTaskReason(
                    keyword, source, meta, originalStrategy, actualStrategy, reasonAdjustment, masked
            );
        }
        // adjustment为null时也通过MaskReasonHelper生成简化格式
        return MaskReasonHelper.buildSingleTaskReason(
                keyword, source, meta, originalStrategy, actualStrategy, null, masked
        );
    }
}
