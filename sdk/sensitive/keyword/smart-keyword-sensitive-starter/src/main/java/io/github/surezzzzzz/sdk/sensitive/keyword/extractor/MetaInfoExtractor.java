package io.github.surezzzzzz.sdk.sensitive.keyword.extractor;

import io.github.surezzzzzz.sdk.sensitive.keyword.annotation.SmartKeywordSensitiveComponent;
import io.github.surezzzzzz.sdk.sensitive.keyword.nlp.NLPProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Meta Information Extractor
 * 整合所有提取器，提取完整的元信息
 *
 * @author surezzzzzz
 */
@Slf4j
@SmartKeywordSensitiveComponent
public class MetaInfoExtractor {

    private static final int TOTAL_META_FIELDS = 5;
    private static final double FAILED_CONFIDENCE = 0.0;

    private final RegionExtractor regionExtractor;
    private final OrgTypeExtractor orgTypeExtractor;
    private final IndustryExtractor industryExtractor;
    private final BracketExtractor bracketExtractor;
    private final BrandExtractor brandExtractor;
    private final NLPProvider nlpProvider;

    @Autowired
    public MetaInfoExtractor(RegionExtractor regionExtractor,
                             OrgTypeExtractor orgTypeExtractor,
                             IndustryExtractor industryExtractor,
                             BracketExtractor bracketExtractor,
                             BrandExtractor brandExtractor,
                             @Autowired(required = false) NLPProvider nlpProvider) {
        this.regionExtractor = regionExtractor;
        this.orgTypeExtractor = orgTypeExtractor;
        this.industryExtractor = industryExtractor;
        this.bracketExtractor = bracketExtractor;
        this.brandExtractor = brandExtractor;
        this.nlpProvider = nlpProvider;

        log.info("MetaInfoExtractor initialized with NLPProvider={}", nlpProvider != null);
    }

    /**
     * 提取完整的元信息
     */
    public MetaInfo extract(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return new MetaInfo(keyword);
        }

        MetaInfo metaInfo = new MetaInfo(keyword);
        ExtractionContext context = new ExtractionContext(keyword);

        try {
            extractBracketInformation(metaInfo, context);
            extractByRuleEngine(metaInfo, context);
            enhanceWithNaturalLanguageProcessing(metaInfo, context);

            metaInfo.setConfidence(calculateConfidence(metaInfo));
        } catch (Exception e) {
            log.warn("Failed to extract meta info from keyword: {}, error: {}", keyword, e.getMessage());
            metaInfo.setConfidence(FAILED_CONFIDENCE);
        }

        return metaInfo;
    }

    /**
     * 提取括号内容及其元信息
     */
    private void extractBracketInformation(MetaInfo metaInfo, ExtractionContext context) {
        String bracketContent = bracketExtractor.extract(context.getCurrentKeyword());

        if (bracketContent == null) {
            log.debug("No bracket content found in keyword: {}", context.getCurrentKeyword());
            return;
        }

        metaInfo.setBracketContent(bracketContent);
        setBracketTypes(metaInfo, context.getCurrentKeyword(), bracketContent);
        metaInfo.setBracketMeta(extractBracketMeta(bracketContent));

        String keywordWithoutBracket = bracketExtractor.removeBracket(context.getCurrentKeyword());
        context.updateKeyword(keywordWithoutBracket);
        context.setMainBody(keywordWithoutBracket);

        log.debug("Keyword after bracket removal: {}", context.getCurrentKeyword());
    }

    /**
     * 设置括号类型
     */
    private void setBracketTypes(MetaInfo metaInfo, String keyword, String bracketContent) {
        String[] bracketTypes = bracketExtractor.extractBracketTypes(keyword);
        if (bracketTypes != null) {
            metaInfo.setLeftBracketType(bracketTypes[0]);
            metaInfo.setRightBracketType(bracketTypes[1]);
            log.debug("Extracted bracket content: {}, leftType: {}, rightType: {}",
                    bracketContent, bracketTypes[0], bracketTypes[1]);
        }
    }

    /**
     * 使用规则引擎提取元信息
     */
    private void extractByRuleEngine(MetaInfo metaInfo, ExtractionContext context) {
        extractRegion(metaInfo, context);
        extractBrand(metaInfo, context);
        extractOrgType(metaInfo, context);
        extractIndustry(metaInfo, context);

        metaInfo.setFromNLP(false);

        log.debug("Rule-based extracted meta info: region={}, brand={}, industry={}, orgType={}",
                metaInfo.getRegionByRule(), metaInfo.getBrandByRule(),
                metaInfo.getIndustryByRule(), metaInfo.getOrgTypeByRule());
    }

    /**
     * 提取地域信息
     */
    private void extractRegion(MetaInfo metaInfo, ExtractionContext context) {
        String region = regionExtractor.extract(context.getOriginalKeyword());
        if (region != null) {
            metaInfo.setRegion(region);
            metaInfo.setRegionByRule(region);
            context.removePrefixIfPresent(region);
        }
    }

    /**
     * 提取品牌信息
     */
    private void extractBrand(MetaInfo metaInfo, ExtractionContext context) {
        String brand = brandExtractor.extract(context.getCurrentKeyword());
        if (brand != null) {
            metaInfo.setBrand(brand);
            metaInfo.setBrandByRule(brand);
            context.removePrefixIfPresent(brand);
        }
    }

    /**
     * 提取组织类型
     */
    private void extractOrgType(MetaInfo metaInfo, ExtractionContext context) {
        String orgType = orgTypeExtractor.extract(context.getCurrentKeyword());
        if (orgType != null) {
            metaInfo.setOrgType(orgType);
            metaInfo.setOrgTypeByRule(orgType);
            context.removeSuffix(orgType);
        }
    }

    /**
     * 提取行业信息
     */
    private void extractIndustry(MetaInfo metaInfo, ExtractionContext context) {
        String industry = industryExtractor.extract(context.getCurrentKeyword());
        if (industry != null) {
            metaInfo.setIndustry(industry);
            metaInfo.setIndustryByRule(industry);
        }
    }

    /**
     * 使用自然语言处理增强元信息
     */
    private void enhanceWithNaturalLanguageProcessing(MetaInfo metaInfo, ExtractionContext context) {
        if (!isNaturalLanguageProcessingAvailable()) {
            return;
        }

        try {
            MetaInfo nlpMetaInfo = nlpProvider.extract(context.getMainBody());

            if (!hasValidMetaInformation(nlpMetaInfo)) {
                return;
            }

            boolean hasNewInformation = mergeNaturalLanguageProcessingResults(metaInfo, nlpMetaInfo, context);

            if (hasNewInformation) {
                metaInfo.setFromNLP(true);
            }

            logNaturalLanguageProcessingResults(metaInfo, context.getOriginalKeyword());

        } catch (Exception e) {
            log.debug("NLP extraction failed for keyword: {}, using rule-based only",
                    context.getOriginalKeyword(), e);
        }
    }

    /**
     * 合并自然语言处理的提取结果
     */
    private boolean mergeNaturalLanguageProcessingResults(MetaInfo metaInfo, MetaInfo nlpMetaInfo,
                                                          ExtractionContext context) {
        boolean hasNewInformation = false;

        if (nlpMetaInfo.getRegion() != null) {
            hasNewInformation |= mergeRegion(metaInfo, nlpMetaInfo);
        }

        if (nlpMetaInfo.getIndustry() != null) {
            hasNewInformation |= mergeIndustry(metaInfo, nlpMetaInfo, context.getMainBody());
        }

        if (nlpMetaInfo.getBrand() != null) {
            hasNewInformation |= mergeBrand(metaInfo, nlpMetaInfo);
        }

        if (nlpMetaInfo.getOrgType() != null) {
            hasNewInformation |= mergeOrgType(metaInfo, nlpMetaInfo);
        }

        return hasNewInformation;
    }

    /**
     * 合并地域信息
     */
    private boolean mergeRegion(MetaInfo metaInfo, MetaInfo nlpMetaInfo) {
        metaInfo.setRegionByNLP(nlpMetaInfo.getRegion());

        if (metaInfo.getRegion() == null) {
            metaInfo.setRegion(nlpMetaInfo.getRegion());
            return true;
        }

        if (nlpMetaInfo.getRegion().contains(metaInfo.getRegion())) {
            metaInfo.setRegion(nlpMetaInfo.getRegion());
            log.debug("Using NLP region (more complete): {} contains rule region: {}",
                    nlpMetaInfo.getRegion(), metaInfo.getRegionByRule());
        }

        return false;
    }

    /**
     * 合并行业信息
     */
    private boolean mergeIndustry(MetaInfo metaInfo, MetaInfo nlpMetaInfo, String mainBody) {
        metaInfo.setIndustryByNLP(nlpMetaInfo.getIndustry());

        String industryKeyword = findIndustryKeywordInText(mainBody, nlpMetaInfo.getIndustry());
        String finalIndustry = industryKeyword != null ? industryKeyword : nlpMetaInfo.getIndustry();

        if (metaInfo.getIndustry() == null) {
            metaInfo.setIndustry(finalIndustry);
            return true;
        } else {
            metaInfo.setIndustry(finalIndustry);
            log.debug("Using NLP industry (more accurate): {} instead of rule industry: {}",
                    nlpMetaInfo.getIndustry(), metaInfo.getIndustryByRule());
            return false;
        }
    }

    /**
     * 合并品牌信息
     */
    private boolean mergeBrand(MetaInfo metaInfo, MetaInfo nlpMetaInfo) {
        metaInfo.setBrandByNLP(nlpMetaInfo.getBrand());

        if (metaInfo.getBrand() == null) {
            metaInfo.setBrand(nlpMetaInfo.getBrand());
            return true;
        }

        if (nlpMetaInfo.getBrand().contains(metaInfo.getBrand())) {
            metaInfo.setBrand(nlpMetaInfo.getBrand());
            log.debug("Using NLP brand (more complete): {} contains rule brand: {}",
                    nlpMetaInfo.getBrand(), metaInfo.getBrandByRule());
        }

        return false;
    }

    /**
     * 合并组织类型
     */
    private boolean mergeOrgType(MetaInfo metaInfo, MetaInfo nlpMetaInfo) {
        metaInfo.setOrgTypeByNLP(nlpMetaInfo.getOrgType());

        if (metaInfo.getOrgType() == null) {
            metaInfo.setOrgType(nlpMetaInfo.getOrgType());
            return true;
        }

        // 对于组织类型，优先使用规则引擎的后缀匹配结果
        // 因为规则引擎基于严格的后缀匹配，更符合脱敏策略的假设
        // NLP可能识别出更长的组织类型（如"证券交易所"而非"交易所"），
        // 但这会导致保留率异常升高，触发不必要的降级
        // 因此仅当规则引擎未找到组织类型时，才使用NLP的结果
        log.debug("Keeping rule-based orgType: {} (NLP identified: {})",
                metaInfo.getOrgTypeByRule(), nlpMetaInfo.getOrgType());

        return false;
    }

    /**
     * 在文本中查找行业关键词
     */
    private String findIndustryKeywordInText(String text, String industryName) {
        if (industryExtractor == null) {
            return null;
        }
        return industryExtractor.extract(text);
    }

    /**
     * 计算置信度
     */
    private double calculateConfidence(MetaInfo metaInfo) {
        int fieldCount = 0;

        if (metaInfo.getRegion() != null) fieldCount++;
        if (metaInfo.getBrand() != null) fieldCount++;
        if (metaInfo.getIndustry() != null) fieldCount++;
        if (metaInfo.getOrgType() != null) fieldCount++;
        if (metaInfo.getBracketContent() != null) fieldCount++;

        return (double) fieldCount / TOTAL_META_FIELDS;
    }

    /**
     * 批量提取
     */
    public List<MetaInfo> batchExtract(List<String> keywords) {
        return keywords.stream()
                .map(this::extract)
                .collect(Collectors.toList());
    }

    /**
     * 提取括号内容的元信息
     */
    private MetaInfo extractBracketMeta(String bracketContent) {
        if (!StringUtils.hasText(bracketContent)) {
            return new MetaInfo(bracketContent);
        }

        MetaInfo metaInfo = new MetaInfo(bracketContent);
        String remaining = bracketContent;

        try {
            String region = regionExtractor.extract(remaining);
            if (region != null) {
                metaInfo.setRegion(region);
                remaining = remaining.substring(region.length());
            }

            String brand = brandExtractor.extract(remaining);
            if (brand != null) {
                metaInfo.setBrand(brand);
                remaining = remaining.substring(brand.length());
            }

            String orgType = orgTypeExtractor.extract(remaining);
            if (orgType != null) {
                metaInfo.setOrgType(orgType);
                remaining = remaining.substring(0, remaining.length() - orgType.length());
            }

            String industry = industryExtractor.extract(remaining);
            if (industry != null) {
                metaInfo.setIndustry(industry);
            }

            log.debug("Extracted bracket meta info: {}", metaInfo);

        } catch (Exception e) {
            log.warn("Failed to extract bracket meta info: {}, error: {}", bracketContent, e.getMessage());
        }

        return metaInfo;
    }

    // ==================== 辅助方法 ====================

    private boolean isNaturalLanguageProcessingAvailable() {
        return nlpProvider != null && nlpProvider.isAvailable();
    }

    private boolean hasValidMetaInformation(MetaInfo metaInfo) {
        return metaInfo != null && (
                metaInfo.getRegion() != null ||
                        metaInfo.getIndustry() != null ||
                        metaInfo.getBrand() != null ||
                        metaInfo.getOrgType() != null
        );
    }

    private void logNaturalLanguageProcessingResults(MetaInfo metaInfo, String originalKeyword) {
        log.debug("NLP extracted meta info: region={}, brand={}, industry={}, orgType={}",
                metaInfo.getRegionByNLP(), metaInfo.getBrandByNLP(),
                metaInfo.getIndustryByNLP(), metaInfo.getOrgTypeByNLP());
        log.debug("Merged NLP and rule-based meta info for keyword: {}", originalKeyword);
    }

    /**
     * 提取上下文 - 封装提取过程中的状态
     */
    private static class ExtractionContext {
        private final String originalKeyword;
        private String currentKeyword;
        private String mainBody;

        public ExtractionContext(String keyword) {
            this.originalKeyword = keyword;
            this.currentKeyword = keyword;
            this.mainBody = keyword;
        }

        public String getOriginalKeyword() {
            return originalKeyword;
        }

        public String getCurrentKeyword() {
            return currentKeyword;
        }

        public String getMainBody() {
            return mainBody;
        }

        public void updateKeyword(String keyword) {
            this.currentKeyword = keyword;
        }

        public void setMainBody(String mainBody) {
            this.mainBody = mainBody;
        }

        public void removePrefixIfPresent(String prefix) {
            if (currentKeyword.startsWith(prefix)) {
                currentKeyword = currentKeyword.substring(prefix.length());
            }
        }

        public void removeSuffix(String suffix) {
            if (currentKeyword.endsWith(suffix)) {
                currentKeyword = currentKeyword.substring(0, currentKeyword.length() - suffix.length());
            }
        }
    }
}
