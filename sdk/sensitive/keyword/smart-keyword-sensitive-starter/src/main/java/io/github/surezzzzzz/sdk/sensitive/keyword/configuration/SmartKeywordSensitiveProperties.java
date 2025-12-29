package io.github.surezzzzzz.sdk.sensitive.keyword.configuration;

import io.github.surezzzzzz.sdk.sensitive.keyword.constant.*;
import io.github.surezzzzzz.sdk.sensitive.keyword.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.sensitive.keyword.extractor.MetaInfo;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.*;

/**
 * Simple Keyword Sensitive Properties
 *
 * @author surezzzzzz
 */
@Getter
@Setter
@NoArgsConstructor
@Slf4j
@ConfigurationProperties(SmartKeywordSensitiveConstant.CONFIG_PREFIX)
@ConditionalOnProperty(prefix = SmartKeywordSensitiveConstant.CONFIG_PREFIX, name = "enable", havingValue = "true")
public class SmartKeywordSensitiveProperties {

    /**
     * 是否启用
     */
    private boolean enable = false;

    /**
     * 默认策略配置
     */
    private DefaultStrategy defaultStrategy = new DefaultStrategy();

    /**
     * NLP配置
     */
    private NLP nlp = new NLP();

    /**
     * 关键词配置列表
     */
    private List<Keyword> keywords = new ArrayList<>();

    /**
     * 关键词集合配置
     */
    private KeywordSets keywordSets = new KeywordSets();

    /**
     * 保留率阈值配置（可覆盖SensitiveOrgType的默认值）
     */
    private RetentionThresholds retentionThresholds = new RetentionThresholds();

    @PostConstruct
    public void init() {
        log.info("Simple Keyword Sensitive enabled: {}", enable);
        log.info("Default strategy - keepRegion: {}, keepIndustry: {}, keepOrgType: {}, maskType: {}",
                defaultStrategy.getKeepRegion(),
                defaultStrategy.getKeepIndustry(),
                defaultStrategy.getKeepOrgType(),
                defaultStrategy.getMaskType());
        log.info("NLP enabled: {}, provider: {}, fallback-to-rule: {}",
                nlp.isEnabled(), nlp.getProvider(), nlp.isFallbackToRule());
        log.info("Configured keywords: {}", keywords.size());

        // 初始化关键词集合
        keywordSets.initialize();
        log.info("Keyword sets initialized - mode: {}", keywordSets.getMode());
        log.info("  - Financial keywords: {} items", keywordSets.getFinancialKeywords().size());
        log.info("  - Government keywords: {} items", keywordSets.getGovernmentKeywords().size());
        log.info("  - Education keywords: {} items", keywordSets.getEducationKeywords().size());
        log.info("  - Non-region blacklist: {} items", keywordSets.getNonRegionBlacklist().size());

        // 校验配置
        validate();
    }

    /**
     * 校验配置
     */
    private void validate() {
        if (!enable) {
            return;
        }

        try {
            // 1. 校验默认策略
            validateDefaultStrategy();

            // 2. 校验NLP配置
            validateNLPConfig();

            // 3. 校验关键词配置
            if (!CollectionUtils.isEmpty(keywords)) {
                validateKeywords();
            }

            log.info("Configuration validation passed");

        } catch (ConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigurationException(ErrorCode.CONFIG_VALIDATION_FAILED,
                    ErrorMessage.CONFIG_VALIDATION_FAILED, e);
        }
    }

    /**
     * 校验默认策略
     */
    private void validateDefaultStrategy() {
        if (defaultStrategy == null) {
            throw new ConfigurationException(ErrorCode.CONFIG_DEFAULT_STRATEGY_INVALID,
                    ErrorMessage.CONFIG_DEFAULT_STRATEGY_INVALID);
        }

        // 校验掩码类型
        if (!StringUtils.hasText(defaultStrategy.getMaskType())) {
            defaultStrategy.setMaskType(SmartKeywordSensitiveConstant.DEFAULT_MASK_TYPE);
        } else if (!MaskType.isValid(defaultStrategy.getMaskType())) {
            throw new ConfigurationException(ErrorCode.CONFIG_MASK_TYPE_INVALID,
                    String.format(ErrorMessage.CONFIG_MASK_TYPE_INVALID,
                            defaultStrategy.getMaskType(),
                            String.join(", ", MaskType.getAllCodes())));
        }

        // 校验占位符
        MaskType maskType = MaskType.fromCode(defaultStrategy.getMaskType());
        if (maskType == MaskType.PLACEHOLDER && !StringUtils.hasText(defaultStrategy.getPlaceholder())) {
            throw new ConfigurationException(ErrorCode.CONFIG_PLACEHOLDER_INVALID,
                    String.format(ErrorMessage.CONFIG_PLACEHOLDER_INVALID, ""));
        }
    }

    /**
     * 校验NLP配置
     */
    private void validateNLPConfig() {
        if (nlp == null) {
            nlp = new NLP();
            return;
        }

        if (nlp.getTimeoutMs() != null && nlp.getTimeoutMs() <= 0) {
            throw new ConfigurationException(ErrorCode.CONFIG_NLP_INVALID,
                    String.format(ErrorMessage.CONFIG_NLP_INVALID, "timeoutMs must be positive"));
        }
    }

    /**
     * 校验关键词配置
     */
    private void validateKeywords() {
        Map<String, Integer> keywordCount = new HashMap<>();

        for (int i = 0; i < keywords.size(); i++) {
            Keyword config = keywords.get(i);
            String prefix = "关键词配置 #" + (i + 1) + " ";

            // 1. 校验关键词不为空
            if (!StringUtils.hasText(config.getKeyword())) {
                throw new ConfigurationException(ErrorCode.CONFIG_KEYWORD_EMPTY,
                        prefix + ErrorMessage.CONFIG_KEYWORD_EMPTY);
            }

            // 2. 校验关键词长度
            if (config.getKeyword().length() < SmartKeywordSensitiveConstant.MIN_KEYWORD_LENGTH
                    || config.getKeyword().length() > SmartKeywordSensitiveConstant.MAX_KEYWORD_LENGTH) {
                throw new ConfigurationException(ErrorCode.CONFIG_KEYWORD_LENGTH_INVALID,
                        prefix + String.format(ErrorMessage.CONFIG_KEYWORD_LENGTH_INVALID,
                                SmartKeywordSensitiveConstant.MIN_KEYWORD_LENGTH,
                                SmartKeywordSensitiveConstant.MAX_KEYWORD_LENGTH,
                                config.getKeyword()));
            }

            // 4. 校验策略配置
            if (config.getStrategy() != null) {
                validateStrategyConfig(config.getStrategy(), prefix);
            }

            // 5. 统计重复关键词
            keywordCount.merge(config.getKeyword(), 1, Integer::sum);
        }

        // 6. 检查重复关键词
        keywordCount.forEach((keyword, count) -> {
            if (count > 1) {
                log.warn("存在重复的关键词：[{}]，共出现 {} 次", keyword, count);
            }
        });
    }

    /**
     * 校验策略配置
     */
    private void validateStrategyConfig(Strategy strategy, String prefix) {
        // 校验掩码类型
        if (StringUtils.hasText(strategy.getMaskType()) && !MaskType.isValid(strategy.getMaskType())) {
            throw new ConfigurationException(ErrorCode.CONFIG_MASK_TYPE_INVALID,
                    prefix + String.format(ErrorMessage.CONFIG_MASK_TYPE_INVALID,
                            strategy.getMaskType(),
                            String.join(", ", MaskType.getAllCodes())));
        }

        // 不再校验占位符是否为空，因为toRuntimeStrategy()会自动填充默认值
    }

    /**
     * 运行时策略配置（扁平化）
     * 用于运行时使用，替代原来独立的 StrategyConfig.java
     */
    @Data
    @NoArgsConstructor
    public static class RuntimeStrategy {
        /**
         * 是否保留地域信息
         */
        private Boolean keepRegion;

        /**
         * 是否保留行业信息
         */
        private Boolean keepIndustry;

        /**
         * 是否保留组织类型
         */
        private Boolean keepOrgType;

        /**
         * 是否保留括号内容
         */
        private Boolean keepBracket;

        /**
         * 是否保留品牌
         */
        private Boolean keepBrand;

        /**
         * 掩码类型
         */
        private MaskType maskType;

        /**
         * 自定义占位符（maskType=PLACEHOLDER时使用）
         */
        private String placeholder;

        /**
         * 是否保留原始长度信息（星号数量等于原文长度）
         */
        private Boolean keepLength;

        /**
         * 固定星号数量（keepLength=false时使用）
         */
        private Integer fixedMaskLength;

        /**
         * 括号内容固定星号数量（未配置时继承自fixedMaskLength）
         */
        private Integer fixedBracketMaskLength;

        /**
         * Fallback脱敏短文本长度阈值
         */
        private Integer fallbackLengthThresholdShort;

        /**
         * Fallback脱敏中文本长度阈值
         */
        private Integer fallbackLengthThresholdMedium;

        /**
         * Fallback脱敏长文本长度阈值
         */
        private Integer fallbackLengthThresholdLong;

        /**
         * Fallback脱敏保留字符数（长度≤thresholdShort时）
         */
        private Integer fallbackKeepCharsShort;

        /**
         * Fallback脱敏保留字符数（长度thresholdShort+1到thresholdMedium时）
         */
        private Integer fallbackKeepCharsMedium;

        /**
         * Fallback脱敏保留字符数（长度thresholdMedium+1到thresholdLong时）
         */
        private Integer fallbackKeepCharsLong;

        /**
         * Fallback脱敏保留字符数（长度>thresholdLong时）
         */
        private Integer fallbackKeepCharsExtraLong;

        /**
         * 与默认配置合并（返回新对象，不修改原对象）
         *
         * @param defaultConfig 默认配置
         * @return 合并后的新配置
         */
        public RuntimeStrategy mergeWithDefault(RuntimeStrategy defaultConfig) {
            if (defaultConfig == null) {
                return this;
            }

            RuntimeStrategy merged = new RuntimeStrategy();
            merged.keepRegion = this.keepRegion != null ? this.keepRegion : defaultConfig.keepRegion;
            merged.keepIndustry = this.keepIndustry != null ? this.keepIndustry : defaultConfig.keepIndustry;
            merged.keepOrgType = this.keepOrgType != null ? this.keepOrgType : defaultConfig.keepOrgType;
            merged.keepBracket = this.keepBracket != null ? this.keepBracket : defaultConfig.keepBracket;
            merged.keepBrand = this.keepBrand != null ? this.keepBrand : defaultConfig.keepBrand;
            merged.maskType = this.maskType != null ? this.maskType : defaultConfig.maskType;
            merged.placeholder = this.placeholder != null ? this.placeholder : defaultConfig.placeholder;
            merged.keepLength = this.keepLength != null ? this.keepLength : defaultConfig.keepLength;
            merged.fixedMaskLength = this.fixedMaskLength != null ? this.fixedMaskLength : defaultConfig.fixedMaskLength;
            merged.fixedBracketMaskLength = this.fixedBracketMaskLength != null ? this.fixedBracketMaskLength : defaultConfig.fixedBracketMaskLength;
            merged.fallbackLengthThresholdShort = this.fallbackLengthThresholdShort != null ? this.fallbackLengthThresholdShort : defaultConfig.fallbackLengthThresholdShort;
            merged.fallbackLengthThresholdMedium = this.fallbackLengthThresholdMedium != null ? this.fallbackLengthThresholdMedium : defaultConfig.fallbackLengthThresholdMedium;
            merged.fallbackLengthThresholdLong = this.fallbackLengthThresholdLong != null ? this.fallbackLengthThresholdLong : defaultConfig.fallbackLengthThresholdLong;
            merged.fallbackKeepCharsShort = this.fallbackKeepCharsShort != null ? this.fallbackKeepCharsShort : defaultConfig.fallbackKeepCharsShort;
            merged.fallbackKeepCharsMedium = this.fallbackKeepCharsMedium != null ? this.fallbackKeepCharsMedium : defaultConfig.fallbackKeepCharsMedium;
            merged.fallbackKeepCharsLong = this.fallbackKeepCharsLong != null ? this.fallbackKeepCharsLong : defaultConfig.fallbackKeepCharsLong;
            merged.fallbackKeepCharsExtraLong = this.fallbackKeepCharsExtraLong != null ? this.fallbackKeepCharsExtraLong : defaultConfig.fallbackKeepCharsExtraLong;

            return merged;
        }

        /**
         * 判断配置是否完整（所有必需字段都有值）
         *
         * @return true表示完整
         */
        public boolean isComplete() {
            return keepRegion != null
                    && keepIndustry != null
                    && keepOrgType != null
                    && keepBracket != null
                    && keepBrand != null
                    && maskType != null
                    && keepLength != null
                    && fallbackLengthThresholdShort != null
                    && fallbackLengthThresholdMedium != null
                    && fallbackLengthThresholdLong != null
                    && fallbackKeepCharsShort != null
                    && fallbackKeepCharsMedium != null
                    && fallbackKeepCharsLong != null
                    && fallbackKeepCharsExtraLong != null;
        }

        /**
         * 创建当前配置的深拷贝
         *
         * @return 新的配置对象
         */
        public RuntimeStrategy copy() {
            RuntimeStrategy copied = new RuntimeStrategy();
            copied.keepRegion = this.keepRegion;
            copied.keepIndustry = this.keepIndustry;
            copied.keepOrgType = this.keepOrgType;
            copied.keepBracket = this.keepBracket;
            copied.keepBrand = this.keepBrand;
            copied.maskType = this.maskType;
            copied.placeholder = this.placeholder;
            copied.keepLength = this.keepLength;
            copied.fixedMaskLength = this.fixedMaskLength;
            copied.fixedBracketMaskLength = this.fixedBracketMaskLength;
            copied.fallbackLengthThresholdShort = this.fallbackLengthThresholdShort;
            copied.fallbackLengthThresholdMedium = this.fallbackLengthThresholdMedium;
            copied.fallbackLengthThresholdLong = this.fallbackLengthThresholdLong;
            copied.fallbackKeepCharsShort = this.fallbackKeepCharsShort;
            copied.fallbackKeepCharsMedium = this.fallbackKeepCharsMedium;
            copied.fallbackKeepCharsLong = this.fallbackKeepCharsLong;
            copied.fallbackKeepCharsExtraLong = this.fallbackKeepCharsExtraLong;
            return copied;
        }

        /**
         * 获取Fallback长度阈值-短文本（带默认值）
         */
        public int getFallbackThresholdShortOrDefault() {
            return fallbackLengthThresholdShort != null ? fallbackLengthThresholdShort : SmartKeywordSensitiveConstant.FALLBACK_LENGTH_THRESHOLD_SHORT;
        }

        /**
         * 获取Fallback长度阈值-中文本（带默认值）
         */
        public int getFallbackThresholdMediumOrDefault() {
            return fallbackLengthThresholdMedium != null ? fallbackLengthThresholdMedium : SmartKeywordSensitiveConstant.FALLBACK_LENGTH_THRESHOLD_MEDIUM;
        }

        /**
         * 获取Fallback长度阈值-长文本（带默认值）
         */
        public int getFallbackThresholdLongOrDefault() {
            return fallbackLengthThresholdLong != null ? fallbackLengthThresholdLong : SmartKeywordSensitiveConstant.FALLBACK_LENGTH_THRESHOLD_LONG;
        }

        /**
         * 获取Fallback保留字符数-短文本（带默认值）
         */
        public int getFallbackKeepCharsShortOrDefault() {
            return fallbackKeepCharsShort != null ? fallbackKeepCharsShort : SmartKeywordSensitiveConstant.FALLBACK_KEEP_CHARS_SHORT;
        }

        /**
         * 获取Fallback保留字符数-中文本（带默认值）
         */
        public int getFallbackKeepCharsMediumOrDefault() {
            return fallbackKeepCharsMedium != null ? fallbackKeepCharsMedium : SmartKeywordSensitiveConstant.FALLBACK_KEEP_CHARS_MEDIUM;
        }

        /**
         * 获取Fallback保留字符数-长文本（带默认值）
         */
        public int getFallbackKeepCharsLongOrDefault() {
            return fallbackKeepCharsLong != null ? fallbackKeepCharsLong : SmartKeywordSensitiveConstant.FALLBACK_KEEP_CHARS_LONG;
        }

        /**
         * 获取Fallback保留字符数-超长文本（带默认值）
         */
        public int getFallbackKeepCharsExtraLongOrDefault() {
            return fallbackKeepCharsExtraLong != null ? fallbackKeepCharsExtraLong : SmartKeywordSensitiveConstant.FALLBACK_KEEP_CHARS_EXTRA_LONG;
        }

        /**
         * 获取固定星号数量（带默认值）
         */
        public int getFixedMaskLengthOrDefault() {
            return fixedMaskLength != null ? fixedMaskLength : SmartKeywordSensitiveConstant.DEFAULT_FIXED_MASK_LENGTH;
        }

        /**
         * 获取括号内容固定星号数量（带默认值，未配置时继承自fixedMaskLength）
         */
        public int getFixedBracketMaskLengthOrDefault() {
            if (fixedBracketMaskLength != null) {
                return fixedBracketMaskLength;
            }
            // 未配置时继承自fixedMaskLength
            return getFixedMaskLengthOrDefault();
        }
    }

    /**
     * 默认策略配置
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class DefaultStrategy {
        /**
         * 是否保留地域前缀（默认false，安全优先）
         */
        private Boolean keepRegion = false;
        /**
         * 是否保留行业信息（默认true，增加可读性）
         */
        private Boolean keepIndustry = true;
        /**
         * 是否保留组织类型后缀（默认true，保持基本可读性）
         */
        private Boolean keepOrgType = true;
        /**
         * 是否保留括号内容（默认false，括号通常是分支机构信息）
         */
        private Boolean keepBracket = false;
        /**
         * 是否保留品牌（默认false，品牌是敏感信息）
         */
        private Boolean keepBrand = false;
        /**
         * 掩码类型（asterisk/placeholder/hash）
         */
        private String maskType = SmartKeywordSensitiveConstant.DEFAULT_MASK_TYPE;
        /**
         * 占位符（当maskType为placeholder时使用）
         */
        private String placeholder = SmartKeywordSensitiveConstant.DEFAULT_PLACEHOLDER_PREFIX + SmartKeywordSensitiveConstant.DEFAULT_PLACEHOLDER_INDEX + SmartKeywordSensitiveConstant.DEFAULT_PLACEHOLDER_SUFFIX;
        /**
         * 是否保留长度信息
         */
        private Boolean keepLength = true;

        /**
         * 固定星号数量（keepLength=false时使用，默认3）
         */
        private Integer fixedMaskLength = SmartKeywordSensitiveConstant.DEFAULT_FIXED_MASK_LENGTH;

        /**
         * 括号内容固定星号数量（未配置时继承自fixedMaskLength）
         */
        private Integer fixedBracketMaskLength = SmartKeywordSensitiveConstant.DEFAULT_FIXED_BRACKET_MASK_LENGTH;

        /**
         * Fallback脱敏策略配置
         */
        private FallbackStrategy fallback = new FallbackStrategy();

        /**
         * 转换为 RuntimeStrategy
         */
        public RuntimeStrategy toRuntimeStrategy() {
            RuntimeStrategy config = new RuntimeStrategy();
            config.setKeepRegion(this.keepRegion);
            config.setKeepIndustry(this.keepIndustry);
            config.setKeepOrgType(this.keepOrgType);
            config.setKeepBracket(this.keepBracket);
            config.setKeepBrand(this.keepBrand);
            config.setMaskType(MaskType.fromCode(this.maskType));
            config.setPlaceholder(this.placeholder);
            config.setKeepLength(this.keepLength);
            config.setFixedMaskLength(this.fixedMaskLength);
            config.setFixedBracketMaskLength(this.fixedBracketMaskLength);
            config.setFallbackLengthThresholdShort(this.fallback.getLengthThresholdShort());
            config.setFallbackLengthThresholdMedium(this.fallback.getLengthThresholdMedium());
            config.setFallbackLengthThresholdLong(this.fallback.getLengthThresholdLong());
            config.setFallbackKeepCharsShort(this.fallback.getKeepCharsShort());
            config.setFallbackKeepCharsMedium(this.fallback.getKeepCharsMedium());
            config.setFallbackKeepCharsLong(this.fallback.getKeepCharsLong());
            config.setFallbackKeepCharsExtraLong(this.fallback.getKeepCharsExtraLong());
            return config;
        }

        public RuntimeStrategy toFallbackRuntimeStrategy() {
            RuntimeStrategy config = new RuntimeStrategy();
            config.setKeepRegion(false);
            config.setKeepIndustry(false);
            config.setKeepOrgType(false);
            config.setKeepBracket(false);
            config.setKeepBrand(false);
            config.setMaskType(MaskType.fromCode(this.fallback.getMaskType()));
            config.setPlaceholder(null);
            config.setKeepLength(this.fallback.getKeepLength());
            config.setFixedMaskLength(this.fallback.getFixedMaskLength());
            config.setFixedBracketMaskLength(this.fallback.getFixedBracketMaskLength());
            config.setFallbackLengthThresholdShort(this.fallback.getLengthThresholdShort());
            config.setFallbackLengthThresholdMedium(this.fallback.getLengthThresholdMedium());
            config.setFallbackLengthThresholdLong(this.fallback.getLengthThresholdLong());
            config.setFallbackKeepCharsShort(this.fallback.getKeepCharsShort());
            config.setFallbackKeepCharsMedium(this.fallback.getKeepCharsMedium());
            config.setFallbackKeepCharsLong(this.fallback.getKeepCharsLong());
            config.setFallbackKeepCharsExtraLong(this.fallback.getKeepCharsExtraLong());
            return config;
        }
    }

    /**
     * Fallback脱敏策略配置
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class FallbackStrategy {
        /**
         * 是否启用Fallback脱敏
         */
        private Boolean enabled = true;
        /**
         * Fallback掩码类型（asterisk/hash）
         */
        private String maskType = SmartKeywordSensitiveConstant.DEFAULT_MASK_TYPE;
        /**
         * 是否保留长度信息
         */
        private Boolean keepLength = true;
        /**
         * 固定星号数量（keepLength=false时使用）
         */
        private Integer fixedMaskLength = SmartKeywordSensitiveConstant.DEFAULT_FIXED_MASK_LENGTH;

        /**
         * 括号内容固定星号数量（未配置时继承自fixedMaskLength）
         */
        private Integer fixedBracketMaskLength = SmartKeywordSensitiveConstant.DEFAULT_FIXED_BRACKET_MASK_LENGTH;

        /**
         * 短文本长度阈值（文本长度≤此值时，保留首字符）
         */
        private Integer lengthThresholdShort = SmartKeywordSensitiveConstant.FALLBACK_LENGTH_THRESHOLD_SHORT;
        /**
         * 中文本长度阈值（文本长度在thresholdShort+1到thresholdMedium之间时，保留首尾各keepCharsShort个字符）
         */
        private Integer lengthThresholdMedium = SmartKeywordSensitiveConstant.FALLBACK_LENGTH_THRESHOLD_MEDIUM;
        /**
         * 长文本长度阈值（文本长度在thresholdMedium+1到thresholdLong之间时，保留首尾各keepCharsMedium个字符）
         */
        private Integer lengthThresholdLong = SmartKeywordSensitiveConstant.FALLBACK_LENGTH_THRESHOLD_LONG;
        /**
         * 保留字符数（长度≤thresholdShort时）
         */
        private Integer keepCharsShort = SmartKeywordSensitiveConstant.FALLBACK_KEEP_CHARS_SHORT;
        /**
         * 保留字符数（长度thresholdShort+1到thresholdMedium时）
         */
        private Integer keepCharsMedium = SmartKeywordSensitiveConstant.FALLBACK_KEEP_CHARS_MEDIUM;
        /**
         * 保留字符数（长度thresholdMedium+1到thresholdLong时）
         */
        private Integer keepCharsLong = SmartKeywordSensitiveConstant.FALLBACK_KEEP_CHARS_LONG;
        /**
         * 保留字符数（长度>thresholdLong时）
         */
        private Integer keepCharsExtraLong = SmartKeywordSensitiveConstant.FALLBACK_KEEP_CHARS_EXTRA_LONG;
    }

    /**
     * NLP配置
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class NLP {
        /**
         * 是否启用NLP
         */
        private boolean enabled = true;

        /**
         * NLP提供者（BUILT_IN / CUSTOM）
         */
        private String provider = SmartKeywordSensitiveConstant.NLP_PROVIDER_BUILT_IN;

        /**
         * NLP失败时是否降级到规则引擎
         */
        private boolean fallbackToRule = true;

        /**
         * NLP处理超时时间（毫秒）
         */
        private Integer timeoutMs = SmartKeywordSensitiveConstant.DEFAULT_NLP_TIMEOUT_MS;
    }

    /**
     * 关键词配置
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Keyword {
        /**
         * 关键词
         */
        private String keyword;

        /**
         * 匹配模式列表（用于多种写法匹配同一个关键词）
         */
        private List<String> patterns = new ArrayList<>();

        /**
         * 元信息（可选，用户预定义）
         */
        private Meta meta;

        /**
         * 策略配置
         */
        private Strategy strategy;
    }

    /**
     * 元信息配置
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Meta {
        /**
         * 地域信息
         */
        private String region;
        /**
         * 行业信息
         */
        private String industry;
        /**
         * 组织类型
         */
        private String orgType;
        /**
         * 品牌信息
         */
        private String brand;
        /**
         * 括号内容
         */
        private String bracketContent;

        /**
         * 转换为 MetaInfo
         */
        public MetaInfo toMetaInfo(String keyword) {
            MetaInfo meta = new MetaInfo(keyword);
            meta.setRegion(this.region);
            meta.setIndustry(this.industry);
            meta.setOrgType(this.orgType);
            meta.setBrand(this.brand);
            meta.setBracketContent(this.bracketContent);
            return meta;
        }
    }

    /**
     * 策略配置
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Strategy {
        /**
         * 是否保留地域前缀
         */
        private Boolean keepRegion;
        /**
         * 是否保留行业信息
         */
        private Boolean keepIndustry;
        /**
         * 是否保留组织类型后缀
         */
        private Boolean keepOrgType;
        /**
         * 是否保留括号内容
         */
        private Boolean keepBracket;
        /**
         * 是否保留品牌
         */
        private Boolean keepBrand;
        /**
         * 掩码类型（asterisk/placeholder/hash）
         */
        private String maskType;
        /**
         * 占位符（当maskType为placeholder时使用）
         */
        private String placeholder;
        /**
         * 是否保留长度信息
         */
        private Boolean keepLength = true;

        /**
         * 固定星号数量（keepLength=false时使用）
         */
        private Integer fixedMaskLength;

        /**
         * 括号内容固定星号数量（未配置时继承自fixedMaskLength）
         */
        private Integer fixedBracketMaskLength;

        private Fallback fallback;

        /**
         * 转换为 RuntimeStrategy
         */
        public RuntimeStrategy toRuntimeStrategy() {
            RuntimeStrategy config = new RuntimeStrategy();
            config.setKeepRegion(this.keepRegion);
            config.setKeepIndustry(this.keepIndustry);
            config.setKeepOrgType(this.keepOrgType);
            config.setKeepBracket(this.keepBracket);
            config.setKeepBrand(this.keepBrand);
            if (StringUtils.hasText(this.maskType)) {
                config.setMaskType(MaskType.fromCode(this.maskType));
            }
            // 如果是placeholder模式但未配置placeholder，使用默认值
            if (MaskType.PLACEHOLDER.getCode().equals(this.maskType)
                    && !StringUtils.hasText(this.placeholder)) {
                config.setPlaceholder(SmartKeywordSensitiveConstant.DEFAULT_PLACEHOLDER_PREFIX
                        + SmartKeywordSensitiveConstant.DEFAULT_PLACEHOLDER_INDEX
                        + SmartKeywordSensitiveConstant.DEFAULT_PLACEHOLDER_SUFFIX);
            } else {
                config.setPlaceholder(this.placeholder);
            }
            config.setKeepLength(this.keepLength);
            config.setFixedMaskLength(this.fixedMaskLength);
            config.setFixedBracketMaskLength(this.fixedBracketMaskLength);
            if (this.fallback != null) {
                config.setFallbackLengthThresholdShort(this.fallback.getLengthThresholdShort());
                config.setFallbackLengthThresholdMedium(this.fallback.getLengthThresholdMedium());
                config.setFallbackLengthThresholdLong(this.fallback.getLengthThresholdLong());
                config.setFallbackKeepCharsShort(this.fallback.getKeepCharsShort());
                config.setFallbackKeepCharsMedium(this.fallback.getKeepCharsMedium());
                config.setFallbackKeepCharsLong(this.fallback.getKeepCharsLong());
                config.setFallbackKeepCharsExtraLong(this.fallback.getKeepCharsExtraLong());
            }
            return config;
        }
    }

    /**
     * 关键词集合配置
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class KeywordSets {
        /**
         * 配置模式
         */
        public enum ConfigMode {
            /**
             * 追加模式：用户配置追加到默认值
             */
            APPEND,
            /**
             * 替换模式：用户配置完全替换默认值
             */
            REPLACE
        }

        /**
         * 配置模式（默认为追加模式）
         */
        private ConfigMode mode = ConfigMode.APPEND;

        /**
         * 金融机构敏感词（用户配置，默认从industry-keywords.txt提取value="金融"的关键词）
         */
        private Set<String> financialKeywords;

        /**
         * 政府机构敏感词（用户配置，默认从industry-keywords.txt提取）
         */
        private Set<String> governmentKeywords;

        /**
         * 教育机构敏感词（用户配置，默认从industry-keywords.txt提取value="教育"的关键词）
         */
        private Set<String> educationKeywords;

        /**
         * 非地域词黑名单（用户配置，默认值在Constant中定义）
         */
        private Set<String> nonRegionBlacklist;

        // ========== 初始化后的关键词集合（运行时使用） ==========

        private Set<String> initializedFinancialKeywords;
        private Set<String> initializedGovernmentKeywords;
        private Set<String> initializedEducationKeywords;
        private Set<String> initializedNonRegionBlacklist;

        /**
         * 初始化关键词集合（合并默认值和用户配置）
         */
        void initialize() {
            this.initializedFinancialKeywords = mergeOrReplace(SmartKeywordSensitiveConstant.FINANCIAL_KEYWORDS, financialKeywords);
            this.initializedGovernmentKeywords = mergeOrReplace(SmartKeywordSensitiveConstant.GOVERNMENT_KEYWORDS, governmentKeywords);
            this.initializedEducationKeywords = mergeOrReplace(SmartKeywordSensitiveConstant.EDUCATION_KEYWORDS, educationKeywords);
            this.initializedNonRegionBlacklist = mergeOrReplace(SmartKeywordSensitiveConstant.NON_REGION_BLACKLIST, nonRegionBlacklist);
        }

        private Set<String> mergeOrReplace(Set<String> defaultSet, Set<String> userSet) {
            if (userSet == null || userSet.isEmpty()) {
                return defaultSet;
            }

            if (mode == ConfigMode.REPLACE) {
                return Collections.unmodifiableSet(new HashSet<>(userSet));
            } else {
                Set<String> merged = new HashSet<>(defaultSet);
                merged.addAll(userSet);
                return Collections.unmodifiableSet(merged);
            }
        }

        // ========== 便捷getter方法（供其他组件使用，简化访问） ==========

        /**
         * 获取金融机构敏感词（初始化后的）
         */
        public Set<String> getFinancialKeywords() {
            return initializedFinancialKeywords != null ? initializedFinancialKeywords : Collections.emptySet();
        }

        /**
         * 获取政府机构敏感词（初始化后的）
         */
        public Set<String> getGovernmentKeywords() {
            return initializedGovernmentKeywords != null ? initializedGovernmentKeywords : Collections.emptySet();
        }

        /**
         * 获取教育机构敏感词（初始化后的）
         */
        public Set<String> getEducationKeywords() {
            return initializedEducationKeywords != null ? initializedEducationKeywords : Collections.emptySet();
        }

        /**
         * 获取非地域词黑名单（初始化后的）
         */
        public Set<String> getNonRegionBlacklist() {
            return initializedNonRegionBlacklist != null ? initializedNonRegionBlacklist : Collections.emptySet();
        }
    }

    /**
     * Fallback脱敏策略配置
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Fallback {
        private Integer lengthThresholdShort;
        private Integer lengthThresholdMedium;
        private Integer lengthThresholdLong;
        private Integer keepCharsShort;
        private Integer keepCharsMedium;
        private Integer keepCharsLong;
        private Integer keepCharsExtraLong;
    }

    /**
     * 保留率阈值配置（可覆盖SensitiveOrgType枚举的默认值）
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class RetentionThresholds {
        /**
         * 普通机构保留率阈值（默认80%）
         */
        private Double none;

        /**
         * 金融机构保留率阈值（默认60%）
         */
        private Double financial;

        /**
         * 政府机构保留率阈值（默认60%）
         */
        private Double government;

        /**
         * 教育机构保留率阈值（默认70%）
         */
        private Double education;

        /**
         * 获取普通机构阈值（带默认值）
         */
        public double getNoneOrDefault() {
            return none != null ? none : SensitiveOrgType.NONE.getRetentionThreshold();
        }

        /**
         * 获取金融机构阈值（带默认值）
         */
        public double getFinancialOrDefault() {
            return financial != null ? financial : SensitiveOrgType.FINANCIAL.getRetentionThreshold();
        }

        /**
         * 获取政府机构阈值（带默认值）
         */
        public double getGovernmentOrDefault() {
            return government != null ? government : SensitiveOrgType.GOVERNMENT.getRetentionThreshold();
        }

        /**
         * 获取教育机构阈值（带默认值）
         */
        public double getEducationOrDefault() {
            return education != null ? education : SensitiveOrgType.EDUCATION.getRetentionThreshold();
        }
    }
}
