package io.github.surezzzzzz.sdk.sensitive.keyword.support;

import io.github.surezzzzzz.sdk.sensitive.keyword.configuration.SmartKeywordSensitiveProperties;
import io.github.surezzzzzz.sdk.sensitive.keyword.constant.MaskType;
import io.github.surezzzzzz.sdk.sensitive.keyword.constant.SmartKeywordSensitiveConstant;
import io.github.surezzzzzz.sdk.sensitive.keyword.extractor.MetaInfo;

/**
 * 脱敏原因构建工具类
 * 负责生成详细的脱敏原因描述
 *
 * @author surezzzzzz
 */
public class MaskReasonHelper {

    private MaskReasonHelper() {
        // 工具类，禁止实例化
    }

    // ==================== 阶段标签常量 ====================
    public static final String PHASE_RECOGNIZE = "【识别】";
    public static final String PHASE_MASK = "【脱敏】";
    public static final String PHASE_FALLBACK = "【兜底】";
    public static final String PHASE_RESULT = "【结果】";
    public static final String PHASE_ERROR = "【错误】";
    public static final String PHASE_EXCEPTION = "【异常】";
    public static final String PHASE_INPUT = "【输入】";

    // ==================== 详细标签常量 ====================
    private static final String META_INFO_LABEL = "[最终元信息: ";
    private static final String STRATEGY_LABEL = "[策略: ";
    private static final String RETENTION_CALC_LABEL = "[保留率计算: ";
    private static final String ORIGINAL_STRATEGY_LABEL = "[原始策略: ";
    private static final String DOWNGRADE_PROCESS_LABEL = "[降级过程: ";
    private static final String ACTUAL_STRATEGY_LABEL = "[实际策略: ";

    // 元信息字段标签
    private static final String REGION_LABEL = "地域=";
    private static final String INDUSTRY_LABEL = " 行业=";
    private static final String BRAND_LABEL = " 品牌=";
    private static final String ORG_TYPE_LABEL = " 组织类型=";
    private static final String BRACKET_CONTENT_LABEL = " 括号内容=";
    private static final String BRACKET_REGION_LABEL = " 括号内地域=";

    // 保留率计算字段标签
    private static final String MAIN_LENGTH_LABEL = "主体长度=";
    private static final String REGION_LENGTH_LABEL = ", 地域长度=";
    private static final String INDUSTRY_LENGTH_LABEL = ", 行业长度=";
    private static final String BRAND_LENGTH_LABEL = ", 品牌长度=";
    private static final String ORG_TYPE_LENGTH_LABEL = ", 组织类型长度=";
    private static final String RETAINED_CHARS_LABEL = ", 保留字符=";
    private static final String RETENTION_RATE_LABEL = ", 保留率=";
    private static final String ORG_TYPE_KIND_LABEL = ", 机构类型=";
    private static final String THRESHOLD_LABEL = ", 阈值=";
    private static final String FINAL_RETENTION_RATE_LABEL = ", 最终保留率=";

    // 策略字段标签
    private static final String KEEP_REGION_LABEL = "保留地域=";
    private static final String KEEP_INDUSTRY_LABEL = " 保留行业=";
    private static final String KEEP_BRAND_LABEL = " 保留品牌=";
    private static final String KEEP_ORG_TYPE_LABEL = " 保留组织类型=";
    private static final String KEEP_BRACKET_LABEL = " 保留括号=";
    private static final String KEEP_LENGTH_LABEL = " 保留长度=";
    private static final String FIXED_MASK_LENGTH_LABEL = " 固定星号数量=";
    private static final String FIXED_USE = "使用固定";
    private static final String FIXED_LENGTH = "个星号";

    // 通用值
    private static final String YES = "是";
    private static final String NO = "否";
    private static final String NONE = "无";
    private static final String CLOSE_BRACKET = "]";

    // Fallback原因
    private static final String FALLBACK_LABEL = "[Fallback脱敏=";
    private static final String FALLBACK_REASON_NO_META = "无有效元信息";
    private static final String FALLBACK_REASON_ZERO_RETENTION = "保留字符=0（完全脱敏）";
    private static final String FALLBACK_REASON_NORMAL = "正常脱敏";

    // ==================== 构建器 ====================

    /**
     * 脱敏原因构建器
     */
    public static class Builder {
        private final StringBuilder sb = new StringBuilder();
        private String keyword;
        private String source;
        private MetaInfo meta;
        private RetentionAdjustment adjustment;
        private SmartKeywordSensitiveProperties.RuntimeStrategy originalStrategy;
        private SmartKeywordSensitiveProperties.RuntimeStrategy actualStrategy;
        private boolean configAdjusted;
        private String masked;

        public Builder keyword(String keyword) {
            this.keyword = keyword;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder meta(MetaInfo meta) {
            this.meta = meta;
            return this;
        }

        public Builder adjustment(RetentionAdjustment adjustment) {
            this.adjustment = adjustment;
            return this;
        }

        public Builder originalStrategy(SmartKeywordSensitiveProperties.RuntimeStrategy strategy) {
            this.originalStrategy = strategy;
            return this;
        }

        public Builder actualStrategy(SmartKeywordSensitiveProperties.RuntimeStrategy strategy) {
            this.actualStrategy = strategy;
            return this;
        }

        public Builder configAdjusted(boolean adjusted) {
            this.configAdjusted = adjusted;
            return this;
        }

        public Builder masked(String masked) {
            this.masked = masked;
            return this;
        }

        public String build() {
            // 检查是否需要完整的详细格式（只有当有adjustment时才需要）
            // Hash和Placeholder策略不计算adjustment，因此不会输出完整格式
            boolean needsDetailedFormat = (adjustment != null);
            boolean hasValidMeta = (meta != null && meta.hasValidInfo());

            // ========== 第一部分：基本信息 ==========
            appendBasicInfo();

            if (!needsDetailedFormat) {
                // 简化格式：根据策略类型显示相应说明
                appendSimplifiedFormat();
                return sb.toString();
            }

            // ========== 第二部分：自然语言叙述（完整格式）==========
            // 1. 元信息提取过程（根据meta的字段动态生成）
            if (hasValidMeta) {
                appendMetaExtractionNarrative();
            }

            // 2. 脱敏策略决策过程（包含保留率计算）
            if (hasValidMeta && actualStrategy != null) {
                appendStrategyDecisionNarrative();
            }

            // 3. 兜底脱敏决策
            appendFallbackDecisionNarrative(hasValidMeta);

            // 4. 最终结果
            if (masked != null) {
                appendFinalResultNarrative();
            }

            // ========== 第三部分：详细的结构化数据 ==========
            sb.append(SmartKeywordSensitiveConstant.NARRATIVE_SEPARATOR);

            // 先显示规则引擎和NLP的提取结果
            appendRuleBasedMeta();
            appendNLPMeta();

            // 再显示最终元信息
            sb.append("\n");
            appendMetaInfo();

            // 保留率计算
            if (hasValidMeta) {
                sb.append("\n");
                appendRetentionCalculation();
            }

            // 策略信息
            if (configAdjusted && originalStrategy != null && actualStrategy != null) {
                sb.append("\n");
                appendStrategyWithDowngrade();
            } else if (actualStrategy != null) {
                sb.append("\n");
                appendStrategy(actualStrategy);
            }

            // Fallback信息
            sb.append("\n");
            appendFallbackInfo(hasValidMeta);

            return sb.toString();
        }

        /**
         * 简化格式输出（用于Hash和Placeholder策略）
         */
        private void appendSimplifiedFormat() {
            if (actualStrategy != null && actualStrategy.getMaskType() != null) {
                MaskType maskType = actualStrategy.getMaskType();
                if (maskType == MaskType.HASH) {
                    sb.append(String.format(SmartKeywordSensitiveConstant.TEMPLATE_HASH_MASK_DESC,
                            SmartKeywordSensitiveConstant.HASH_ALGORITHM));
                } else if (maskType == MaskType.PLACEHOLDER) {
                    sb.append(SmartKeywordSensitiveConstant.MESSAGE_PLACEHOLDER_PREFIX);
                    if (meta != null && meta.getOrgType() != null) {
                        sb.append(String.format(SmartKeywordSensitiveConstant.TEMPLATE_PLACEHOLDER_WITH_ORG,
                                meta.getOrgType()));
                    } else if (meta != null && meta.getIndustry() != null) {
                        sb.append(String.format(SmartKeywordSensitiveConstant.TEMPLATE_PLACEHOLDER_WITH_INDUSTRY,
                                meta.getIndustry()));
                    } else {
                        sb.append(SmartKeywordSensitiveConstant.MESSAGE_PLACEHOLDER_DEFAULT);
                    }
                }
            }

            if (masked != null) {
                sb.append(String.format(SmartKeywordSensitiveConstant.TEMPLATE_SIMPLIFIED_RESULT,
                        keyword, masked));
            }
        }

        private void appendBasicInfo() {
            if (source != null) {
                sb.append(String.format("%s(%s)", keyword, source));
            } else {
                sb.append(keyword);
            }
        }

        // ==================== 自然语言叙述部分 ====================

        /**
         * 元信息提取过程（根据 MetaInfo 的字段动态生成提取叙述）
         * 逻辑平移自：MetaInfoExtractor.extract() 中的 narrative 生成逻辑
         */
        private void appendMetaExtractionNarrative() {
            sb.append(String.format(SmartKeywordSensitiveConstant.TEMPLATE_NARRATIVE_SECTION,
                    1, SmartKeywordSensitiveConstant.NARRATIVE_SECTION_TITLE_META_EXTRACTION));

            StringBuilder narrative = new StringBuilder();
            narrative.append(SmartKeywordSensitiveConstant.NARRATIVE_RULE_START)
                    .append(meta.getOriginalKeyword())
                    .append(SmartKeywordSensitiveConstant.NARRATIVE_IN);

            // 规则引擎提取步骤
            StringBuilder ruleSteps = new StringBuilder();
            if (meta.getRegionByRule() != null) {
                ruleSteps.append(SmartKeywordSensitiveConstant.NARRATIVE_EXTRACT_REGION)
                        .append(meta.getRegionByRule())
                        .append(SmartKeywordSensitiveConstant.NARRATIVE_QUOTE);
            }
            if (meta.getBrandByRule() != null) {
                if (ruleSteps.length() > 0) ruleSteps.append(SmartKeywordSensitiveConstant.NARRATIVE_COMMA);
                ruleSteps.append(SmartKeywordSensitiveConstant.NARRATIVE_BRAND)
                        .append(meta.getBrandByRule())
                        .append(SmartKeywordSensitiveConstant.NARRATIVE_QUOTE);
            }
            if (meta.getOrgTypeByRule() != null) {
                if (ruleSteps.length() > 0) ruleSteps.append(SmartKeywordSensitiveConstant.NARRATIVE_COMMA);
                ruleSteps.append(SmartKeywordSensitiveConstant.NARRATIVE_ORG_TYPE)
                        .append(meta.getOrgTypeByRule())
                        .append(SmartKeywordSensitiveConstant.NARRATIVE_QUOTE);
            }
            if (meta.getIndustryByRule() != null) {
                if (ruleSteps.length() > 0) ruleSteps.append(SmartKeywordSensitiveConstant.NARRATIVE_COMMA);
                ruleSteps.append(SmartKeywordSensitiveConstant.NARRATIVE_INDUSTRY_KEYWORD)
                        .append(meta.getIndustryByRule())
                        .append(SmartKeywordSensitiveConstant.NARRATIVE_QUOTE);
            }

            if (ruleSteps.length() > 0) {
                narrative.append(SmartKeywordSensitiveConstant.NARRATIVE_IN_ORDER)
                        .append(ruleSteps.toString());
                narrative.append(SmartKeywordSensitiveConstant.NARRATIVE_PERIOD);
            } else {
                narrative.append(SmartKeywordSensitiveConstant.NARRATIVE_NO_META);
                narrative.append(SmartKeywordSensitiveConstant.NARRATIVE_PERIOD);
            }

            // NLP提取结果
            boolean hasNLPResult = (meta.getRegionByNLP() != null || meta.getIndustryByNLP() != null ||
                    meta.getBrandByNLP() != null || meta.getOrgTypeByNLP() != null);

            if (hasNLPResult) {
                narrative.append(SmartKeywordSensitiveConstant.NARRATIVE_NLP_ANALYZE);
                StringBuilder nlpFindings = new StringBuilder();
                boolean hasAnyNLP = false;

                if (meta.getRegionByNLP() != null && meta.getRegionByRule() == null) {
                    nlpFindings.append(SmartKeywordSensitiveConstant.NARRATIVE_REGION)
                            .append(meta.getRegionByNLP())
                            .append(SmartKeywordSensitiveConstant.NARRATIVE_QUOTE);
                    hasAnyNLP = true;
                }

                if (meta.getIndustryByNLP() != null && meta.getIndustryByRule() == null) {
                    if (hasAnyNLP) nlpFindings.append(SmartKeywordSensitiveConstant.NARRATIVE_COMMA);
                    nlpFindings.append(SmartKeywordSensitiveConstant.NARRATIVE_INDUSTRY)
                            .append(meta.getIndustryByNLP())
                            .append(SmartKeywordSensitiveConstant.NARRATIVE_QUOTE);
                    hasAnyNLP = true;
                } else if (meta.getIndustryByNLP() != null && meta.getIndustryByRule() != null) {
                    // NLP 和规则都提取到了行业，显示为 "行业"能源"(关键词:电网)"
                    if (hasAnyNLP) nlpFindings.append(SmartKeywordSensitiveConstant.NARRATIVE_COMMA);
                    nlpFindings.append(SmartKeywordSensitiveConstant.NARRATIVE_INDUSTRY)
                            .append(meta.getIndustryByNLP())
                            .append(SmartKeywordSensitiveConstant.NARRATIVE_KEYWORD_LABEL)
                            .append(meta.getIndustryByRule())
                            .append(SmartKeywordSensitiveConstant.NARRATIVE_RIGHT_PAREN);
                    hasAnyNLP = true;
                }

                if (meta.getBrandByNLP() != null && meta.getBrandByRule() == null) {
                    if (hasAnyNLP) nlpFindings.append(SmartKeywordSensitiveConstant.NARRATIVE_COMMA);
                    nlpFindings.append(SmartKeywordSensitiveConstant.NARRATIVE_BRAND)
                            .append(meta.getBrandByNLP())
                            .append(SmartKeywordSensitiveConstant.NARRATIVE_QUOTE);
                    hasAnyNLP = true;
                }

                if (meta.getOrgTypeByNLP() != null && meta.getOrgTypeByRule() == null) {
                    if (hasAnyNLP) nlpFindings.append(SmartKeywordSensitiveConstant.NARRATIVE_COMMA);
                    nlpFindings.append(SmartKeywordSensitiveConstant.NARRATIVE_ORG_TYPE)
                            .append(meta.getOrgTypeByNLP())
                            .append(SmartKeywordSensitiveConstant.NARRATIVE_QUOTE);
                    hasAnyNLP = true;
                }

                if (hasAnyNLP) {
                    narrative.append(SmartKeywordSensitiveConstant.NARRATIVE_RECOGNIZED)
                            .append(nlpFindings.toString())
                            .append(SmartKeywordSensitiveConstant.NARRATIVE_PERIOD);
                } else {
                    narrative.append(SmartKeywordSensitiveConstant.NARRATIVE_NO_NEW_META);
                }
            }

            // 最终采用的元信息
            narrative.append(SmartKeywordSensitiveConstant.NARRATIVE_FINAL_RESULT);
            StringBuilder finalSummary = new StringBuilder();

            if (meta.getRegion() != null) {
                finalSummary.append(SmartKeywordSensitiveConstant.NARRATIVE_REGION)
                        .append(meta.getRegion())
                        .append(SmartKeywordSensitiveConstant.NARRATIVE_QUOTE);
                if (meta.getRegionByRule() != null && meta.getRegionByNLP() != null && meta.getRegion().equals(meta.getRegionByRule()) && meta.getRegion().equals(meta.getRegionByNLP())) {
                    finalSummary.append(SmartKeywordSensitiveConstant.NARRATIVE_RULE_NLP_MATCH);
                } else if (meta.getRegionByRule() != null && meta.getRegionByNLP() == null) {
                    finalSummary.append(SmartKeywordSensitiveConstant.NARRATIVE_RULE);
                } else if (meta.getRegionByNLP() != null && meta.getRegionByRule() == null) {
                    finalSummary.append(SmartKeywordSensitiveConstant.NARRATIVE_NLP);
                } else {
                    finalSummary.append(SmartKeywordSensitiveConstant.NARRATIVE_RULE_NLP);
                }
            }

            if (meta.getIndustry() != null) {
                if (finalSummary.length() > 0) finalSummary.append(SmartKeywordSensitiveConstant.NARRATIVE_PLUS);
                finalSummary.append(SmartKeywordSensitiveConstant.NARRATIVE_INDUSTRY)
                        .append(meta.getIndustry())
                        .append(SmartKeywordSensitiveConstant.NARRATIVE_QUOTE);
                if (meta.getIndustryByRule() != null && meta.getIndustryByNLP() != null) {
                    finalSummary.append(SmartKeywordSensitiveConstant.NARRATIVE_RULE_NLP);
                } else if (meta.getIndustryByRule() != null && meta.getIndustryByNLP() == null) {
                    finalSummary.append(SmartKeywordSensitiveConstant.NARRATIVE_RULE);
                } else if (meta.getIndustryByNLP() != null && meta.getIndustryByRule() == null) {
                    finalSummary.append(SmartKeywordSensitiveConstant.NARRATIVE_NLP);
                } else {
                    finalSummary.append(SmartKeywordSensitiveConstant.NARRATIVE_RULE_NLP);
                }
            }

            if (meta.getBrand() != null) {
                if (finalSummary.length() > 0) finalSummary.append(SmartKeywordSensitiveConstant.NARRATIVE_PLUS);
                finalSummary.append(SmartKeywordSensitiveConstant.NARRATIVE_BRAND)
                        .append(meta.getBrand())
                        .append(SmartKeywordSensitiveConstant.NARRATIVE_QUOTE);
                if (meta.getBrandByRule() != null && meta.getBrandByNLP() != null && meta.getBrand().equals(meta.getBrandByRule()) && meta.getBrand().equals(meta.getBrandByNLP())) {
                    finalSummary.append(SmartKeywordSensitiveConstant.NARRATIVE_RULE_NLP_MATCH);
                } else if (meta.getBrandByRule() != null && meta.getBrandByNLP() == null) {
                    finalSummary.append(SmartKeywordSensitiveConstant.NARRATIVE_RULE);
                } else if (meta.getBrandByNLP() != null && meta.getBrandByRule() == null) {
                    finalSummary.append(SmartKeywordSensitiveConstant.NARRATIVE_NLP);
                } else {
                    finalSummary.append(SmartKeywordSensitiveConstant.NARRATIVE_RULE_NLP);
                }
            }

            if (meta.getOrgType() != null) {
                if (finalSummary.length() > 0) finalSummary.append(SmartKeywordSensitiveConstant.NARRATIVE_PLUS);
                finalSummary.append(SmartKeywordSensitiveConstant.NARRATIVE_ORG_TYPE)
                        .append(meta.getOrgType())
                        .append(SmartKeywordSensitiveConstant.NARRATIVE_QUOTE);
                if (meta.getOrgTypeByRule() != null && meta.getOrgTypeByNLP() != null && meta.getOrgType().equals(meta.getOrgTypeByRule()) && meta.getOrgType().equals(meta.getOrgTypeByNLP())) {
                    finalSummary.append(SmartKeywordSensitiveConstant.NARRATIVE_RULE_NLP_MATCH);
                } else if (meta.getOrgTypeByRule() != null && meta.getOrgTypeByNLP() == null) {
                    finalSummary.append(SmartKeywordSensitiveConstant.NARRATIVE_RULE);
                } else if (meta.getOrgTypeByNLP() != null && meta.getOrgTypeByRule() == null) {
                    finalSummary.append(SmartKeywordSensitiveConstant.NARRATIVE_NLP);
                } else {
                    finalSummary.append(SmartKeywordSensitiveConstant.NARRATIVE_RULE_NLP);
                }
            }

            if (finalSummary.length() > 0) {
                narrative.append(finalSummary.toString())
                        .append(SmartKeywordSensitiveConstant.NARRATIVE_PERIOD);
            } else {
                narrative.append(SmartKeywordSensitiveConstant.NARRATIVE_NO_VALID_META);
            }

            sb.append(narrative.toString());
        }

        /**
         * 脱敏策略决策过程（包含保留率计算）
         */
        private void appendStrategyDecisionNarrative() {
            sb.append(String.format(SmartKeywordSensitiveConstant.TEMPLATE_NARRATIVE_SECTION,
                    2, SmartKeywordSensitiveConstant.NARRATIVE_SECTION_TITLE_STRATEGY_DECISION));

            // 原脱敏策略
            sb.append(SmartKeywordSensitiveConstant.NARRATIVE_ORIGINAL_STRATEGY);
            if (originalStrategy != null) {
                appendStrategyNarrative(originalStrategy);
            } else {
                appendStrategyNarrative(actualStrategy);
            }
            sb.append(SmartKeywordSensitiveConstant.NARRATIVE_PERIOD);

            // 初始保留率计算 - 使用模板
            sb.append(String.format(SmartKeywordSensitiveConstant.TEMPLATE_MAIN_LENGTH, adjustment.mainLength));

            if (adjustment.regionLength > 0 || adjustment.industryLength > 0 ||
                    adjustment.brandLength > 0 || adjustment.orgTypeLength > 0) {
                sb.append(SmartKeywordSensitiveConstant.NARRATIVE_AMONG);

                boolean hasContent = false;
                if (adjustment.regionLength > 0 && meta.getRegion() != null) {
                    sb.append(String.format(SmartKeywordSensitiveConstant.TEMPLATE_FIELD_DETAIL,
                            SmartKeywordSensitiveConstant.FIELD_NAME_REGION,
                            meta.getRegion(),
                            adjustment.regionLength));
                    hasContent = true;
                }

                if (adjustment.brandLength > 0 && meta.getBrand() != null) {
                    if (hasContent) sb.append(SmartKeywordSensitiveConstant.NARRATIVE_COMMA);
                    sb.append(String.format(SmartKeywordSensitiveConstant.TEMPLATE_FIELD_DETAIL,
                            SmartKeywordSensitiveConstant.FIELD_NAME_BRAND,
                            meta.getBrand(),
                            adjustment.brandLength));
                    hasContent = true;
                }

                if (adjustment.industryLength > 0 && meta.getIndustry() != null) {
                    if (hasContent) sb.append(SmartKeywordSensitiveConstant.NARRATIVE_COMMA);
                    sb.append(String.format(SmartKeywordSensitiveConstant.TEMPLATE_FIELD_DETAIL,
                            SmartKeywordSensitiveConstant.FIELD_NAME_INDUSTRY,
                            meta.getIndustry(),
                            adjustment.industryLength));
                    hasContent = true;
                }

                if (adjustment.orgTypeLength > 0 && meta.getOrgType() != null) {
                    if (hasContent) sb.append(SmartKeywordSensitiveConstant.NARRATIVE_COMMA);
                    sb.append(String.format(SmartKeywordSensitiveConstant.TEMPLATE_FIELD_DETAIL,
                            SmartKeywordSensitiveConstant.FIELD_NAME_ORG_TYPE,
                            meta.getOrgType(),
                            adjustment.orgTypeLength));
                }
            }

            // 使用模板 - 原策略保留率
            sb.append(String.format(SmartKeywordSensitiveConstant.TEMPLATE_ORIGINAL_RETENTION,
                    adjustment.retainedChars,
                    adjustment.retentionRate * 100));

            // 使用模板 - 机构类型判定和阈值
            sb.append(String.format(SmartKeywordSensitiveConstant.TEMPLATE_ORG_TYPE_THRESHOLD,
                    adjustment.orgType,
                    adjustment.threshold * 100));

            // 判断是否有降级
            if (configAdjusted && originalStrategy != null && adjustment != null) {
                // 有降级，详细说明降级过程
                sb.append(adjustment.downgradeReason);
                sb.append(SmartKeywordSensitiveConstant.NARRATIVE_FINAL_STRATEGY);
                appendStrategyNarrative(actualStrategy);
                // 使用模板 - 最终保留率
                sb.append(String.format(SmartKeywordSensitiveConstant.TEMPLATE_FINAL_RETENTION,
                        adjustment.finalRetainedChars,
                        adjustment.finalRetentionRate * 100));
                sb.append(SmartKeywordSensitiveConstant.NARRATIVE_PERIOD);
            } else if (adjustment.finalRetentionRate < adjustment.threshold) {
                // 无降级但未达到阈值 - 使用模板
                sb.append(String.format(SmartKeywordSensitiveConstant.TEMPLATE_RATE_NOT_REACH,
                        adjustment.finalRetentionRate * 100));
                sb.append(SmartKeywordSensitiveConstant.NARRATIVE_NO_DOWNGRADE_OPTION);
                sb.append(SmartKeywordSensitiveConstant.NARRATIVE_KEEP_ORIGINAL);
                sb.append(SmartKeywordSensitiveConstant.NARRATIVE_PERIOD);
            } else {
                // 达到阈值，未触发降级 - 使用模板
                sb.append(SmartKeywordSensitiveConstant.TEMPLATE_RATE_REACHED);
                sb.append(SmartKeywordSensitiveConstant.NARRATIVE_CHINESE_COMMA);
                sb.append(SmartKeywordSensitiveConstant.NARRATIVE_NO_DOWNGRADE);
                sb.append(SmartKeywordSensitiveConstant.NARRATIVE_PERIOD);
            }
        }

        /**
         * 生成策略的自然语言描述
         */
        private void appendStrategyNarrative(SmartKeywordSensitiveProperties.RuntimeStrategy strategy) {
            StringBuilder kept = new StringBuilder();
            StringBuilder notKept = new StringBuilder();

            if (strategy.getKeepRegion()) {
                if (kept.length() > 0) kept.append(SmartKeywordSensitiveConstant.NARRATIVE_COMMA);
                kept.append(SmartKeywordSensitiveConstant.FIELD_NAME_REGION);
            } else {
                if (notKept.length() > 0) notKept.append(SmartKeywordSensitiveConstant.NARRATIVE_COMMA);
                notKept.append(SmartKeywordSensitiveConstant.FIELD_NAME_REGION);
            }

            if (strategy.getKeepIndustry()) {
                if (kept.length() > 0) kept.append(SmartKeywordSensitiveConstant.NARRATIVE_COMMA);
                kept.append(SmartKeywordSensitiveConstant.FIELD_NAME_INDUSTRY);
            } else {
                if (notKept.length() > 0) notKept.append(SmartKeywordSensitiveConstant.NARRATIVE_COMMA);
                notKept.append(SmartKeywordSensitiveConstant.FIELD_NAME_INDUSTRY);
            }

            if (strategy.getKeepBrand()) {
                if (kept.length() > 0) kept.append(SmartKeywordSensitiveConstant.NARRATIVE_COMMA);
                kept.append(SmartKeywordSensitiveConstant.FIELD_NAME_BRAND);
            } else {
                if (notKept.length() > 0) notKept.append(SmartKeywordSensitiveConstant.NARRATIVE_COMMA);
                notKept.append(SmartKeywordSensitiveConstant.FIELD_NAME_BRAND);
            }

            if (strategy.getKeepOrgType()) {
                if (kept.length() > 0) kept.append(SmartKeywordSensitiveConstant.NARRATIVE_COMMA);
                kept.append(SmartKeywordSensitiveConstant.FIELD_NAME_ORG_TYPE);
            } else {
                if (notKept.length() > 0) notKept.append(SmartKeywordSensitiveConstant.NARRATIVE_COMMA);
                notKept.append(SmartKeywordSensitiveConstant.FIELD_NAME_ORG_TYPE);
            }

            if (strategy.getKeepBracket()) {
                if (kept.length() > 0) kept.append(SmartKeywordSensitiveConstant.NARRATIVE_COMMA);
                kept.append(SmartKeywordSensitiveConstant.FIELD_NAME_BRACKET);
            } else {
                if (notKept.length() > 0) notKept.append(SmartKeywordSensitiveConstant.NARRATIVE_COMMA);
                notKept.append(SmartKeywordSensitiveConstant.FIELD_NAME_BRACKET);
            }

            if (strategy.getKeepLength()) {
                if (kept.length() > 0) kept.append(SmartKeywordSensitiveConstant.NARRATIVE_COMMA);
                kept.append(SmartKeywordSensitiveConstant.FIELD_NAME_LENGTH);
            } else {
                if (notKept.length() > 0) notKept.append(SmartKeywordSensitiveConstant.NARRATIVE_COMMA);
                notKept.append(SmartKeywordSensitiveConstant.FIELD_NAME_LENGTH);
            }

            if (kept.length() > 0) {
                sb.append(SmartKeywordSensitiveConstant.NARRATIVE_KEEP);
                sb.append(kept.toString());
            }

            if (notKept.length() > 0) {
                if (kept.length() > 0) {
                    sb.append(SmartKeywordSensitiveConstant.NARRATIVE_CHINESE_COMMA);
                }
                sb.append(SmartKeywordSensitiveConstant.NARRATIVE_NOT_KEEP);
                sb.append(notKept.toString());
            }

            // 当 keep-length=false 时，说明使用固定星号数量
            if (!Boolean.TRUE.equals(strategy.getKeepLength())) {
                if (kept.length() > 0 || notKept.length() > 0) {
                    sb.append(SmartKeywordSensitiveConstant.NARRATIVE_CHINESE_COMMA);
                }
                sb.append(FIXED_USE);
                sb.append(strategy.getFixedMaskLengthOrDefault());
                sb.append(FIXED_LENGTH);
            }
        }

        /**
         * 兜底脱敏决策
         */
        private void appendFallbackDecisionNarrative(boolean hasValidMeta) {
            sb.append(String.format(SmartKeywordSensitiveConstant.TEMPLATE_NARRATIVE_SECTION,
                    3, SmartKeywordSensitiveConstant.NARRATIVE_SECTION_TITLE_FALLBACK_DECISION));

            if (!hasValidMeta) {
                sb.append(SmartKeywordSensitiveConstant.NARRATIVE_FALLBACK_TRIGGERED);
                sb.append(SmartKeywordSensitiveConstant.NARRATIVE_NO_VALID_META_INFO);
            } else if (adjustment.finalRetainedChars == 0) {
                sb.append(SmartKeywordSensitiveConstant.NARRATIVE_FALLBACK_TRIGGERED);
                sb.append(SmartKeywordSensitiveConstant.NARRATIVE_ZERO_RETENTION);
            } else {
                sb.append(SmartKeywordSensitiveConstant.NARRATIVE_NO_FALLBACK);
                sb.append(SmartKeywordSensitiveConstant.NARRATIVE_NORMAL_MASK);
            }
            sb.append(SmartKeywordSensitiveConstant.NARRATIVE_PERIOD);
        }

        /**
         * 最终结果
         */
        private void appendFinalResultNarrative() {
            sb.append(String.format(SmartKeywordSensitiveConstant.TEMPLATE_NARRATIVE_SECTION,
                    4, SmartKeywordSensitiveConstant.NARRATIVE_SECTION_TITLE_FINAL_RESULT));
            sb.append(keyword);
            sb.append(SmartKeywordSensitiveConstant.NARRATIVE_ARROW);
            sb.append(masked);
        }

        // ==================== 结构化数据部分 ====================

        private void appendMetaInfo() {
            sb.append(META_INFO_LABEL);
            sb.append(REGION_LABEL).append(meta != null && meta.getRegion() != null ? meta.getRegion() : NONE);
            sb.append(INDUSTRY_LABEL).append(meta != null && meta.getIndustry() != null ? meta.getIndustry() : NONE);
            sb.append(BRAND_LABEL).append(meta != null && meta.getBrand() != null ? meta.getBrand() : NONE);
            sb.append(ORG_TYPE_LABEL).append(meta != null && meta.getOrgType() != null ? meta.getOrgType() : NONE);

            if (meta != null && meta.getBracketContent() != null) {
                sb.append(BRACKET_CONTENT_LABEL).append(meta.getBracketContent());
                sb.append(BRACKET_REGION_LABEL).append(
                        meta.getBracketMeta() != null && meta.getBracketMeta().getRegion() != null
                                ? meta.getBracketMeta().getRegion() : NONE);
            }
            sb.append(CLOSE_BRACKET);
        }

        private void appendRuleBasedMeta() {
            if (meta == null) {
                return;
            }
            sb.append(SmartKeywordSensitiveConstant.LABEL_RULE_BASED_EXTRACTION);
            sb.append(REGION_LABEL).append(meta.getRegionByRule() != null ? meta.getRegionByRule() : NONE);
            sb.append(INDUSTRY_LABEL).append(meta.getIndustryByRule() != null ? meta.getIndustryByRule() : NONE);
            sb.append(BRAND_LABEL).append(meta.getBrandByRule() != null ? meta.getBrandByRule() : NONE);
            sb.append(ORG_TYPE_LABEL).append(meta.getOrgTypeByRule() != null ? meta.getOrgTypeByRule() : NONE);
            sb.append(CLOSE_BRACKET);
        }

        private void appendNLPMeta() {
            if (meta == null) {
                return;
            }
            // 只要有任何NLP提取结果，就输出NLP提取行
            boolean hasAnyNLP = (meta.getRegionByNLP() != null || meta.getIndustryByNLP() != null ||
                    meta.getBrandByNLP() != null || meta.getOrgTypeByNLP() != null);
            if (!hasAnyNLP) {
                return;
            }
            sb.append(SmartKeywordSensitiveConstant.LABEL_NLP_EXTRACTION);
            sb.append(REGION_LABEL).append(meta.getRegionByNLP() != null ? meta.getRegionByNLP() : NONE);
            sb.append(INDUSTRY_LABEL).append(meta.getIndustryByNLP() != null ? meta.getIndustryByNLP() : NONE);
            sb.append(BRAND_LABEL).append(meta.getBrandByNLP() != null ? meta.getBrandByNLP() : NONE);
            sb.append(ORG_TYPE_LABEL).append(meta.getOrgTypeByNLP() != null ? meta.getOrgTypeByNLP() : NONE);
            sb.append(CLOSE_BRACKET);
        }

        private void appendRetentionCalculation() {
            sb.append(RETENTION_CALC_LABEL);
            sb.append(MAIN_LENGTH_LABEL).append(adjustment.mainLength);
            sb.append(REGION_LENGTH_LABEL).append(adjustment.regionLength);
            sb.append(INDUSTRY_LENGTH_LABEL).append(adjustment.industryLength);
            sb.append(BRAND_LENGTH_LABEL).append(adjustment.brandLength);
            sb.append(ORG_TYPE_LENGTH_LABEL).append(adjustment.orgTypeLength);
            sb.append(RETAINED_CHARS_LABEL).append(adjustment.retainedChars);
            sb.append(RETENTION_RATE_LABEL).append(String.format("%.1f%%", adjustment.retentionRate * 100));
            sb.append(ORG_TYPE_KIND_LABEL).append(adjustment.orgType);
            sb.append(THRESHOLD_LABEL).append(String.format("%.0f%%", adjustment.threshold * 100));
            sb.append(CLOSE_BRACKET);
        }

        private void appendStrategyWithDowngrade() {
            // 原始策略
            sb.append(ORIGINAL_STRATEGY_LABEL);
            appendStrategyFields(originalStrategy);
            sb.append(CLOSE_BRACKET);

            // 降级过程
            sb.append(DOWNGRADE_PROCESS_LABEL);
            sb.append(adjustment.downgradeReason);
            sb.append(CLOSE_BRACKET);

            // 实际策略
            sb.append(ACTUAL_STRATEGY_LABEL);
            appendStrategyFields(actualStrategy);
            sb.append(FINAL_RETENTION_RATE_LABEL).append(String.format("%.1f%%", adjustment.finalRetentionRate * 100));
            sb.append(CLOSE_BRACKET);
        }

        private void appendStrategy(SmartKeywordSensitiveProperties.RuntimeStrategy strategy) {
            sb.append(STRATEGY_LABEL);
            appendStrategyFields(strategy);
            sb.append(CLOSE_BRACKET);
        }

        private void appendStrategyFields(SmartKeywordSensitiveProperties.RuntimeStrategy strategy) {
            sb.append(KEEP_REGION_LABEL).append(strategy.getKeepRegion() ? YES : NO);
            sb.append(KEEP_INDUSTRY_LABEL).append(strategy.getKeepIndustry() ? YES : NO);
            sb.append(KEEP_BRAND_LABEL).append(strategy.getKeepBrand() ? YES : NO);
            sb.append(KEEP_ORG_TYPE_LABEL).append(strategy.getKeepOrgType() ? YES : NO);
            sb.append(KEEP_BRACKET_LABEL).append(strategy.getKeepBracket() ? YES : NO);
            sb.append(KEEP_LENGTH_LABEL).append(strategy.getKeepLength() ? YES : NO);
            // 当 keep-length=false 时，显示固定星号数量
            if (!Boolean.TRUE.equals(strategy.getKeepLength())) {
                sb.append(FIXED_MASK_LENGTH_LABEL).append(strategy.getFixedMaskLengthOrDefault());
            }
        }

        private void appendFallbackInfo(boolean hasValidMeta) {
            sb.append(FALLBACK_LABEL);
            if (!hasValidMeta) {
                sb.append(YES).append(": ").append(FALLBACK_REASON_NO_META);
            } else if (adjustment.finalRetainedChars == 0) {
                sb.append(YES).append(": ").append(FALLBACK_REASON_ZERO_RETENTION);
            } else {
                sb.append(NO).append(": ").append(FALLBACK_REASON_NORMAL);
            }
            sb.append(CLOSE_BRACKET);
        }
    }

    /**
     * 保留率调整信息（简化版）
     */
    public static class RetentionAdjustment {
        public final int mainLength;
        public final int regionLength;
        public final int industryLength;
        public final int brandLength;
        public final int orgTypeLength;
        public final int retainedChars;
        public final int finalRetainedChars;
        public final double retentionRate;
        public final double finalRetentionRate;
        public final String orgType;
        public final double threshold;
        public final String downgradeReason;

        public RetentionAdjustment(int mainLength, int regionLength, int industryLength, int brandLength,
                                   int orgTypeLength, int retainedChars, int finalRetainedChars, double retentionRate,
                                   double finalRetentionRate, String orgType, double threshold,
                                   String downgradeReason) {
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
    }

    /**
     * 构建单个脱敏任务的详细原因
     *
     * @param keyword          待脱敏关键词
     * @param source           识别来源（可选，如RULE/NLP/CONFIG）
     * @param meta             元信息
     * @param originalStrategy 原始策略配置
     * @param actualStrategy   实际使用的策略
     * @param adjustment       保留率调整详情
     * @param masked           脱敏后的结果
     * @return 脱敏原因字符串
     */
    public static String buildSingleTaskReason(String keyword, String source, MetaInfo meta,
                                               SmartKeywordSensitiveProperties.RuntimeStrategy originalStrategy,
                                               SmartKeywordSensitiveProperties.RuntimeStrategy actualStrategy,
                                               RetentionAdjustment adjustment, String masked) {
        boolean configAdjusted = (actualStrategy != originalStrategy);

        return new Builder()
                .keyword(keyword)
                .source(source)
                .meta(meta)
                .adjustment(adjustment)
                .originalStrategy(originalStrategy)
                .actualStrategy(actualStrategy)
                .configAdjusted(configAdjusted)
                .masked(masked)
                .build();
    }
}
