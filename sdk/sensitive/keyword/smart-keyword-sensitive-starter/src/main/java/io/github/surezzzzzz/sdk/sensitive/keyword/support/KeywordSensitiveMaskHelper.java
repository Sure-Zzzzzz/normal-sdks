package io.github.surezzzzzz.sdk.sensitive.keyword.support;

import io.github.surezzzzzz.sdk.sensitive.keyword.annotation.SmartKeywordSensitiveComponent;
import io.github.surezzzzzz.sdk.sensitive.keyword.configuration.SmartKeywordSensitiveProperties;
import io.github.surezzzzzz.sdk.sensitive.keyword.constant.*;
import io.github.surezzzzzz.sdk.sensitive.keyword.exception.MaskException;
import io.github.surezzzzzz.sdk.sensitive.keyword.extractor.MetaInfo;
import io.github.surezzzzzz.sdk.sensitive.keyword.extractor.MetaInfoExtractor;
import io.github.surezzzzzz.sdk.sensitive.keyword.matcher.KeywordMatcher;
import io.github.surezzzzzz.sdk.sensitive.keyword.matcher.MatchResult;
import io.github.surezzzzzz.sdk.sensitive.keyword.recognizer.CompositeEntityRecognizer;
import io.github.surezzzzzz.sdk.sensitive.keyword.recognizer.RecognizeResult;
import io.github.surezzzzzz.sdk.sensitive.keyword.registry.KeywordRegistry;
import io.github.surezzzzzz.sdk.sensitive.keyword.strategy.MaskStrategy;
import io.github.surezzzzzz.sdk.sensitive.keyword.strategy.MaskStrategyFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

import static io.github.surezzzzzz.sdk.sensitive.keyword.support.MaskReasonHelper.*;

/**
 * Keyword Sensitive Mask Helper
 *
 * @author surezzzzzz
 */
@Slf4j
@SmartKeywordSensitiveComponent
public class KeywordSensitiveMaskHelper {

    private final KeywordRegistry keywordRegistry;
    private final MetaInfoExtractor metaInfoExtractor;
    private final MaskStrategyFactory maskStrategyFactory;
    private final CompositeEntityRecognizer organizationRecognizer;
    private final SmartKeywordSensitiveProperties properties;

    @Autowired
    public KeywordSensitiveMaskHelper(KeywordRegistry keywordRegistry,
                                      MetaInfoExtractor metaInfoExtractor,
                                      MaskStrategyFactory maskStrategyFactory,
                                      CompositeEntityRecognizer organizationRecognizer,
                                      SmartKeywordSensitiveProperties properties) {
        this.keywordRegistry = keywordRegistry;
        this.metaInfoExtractor = metaInfoExtractor;
        this.maskStrategyFactory = maskStrategyFactory;
        this.organizationRecognizer = organizationRecognizer;
        this.properties = properties;

        log.info("KeywordSensitiveMaskHelper initialized");
    }

    /**
     * 脱敏处理（自动识别 + 配置的关键词）
     */
    public String mask(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }

        text = text.trim();

        try {
            List<RecognizeResult> recognizeResults = organizationRecognizer.recognize(text);
            List<MatchResult> configMatches = matchConfiguredKeywords(text);

            List<MaskTask> tasks = prepareMaskTasks(recognizeResults, configMatches);

            if (tasks.isEmpty()) {
                log.debug("No organizations or keywords found in text, applying fallback masking");
                return applyFallbackMasking(text);
            }

            String maskedResult = executeMasking(text, tasks);

            if (maskedResult.equals(text)) {
                log.debug("Masked result equals original text, applying fallback masking");
                maskedResult = applyFallbackMasking(text);
            }

            return maskedResult;

        } catch (Exception e) {
            log.error("Failed to mask text", e);
            throw new MaskException(ErrorCode.MASK_PROCESS_FAILED, ErrorMessage.MASK_PROCESS_FAILED, e);
        }
    }

    /**
     * 批量脱敏
     */
    public List<String> batchMask(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return texts;
        }

        return texts.stream()
                .map(this::mask)
                .collect(Collectors.toList());
    }

    /**
     * 脱敏并返回详细信息（包含脱敏原因）
     */
    public MaskResultDetail maskWithDetail(String text) {
        if (!StringUtils.hasText(text)) {
            return createEmptyResultDetail(text);
        }

        String originalText = text.trim();
        StringBuilder reasonBuilder = new StringBuilder();

        try {
            // 识别和匹配
            List<RecognizeResult> recognizeResults = organizationRecognizer.recognize(originalText);
            List<MatchResult> configMatches = matchConfiguredKeywords(originalText);

            appendRecognizePhase(reasonBuilder, recognizeResults, configMatches);

            // 准备脱敏任务
            List<MaskTask> tasks = prepareMaskTasks(recognizeResults, configMatches);

            if (tasks.isEmpty()) {
                return handleNoMatchCase(originalText, recognizeResults, configMatches, reasonBuilder);
            }

            // 执行脱敏
            String maskedResult = executeMaskingWithDetails(originalText, tasks, reasonBuilder);

            // 检查是否需要fallback
            if (maskedResult.equals(originalText)) {
                return handleSameResultCase(originalText, recognizeResults, configMatches, reasonBuilder);
            }

            appendResultPhase(reasonBuilder, originalText, maskedResult);

            return new MaskResultDetail(originalText, maskedResult, recognizeResults, configMatches, reasonBuilder.toString());

        } catch (Exception e) {
            reasonBuilder.append(SmartKeywordSensitiveConstant.SEPARATOR_PIPE).append(PHASE_ERROR).append(e.getMessage());
            log.error("Failed to mask text with details", e);
            throw new MaskException(ErrorCode.MASK_PROCESS_FAILED, ErrorMessage.MASK_PROCESS_FAILED, e);
        }
    }

    /**
     * 检测文本中的敏感内容（不脱敏）
     */
    public DetectResult detect(String text) {
        if (!StringUtils.hasText(text)) {
            return new DetectResult(Collections.emptyList(), Collections.emptyList());
        }

        text = text.trim();

        List<RecognizeResult> organizations = organizationRecognizer.recognize(text);
        List<String> keywords = extractConfiguredKeywords(text);

        return new DetectResult(
                organizations.stream().map(RecognizeResult::getEntity).collect(Collectors.toList()),
                keywords
        );
    }

    /**
     * 判断文本是否包含敏感内容
     */
    public boolean contains(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }

        text = text.trim();

        if (!organizationRecognizer.recognize(text).isEmpty()) {
            return true;
        }

        KeywordMatcher configMatcher = keywordRegistry.getMatcher();
        return configMatcher != null && !configMatcher.isEmpty() && configMatcher.contains(text);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 准备脱敏任务（合并、去重、排序）
     */
    private List<MaskTask> prepareMaskTasks(List<RecognizeResult> recognizeResults, List<MatchResult> configMatches) {
        List<MaskTask> tasks = mergeRecognizeAndConfigResults(recognizeResults, configMatches);
        tasks = removeOverlappingTasks(tasks);
        tasks.sort(Comparator.comparingInt((MaskTask t) -> t.endIndex).reversed());
        return tasks;
    }

    /**
     * 匹配配置的关键词
     */
    private List<MatchResult> matchConfiguredKeywords(String text) {
        KeywordMatcher configMatcher = keywordRegistry.getMatcher();
        if (configMatcher == null || configMatcher.isEmpty()) {
            return Collections.emptyList();
        }
        return configMatcher.match(text);
    }

    /**
     * 提取配置的关键词（用于detect）
     */
    private List<String> extractConfiguredKeywords(String text) {
        KeywordMatcher configMatcher = keywordRegistry.getMatcher();
        if (configMatcher == null || configMatcher.isEmpty()) {
            return Collections.emptyList();
        }
        return configMatcher.match(text).stream()
                .map(MatchResult::getKeyword)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 合并识别结果和配置匹配结果
     */
    private List<MaskTask> mergeRecognizeAndConfigResults(List<RecognizeResult> recognizeResults, List<MatchResult> configMatches) {
        Map<String, MaskTask> taskMap = new LinkedHashMap<>();

        // 添加自动识别的组织
        for (RecognizeResult result : recognizeResults) {
            String key = createPositionKey(result.getStartIndex(), result.getEndIndex());
            taskMap.put(key, new MaskTask(
                    result.getEntity(),
                    result.getStartIndex(),
                    result.getEndIndex(),
                    false,
                    result.getSource()
            ));
        }

        // 添加配置的关键词（优先级更高，覆盖自动识别的）
        for (MatchResult match : configMatches) {
            String key = createPositionKey(match.getStartIndex(), match.getEndIndex());
            taskMap.put(key, new MaskTask(
                    match.getKeyword(),
                    match.getStartIndex(),
                    match.getEndIndex(),
                    true,
                    SmartKeywordSensitiveConstant.SOURCE_TYPE_CONFIG
            ));
        }

        return new ArrayList<>(taskMap.values());
    }

    /**
     * 创建位置键
     */
    private String createPositionKey(int startIndex, int endIndex) {
        return startIndex + "-" + endIndex;
    }

    /**
     * 去除重叠的匹配，保留最长的匹配
     */
    private List<MaskTask> removeOverlappingTasks(List<MaskTask> tasks) {
        if (tasks.isEmpty()) {
            return tasks;
        }

        // 按startIndex排序，如果startIndex相同则按长度倒序
        List<MaskTask> sorted = new ArrayList<>(tasks);
        sorted.sort((t1, t2) -> {
            int cmp = Integer.compare(t1.startIndex, t2.startIndex);
            if (cmp != 0) return cmp;
            return Integer.compare(t2.getLength(), t1.getLength());
        });

        List<MaskTask> result = new ArrayList<>();
        int lastEnd = -1;

        for (MaskTask task : sorted) {
            if (task.startIndex >= lastEnd) {
                result.add(task);
                lastEnd = task.endIndex;
            }
        }

        return result;
    }

    /**
     * 执行脱敏处理
     */
    private String executeMasking(String text, List<MaskTask> tasks) {
        StringBuilder result = new StringBuilder(text);

        for (MaskTask task : tasks) {
            String masked = maskSingleTask(task);
            result.replace(task.startIndex, task.endIndex, masked);

            log.debug("Masked {} ({}): {} -> {}, position: [{}, {})",
                    task.isConfigured ? "keyword" : "entity",
                    task.source, task.keyword, masked, task.startIndex, task.endIndex);
        }

        return result.toString();
    }

    /**
     * 执行脱敏并记录详情
     */
    private String executeMaskingWithDetails(String text, List<MaskTask> tasks, StringBuilder reasonBuilder) {
        StringBuilder result = new StringBuilder(text);
        List<String> maskDetails = new ArrayList<>(tasks.size());

        reasonBuilder.append(String.format(SmartKeywordSensitiveConstant.SEPARATOR_PIPE + PHASE_MASK +
                SmartKeywordSensitiveConstant.TEMPLATE_PROCESS_TASKS, tasks.size()));

        for (MaskTask task : tasks) {
            MaskTaskResult taskResult = maskTaskWithDetail(task);
            result.replace(task.startIndex, task.endIndex, taskResult.masked);
            maskDetails.add(taskResult.reason);
        }

        reasonBuilder.append(String.join(" | ", maskDetails));
        return result.toString();
    }

    /**
     * 脱敏单个任务
     */
    private String maskSingleTask(MaskTask task) {
        MetaInfo meta = getMetaInfo(task.keyword);
        SmartKeywordSensitiveProperties.RuntimeStrategy strategy = getStrategy(task.keyword, task.isConfigured);
        MaskStrategy maskStrategy = maskStrategyFactory.getStrategy(strategy.getMaskType());

        return maskStrategy.mask(task.keyword, meta, strategy);
    }

    /**
     * 脱敏单个任务并返回详情
     */
    private MaskTaskResult maskTaskWithDetail(MaskTask task) {
        SmartKeywordSensitiveProperties.RuntimeStrategy strategy = getStrategy(task.keyword, task.isConfigured);
        MaskStrategy maskStrategy = maskStrategyFactory.getStrategy(strategy.getMaskType());

        // 根据策略声明决定是否需要完整元信息提取
        MetaInfo meta = maskStrategy.requiresFullMetaExtraction()
                ? getMetaInfo(task.keyword)
                : null;

        // 根据策略声明决定是否需要保留率计算
        RetentionRateAdjustment adjustment = null;
        SmartKeywordSensitiveProperties.RuntimeStrategy actualStrategy = strategy;
        MaskReasonHelper.RetentionAdjustment reasonAdjustment = null;

        if (maskStrategy.requiresRetentionCalculation() && meta != null) {
            adjustment = calculateRetentionRateAdjustment(task.keyword, meta, strategy);
            actualStrategy = adjustment.getAdjustedConfig();
            reasonAdjustment = adjustment.toReasonAdjustment();
        }

        String masked = maskStrategy.mask(task.keyword, meta, actualStrategy);
        String reason = maskStrategy.buildMaskReason(
                task.keyword, task.source, meta, strategy, actualStrategy, reasonAdjustment, masked
        );

        return new MaskTaskResult(masked, reason);
    }

    /**
     * 获取策略配置
     */
    private SmartKeywordSensitiveProperties.RuntimeStrategy getStrategy(String keyword, boolean isConfigured) {
        if (isConfigured && keywordRegistry.contains(keyword)) {
            return keywordRegistry.getStrategy(keyword);
        }
        return keywordRegistry.getDefaultStrategy();
    }

    /**
     * 应用兜底脱敏
     */
    private String applyFallbackMasking(String text) {
        SmartKeywordSensitiveProperties.RuntimeStrategy fallbackStrategy = keywordRegistry.getFallbackStrategy();
        MaskStrategy strategy = maskStrategyFactory.getStrategy(fallbackStrategy.getMaskType());
        return strategy.maskWithFallback(text, fallbackStrategy);
    }

    /**
     * 获取元信息（优先级：用户配置 > MetaInfoExtractor自动提取）
     */
    private MetaInfo getMetaInfo(String keyword) {
        MetaInfo userMeta = keywordRegistry.getMetaInfo(keyword);
        if (userMeta != null && userMeta.hasValidInfo()) {
            log.debug("Using user-defined meta info for keyword: {}", keyword);
            return userMeta;
        }

        return metaInfoExtractor.extract(keyword);
    }

    /**
     * 提取主体关键词（去除括号）
     */
    private String extractMainKeyword(String keyword, MetaInfo meta) {
        if (meta.getBracketContent() == null) {
            return keyword;
        }

        String bracketStr = buildBracketString(meta);
        if (bracketStr != null && keyword.contains(bracketStr)) {
            return keyword.replace(bracketStr, "");
        }

        return keyword;
    }

    /**
     * 构建括号字符串
     */
    private String buildBracketString(MetaInfo meta) {
        if (meta.getLeftBracketType() == null) {
            return null;
        }

        String leftBracket = BracketType.CHINESE.name().equals(meta.getLeftBracketType())
                ? BracketType.CHINESE.getLeftBracket()
                : BracketType.ENGLISH.getLeftBracket();

        String rightBracket = BracketType.CHINESE.name().equals(meta.getRightBracketType())
                ? BracketType.CHINESE.getRightBracket()
                : BracketType.ENGLISH.getRightBracket();

        return leftBracket + meta.getBracketContent() + rightBracket;
    }

    /**
     * 计算保留率调整
     */
    private RetentionRateAdjustment calculateRetentionRateAdjustment(String keyword, MetaInfo meta,
                                                                     SmartKeywordSensitiveProperties.RuntimeStrategy config) {
        if (meta == null || !meta.hasValidInfo()) {
            return RetentionRateAdjustment.noAdjustment(config);
        }

        // 识别敏感机构类型
        SensitiveOrgType orgType = MaskStrategyHelper.detectSensitiveOrgType(keyword, meta, properties.getKeywordSets());
        double retentionThreshold = orgType.getRetentionThreshold();

        // 计算主体部分
        String mainKeyword = extractMainKeyword(keyword, meta);
        int totalLength = mainKeyword.length();

        // 计算初始保留率
        RetentionCalculator calculator = new RetentionCalculator(mainKeyword, meta, config);
        int retainedChars = calculator.calculateRetainedCharacters();
        double retentionRate = totalLength > 0 ? (double) retainedChars / totalLength : 0.0;

        // 如果保留率低于阈值，不需要调整
        if (retentionRate < retentionThreshold) {
            return RetentionRateAdjustment.belowThreshold(
                    config, totalLength, calculator.getComponentLengths(),
                    retainedChars, retentionRate, orgType, retentionThreshold
            );
        }

        // 需要降级
        RetentionRateAdjustment adjustment = performDowngrade(
                config, mainKeyword, meta, totalLength, calculator.getComponentLengths(),
                retainedChars, retentionRate, orgType, retentionThreshold
        );

        log.debug("Config adjusted for '{}' ({}): {:.1f}% → {:.1f}% (threshold={:.0f}%)",
                keyword, orgType, retentionRate * 100,
                adjustment.getFinalRetentionRate() * 100, retentionThreshold * 100);

        return adjustment;
    }

    /**
     * 执行降级策略（移除日志）
     */
    private RetentionRateAdjustment performDowngrade(SmartKeywordSensitiveProperties.RuntimeStrategy config,
                                                     String mainKeyword, MetaInfo meta, int totalLength,
                                                     ComponentLengths originalLengths, int retainedChars,
                                                     double retentionRate, SensitiveOrgType orgType,
                                                     double retentionThreshold) {
        SmartKeywordSensitiveProperties.RuntimeStrategy adjustedConfig = config.copy();
        StringBuilder downgradeReason = new StringBuilder();

        // 第一级降级：关闭地域
        adjustedConfig.setKeepRegion(false);
        RetentionCalculator calculator = new RetentionCalculator(mainKeyword, meta, adjustedConfig);
        int newRetainedChars = calculator.calculateRetainedCharacters();
        double newRetentionRate = totalLength > 0 ? (double) newRetainedChars / totalLength : 0.0;

        downgradeReason.append(String.format(SmartKeywordSensitiveConstant.TEMPLATE_FIRST_DOWNGRADE,
                retentionRate * 100, newRetentionRate * 100));

        // 第二级降级：关闭行业
        if (newRetentionRate >= SmartKeywordSensitiveConstant.SECOND_DOWNGRADE_THRESHOLD) {
            adjustedConfig.setKeepIndustry(false);
            calculator = new RetentionCalculator(mainKeyword, meta, adjustedConfig);
            newRetainedChars = calculator.calculateRetainedCharacters();
            double afterSecondDowngrade = totalLength > 0 ? (double) newRetainedChars / totalLength : 0.0;

            downgradeReason.append(String.format(SmartKeywordSensitiveConstant.TEMPLATE_SECOND_DOWNGRADE,
                    newRetentionRate * 100, afterSecondDowngrade * 100));
            newRetentionRate = afterSecondDowngrade;

            // 第三级降级：关闭组织类型
            if (newRetentionRate >= SmartKeywordSensitiveConstant.SECOND_DOWNGRADE_THRESHOLD) {
                adjustedConfig.setKeepOrgType(false);
                calculator = new RetentionCalculator(mainKeyword, meta, adjustedConfig);
                newRetainedChars = calculator.calculateRetainedCharacters();
                double afterThirdDowngrade = totalLength > 0 ? (double) newRetainedChars / totalLength : 0.0;

                downgradeReason.append(String.format(SmartKeywordSensitiveConstant.TEMPLATE_THIRD_DOWNGRADE,
                        newRetentionRate * 100, afterThirdDowngrade * 100));
                newRetentionRate = afterThirdDowngrade;
            }
        }

        return new RetentionRateAdjustment(adjustedConfig, totalLength, originalLengths,
                retainedChars, newRetainedChars, retentionRate, newRetentionRate,
                orgType, retentionThreshold, downgradeReason.toString());
    }

    // ==================== Detail相关辅助方法 ====================

    private MaskResultDetail createEmptyResultDetail(String text) {
        return new MaskResultDetail(text, text, Collections.emptyList(), Collections.emptyList(),
                PHASE_INPUT + SmartKeywordSensitiveConstant.MESSAGE_EMPTY_TEXT);
    }

    private void appendRecognizePhase(StringBuilder reasonBuilder, List<RecognizeResult> recognizeResults,
                                      List<MatchResult> configMatches) {
        reasonBuilder.append(String.format(PHASE_RECOGNIZE + SmartKeywordSensitiveConstant.TEMPLATE_RECOGNIZE_STATS,
                recognizeResults.size(),
                formatRecognizeResults(recognizeResults),
                configMatches.size(),
                formatConfigMatches(configMatches)));
    }

    private String formatRecognizeResults(List<RecognizeResult> results) {
        if (results.isEmpty()) {
            return "";
        }
        return SmartKeywordSensitiveConstant.SEPARATOR_COLON_SPACE + results.stream()
                .map(r -> String.format(SmartKeywordSensitiveConstant.TEMPLATE_ENTITY_SOURCE, r.getEntity(), r.getSource()))
                .collect(Collectors.joining(SmartKeywordSensitiveConstant.SEPARATOR_COMMA_SPACE));
    }

    private String formatConfigMatches(List<MatchResult> matches) {
        if (matches.isEmpty()) {
            return "";
        }
        return SmartKeywordSensitiveConstant.SEPARATOR_COLON_SPACE + matches.stream()
                .map(MatchResult::getKeyword)
                .distinct()
                .collect(Collectors.joining(SmartKeywordSensitiveConstant.SEPARATOR_COMMA_SPACE));
    }

    private MaskResultDetail handleNoMatchCase(String originalText, List<RecognizeResult> recognizeResults,
                                               List<MatchResult> configMatches, StringBuilder reasonBuilder) {
        reasonBuilder.append(SmartKeywordSensitiveConstant.SEPARATOR_PIPE)
                .append(PHASE_FALLBACK)
                .append(SmartKeywordSensitiveConstant.MESSAGE_NO_MATCH_FALLBACK);

        String maskedText = applyFallbackMasking(originalText);
        return new MaskResultDetail(originalText, maskedText, recognizeResults, configMatches, reasonBuilder.toString());
    }

    private MaskResultDetail handleSameResultCase(String originalText, List<RecognizeResult> recognizeResults,
                                                  List<MatchResult> configMatches, StringBuilder reasonBuilder) {
        reasonBuilder.append(SmartKeywordSensitiveConstant.SEPARATOR_PIPE)
                .append(PHASE_EXCEPTION)
                .append(SmartKeywordSensitiveConstant.MESSAGE_RESULT_SAME_FALLBACK);

        String maskedResult = applyFallbackMasking(originalText);
        return new MaskResultDetail(originalText, maskedResult, recognizeResults, configMatches, reasonBuilder.toString());
    }

    private void appendResultPhase(StringBuilder reasonBuilder, String originalText, String maskedText) {
        reasonBuilder.append(String.format(SmartKeywordSensitiveConstant.SEPARATOR_PIPE + PHASE_RESULT +
                        SmartKeywordSensitiveConstant.TEMPLATE_MASK_SUCCESS,
                originalText.length(), maskedText.length()));
    }

    // ==================== 内部类 ====================

    /**
     * 保留率计算器
     */
    private static class RetentionCalculator {
        private final String mainKeyword;
        private final MetaInfo meta;
        private final SmartKeywordSensitiveProperties.RuntimeStrategy config;
        private final ComponentLengths componentLengths;

        public RetentionCalculator(String mainKeyword, MetaInfo meta,
                                   SmartKeywordSensitiveProperties.RuntimeStrategy config) {
            this.mainKeyword = mainKeyword;
            this.meta = meta;
            this.config = config;
            this.componentLengths = new ComponentLengths();
        }

        public int calculateRetainedCharacters() {
            if (mainKeyword == null || mainKeyword.isEmpty()) {
                return 0;
            }

            boolean[] keepMask = new boolean[mainKeyword.length()];

            markRegion(keepMask);
            markIndustry(keepMask);
            markBrand(keepMask);
            markOrgType(keepMask);

            return countMarkedCharacters(keepMask);
        }

        private void markRegion(boolean[] keepMask) {
            if (!Boolean.TRUE.equals(config.getKeepRegion()) || meta.getRegion() == null) {
                return;
            }

            String region = meta.getRegionForCalculation();
            if (region != null) {
                int index = mainKeyword.indexOf(region);
                if (index >= 0) {
                    componentLengths.regionLength = region.length();
                    markRange(keepMask, index, region.length());
                }
            }
        }

        private void markIndustry(boolean[] keepMask) {
            if (!Boolean.TRUE.equals(config.getKeepIndustry()) || meta.getIndustry() == null) {
                return;
            }

            String industry = meta.getIndustryForCalculation();
            if (industry != null) {
                int index = mainKeyword.indexOf(industry);
                if (index >= 0) {
                    componentLengths.industryLength = industry.length();
                    markRange(keepMask, index, industry.length());
                }
            }
        }

        private void markBrand(boolean[] keepMask) {
            if (!Boolean.TRUE.equals(config.getKeepBrand()) || meta.getBrand() == null) {
                return;
            }

            String brand = meta.getBrandForCalculation();
            if (brand != null) {
                int index = mainKeyword.indexOf(brand);
                if (index >= 0) {
                    componentLengths.brandLength = brand.length();
                    markRange(keepMask, index, brand.length());
                }
            }
        }

        private void markOrgType(boolean[] keepMask) {
            if (!Boolean.TRUE.equals(config.getKeepOrgType()) || meta.getOrgType() == null) {
                return;
            }

            String orgType = meta.getOrgTypeForCalculation();
            if (orgType != null) {
                int index = mainKeyword.lastIndexOf(orgType);
                if (index >= 0) {
                    componentLengths.orgTypeLength = orgType.length();
                    markRange(keepMask, index, orgType.length());
                }
            }
        }

        private void markRange(boolean[] keepMask, int start, int length) {
            for (int i = start; i < start + length && i < keepMask.length; i++) {
                keepMask[i] = true;
            }
        }

        private int countMarkedCharacters(boolean[] keepMask) {
            int count = 0;
            for (boolean keep : keepMask) {
                if (keep) {
                    count++;
                }
            }
            return count;
        }

        public ComponentLengths getComponentLengths() {
            return componentLengths;
        }
    }

    /**
     * 组件长度信息
     */
    private static class ComponentLengths {
        int regionLength = 0;
        int industryLength = 0;
        int brandLength = 0;
        int orgTypeLength = 0;
    }

    /**
     * 脱敏任务
     */
    private static class MaskTask {
        final String keyword;
        final int startIndex;
        final int endIndex;
        final boolean isConfigured;
        final String source;

        MaskTask(String keyword, int startIndex, int endIndex, boolean isConfigured, String source) {
            this.keyword = keyword;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.isConfigured = isConfigured;
            this.source = source;
        }

        int getLength() {
            return endIndex - startIndex;
        }
    }

    /**
     * 脱敏任务结果
     */
    private static class MaskTaskResult {
        final String masked;
        final String reason;

        MaskTaskResult(String masked, String reason) {
            this.masked = masked;
            this.reason = reason;
        }
    }

    /**
     * 保留率调整详情
     */
    private static class RetentionRateAdjustment {
        private final SmartKeywordSensitiveProperties.RuntimeStrategy adjustedConfig;
        private final int mainLength;
        private final int regionLength;
        private final int industryLength;
        private final int brandLength;
        private final int orgTypeLength;
        private final int retainedChars;
        private final int finalRetainedChars;
        private final double retentionRate;
        private final double finalRetentionRate;
        private final SensitiveOrgType orgType;
        private final double threshold;
        private final String downgradeReason;

        public RetentionRateAdjustment(SmartKeywordSensitiveProperties.RuntimeStrategy adjustedConfig,
                                       int mainLength, ComponentLengths lengths,
                                       int retainedChars, int finalRetainedChars,
                                       double retentionRate, double finalRetentionRate,
                                       SensitiveOrgType orgType, double threshold, String downgradeReason) {
            this.adjustedConfig = adjustedConfig;
            this.mainLength = mainLength;
            this.regionLength = lengths.regionLength;
            this.industryLength = lengths.industryLength;
            this.brandLength = lengths.brandLength;
            this.orgTypeLength = lengths.orgTypeLength;
            this.retainedChars = retainedChars;
            this.finalRetainedChars = finalRetainedChars;
            this.retentionRate = retentionRate;
            this.finalRetentionRate = finalRetentionRate;
            this.orgType = orgType;
            this.threshold = threshold;
            this.downgradeReason = downgradeReason;
        }

        /**
         * 创建无需调整的结果
         */
        public static RetentionRateAdjustment noAdjustment(SmartKeywordSensitiveProperties.RuntimeStrategy config) {
            return new RetentionRateAdjustment(config, 0, new ComponentLengths(),
                    0, 0, 0.0, 0.0, SensitiveOrgType.NONE, 0.0,
                    SmartKeywordSensitiveConstant.MESSAGE_NO_ADJUSTMENT);
        }

        /**
         * 创建低于阈值的结果
         */
        public static RetentionRateAdjustment belowThreshold(SmartKeywordSensitiveProperties.RuntimeStrategy config,
                                                             int mainLength, ComponentLengths lengths,
                                                             int retainedChars, double retentionRate,
                                                             SensitiveOrgType orgType, double threshold) {
            return new RetentionRateAdjustment(config, mainLength, lengths,
                    retainedChars, retainedChars, retentionRate, retentionRate,
                    orgType, threshold, SmartKeywordSensitiveConstant.MESSAGE_RETENTION_BELOW_THRESHOLD);
        }

        /**
         * 转换为MaskReasonHelper的RetentionAdjustment
         */
        public MaskReasonHelper.RetentionAdjustment toReasonAdjustment() {
            return new MaskReasonHelper.RetentionAdjustment(
                    mainLength, regionLength, industryLength, brandLength, orgTypeLength,
                    retainedChars, finalRetainedChars, retentionRate, finalRetentionRate,
                    orgType.toString(), threshold, downgradeReason
            );
        }

        public SmartKeywordSensitiveProperties.RuntimeStrategy getAdjustedConfig() {
            return adjustedConfig;
        }

        public int getMainLength() {
            return mainLength;
        }

        public int getRegionLength() {
            return regionLength;
        }

        public int getIndustryLength() {
            return industryLength;
        }

        public int getBrandLength() {
            return brandLength;
        }

        public int getOrgTypeLength() {
            return orgTypeLength;
        }

        public int getRetainedChars() {
            return retainedChars;
        }

        public int getFinalRetainedChars() {
            return finalRetainedChars;
        }

        public double getRetentionRate() {
            return retentionRate;
        }

        public double getFinalRetentionRate() {
            return finalRetentionRate;
        }

        public String getOrgType() {
            return orgType.toString();
        }

        public double getThreshold() {
            return threshold;
        }

        public String getDowngradeReason() {
            return downgradeReason;
        }
    }

    /**
     * 脱敏结果详情
     */
    public static class MaskResultDetail {
        private final String originalText;
        private final String maskedText;
        private final List<RecognizeResult> recognizedOrganizations;
        private final List<MatchResult> configuredKeywords;
        private final String reason;

        public MaskResultDetail(String originalText, String maskedText,
                                List<RecognizeResult> recognizedOrganizations,
                                List<MatchResult> configuredKeywords,
                                String reason) {
            this.originalText = originalText;
            this.maskedText = maskedText;
            this.recognizedOrganizations = recognizedOrganizations;
            this.configuredKeywords = configuredKeywords;
            this.reason = reason;
        }

        public String getOriginalText() {
            return originalText;
        }

        public String getMaskedText() {
            return maskedText;
        }

        public List<RecognizeResult> getRecognizedOrganizations() {
            return recognizedOrganizations;
        }

        public List<MatchResult> getConfiguredKeywords() {
            return configuredKeywords;
        }

        public String getReason() {
            return reason;
        }

        public int getTotalMatchCount() {
            return recognizedOrganizations.size() + configuredKeywords.size();
        }

        @Override
        public String toString() {
            return "MaskResultDetail{" +
                    "originalText='" + originalText + '\'' +
                    ", maskedText='" + maskedText + '\'' +
                    ", recognizedOrgs=" + recognizedOrganizations.size() +
                    ", configuredKeywords=" + configuredKeywords.size() +
                    ", reason='" + reason + '\'' +
                    '}';
        }
    }

    /**
     * 检测结果
     */
    public static class DetectResult {
        private final List<String> organizations;
        private final List<String> keywords;

        public DetectResult(List<String> organizations, List<String> keywords) {
            this.organizations = organizations;
            this.keywords = keywords;
        }

        public List<String> getOrganizations() {
            return organizations;
        }

        public List<String> getKeywords() {
            return keywords;
        }

        public List<String> getAll() {
            List<String> all = new ArrayList<>(organizations);
            all.addAll(keywords);
            return all;
        }

        public int getTotalCount() {
            return organizations.size() + keywords.size();
        }

        @Override
        public String toString() {
            return "DetectResult{" +
                    "organizations=" + organizations +
                    ", keywords=" + keywords +
                    '}';
        }
    }

// ==================== 公共API方法 ====================

    /**
     * 构建脱敏原因字符串（用于测试或自定义场景）
     *
     * @param keyword      待脱敏关键词
     * @param meta         元信息
     * @param config       策略配置
     * @param maskedResult 脱敏后的结果
     * @return 脱敏原因字符串
     */
    public String buildDetailReason(String keyword, MetaInfo meta,
                                    SmartKeywordSensitiveProperties.RuntimeStrategy config,
                                    String maskedResult) {
        // 计算保留率调整
        RetentionRateAdjustment adjustment = calculateRetentionRateAdjustment(keyword, meta, config);
        SmartKeywordSensitiveProperties.RuntimeStrategy actualStrategy = adjustment.getAdjustedConfig();

        // 转换为MaskReasonHelper.RetentionAdjustment（使用新的toReasonAdjustment方法）
        MaskReasonHelper.RetentionAdjustment reasonAdjustment = adjustment.toReasonAdjustment();

        // 使用MaskReasonHelper构建详情
        return MaskReasonHelper.buildSingleTaskReason(keyword, null, meta, config,
                actualStrategy, reasonAdjustment, maskedResult);
    }

}
