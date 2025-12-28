package io.github.surezzzzzz.sdk.sensitive.keyword.nlp;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import io.github.surezzzzzz.sdk.sensitive.keyword.constant.ErrorCode;
import io.github.surezzzzzz.sdk.sensitive.keyword.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.sensitive.keyword.exception.NLPException;
import io.github.surezzzzzz.sdk.sensitive.keyword.extractor.MetaInfo;
import io.github.surezzzzzz.sdk.sensitive.keyword.support.ResourceLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HanLP NLP Provider Implementation
 * 增强版：支持智能拆分和行业提取
 *
 * @author surezzzzzz
 */
@Slf4j
public class HanLPProvider implements NLPProvider {

    private volatile boolean available = false;
    private final Map<String, String> industryMap;
    private final Map<String, String> brandMap;

    public HanLPProvider() {
        // 初始化final字段
        Map<String, String> tempIndustryMap;
        Map<String, String> tempBrandMap;

        try {
            // 测试HanLP是否可用
            HanLP.segment("测试");
            this.available = true;

            // 加载行业词典
            tempIndustryMap = loadIndustryKeywordsInternal();

            // 加载品牌词典
            tempBrandMap = loadBrandKeywordsInternal();

            log.info("HanLP initialized successfully with {} industry keywords and {} brands",
                    tempIndustryMap.size(), tempBrandMap.size());
        } catch (Exception e) {
            log.error("Failed to initialize HanLP", e);
            tempIndustryMap = new HashMap<>();
            tempBrandMap = new HashMap<>();
            throw new NLPException(ErrorCode.NLP_MODEL_LOAD_FAILED,
                    String.format(ErrorMessage.NLP_MODEL_LOAD_FAILED, e.getMessage()), e);
        }

        this.industryMap = tempIndustryMap;
        this.brandMap = tempBrandMap;
    }

    /**
     * 加载行业关键词词典（内部方法，用于构造器初始化）
     */
    private Map<String, String> loadIndustryKeywordsInternal() {
        try {
            Map<String, String> map = ResourceLoader.loadIndustryMap();
            log.debug("Loaded {} industry keywords from built-in dictionary", map.size());
            return map;
        } catch (Exception e) {
            log.warn("Failed to load industry keywords, NLP industry extraction will be limited", e);
            return new HashMap<>();
        }
    }

    /**
     * 加载品牌词典（内部方法，用于构造器初始化）
     */
    private Map<String, String> loadBrandKeywordsInternal() {
        try {
            Map<String, String> map = ResourceLoader.loadBrandMap();
            log.debug("Loaded {} brands from built-in dictionary", map.size());
            return map;
        } catch (Exception e) {
            log.warn("Failed to load brand keywords, NLP brand extraction will be limited", e);
            return new HashMap<>();
        }
    }

    @Override
    public MetaInfo extract(String text) {
        if (!StringUtils.hasText(text)) {
            return new MetaInfo(text);
        }

        if (!available) {
            throw new NLPException(ErrorCode.NLP_PROVIDER_NOT_CONFIGURED,
                    ErrorMessage.NLP_PROVIDER_NOT_CONFIGURED);
        }

        try {
            MetaInfo meta = new MetaInfo(text);
            meta.setFromNLP(true);

            // 使用HanLP进行分词和词性标注
            List<Term> terms = HanLP.segment(text);

            log.debug("HanLP segmentation for '{}': {}", text, terms);

            // 第一遍：提取各类信息
            for (Term term : terms) {
                String word = term.word;
                String nature = term.nature.toString();

                // 1. 提取地名（ns: 地名）
                if (nature.equals("ns") && meta.getRegion() == null) {
                    meta.setRegion(word);
                    log.debug("NLP extracted region: {} from text: {}", word, text);
                }

                // 2. 提取品牌（nrf: 音译人名，常用于品牌；nz: 其他专名）
                if ((nature.equals("nrf") || nature.equals("nz")) && meta.getBrand() == null) {
                    // 优先从品牌词典中查找
                    if (brandMap != null && brandMap.containsKey(word)) {
                        meta.setBrand(word);
                        log.debug("NLP extracted brand from dictionary: {} from text: {}", word, text);
                    } else if (isBrand(word)) {
                        // 兜底：检查是否符合品牌特征
                        meta.setBrand(word);
                        log.debug("NLP extracted brand (heuristic): {} from text: {}", word, text);
                    }
                }

                // 3. 提取行业（通过词典匹配分词结果）
                if (meta.getIndustry() == null && industryMap != null) {
                    String industry = industryMap.get(word);
                    if (industry != null) {
                        meta.setIndustry(industry);
                        log.debug("NLP extracted industry: {} (keyword: {}) from text: {}", industry, word, text);
                    }
                }

                // 4. 提取组织类型（nt: 机构团体，ni: 机构相关）
                if ((nature.equals("nt") || nature.equals("ni")) && meta.getOrgType() == null) {
                    // 尝试提取组织类型（如：公司、银行、中心等）
                    if (isOrgType(word)) {
                        meta.setOrgType(word);
                        log.debug("NLP extracted org type: {} from text: {}", word, text);
                    }
                }
            }

            // 第二遍：智能拆分复合词
            // 当无法直接识别时，尝试从分词结果中组合识别
            if (meta.getIndustry() == null || meta.getOrgType() == null) {
                intelligentExtraction(terms, meta);
            }

            // 计算置信度（基于提取到的字段数量）
            meta.setConfidence(calculateConfidence(meta));

            log.debug("NLP extraction result: region={}, industry={}, brand={}, orgType={}, confidence={}",
                    meta.getRegion(), meta.getIndustry(), meta.getBrand(), meta.getOrgType(), meta.getConfidence());

            return meta;

        } catch (Exception e) {
            log.warn("NLP extraction failed for text: {}, error: {}", text, e.getMessage());
            throw new NLPException(ErrorCode.MASK_NLP_FAILED,
                    String.format(ErrorMessage.MASK_NLP_FAILED, e.getMessage()), e);
        }
    }

    /**
     * 智能提取：从分词结果中组合识别
     * 适用场景：处理复合词，如"外轮代理有限公司"会被分为["外轮代理", "有限公司"]
     */
    private void intelligentExtraction(List<Term> terms, MetaInfo meta) {
        if (terms == null || terms.isEmpty()) {
            return;
        }

        // 尝试识别行业+组织类型的组合
        for (int i = 0; i < terms.size(); i++) {
            Term term = terms.get(i);
            String word = term.word;

            // 检查是否为行业词
            if (meta.getIndustry() == null && industryMap != null && industryMap.containsKey(word)) {
                // 检查下一个词是否为组织类型
                if (i + 1 < terms.size()) {
                    Term nextTerm = terms.get(i + 1);
                    if (isOrgType(nextTerm.word)) {
                        meta.setIndustry(industryMap.get(word));
                        if (meta.getOrgType() == null) {
                            meta.setOrgType(nextTerm.word);
                        }
                        log.debug("NLP intelligent extraction: industry={}, orgType={}", meta.getIndustry(), nextTerm.word);
                        break;
                    }
                }
            }

            // 检查是否为组织类型后缀
            if (meta.getOrgType() == null && isOrgType(word)) {
                meta.setOrgType(word);
                // 向前查找可能的行业词
                if (meta.getIndustry() == null && i > 0 && industryMap != null) {
                    Term prevTerm = terms.get(i - 1);
                    String industry = industryMap.get(prevTerm.word);
                    if (industry != null) {
                        meta.setIndustry(industry);
                        log.debug("NLP intelligent extraction (backward): industry={}, orgType={}", industry, word);
                    }
                }
            }
        }
    }

    /**
     * 判断词是否为品牌（启发式判断，作为兜底）
     * 优先使用 brandMap 词典匹配
     */
    private boolean isBrand(String word) {
        // 启发式判断：长度>=2的专有名词可能是品牌
        // 这只是兜底逻辑，实际应该优先使用 brandMap
        return word.length() >= 2;
    }

    /**
     * 判断词是否为组织类型
     *
     * @param word 词
     * @return true表示是组织类型
     */
    private boolean isOrgType(String word) {
        // 简单判断：包含常见组织类型关键词
        String[] orgTypeKeywords = {
                "公司", "集团", "银行", "中心", "研究院", "交易所",
                "学院", "大学", "医院", "图书馆", "局", "厅", "部", "委"
        };

        for (String keyword : orgTypeKeywords) {
            if (word.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 计算置信度
     *
     * @param meta 元信息
     * @return 置信度（0.0-1.0）
     */
    private double calculateConfidence(MetaInfo meta) {
        int fieldCount = 0;
        int totalFields = 4; // NLP提取地域、行业、品牌、组织类型

        if (meta.getRegion() != null) fieldCount++;
        if (meta.getIndustry() != null) fieldCount++;
        if (meta.getBrand() != null) fieldCount++;
        if (meta.getOrgType() != null) fieldCount++;

        // NLP提取的置信度相对较低（0.5-0.75）
        double baseConfidence = (double) fieldCount / totalFields;
        return baseConfidence * 0.75;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public String getProviderName() {
        return "HanLP";
    }
}
