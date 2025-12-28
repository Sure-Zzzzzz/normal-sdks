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

    /**
     * 构造函数 - 使用构造器注入（统一注入风格）
     *
     * @param keywordRegistry        关键词注册表（必须）
     * @param metaInfoExtractor      元信息提取器（必须）
     * @param maskStrategyFactory    脱敏策略工厂（必须）
     * @param organizationRecognizer 组织识别器（必须）
     * @param properties             配置属性（必须）
     */
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
     *
     * @param text 原始文本
     * @return 脱敏后的文本
     */
    public String mask(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }

        text = text.trim();

        try {
            // 1. 识别和匹配
            List<RecognizeResult> recognizeResults = organizationRecognizer.recognize(text);
            List<MatchResult> configMatches = matchConfiguredKeywords(text);

            // 2. 合并并排序任务
            List<MaskTask> tasks = mergeTasks(recognizeResults, configMatches);

            if (tasks.isEmpty()) {
                log.debug("No organizations or keywords found in text, applying fallback masking");
                return applyFallbackMasking(text);
            }

            // 3. 执行脱敏
            String maskedResult = executeMasking(text, tasks);

            // 4. 检查是否需要fallback
            if (maskedResult.equals(text)) {
                log.debug("Masked result equals original text, applying fallback masking");
                maskedResult = applyFallbackMasking(text);
            }

            return maskedResult;

        } catch (Exception e) {
            log.error("Failed to mask text", e);
            throw new MaskException(ErrorCode.MASK_PROCESS_FAILED,
                    ErrorMessage.MASK_PROCESS_FAILED, e);
        }
    }

    /**
     * 批量脱敏
     *
     * @param texts 原始文本列表
     * @return 脱敏后的文本列表
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
     *
     * @param text 原始文本
     * @return 脱敏结果详情
     */
    public MaskResultDetail maskWithDetail(String text) {
        if (!StringUtils.hasText(text)) {
            return new MaskResultDetail(text, text, Collections.emptyList(), Collections.emptyList(),
                    PHASE_INPUT + SmartKeywordSensitiveConstant.MESSAGE_EMPTY_TEXT);
        }

        String originalText = text.trim();
        StringBuilder reasonBuilder = new StringBuilder();

        try {
            // 1. 识别和匹配
            List<RecognizeResult> recognizeResults = organizationRecognizer.recognize(originalText);
            List<MatchResult> configMatches = matchConfiguredKeywords(originalText);

            reasonBuilder.append(String.format(PHASE_RECOGNIZE + SmartKeywordSensitiveConstant.TEMPLATE_RECOGNIZE_STATS,
                    recognizeResults.size(),
                    recognizeResults.isEmpty() ? "" : SmartKeywordSensitiveConstant.SEPARATOR_COLON_SPACE + recognizeResults.stream()
                            .map(r -> String.format(SmartKeywordSensitiveConstant.TEMPLATE_ENTITY_SOURCE, r.getEntity(), r.getSource()))
                            .collect(Collectors.joining(SmartKeywordSensitiveConstant.SEPARATOR_COMMA_SPACE)),
                    configMatches.size(),
                    configMatches.isEmpty() ? "" : SmartKeywordSensitiveConstant.SEPARATOR_COLON_SPACE + configMatches.stream()
                            .map(MatchResult::getKeyword)
                            .distinct()
                            .collect(Collectors.joining(SmartKeywordSensitiveConstant.SEPARATOR_COMMA_SPACE))));

            // 2. 合并并排序
            List<MaskTask> tasks = mergeTasks(recognizeResults, configMatches);

            if (tasks.isEmpty()) {
                reasonBuilder.append(SmartKeywordSensitiveConstant.SEPARATOR_PIPE).append(PHASE_FALLBACK).append(SmartKeywordSensitiveConstant.MESSAGE_NO_MATCH_FALLBACK);
                String maskedText = applyFallbackMasking(originalText);
                return new MaskResultDetail(originalText, maskedText, recognizeResults, configMatches, reasonBuilder.toString());
            }

            // 3. 执行脱敏并记录详情
            reasonBuilder.append(String.format(SmartKeywordSensitiveConstant.SEPARATOR_PIPE + PHASE_MASK + SmartKeywordSensitiveConstant.TEMPLATE_PROCESS_TASKS, tasks.size()));
            String maskedResult = executeMaskingWithDetails(originalText, tasks, reasonBuilder);

            // 4. 检查是否需要fallback
            if (maskedResult.equals(originalText)) {
                reasonBuilder.append(SmartKeywordSensitiveConstant.SEPARATOR_PIPE).append(PHASE_EXCEPTION).append(SmartKeywordSensitiveConstant.MESSAGE_RESULT_SAME_FALLBACK);
                maskedResult = applyFallbackMasking(originalText);
            } else {
                reasonBuilder.append(String.format(SmartKeywordSensitiveConstant.SEPARATOR_PIPE + PHASE_RESULT + SmartKeywordSensitiveConstant.TEMPLATE_MASK_SUCCESS,
                        originalText.length(), maskedResult.length()));
            }

            return new MaskResultDetail(originalText, maskedResult, recognizeResults, configMatches, reasonBuilder.toString());

        } catch (Exception e) {
            reasonBuilder.append(SmartKeywordSensitiveConstant.SEPARATOR_PIPE).append(PHASE_ERROR).append(e.getMessage());
            log.error("Failed to mask text with details", e);
            throw new MaskException(ErrorCode.MASK_PROCESS_FAILED,
                    ErrorMessage.MASK_PROCESS_FAILED, e);
        }
    }

    /**
     * 执行脱敏并记录详情
     *
     * @param text          原始文本
     * @param tasks         脱敏任务列表
     * @param reasonBuilder 原因构建器
     * @return 脱敏后的文本
     */
    private String executeMaskingWithDetails(String text, List<MaskTask> tasks, StringBuilder reasonBuilder) {
        StringBuilder result = new StringBuilder(text);
        List<String> maskDetails = new ArrayList<>(tasks.size());

        for (MaskTask task : tasks) {
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
                reasonAdjustment = convertToReasonAdjustment(adjustment);
            }

            String masked = maskStrategy.mask(task.keyword, meta, actualStrategy);
            result.replace(task.startIndex, task.endIndex, masked);

            // 让策略自己构建reason
            String detail = maskStrategy.buildMaskReason(
                    task.keyword, task.source, meta, strategy, actualStrategy, reasonAdjustment, masked
            );
            maskDetails.add(detail);
        }

        reasonBuilder.append(String.join(" | ", maskDetails));
        return result.toString();
    }

    /**
     * 构建脱敏原因字符串（用于测试或自定义场景）
     *
     * @param keyword      待脱敏关键词
     * @param meta         元信息
     * @param config       策略配置
     * @param maskedResult 脱敏后的结果
     * @return 脱敏原因字符串
     */
    public String buildDetailReason(String keyword, MetaInfo meta, SmartKeywordSensitiveProperties.RuntimeStrategy config, String maskedResult) {
        // 计算保留率调整
        RetentionRateAdjustment adjustment = calculateRetentionRateAdjustment(keyword, meta, config);
        SmartKeywordSensitiveProperties.RuntimeStrategy actualStrategy = adjustment.getAdjustedConfig();

        // 转换为MaskReasonHelper.RetentionAdjustment
        MaskReasonHelper.RetentionAdjustment reasonAdjustment = convertToReasonAdjustment(adjustment);

        // 使用MaskReasonHelper构建详情
        return MaskReasonHelper.buildSingleTaskReason(keyword, null, meta, config, actualStrategy, reasonAdjustment, maskedResult);
    }

    /**
     * 转换RetentionRateAdjustment为MaskReasonHelper.RetentionAdjustment
     *
     * @param adjustment 内部RetentionRateAdjustment对象
     * @return MaskReasonHelper.RetentionAdjustment对象
     */
    private MaskReasonHelper.RetentionAdjustment convertToReasonAdjustment(RetentionRateAdjustment adjustment) {
        return new MaskReasonHelper.RetentionAdjustment(
                adjustment.getMainLength(),
                adjustment.getRegionLength(),
                adjustment.getIndustryLength(),
                adjustment.getBrandLength(),
                adjustment.getOrgTypeLength(),
                adjustment.getRetainedChars(),
                adjustment.getFinalRetainedChars(),
                adjustment.getRetentionRate(),
                adjustment.getFinalRetentionRate(),
                adjustment.getOrgType(),
                adjustment.getThreshold(),
                adjustment.getDowngradeReason()
        );
    }

    /**
     * 检测文本中的敏感内容（不脱敏）
     *
     * @param text 原始文本
     * @return 检测到的组织机构和关键词列表
     */
    public DetectResult detect(String text) {
        if (!StringUtils.hasText(text)) {
            return new DetectResult(Collections.emptyList(), Collections.emptyList());
        }

        // Trim input to remove leading and trailing whitespaces
        text = text.trim();

        List<RecognizeResult> organizations = organizationRecognizer.recognize(text);

        KeywordMatcher configMatcher = keywordRegistry.getMatcher();
        List<String> keywords = new ArrayList<>();
        if (configMatcher != null && !configMatcher.isEmpty()) {
            keywords = configMatcher.match(text).stream()
                    .map(MatchResult::getKeyword)
                    .distinct()
                    .collect(Collectors.toList());
        }

        return new DetectResult(
                organizations.stream().map(RecognizeResult::getEntity).collect(Collectors.toList()),
                keywords
        );
    }

    /**
     * 判断文本是否包含敏感内容
     *
     * @param text 原始文本
     * @return true表示包含敏感内容
     */
    public boolean contains(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }

        // Trim input to remove leading and trailing whitespaces
        text = text.trim();

        // 检查自动识别的组织
        List<RecognizeResult> organizations = organizationRecognizer.recognize(text);
        if (!organizations.isEmpty()) {
            return true;
        }

        // 检查配置的关键词
        KeywordMatcher configMatcher = keywordRegistry.getMatcher();
        if (configMatcher != null && !configMatcher.isEmpty()) {
            return configMatcher.contains(text);
        }

        return false;
    }

    /**
     * 匹配配置的关键词
     *
     * @param text 文本
     * @return 配置匹配结果列表
     */
    private List<MatchResult> matchConfiguredKeywords(String text) {
        KeywordMatcher configMatcher = keywordRegistry.getMatcher();
        if (configMatcher == null || configMatcher.isEmpty()) {
            return Collections.emptyList();
        }
        return configMatcher.match(text);
    }

    /**
     * 合并识别结果和配置匹配结果，并按位置排序
     *
     * @param recognizeResults 识别结果
     * @param configMatches    配置匹配结果
     * @return 排序后的脱敏任务列表
     */
    private List<MaskTask> mergeTasks(List<RecognizeResult> recognizeResults, List<MatchResult> configMatches) {
        List<MaskTask> tasks = mergeResults(recognizeResults, configMatches);
        // 从后往前排序，避免替换时的位置偏移
        tasks.sort(Comparator.comparingInt((MaskTask t) -> t.endIndex).reversed());
        return tasks;
    }

    /**
     * 执行脱敏处理
     *
     * @param text  原始文本
     * @param tasks 脱敏任务列表（已排序）
     * @return 脱敏后的文本
     */
    private String executeMasking(String text, List<MaskTask> tasks) {
        StringBuilder result = new StringBuilder(text);

        for (MaskTask task : tasks) {
            MetaInfo meta = getMetaInfo(task.keyword);
            SmartKeywordSensitiveProperties.RuntimeStrategy strategy = getStrategy(task.keyword, task.isConfigured);

            // 根据策略的MaskType选择对应的策略实现
            MaskStrategy maskStrategy = maskStrategyFactory.getStrategy(strategy.getMaskType());
            String masked = maskStrategy.mask(task.keyword, meta, strategy);

            result.replace(task.startIndex, task.endIndex, masked);

            log.debug("Masked {} ({}): {} -> {}, position: [{}, {})",
                    task.isConfigured ? "keyword" : "entity",
                    task.source,
                    task.keyword, masked, task.startIndex, task.endIndex);
        }

        return result.toString();
    }

    /**
     * 合并识别结果和配置匹配结果
     */
    private List<MaskTask> mergeResults(List<RecognizeResult> recognizeResults, List<MatchResult> configMatches) {
        Map<String, MaskTask> taskMap = new LinkedHashMap<>();

        // 1. 添加自动识别的组织
        for (RecognizeResult result : recognizeResults) {
            String key = result.getStartIndex() + "-" + result.getEndIndex();
            taskMap.put(key, new MaskTask(
                    result.getEntity(),
                    result.getStartIndex(),
                    result.getEndIndex(),
                    false,  // 不是配置的
                    result.getSource()
            ));
        }

        // 2. 添加配置的关键词（优先级更高，覆盖自动识别的）
        for (MatchResult match : configMatches) {
            String key = match.getStartIndex() + "-" + match.getEndIndex();
            taskMap.put(key, new MaskTask(
                    match.getKeyword(),
                    match.getStartIndex(),
                    match.getEndIndex(),
                    true,  // 是配置的
                    SmartKeywordSensitiveConstant.SOURCE_TYPE_CONFIG
            ));
        }

        return new ArrayList<>(taskMap.values());
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

        // 根据fallbackStrategy的MaskType选择对应的策略并调用其maskWithFallback方法
        MaskStrategy strategy = maskStrategyFactory.getStrategy(fallbackStrategy.getMaskType());
        return strategy.maskWithFallback(text, fallbackStrategy);
    }

    /**
     * 获取元信息（优先级：用户配置 > MetaInfoExtractor自动提取（含NLP增强））
     */
    private MetaInfo getMetaInfo(String keyword) {
        // 1. 优先使用用户预定义的元信息
        MetaInfo userMeta = keywordRegistry.getMetaInfo(keyword);
        if (userMeta != null && userMeta.hasValidInfo()) {
            log.debug("Using user-defined meta info for keyword: {}", keyword);
            return userMeta;
        }

        // 2. 使用MetaInfoExtractor自动提取（包含规则引擎 + NLP增强）
        return metaInfoExtractor.extract(keyword);
    }


    /**
     * 脱敏任务
     */
    private static class MaskTask {
        String keyword;
        int startIndex;
        int endIndex;
        boolean isConfigured;  // 是否来自配置文件
        String source;  // RULE / NLP / CONFIG

        MaskTask(String keyword, int startIndex, int endIndex, boolean isConfigured, String source) {
            this.keyword = keyword;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.isConfigured = isConfigured;
            this.source = source;
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
                                List<RecognizeResult> recognizedOrganizations, List<MatchResult> configuredKeywords,
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
     * 根据保留率调整配置，防止100%保留导致脱敏失效
     * 此方法与DefaultMaskStrategy中的逻辑保持一致
     *
     * @param keyword 待脱敏关键词
     * @param meta    元信息
     * @param config  原始策略配置
     * @return 保留率调整详情
     */
    private RetentionRateAdjustment calculateRetentionRateAdjustment(String keyword, MetaInfo meta, SmartKeywordSensitiveProperties.RuntimeStrategy config) {
        // 如果没有有效元信息，不需要调整
        if (meta == null || !meta.hasValidInfo()) {
            return new RetentionRateAdjustment(config, 0, 0, 0, 0, 0, 0, 0, 0.0, 0.0, SensitiveOrgType.NONE, 0.0, SmartKeywordSensitiveConstant.MESSAGE_NO_ADJUSTMENT);
        }

        // 识别敏感机构类型并获取对应的保留率阈值
        SensitiveOrgType orgType = MaskStrategyHelper.detectSensitiveOrgType(keyword, properties.getKeywordSets());
        double retentionThreshold = orgType.getRetentionThreshold();

        // 计算主体部分的保留率（不考虑括号内容）
        String mainKeyword = keyword;
        int totalLength = keyword.length();

        // 如果有括号，去除括号部分计算主体长度
        if (meta.getBracketContent() != null) {
            String bracketStr = null;
            if (meta.getLeftBracketType() != null) {
                String leftBracket = BracketType.CHINESE.name().equals(meta.getLeftBracketType()) ? BracketType.CHINESE.getLeftBracket() : BracketType.ENGLISH.getLeftBracket();
                String rightBracket = BracketType.CHINESE.name().equals(meta.getRightBracketType()) ? BracketType.CHINESE.getRightBracket() : BracketType.ENGLISH.getRightBracket();
                bracketStr = leftBracket + meta.getBracketContent() + rightBracket;
            }
            if (bracketStr != null && keyword.contains(bracketStr)) {
                mainKeyword = keyword.replace(bracketStr, "");
                totalLength = mainKeyword.length();
            }
        }

        // 计算各元信息在主体中的字符数（使用boolean数组避免重复计算重叠部分）
        boolean[] keepMask = new boolean[totalLength];
        int regionLength = 0;
        int industryLength = 0;
        int brandLength = 0;
        int orgTypeLength = 0;

        if (Boolean.TRUE.equals(config.getKeepRegion()) && meta.getRegion() != null) {
            // 使用ForCalculation方法获取实际在文本中存在的关键词
            String region = meta.getRegionForCalculation();
            if (region != null) {
                int index = mainKeyword.indexOf(region);
                if (index >= 0) {
                    regionLength = region.length();
                    for (int i = index; i < index + region.length() && i < totalLength; i++) {
                        keepMask[i] = true;
                    }
                }
            }
        }

        if (Boolean.TRUE.equals(config.getKeepIndustry()) && meta.getIndustry() != null) {
            // 使用ForCalculation方法获取实际在文本中存在的关键词
            String industry = meta.getIndustryForCalculation();
            if (industry != null) {
                int index = mainKeyword.indexOf(industry);
                if (index >= 0) {
                    industryLength = industry.length();
                    for (int i = index; i < index + industry.length() && i < totalLength; i++) {
                        keepMask[i] = true;
                    }
                }
            }
        }

        if (Boolean.TRUE.equals(config.getKeepBrand()) && meta.getBrand() != null) {
            // 使用ForCalculation方法获取实际在文本中存在的关键词
            String brand = meta.getBrandForCalculation();
            if (brand != null) {
                int index = mainKeyword.indexOf(brand);
                if (index >= 0) {
                    brandLength = brand.length();
                    for (int i = index; i < index + brand.length() && i < totalLength; i++) {
                        keepMask[i] = true;
                    }
                }
            }
        }

        if (Boolean.TRUE.equals(config.getKeepOrgType()) && meta.getOrgType() != null) {
            // 使用ForCalculation方法获取实际在文本中存在的关键词
            String orgTypeStr = meta.getOrgTypeForCalculation();
            if (orgTypeStr != null) {
                int index = mainKeyword.lastIndexOf(orgTypeStr);  // 使用lastIndexOf避免匹配到中间的
                if (index >= 0) {
                    orgTypeLength = orgTypeStr.length();
                    for (int i = index; i < index + orgTypeStr.length() && i < totalLength; i++) {
                        keepMask[i] = true;
                    }
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

        // 如果保留率低于阈值，不需要调整
        if (retentionRate < retentionThreshold) {
            return new RetentionRateAdjustment(config, totalLength, regionLength, industryLength, brandLength, orgTypeLength, retainedChars, retainedChars, retentionRate, retentionRate, orgType, retentionThreshold, SmartKeywordSensitiveConstant.MESSAGE_RETENTION_BELOW_THRESHOLD);
        }

        // 需要降级，创建新的配置对象
        SmartKeywordSensitiveProperties.RuntimeStrategy adjustedConfig = config.copy();
        StringBuilder downgradeReason = new StringBuilder();

        // 第一级降级：关闭地域保留，重新计算保留字符数
        adjustedConfig.setKeepRegion(false);
        int newRetainedChars = calculateRetainedChars(mainKeyword, meta, adjustedConfig);
        double newRetentionRate = totalLength > 0 ? (double) newRetainedChars / totalLength : 0.0;

        downgradeReason.append(String.format(SmartKeywordSensitiveConstant.TEMPLATE_FIRST_DOWNGRADE, retentionRate * 100, newRetentionRate * 100));

        // 如果降级后保留率仍 >= SECOND_DOWNGRADE_THRESHOLD，继续降级
        if (newRetentionRate >= SmartKeywordSensitiveConstant.SECOND_DOWNGRADE_THRESHOLD) {
            adjustedConfig.setKeepIndustry(false);
            newRetainedChars = calculateRetainedChars(mainKeyword, meta, adjustedConfig);
            double afterSecondDowngrade = totalLength > 0 ? (double) newRetainedChars / totalLength : 0.0;
            downgradeReason.append(String.format(SmartKeywordSensitiveConstant.TEMPLATE_SECOND_DOWNGRADE, newRetentionRate * 100, afterSecondDowngrade * 100));
            newRetentionRate = afterSecondDowngrade;

            // 如果第二级降级后保留率仍 >= SECOND_DOWNGRADE_THRESHOLD，第三级降级关闭组织类型保留
            if (newRetentionRate >= SmartKeywordSensitiveConstant.SECOND_DOWNGRADE_THRESHOLD) {
                adjustedConfig.setKeepOrgType(false);
                newRetainedChars = calculateRetainedChars(mainKeyword, meta, adjustedConfig);
                double afterThirdDowngrade = totalLength > 0 ? (double) newRetainedChars / totalLength : 0.0;
                downgradeReason.append(String.format(SmartKeywordSensitiveConstant.TEMPLATE_THIRD_DOWNGRADE, newRetentionRate * 100, afterThirdDowngrade * 100));
                newRetentionRate = afterThirdDowngrade;
            }
        }

        log.debug("Config adjusted for '{}' ({}): {:.1f}% → {:.1f}% (threshold={:.0f}%)",
                keyword, orgType, retentionRate * 100, newRetentionRate * 100, retentionThreshold * 100);

        return new RetentionRateAdjustment(adjustedConfig, totalLength, regionLength, industryLength, brandLength, orgTypeLength, retainedChars, newRetainedChars, retentionRate, newRetentionRate, orgType, retentionThreshold, downgradeReason.toString());
    }

    /**
     * 计算根据配置实际会保留的字符数（使用boolean数组避免重复计算重叠部分）
     *
     * @param mainKeyword 主体关键词（不含括号）
     * @param meta        元信息
     * @param config      策略配置
     * @return 实际保留的字符数
     */
    private int calculateRetainedChars(String mainKeyword, MetaInfo meta, SmartKeywordSensitiveProperties.RuntimeStrategy config) {
        if (mainKeyword == null || mainKeyword.isEmpty()) {
            return 0;
        }

        boolean[] keepMask = new boolean[mainKeyword.length()];

        // 地域 - 使用ForCalculation方法获取实际在文本中存在的关键词
        if (Boolean.TRUE.equals(config.getKeepRegion()) && meta.getRegion() != null) {
            String region = meta.getRegionForCalculation();
            if (region != null) {
                int index = mainKeyword.indexOf(region);
                if (index >= 0) {
                    for (int i = index; i < index + region.length() && i < mainKeyword.length(); i++) {
                        keepMask[i] = true;
                    }
                }
            }
        }

        // 行业 - 使用ForCalculation方法获取实际在文本中存在的关键词
        if (Boolean.TRUE.equals(config.getKeepIndustry()) && meta.getIndustry() != null) {
            String industry = meta.getIndustryForCalculation();
            if (industry != null) {
                int index = mainKeyword.indexOf(industry);
                if (index >= 0) {
                    for (int i = index; i < index + industry.length() && i < mainKeyword.length(); i++) {
                        keepMask[i] = true;
                    }
                }
            }
        }

        // 品牌 - 使用ForCalculation方法获取实际在文本中存在的关键词
        if (Boolean.TRUE.equals(config.getKeepBrand()) && meta.getBrand() != null) {
            String brand = meta.getBrandForCalculation();
            if (brand != null) {
                int index = mainKeyword.indexOf(brand);
                if (index >= 0) {
                    for (int i = index; i < index + brand.length() && i < mainKeyword.length(); i++) {
                        keepMask[i] = true;
                    }
                }
            }
        }

        // 组织类型 - 使用ForCalculation方法获取实际在文本中存在的关键词
        if (Boolean.TRUE.equals(config.getKeepOrgType()) && meta.getOrgType() != null) {
            String orgType = meta.getOrgTypeForCalculation();
            if (orgType != null) {
                int index = mainKeyword.lastIndexOf(orgType);
                if (index >= 0) {
                    for (int i = index; i < index + orgType.length() && i < mainKeyword.length(); i++) {
                        keepMask[i] = true;
                    }
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

        public RetentionRateAdjustment(SmartKeywordSensitiveProperties.RuntimeStrategy adjustedConfig, int mainLength, int regionLength, int industryLength, int brandLength, int orgTypeLength, int retainedChars, int finalRetainedChars, double retentionRate, double finalRetentionRate, SensitiveOrgType orgType, double threshold, String downgradeReason) {
            this.adjustedConfig = adjustedConfig;
            this.mainLength = mainLength;
            this.regionLength = regionLength;
            this.industryLength = industryLength;
            this.brandLength = brandLength;
            this.orgTypeLength = orgTypeLength;
            this.retainedChars = retainedChars;
            this.finalRetainedChars = finalRetainedChars;
            this.retentionRate = retentionRate;
            this.finalRetentionRate = finalRetentionRate;
            this.orgType = orgType;
            this.threshold = threshold;
            this.downgradeReason = downgradeReason;
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
}
