package io.github.surezzzzzz.sdk.sensitive.keyword.extractor;

import io.github.surezzzzzz.sdk.sensitive.keyword.annotation.SmartKeywordSensitiveComponent;
import io.github.surezzzzzz.sdk.sensitive.keyword.constant.SmartKeywordSensitiveConstant;
import io.github.surezzzzzz.sdk.sensitive.keyword.nlp.NLPProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

/**
 * Meta Information Extractor
 * 整合所有提取器，提取完整的元信息
 *
 * @author surezzzzzz
 */
@Slf4j
@SmartKeywordSensitiveComponent
public class MetaInfoExtractor {

    private final RegionExtractor regionExtractor;
    private final OrgTypeExtractor orgTypeExtractor;
    private final IndustryExtractor industryExtractor;
    private final BracketExtractor bracketExtractor;
    private final BrandExtractor brandExtractor;
    private final NLPProvider nlpProvider;  // 可选依赖

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
     *
     * @param keyword 关键词
     * @return 元信息
     */
    public MetaInfo extract(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return new MetaInfo(keyword);
        }

        MetaInfo meta = new MetaInfo(keyword);
        String originalKeyword = keyword;

        try {
            // ==================== 阶段1：提取括号内容 ====================
            String bracketContent = bracketExtractor.extract(keyword);
            if (bracketContent != null) {
                meta.setBracketContent(bracketContent);
                String[] bracketTypes = bracketExtractor.extractBracketTypes(keyword);
                if (bracketTypes != null) {
                    meta.setLeftBracketType(bracketTypes[0]);
                    meta.setRightBracketType(bracketTypes[1]);
                    log.debug("Extracted bracket content: {}, leftType: {}, rightType: {}", bracketContent, bracketTypes[0], bracketTypes[1]);
                }
                MetaInfo bracketMeta = extractBracketMeta(bracketContent);
                meta.setBracketMeta(bracketMeta);
                keyword = bracketExtractor.removeBracket(keyword);
                log.debug("Keyword after bracket removal: {}", keyword);
            } else {
                log.debug("No bracket content found in keyword: {}", keyword);
            }

            // ==================== 阶段2：规则引擎提取 ====================
            String region = regionExtractor.extract(originalKeyword);
            if (region != null) {
                meta.setRegion(region);
                meta.setRegionByRule(region);
                if (keyword.startsWith(region)) {
                    keyword = keyword.substring(region.length());
                }
            }

            String brand = brandExtractor.extract(keyword);
            if (brand != null) {
                meta.setBrand(brand);
                meta.setBrandByRule(brand);
                if (keyword.startsWith(brand)) {
                    keyword = keyword.substring(brand.length());
                }
            }

            String orgType = orgTypeExtractor.extract(keyword);
            if (orgType != null) {
                meta.setOrgType(orgType);
                meta.setOrgTypeByRule(orgType);
                keyword = keyword.substring(0, keyword.length() - orgType.length());
            }

            String industry = industryExtractor.extract(keyword);
            if (industry != null) {
                meta.setIndustry(industry);
                meta.setIndustryByRule(industry);
            }

            // 设置置信度
            meta.setConfidence(calculateConfidence(meta));
            meta.setFromNLP(false);

            log.debug("Rule-based extracted meta info: region={}, brand={}, industry={}, orgType={}",
                    meta.getRegionByRule(), meta.getBrandByRule(), meta.getIndustryByRule(), meta.getOrgTypeByRule());

            // ==================== 阶段3：NLP增强（如果可用） ====================
            if (nlpProvider != null && nlpProvider.isAvailable()) {
                try {
                    MetaInfo nlpMeta = nlpProvider.extract(originalKeyword);

                    if (nlpMeta != null && (nlpMeta.getRegion() != null || nlpMeta.getIndustry() != null ||
                            nlpMeta.getBrand() != null || nlpMeta.getOrgType() != null)) {

                        boolean hasNLPResult = false;

                        if (nlpMeta.getRegion() != null) {
                            meta.setRegionByNLP(nlpMeta.getRegion());
                            if (meta.getRegion() == null) {
                                meta.setRegion(nlpMeta.getRegion());
                                hasNLPResult = true;
                            } else if (nlpMeta.getRegion().contains(meta.getRegion())) {
                                meta.setRegion(nlpMeta.getRegion());
                                log.debug("Using NLP region (more complete): {} contains rule region: {}", nlpMeta.getRegion(), meta.getRegionByRule());
                            }
                        }

                        if (nlpMeta.getIndustry() != null) {
                            meta.setIndustryByNLP(nlpMeta.getIndustry());
                            if (meta.getIndustry() == null) {
                                String industryKeyword = findIndustryKeywordInText(originalKeyword, nlpMeta.getIndustry());
                                if (industryKeyword != null) {
                                    meta.setIndustry(industryKeyword);
                                    hasNLPResult = true;
                                } else {
                                    meta.setIndustry(nlpMeta.getIndustry());
                                    hasNLPResult = true;
                                }
                            } else {
                                String industryKeyword = findIndustryKeywordInText(originalKeyword, nlpMeta.getIndustry());
                                if (industryKeyword != null) {
                                    meta.setIndustry(industryKeyword);
                                    log.debug("Using NLP industry (more accurate): {} instead of rule industry: {}", nlpMeta.getIndustry(), meta.getIndustryByRule());
                                } else {
                                    meta.setIndustry(nlpMeta.getIndustry());
                                    log.debug("Using NLP industry (more accurate): {} instead of rule industry: {}", nlpMeta.getIndustry(), meta.getIndustryByRule());
                                }
                            }
                        }

                        if (nlpMeta.getBrand() != null) {
                            meta.setBrandByNLP(nlpMeta.getBrand());
                            if (meta.getBrand() == null) {
                                meta.setBrand(nlpMeta.getBrand());
                                hasNLPResult = true;
                            } else if (nlpMeta.getBrand().contains(meta.getBrand())) {
                                meta.setBrand(nlpMeta.getBrand());
                                log.debug("Using NLP brand (more complete): {} contains rule brand: {}", nlpMeta.getBrand(), meta.getBrandByRule());
                            }
                        }

                        if (nlpMeta.getOrgType() != null) {
                            meta.setOrgTypeByNLP(nlpMeta.getOrgType());
                            if (meta.getOrgType() == null) {
                                meta.setOrgType(nlpMeta.getOrgType());
                                hasNLPResult = true;
                            }
                            // 对于组织类型，优先使用规则引擎的后缀匹配结果
                            // 因为规则引擎基于严格的后缀匹配，更符合脱敏策略的假设
                            // NLP可能识别出更长的组织类型（如"证券交易所"而非"交易所"），
                            // 但这会导致保留率异常升高，触发不必要的降级
                            // 因此仅当规则引擎未找到组织类型时，才使用NLP的结果
                            log.debug("Keeping rule-based orgType: {} (NLP identified: {})", meta.getOrgTypeByRule(), nlpMeta.getOrgType());
                        }

                        if (hasNLPResult) {
                            meta.setFromNLP(true);
                        }

                        log.debug("NLP extracted meta info: region={}, brand={}, industry={}, orgType={}",
                                meta.getRegionByNLP(), meta.getBrandByNLP(), meta.getIndustryByNLP(), meta.getOrgTypeByNLP());
                        log.debug("Merged NLP and rule-based meta info for keyword: {}", originalKeyword);
                    }
                } catch (Exception e) {
                    log.debug("NLP extraction failed for keyword: {}, using rule-based only", originalKeyword, e);
                }
            }

        } catch (Exception e) {
            log.warn("Failed to extract meta info from keyword: {}, error: {}", keyword, e.getMessage());
            meta.setConfidence(0.0);
        }

        return meta;
    }

    /**
     * 根据行业名称在文本中查找对应的关键词
     * 例如：行业="能源"，在"南方电网"中找到关键词"电网"
     *
     * @param text 原始文本
     * @param industryName 行业名称
     * @return 找到的关键词，未找到返回null
     */
    private String findIndustryKeywordInText(String text, String industryName) {
        if (industryExtractor == null) {
            return null;
        }

        // 直接尝试从文本中提取关键词
        String keyword = industryExtractor.extract(text);
        if (keyword != null) {
            return keyword;
        }

        return null;
    }

    /**
     * 计算置信度
     *
     * @param meta 元信息
     * @return 置信度（0.0-1.0）
     */
    private double calculateConfidence(MetaInfo meta) {
        int fieldCount = 0;
        int totalFields = 5;

        if (meta.getRegion() != null) fieldCount++;
        if (meta.getBrand() != null) fieldCount++;
        if (meta.getIndustry() != null) fieldCount++;
        if (meta.getOrgType() != null) fieldCount++;
        if (meta.getBracketContent() != null) fieldCount++;

        return (double) fieldCount / totalFields;
    }

    /**
     * 批量提取
     *
     * @param keywords 关键词列表
     * @return 元信息列表
     */
    public java.util.List<MetaInfo> batchExtract(java.util.List<String> keywords) {
        java.util.List<MetaInfo> results = new java.util.ArrayList<>();
        for (String keyword : keywords) {
            results.add(extract(keyword));
        }
        return results;
    }

    /**
     * 提取括号内容的元信息（用于智能脱敏）
     * 只提取地域和组织类型，不递归提取括号
     *
     * @param bracketContent 括号内容
     * @return 元信息
     */
    private MetaInfo extractBracketMeta(String bracketContent) {
        if (!StringUtils.hasText(bracketContent)) {
            return new MetaInfo(bracketContent);
        }

        MetaInfo meta = new MetaInfo(bracketContent);
        String remaining = bracketContent;

        try {
            // 1. 提取地域（前缀匹配）
            String region = regionExtractor.extract(remaining);
            if (region != null) {
                meta.setRegion(region);
                remaining = remaining.substring(region.length());
            }

            // 2. 提取品牌（前缀匹配）
            String brand = brandExtractor.extract(remaining);
            if (brand != null) {
                meta.setBrand(brand);
                remaining = remaining.substring(brand.length());
            }

            // 3. 提取组织类型（后缀匹配）
            String orgType = orgTypeExtractor.extract(remaining);
            if (orgType != null) {
                meta.setOrgType(orgType);
                remaining = remaining.substring(0, remaining.length() - orgType.length());
            }

            // 4. 提取行业（中间部分匹配）
            String industry = industryExtractor.extract(remaining);
            if (industry != null) {
                meta.setIndustry(industry);
            }

            log.debug("Extracted bracket meta info: {}", meta);

        } catch (Exception e) {
            log.warn("Failed to extract bracket meta info: {}, error: {}", bracketContent, e.getMessage());
        }

        return meta;
    }
}
