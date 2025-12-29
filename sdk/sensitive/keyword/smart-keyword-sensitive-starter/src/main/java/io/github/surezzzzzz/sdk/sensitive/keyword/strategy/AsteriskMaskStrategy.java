package io.github.surezzzzzz.sdk.sensitive.keyword.strategy;

import io.github.surezzzzzz.sdk.sensitive.keyword.annotation.SmartKeywordSensitiveComponent;
import io.github.surezzzzzz.sdk.sensitive.keyword.configuration.SmartKeywordSensitiveProperties;
import io.github.surezzzzzz.sdk.sensitive.keyword.constant.*;
import io.github.surezzzzzz.sdk.sensitive.keyword.exception.MaskException;
import io.github.surezzzzzz.sdk.sensitive.keyword.extractor.MetaInfo;
import io.github.surezzzzzz.sdk.sensitive.keyword.support.MaskReasonHelper;
import io.github.surezzzzzz.sdk.sensitive.keyword.support.MaskStrategyHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Asterisk Mask Strategy Implementation
 * 星号脱敏策略实现
 *
 * @author surezzzzzz
 */
@Slf4j
@SmartKeywordSensitiveComponent
public class AsteriskMaskStrategy implements MaskStrategy {

    private final SmartKeywordSensitiveProperties properties;

    @Autowired
    public AsteriskMaskStrategy(SmartKeywordSensitiveProperties properties) {
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

        return maskWithAsterisk(keyword, meta, config);
    }

    /**
     * 星号掩码
     * 根据元信息进行智能脱敏，保留地域、行业、组织类型等关键信息
     * 处理步骤：
     * 1. 保留地域前缀（如果配置了keepRegion）
     * 2. 处理括号内容（根据keepBracket配置决定是否保留）
     * 3. 保留组织类型后缀（如果配置了keepOrgType，并进行部分脱敏）
     * 4. 保留行业信息（如果配置了keepIndustry）
     * 5. 中间部分用星号替换
     * 6. 如果结果全部为星号，则使用fallback策略
     *
     * @param keyword 待脱敏关键词
     * @param meta    元信息（包含地域、行业、组织类型、括号内容等）
     * @param config  策略配置
     * @return 脱敏后的文本
     */
    private String maskWithAsterisk(String keyword, MetaInfo meta, SmartKeywordSensitiveProperties.RuntimeStrategy config) {
        log.debug("=== maskWithAsterisk START ===");
        log.debug("keyword: {}", keyword);
        log.debug("meta: {}", meta);
        log.debug("config: {}", config);

        if (meta == null || !meta.hasValidInfo()) {
            log.debug("No valid meta info for keyword: {}, using fallback masking", keyword);
            return maskWithFallbackAsterisk(keyword, config);
        }

        // 1. 计算保留率，防止100%保留导致脱敏失效
        SmartKeywordSensitiveProperties.RuntimeStrategy adjustedConfig = adjustConfigByRetentionRate(keyword, meta, config);
        if (adjustedConfig != config) {
            log.debug("Config adjusted due to high retention rate. Original: keep-region={}, keep-industry={}, keep-org-type={}. Adjusted: keep-region={}, keep-industry={}, keep-org-type={}",
                    config.getKeepRegion(), config.getKeepIndustry(), config.getKeepOrgType(),
                    adjustedConfig.getKeepRegion(), adjustedConfig.getKeepIndustry(), adjustedConfig.getKeepOrgType());
        }

        // 使用标记数组记录哪些字符需要保留
        boolean[] keepMask = new boolean[keyword.length()];

        // 1.5. 先确定括号位置，以便后续只在主体部分查找元信息字段
        String bracketContent = null;
        String leftBracket = null;
        String rightBracket = null;
        int bracketStart = -1;
        int bracketEnd = -1;
        String mainBody = keyword;  // 主体部分（不含括号）

        if (meta != null && meta.getBracketContent() != null) {
            bracketContent = meta.getBracketContent();
            leftBracket = BracketType.CHINESE.name().equals(meta.getLeftBracketType()) ? BracketType.CHINESE.getLeftBracket() : BracketType.ENGLISH.getLeftBracket();
            rightBracket = BracketType.CHINESE.name().equals(meta.getRightBracketType()) ? BracketType.CHINESE.getRightBracket() : BracketType.ENGLISH.getRightBracket();
            String bracketStr = leftBracket + bracketContent + rightBracket;
            bracketStart = keyword.indexOf(bracketStr);
            if (bracketStart >= 0) {
                bracketEnd = bracketStart + bracketStr.length();
                // 提取主体部分（去除括号）
                mainBody = keyword.substring(0, bracketStart) + keyword.substring(bracketEnd);
            }
        }

        // 2. 标记需要保留的地域（只在主体部分查找）
        if (Boolean.TRUE.equals(adjustedConfig.getKeepRegion()) && meta != null && meta.getRegion() != null) {
            String region = meta.getRegion();
            int index = mainBody.indexOf(region);
            if (index >= 0) {
                // 转换为完整关键词中的位置
                int actualIndex = (bracketStart >= 0 && index >= bracketStart) ? index + (bracketEnd - bracketStart) : index;
                for (int i = actualIndex; i < actualIndex + region.length() && i < keyword.length(); i++) {
                    keepMask[i] = true;
                }
            }
        }

        // 3. 标记需要保留的行业（只在主体部分查找）
        if (Boolean.TRUE.equals(adjustedConfig.getKeepIndustry()) && meta != null && meta.getIndustry() != null) {
            String industry = meta.getIndustry();
            int index = mainBody.indexOf(industry);
            if (index >= 0) {
                // 转换为完整关键词中的位置
                int actualIndex = (bracketStart >= 0 && index >= bracketStart) ? index + (bracketEnd - bracketStart) : index;
                for (int i = actualIndex; i < actualIndex + industry.length() && i < keyword.length(); i++) {
                    keepMask[i] = true;
                }
            }
        }

        // 3.5. 标记需要保留的品牌（只在主体部分查找）
        if (Boolean.TRUE.equals(adjustedConfig.getKeepBrand()) && meta != null && meta.getBrand() != null) {
            String brand = meta.getBrand();
            int index = mainBody.indexOf(brand);
            if (index >= 0) {
                // 转换为完整关键词中的位置
                int actualIndex = (bracketStart >= 0 && index >= bracketStart) ? index + (bracketEnd - bracketStart) : index;
                for (int i = actualIndex; i < actualIndex + brand.length() && i < keyword.length(); i++) {
                    keepMask[i] = true;
                }
            }
        }

        // 4. 标记需要保留的组织类型（只在主体部分查找，使用lastIndexOf避免匹配到中间的）
        if (Boolean.TRUE.equals(adjustedConfig.getKeepOrgType()) && meta != null && meta.getOrgType() != null) {
            String orgType = meta.getOrgType();
            int index = mainBody.lastIndexOf(orgType);
            if (index >= 0) {
                // 转换为完整关键词中的位置
                int actualIndex = (bracketStart >= 0 && index >= bracketStart) ? index + (bracketEnd - bracketStart) : index;
                for (int i = actualIndex; i < actualIndex + orgType.length() && i < keyword.length(); i++) {
                    keepMask[i] = true;
                }
            }
        }

        // 5. 清除括号部分的标记（括号部分稍后单独处理）
        if (bracketStart >= 0 && bracketEnd > bracketStart) {
            for (int i = bracketStart; i < bracketEnd; i++) {
                keepMask[i] = false;
            }
        }

        // 5. 根据标记数组构建脱敏后的主体部分
        StringBuilder mainPart = new StringBuilder();

        // keep-length=true: 每个字符一个星号 (保留长度信息)
        // keep-length=false: 固定数量星号 (隐藏长度信息)
        if (Boolean.TRUE.equals(adjustedConfig.getKeepLength())) {
            // 保留长度模式：逐字符处理
            for (int i = 0; i < keyword.length(); i++) {
                // 跳过括号部分
                if (bracketStart >= 0 && i >= bracketStart && i < bracketEnd) {
                    continue;
                }

                if (keepMask[i]) {
                    mainPart.append(keyword.charAt(i));
                } else {
                    mainPart.append(SmartKeywordSensitiveConstant.DEFAULT_MASK_CHAR);
                }
            }
        } else {
            // 固定长度模式：先统计脱敏字符数，再用固定星号替换
            List<String> parts = new ArrayList<>();
            int maskedCount = 0;
            StringBuilder currentPart = new StringBuilder();
            boolean inMaskedSection = false;
            int fixedMaskLength = adjustedConfig.getFixedMaskLengthOrDefault();

            for (int i = 0; i < keyword.length(); i++) {
                // 跳过括号部分
                if (bracketStart >= 0 && i >= bracketStart && i < bracketEnd) {
                    continue;
                }

                if (keepMask[i]) {
                    // 保留字符
                    if (inMaskedSection && maskedCount > 0) {
                        // 结束上一个脱敏段，添加固定数量星号
                        parts.add(MaskStrategyHelper.repeat(SmartKeywordSensitiveConstant.DEFAULT_MASK_CHAR, fixedMaskLength));
                        maskedCount = 0;
                        inMaskedSection = false;
                    }
                    currentPart.append(keyword.charAt(i));
                } else {
                    // 脱敏字符
                    if (currentPart.length() > 0) {
                        // 保存之前的保留字符
                        parts.add(currentPart.toString());
                        currentPart = new StringBuilder();
                    }
                    maskedCount++;
                    inMaskedSection = true;
                }
            }

            // 处理最后的部分
            if (maskedCount > 0) {
                parts.add(MaskStrategyHelper.repeat(SmartKeywordSensitiveConstant.DEFAULT_MASK_CHAR, fixedMaskLength));
            }
            if (currentPart.length() > 0) {
                parts.add(currentPart.toString());
            }

            mainPart.append(String.join("", parts));
        }

        List<String> parts = new ArrayList<>();
        parts.add(mainPart.toString());

        // 8. 添加括号内容
        if (meta != null && meta.getBracketContent() != null) {
            if (Boolean.TRUE.equals(adjustedConfig.getKeepBracket())) {
                // keepBracket=true 时完整保留括号内容，不脱敏
                parts.add(leftBracket + meta.getBracketContent() + rightBracket);
            } else {
                // keepBracket=false 时对括号内容进行智能脱敏（保留地域、组织类型等）
                String maskedBracketContent = maskBracketContentSmart(meta.getBracketContent(), meta.getBracketMeta(), adjustedConfig);
                parts.add(leftBracket + maskedBracketContent + rightBracket);
            }
        }

        String result = String.join("", parts);

        // 检查主体部分是否全部脱敏（只有在没有括号内容时才触发Fallback）
        // 如果有括号内容，即使主体全部脱敏也是合理的（例如：品牌全部脱敏，只保留括号信息）
        boolean hasBracketContent = meta != null && meta.getBracketContent() != null;
        if (isAllMasked(mainPart.toString()) && !hasBracketContent) {
            log.debug("Main part is all masked and no bracket content, using fallback masking");
            return maskWithFallbackAsterisk(keyword, adjustedConfig);
        }

        // 检查输出是否与输入完全相同（未脱敏）
        if (result.equals(keyword)) {
            log.debug("Result equals original keyword, using fallback masking");
            return maskWithFallbackAsterisk(keyword, adjustedConfig);
        }

        return result;
    }

    /**
     * 智能脱敏括号内容
     * 根据括号内容的元信息，保留地域、组织类型等，脱敏其他部分
     *
     * @param bracketContent 括号内容
     * @param bracketMeta    括号内容的元信息
     * @param config         策略配置
     * @return 脱敏后的括号内容
     */
    private String maskBracketContentSmart(String bracketContent, MetaInfo bracketMeta, SmartKeywordSensitiveProperties.RuntimeStrategy config) {
        if (!StringUtils.hasText(bracketContent)) {
            return bracketContent;
        }

        // 如果没有元信息，使用全星号脱敏
        if (bracketMeta == null || !bracketMeta.hasValidInfo()) {
            // keep-length=false 时使用固定数量星号
            if (!Boolean.TRUE.equals(config.getKeepLength())) {
                int fixedLength = config.getFixedBracketMaskLengthOrDefault();
                return MaskStrategyHelper.repeat(SmartKeywordSensitiveConstant.DEFAULT_MASK_CHAR, fixedLength);
            }
            return MaskStrategyHelper.repeat(SmartKeywordSensitiveConstant.DEFAULT_MASK_CHAR, bracketContent.length());
        }

        List<String> parts = new ArrayList<>();
        String remaining = bracketContent;

        // 1. 保留地域（前缀）- keepRegion=true 时完整保留
        if (Boolean.TRUE.equals(config.getKeepRegion()) && bracketMeta.getRegion() != null) {
            String region = bracketMeta.getRegion();
            if (remaining.startsWith(region)) {
                parts.add(region);
                remaining = remaining.substring(region.length());
            }
        }

        // 2. 提取组织类型（后缀）
        String orgTypePart = "";
        if (Boolean.TRUE.equals(config.getKeepOrgType()) && bracketMeta.getOrgType() != null) {
            if (remaining.endsWith(bracketMeta.getOrgType())) {
                orgTypePart = bracketMeta.getOrgType();
                remaining = remaining.substring(0, remaining.length() - bracketMeta.getOrgType().length());
            }
        }

        // 3. 提取行业（中间部分）
        // 注意：括号内容不保留行业信息（即使主体配置keep-industry=true）
        // 原因：括号通常是分支机构或补充信息，行业字段不重要且可能泄露敏感信息
        // String industryPart = "";
        // if (Boolean.TRUE.equals(config.getKeepIndustry()) && bracketMeta.getIndustry() != null) {
        //     String industryKeyword = bracketMeta.getIndustry();
        //     if (remaining.contains(industryKeyword)) {
        //         industryPart = industryKeyword;
        //         remaining = remaining.replace(industryKeyword, "");
        //     }
        // }

        // 4. 中间部分用星号
        if (remaining.length() > 0) {
            // keep-length=false 时使用固定数量星号
            if (!Boolean.TRUE.equals(config.getKeepLength())) {
                int fixedLength = config.getFixedBracketMaskLengthOrDefault();
                parts.add(MaskStrategyHelper.repeat(SmartKeywordSensitiveConstant.DEFAULT_MASK_CHAR, fixedLength));
            } else {
                parts.add(MaskStrategyHelper.repeat(SmartKeywordSensitiveConstant.DEFAULT_MASK_CHAR, remaining.length()));
            }
        }

        // 5. 添加组织类型
        if (!orgTypePart.isEmpty()) {
            parts.add(orgTypePart);
        }

        String result = String.join("", parts);

        // 如果结果为空或全是星号且原文不应该全脱敏，返回全星号
        if (!StringUtils.hasText(result)) {
            return MaskStrategyHelper.repeat(SmartKeywordSensitiveConstant.DEFAULT_MASK_CHAR, bracketContent.length());
        }

        return result;
    }

    /**
     * 判断脱敏结果是否全是星号
     *
     * @param result 脱敏结果
     * @return true表示全是星号
     */
    private boolean isAllMasked(String result) {
        if (!StringUtils.hasText(result)) {
            return false;
        }
        for (int i = 0; i < result.length(); i++) {
            if (result.charAt(i) != SmartKeywordSensitiveConstant.DEFAULT_MASK_CHAR.charAt(0)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Fallback 脱敏
     * 当没有匹配到任何关键词或组织机构时使用
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

        return maskWithFallbackAsterisk(text, config);
    }

    /**
     * Fallback 星号掩码
     * 当无法提取元信息或脱敏结果不理想时使用此方法
     * 改进策略：
     * - 文本长度 ≤ 3：保留首字符，其余用星号替换
     * - 文本长度 4-7：保留首尾各1个字符，中间用星号替换
     * - 文本长度 8-11：保留首尾各1个字符，中间用星号替换
     * - 文本长度 ≥ 12：保留首尾各2个字符，中间用星号替换
     *
     * @param text   待脱敏文本
     * @param config 策略配置
     * @return 脱敏后的文本
     */
    private String maskWithFallbackAsterisk(String text, SmartKeywordSensitiveProperties.RuntimeStrategy config) {
        int length = text.length();

        int thresholdShort = config.getFallbackThresholdShortOrDefault();
        int thresholdMedium = config.getFallbackThresholdMediumOrDefault();
        int thresholdLong = config.getFallbackThresholdLongOrDefault();
        int keepCharsShort = config.getFallbackKeepCharsShortOrDefault();
        int keepCharsMedium = config.getFallbackKeepCharsMediumOrDefault();
        int keepCharsLong = config.getFallbackKeepCharsLongOrDefault();
        int keepCharsExtraLong = config.getFallbackKeepCharsExtraLongOrDefault();

        // keep-length=false 时使用固定数量星号
        boolean useFixedLength = !Boolean.TRUE.equals(config.getKeepLength());
        int fixedMaskLength = config.getFixedMaskLengthOrDefault();

        if (length <= thresholdShort) {
            // ≤3字符：保留keepCharsShort字符
            String firstChar = text.substring(0, keepCharsShort);
            int maskedLength = length - keepCharsShort;
            String maskedPart = useFixedLength && maskedLength > 0
                    ? MaskStrategyHelper.repeat(SmartKeywordSensitiveConstant.DEFAULT_MASK_CHAR, fixedMaskLength)
                    : MaskStrategyHelper.repeat(SmartKeywordSensitiveConstant.DEFAULT_MASK_CHAR, maskedLength);
            return firstChar + maskedPart;
        } else if (length <= thresholdMedium) {
            // 4-7字符：保留首尾各keepCharsMedium个字符
            String prefix = text.substring(0, keepCharsMedium);
            String suffix = text.substring(length - keepCharsMedium);
            int maskedLength = length - keepCharsMedium * 2;
            String maskedPart = useFixedLength && maskedLength > 0
                    ? MaskStrategyHelper.repeat(SmartKeywordSensitiveConstant.DEFAULT_MASK_CHAR, fixedMaskLength)
                    : MaskStrategyHelper.repeat(SmartKeywordSensitiveConstant.DEFAULT_MASK_CHAR, maskedLength);
            return prefix + maskedPart + suffix;
        } else if (length <= thresholdLong) {
            // 8-11字符：保留首尾各keepCharsLong个字符
            String prefix = text.substring(0, keepCharsLong);
            String suffix = text.substring(length - keepCharsLong);
            int maskedLength = length - keepCharsLong * 2;
            String maskedPart = useFixedLength && maskedLength > 0
                    ? MaskStrategyHelper.repeat(SmartKeywordSensitiveConstant.DEFAULT_MASK_CHAR, fixedMaskLength)
                    : MaskStrategyHelper.repeat(SmartKeywordSensitiveConstant.DEFAULT_MASK_CHAR, maskedLength);
            return prefix + maskedPart + suffix;
        } else {
            // ≥12字符：保留首尾各keepCharsExtraLong个字符
            String prefix = text.substring(0, keepCharsExtraLong);
            String suffix = text.substring(length - keepCharsExtraLong);
            int maskedLength = length - keepCharsExtraLong * 2;
            String maskedPart = useFixedLength && maskedLength > 0
                    ? MaskStrategyHelper.repeat(SmartKeywordSensitiveConstant.DEFAULT_MASK_CHAR, fixedMaskLength)
                    : MaskStrategyHelper.repeat(SmartKeywordSensitiveConstant.DEFAULT_MASK_CHAR, maskedLength);
            return prefix + maskedPart + suffix;
        }
    }

    /**
     * 根据机构类型获取保留率阈值
     * 优先从配置读取，否则使用枚举默认值
     *
     * @param orgType 机构类型
     * @return 保留率阈值
     */
    private double getRetentionThresholdForOrgType(SensitiveOrgType orgType) {
        switch (orgType) {
            case FINANCIAL:
                return properties.getRetentionThresholds().getFinancialOrDefault();
            case GOVERNMENT:
                return properties.getRetentionThresholds().getGovernmentOrDefault();
            case EDUCATION:
                return properties.getRetentionThresholds().getEducationOrDefault();
            case NONE:
            default:
                return properties.getRetentionThresholds().getNoneOrDefault();
        }
    }

    /**
     * 根据保留率调整配置，防止100%保留导致脱敏失效
     * <p>
     * 降级策略：
     * - 敏感机构（金融/政府）：保留率阈值60%（可通过配置覆盖），更严格
     * - 教育机构：保留率阈值70%（可通过配置覆盖）
     * - 普通机构：保留率阈值80%（可通过配置覆盖）
     * - 保留率超过阈值时：强制脱敏地域（keep-region=false）
     * - 保留率仍 >= 60%: 继续脱敏行业（keep-industry=false）
     * - 始终保留组织类型（keep-org-type=true），确保可读性
     *
     * @param keyword 待脱敏关键词
     * @param meta    元信息
     * @param config  原始策略配置
     * @return 调整后的策略配置
     */
    private SmartKeywordSensitiveProperties.RuntimeStrategy adjustConfigByRetentionRate(String keyword, MetaInfo meta, SmartKeywordSensitiveProperties.RuntimeStrategy config) {
        if (meta == null || !meta.hasValidInfo()) {
            return config;
        }

        // 识别敏感机构类型并获取对应的保留率阈值（优先从配置读取，否则使用枚举默认值）
        SensitiveOrgType orgType = MaskStrategyHelper.detectSensitiveOrgType(keyword, meta, properties.getKeywordSets());
        double retentionThreshold = getRetentionThresholdForOrgType(orgType);
        if (orgType != SensitiveOrgType.NONE) {
            log.debug("Detected sensitive org type: {} for keyword: {}, retention threshold: {}%",
                    orgType, keyword, String.format("%.0f", retentionThreshold * 100));
        }

        // 不考虑括号内容，只计算主体部分的保留率
        String mainKeyword = keyword;
        int totalLength = keyword.length();

        // 如果有括号，去除括号部分计算主体长度
        if (meta.getBracketContent() != null) {
            String bracketStr = null;
            if (BracketType.CHINESE.name().equals(meta.getLeftBracketType())) {
                bracketStr = BracketType.CHINESE.getLeftBracket() + meta.getBracketContent() + BracketType.CHINESE.getRightBracket();
            } else if (BracketType.ENGLISH.name().equals(meta.getLeftBracketType())) {
                bracketStr = BracketType.ENGLISH.getLeftBracket() + meta.getBracketContent() + BracketType.ENGLISH.getRightBracket();
            }
            if (bracketStr != null && keyword.contains(bracketStr)) {
                mainKeyword = keyword.replace(bracketStr, "");
                totalLength = mainKeyword.length();
            }
        }

        // 计算各元信息在主体中的字符数（使用boolean数组避免重复计算重叠部分）
        boolean[] keepMask = new boolean[totalLength];

        if (Boolean.TRUE.equals(config.getKeepRegion()) && meta.getRegion() != null) {
            String region = meta.getRegion();
            int index = mainKeyword.indexOf(region);
            if (index >= 0) {
                for (int i = index; i < index + region.length() && i < totalLength; i++) {
                    keepMask[i] = true;
                }
            }
        }

        if (Boolean.TRUE.equals(config.getKeepIndustry()) && meta.getIndustry() != null) {
            String industry = meta.getIndustry();
            int index = mainKeyword.indexOf(industry);
            if (index >= 0) {
                for (int i = index; i < index + industry.length() && i < totalLength; i++) {
                    keepMask[i] = true;
                }
            }
        }

        if (Boolean.TRUE.equals(config.getKeepBrand()) && meta.getBrand() != null) {
            String brand = meta.getBrand();
            int index = mainKeyword.indexOf(brand);
            if (index >= 0) {
                for (int i = index; i < index + brand.length() && i < totalLength; i++) {
                    keepMask[i] = true;
                }
            }
        }

        if (Boolean.TRUE.equals(config.getKeepOrgType()) && meta.getOrgType() != null) {
            String orgTypeStr = meta.getOrgType();
            int index = mainKeyword.lastIndexOf(orgTypeStr);  // 使用lastIndexOf避免匹配到中间的
            if (index >= 0) {
                for (int i = index; i < index + orgTypeStr.length() && i < totalLength; i++) {
                    keepMask[i] = true;
                }
            }
        }

        // 统计实际保留的字符数（去除重叠）
        int retainedChars = 0;
        for (boolean keep : keepMask) {
            if (keep) {
                retainedChars++;
            }
        }

        // 计算保留率
        double retentionRate = totalLength > 0 ? (double) retainedChars / totalLength : 0.0;
        log.debug("Retention rate calculation: keyword={}, mainKeyword={}, totalLength={}, retainedChars={}, retentionRate={}%, threshold={}%",
                keyword, mainKeyword, totalLength, retainedChars, String.format("%.1f", retentionRate * 100), String.format("%.0f", retentionThreshold * 100));
        log.debug("Retention details: region={} ({}), industry={} ({}), brand={} ({}), orgType={} ({})",
                meta.getRegion(), Boolean.TRUE.equals(config.getKeepRegion()) ? "kept" : "masked",
                meta.getIndustry(), Boolean.TRUE.equals(config.getKeepIndustry()) ? "kept" : "masked",
                meta.getBrand(), Boolean.TRUE.equals(config.getKeepBrand()) ? "kept" : "masked",
                meta.getOrgType(), Boolean.TRUE.equals(config.getKeepOrgType()) ? "kept" : "masked");

        // 如果保留率低于阈值，不需要调整
        if (retentionRate < retentionThreshold) {
            return config;
        }

        // 需要降级，创建新的配置对象
        SmartKeywordSensitiveProperties.RuntimeStrategy adjustedConfig = config.copy();

        // 第一级降级：关闭地域保留，重新计算保留字符数
        adjustedConfig.setKeepRegion(false);
        int newRetainedChars = calculateRetainedCharsForAdjustment(mainKeyword, meta, adjustedConfig);
        double newRetentionRate = totalLength > 0 ? (double) newRetainedChars / totalLength : 0.0;

        log.debug("First-level downgrade: keep-region=false, newRetentionRate={}%", String.format("%.1f", newRetentionRate * 100));

        // 如果降级后保留率仍 >= SECOND_DOWNGRADE_THRESHOLD，继续降级
        if (newRetentionRate >= SmartKeywordSensitiveConstant.SECOND_DOWNGRADE_THRESHOLD) {
            adjustedConfig.setKeepIndustry(false);
            newRetainedChars = calculateRetainedCharsForAdjustment(mainKeyword, meta, adjustedConfig);
            newRetentionRate = totalLength > 0 ? (double) newRetainedChars / totalLength : 0.0;
            log.debug("Second-level downgrade: keep-industry=false, newRetentionRate={}%", String.format("%.1f", newRetentionRate * 100));

            // 如果第二级降级后保留率仍 >= SECOND_DOWNGRADE_THRESHOLD，第三级降级关闭组织类型保留
            if (newRetentionRate >= SmartKeywordSensitiveConstant.SECOND_DOWNGRADE_THRESHOLD) {
                adjustedConfig.setKeepOrgType(false);
                newRetainedChars = calculateRetainedCharsForAdjustment(mainKeyword, meta, adjustedConfig);
                newRetentionRate = totalLength > 0 ? (double) newRetainedChars / totalLength : 0.0;
                log.debug("Third-level downgrade: keep-org-type=false, newRetentionRate={}%", String.format("%.1f", newRetentionRate * 100));
            }
        }

        log.warn("High retention rate detected for {} '{}': {}% → {}% after adjustment (threshold={}%). Adjusted config: keep-region={}, keep-industry={}, keep-org-type={}",
                orgType, keyword, String.format("%.1f", retentionRate * 100), String.format("%.1f", newRetentionRate * 100), String.format("%.0f", retentionThreshold * 100),
                adjustedConfig.getKeepRegion(), adjustedConfig.getKeepIndustry(), adjustedConfig.getKeepOrgType());

        return adjustedConfig;
    }

    /**
     * 计算根据配置实际会保留的字符数（使用boolean数组避免重复计算重叠部分）
     *
     * @param mainKeyword 主体关键词（不含括号）
     * @param meta        元信息
     * @param config      策略配置
     * @return 实际保留的字符数
     */
    private int calculateRetainedCharsForAdjustment(String mainKeyword, MetaInfo meta, SmartKeywordSensitiveProperties.RuntimeStrategy config) {
        if (mainKeyword == null || mainKeyword.isEmpty()) {
            return 0;
        }

        boolean[] keepMask = new boolean[mainKeyword.length()];

        // 地域
        if (Boolean.TRUE.equals(config.getKeepRegion()) && meta.getRegion() != null) {
            int index = mainKeyword.indexOf(meta.getRegion());
            if (index >= 0) {
                for (int i = index; i < index + meta.getRegion().length() && i < mainKeyword.length(); i++) {
                    keepMask[i] = true;
                }
            }
        }

        // 行业
        if (Boolean.TRUE.equals(config.getKeepIndustry()) && meta.getIndustry() != null) {
            int index = mainKeyword.indexOf(meta.getIndustry());
            if (index >= 0) {
                for (int i = index; i < index + meta.getIndustry().length() && i < mainKeyword.length(); i++) {
                    keepMask[i] = true;
                }
            }
        }

        // 品牌
        if (Boolean.TRUE.equals(config.getKeepBrand()) && meta.getBrand() != null) {
            int index = mainKeyword.indexOf(meta.getBrand());
            if (index >= 0) {
                for (int i = index; i < index + meta.getBrand().length() && i < mainKeyword.length(); i++) {
                    keepMask[i] = true;
                }
            }
        }

        // 组织类型
        if (Boolean.TRUE.equals(config.getKeepOrgType()) && meta.getOrgType() != null) {
            int index = mainKeyword.lastIndexOf(meta.getOrgType());
            if (index >= 0) {
                for (int i = index; i < index + meta.getOrgType().length() && i < mainKeyword.length(); i++) {
                    keepMask[i] = true;
                }
            }
        }

        // 统计保留字符数
        int count = 0;
        for (boolean keep : keepMask) {
            if (keep) {
                count++;
            }
        }
        return count;
    }

    /**
     * Asterisk策略需要完整元信息提取（包含NLP）
     */
    @Override
    public boolean requiresFullMetaExtraction() {
        return true;
    }

    /**
     * Asterisk策略需要保留率计算和策略降级
     */
    @Override
    public boolean requiresRetentionCalculation() {
        return true;
    }

    /**
     * 构建Asterisk脱敏的详细原因（使用MaskReasonHelper生成完整的4部分叙述）
     */
    @Override
    public String buildMaskReason(String keyword, String source, MetaInfo meta,
                                  SmartKeywordSensitiveProperties.RuntimeStrategy originalStrategy,
                                  SmartKeywordSensitiveProperties.RuntimeStrategy actualStrategy,
                                  Object adjustment, String masked) {
        // adjustment是KeywordSensitiveMaskHelper.RetentionRateAdjustment类型
        // 需要转换为MaskReasonHelper.RetentionAdjustment
        if (adjustment == null) {
            // 没有adjustment，使用简单格式
            return String.format("%s → %s", keyword, masked);
        }

        // 将adjustment转换为MaskReasonHelper.RetentionAdjustment
        // 注意：这里adjustment的类型是Object，实际是KeywordSensitiveMaskHelper.RetentionRateAdjustment
        // 我们需要通过反射或者其他方式访问其字段
        // 但更好的方式是在KeywordSensitiveMaskHelper中就转换好
        // 现在先假设调用方已经传入了MaskReasonHelper.RetentionAdjustment类型

        if (adjustment instanceof MaskReasonHelper.RetentionAdjustment) {
            MaskReasonHelper.RetentionAdjustment reasonAdjustment =
                    (MaskReasonHelper.RetentionAdjustment) adjustment;

            return MaskReasonHelper.buildSingleTaskReason(
                    keyword, source, meta, originalStrategy, actualStrategy, reasonAdjustment, masked
            );
        }

        // 如果类型不对，返回简单格式
        return String.format("%s → %s", keyword, masked);
    }
}
