package io.github.surezzzzzz.sdk.sensitive.keyword.extractor;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Meta Information Entity
 *
 * @author surezzzzzz
 */
@Data
@NoArgsConstructor
public class MetaInfo {

    /**
     * 地域信息（如：上海、北京、江苏）
     */
    private String region;

    /**
     * 行业信息（如：银行、汽车、互联网络）
     */
    private String industry;

    /**
     * 品牌信息（如：华为、小米）
     */
    private String brand;

    /**
     * 组织类型（如：有限公司、集团、交易所、中心）
     */
    private String orgType;

    /**
     * 括号内容（如：(江苏)）
     */
    private String bracketContent;

    /**
     * 括号内容的元信息（用于对括号内容进行智能脱敏）
     */
    private MetaInfo bracketMeta;

    /**
     * 左括号类型（"CHINESE"或"ENGLISH"）
     */
    private String leftBracketType;

    /**
     * 右括号类型（"CHINESE"或"ENGLISH"）
     */
    private String rightBracketType;

    /**
     * 置信度（0.0 - 1.0）
     */
    private double confidence = 1.0;

    /**
     * 是否通过NLP提取
     */
    private boolean fromNLP = false;

    /**
     * 原始关键词
     */
    private String originalKeyword;

    // ==================== 规则引擎提取结果 ====================
    /**
     * 规则引擎提取的地域（原始关键词，在文本中真实存在）
     */
    private String regionByRule;

    /**
     * 规则引擎提取的行业关键词（在文本中真实存在）
     */
    private String industryByRule;

    /**
     * 规则引擎提取的品牌（在文本中真实存在）
     */
    private String brandByRule;

    /**
     * 规则引擎提取的组织类型（在文本中真实存在）
     */
    private String orgTypeByRule;

    // ==================== NLP提取结果 ====================
    /**
     * NLP提取的地域
     */
    private String regionByNLP;

    /**
     * NLP提取的行业（可能是语义化名称，如"能源"）
     */
    private String industryByNLP;

    /**
     * NLP提取的品牌
     */
    private String brandByNLP;

    /**
     * NLP提取的组织类型
     */
    private String orgTypeByNLP;

    public MetaInfo(String originalKeyword) {
        this.originalKeyword = originalKeyword;
    }

    /**
     * 判断是否有有效的元信息
     *
     * @return true表示至少有一个字段不为空
     */
    public boolean hasValidInfo() {
        return region != null || industry != null || brand != null || orgType != null || bracketContent != null;
    }

    /**
     * 获取用于长度计算的地域（优先使用规则引擎结果）
     */
    public String getRegionForCalculation() {
        return regionByRule != null ? regionByRule : region;
    }

    /**
     * 获取用于长度计算的行业（优先使用规则引擎结果）
     */
    public String getIndustryForCalculation() {
        return industryByRule != null ? industryByRule : industry;
    }

    /**
     * 获取用于长度计算的品牌（优先使用规则引擎结果）
     */
    public String getBrandForCalculation() {
        return brandByRule != null ? brandByRule : brand;
    }

    /**
     * 获取用于长度计算的组织类型（优先使用规则引擎结果）
     */
    public String getOrgTypeForCalculation() {
        return orgTypeByRule != null ? orgTypeByRule : orgType;
    }
}
