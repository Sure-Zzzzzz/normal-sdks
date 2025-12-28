package io.github.surezzzzzz.sdk.sensitive.keyword.recognizer;

import io.github.surezzzzzz.sdk.sensitive.keyword.annotation.SmartKeywordSensitiveComponent;
import io.github.surezzzzzz.sdk.sensitive.keyword.constant.BracketType;
import io.github.surezzzzzz.sdk.sensitive.keyword.constant.SmartKeywordSensitiveConstant;
import io.github.surezzzzzz.sdk.sensitive.keyword.extractor.BrandExtractor;
import io.github.surezzzzzz.sdk.sensitive.keyword.extractor.IndustryExtractor;
import io.github.surezzzzzz.sdk.sensitive.keyword.extractor.OrgTypeExtractor;
import io.github.surezzzzzz.sdk.sensitive.keyword.extractor.RegionExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Rule-Based Entity Recognizer
 * 基于规则引擎识别实体（地域 + 行业 + 品牌 + 组织类型）
 *
 * @author surezzzzzz
 */
@Slf4j
@RequiredArgsConstructor
@SmartKeywordSensitiveComponent
public class RuleBasedRecognizer implements EntityRecognizer {

    private final RegionExtractor regionExtractor;
    private final OrgTypeExtractor orgTypeExtractor;
    private final BrandExtractor brandExtractor;
    private final IndustryExtractor industryExtractor;

    @Override
    public List<RecognizeResult> recognize(String text) {
        if (!StringUtils.hasText(text)) {
            return new ArrayList<>();
        }

        List<RecognizeResult> results = new ArrayList<>();
        Set<String> recognized = new HashSet<>();

        // 获取词典
        List<String> regions = regionExtractor.getRegions();
        List<String> orgTypes = orgTypeExtractor.getOrgTypes();

        if (regions.isEmpty() || orgTypes.isEmpty()) {
            log.warn("Regions or OrgTypes dictionary is empty, recognition may not work properly");
            return results;
        }

        log.debug("RuleBasedRecognizer using {} regions and {} org types", regions.size(), orgTypes.size());

        // 策略1：基于品牌识别
        results.addAll(recognizeByBrand(text, recognized));

        // 策略2：基于行业识别
        results.addAll(recognizeByIndustry(text, recognized));

        // 策略3：查找所有组织类型后缀
        results.addAll(recognizeByOrgType(text, recognized, orgTypes));

        // 策略4：查找地域前缀 + 组织类型后缀的组合
        results.addAll(recognizeByRegionAndOrgType(text, recognized, regions, orgTypes));

        log.debug("Rule-based recognizer found {} organizations", results.size());
        return results;
    }

    /**
     * 基于品牌识别
     */
    private List<RecognizeResult> recognizeByBrand(String text, Set<String> recognized) {
        List<RecognizeResult> results = new ArrayList<>();

        if (brandExtractor == null) {
            return results;
        }

        // 提取品牌
        String brand = brandExtractor.extract(text);
        if (brand == null) {
            return results;
        }

        int brandIndex = text.indexOf(brand);
        if (brandIndex == -1) {
            return results;
        }

        // 从品牌后开始查找组织类型
        String remaining = text.substring(brandIndex + brand.length());
        List<String> orgTypes = orgTypeExtractor.getOrgTypes();

        for (String orgType : orgTypes) {
            int orgTypeIndex = remaining.indexOf(orgType);
            if (orgTypeIndex >= 0) {
                int endIndex = brandIndex + brand.length() + orgTypeIndex + orgType.length();
                String org = text.substring(brandIndex, endIndex);

                // 验证长度
                if (org.length() >= SmartKeywordSensitiveConstant.MIN_ORG_LENGTH && org.length() <= SmartKeywordSensitiveConstant.MAX_ORG_LENGTH) {
                    // 去重
                    if (recognized.add(org)) {
                        results.add(new RecognizeResult(org, brandIndex, endIndex, 0.85, "BRAND"));
                        log.debug("Brand recognizer found: {} at [{}, {})", org, brandIndex, endIndex);
                    }
                }
            }
        }

        return results;
    }

    /**
     * 基于行业识别
     * 识别"行业关键词 + 组织类型"的组合，例如"科技公司"、"金融中心"
     */
    private List<RecognizeResult> recognizeByIndustry(String text, Set<String> recognized) {
        List<RecognizeResult> results = new ArrayList<>();

        if (industryExtractor == null) {
            return results;
        }

        // 提取行业关键词
        String industry = industryExtractor.extract(text);
        if (industry == null) {
            return results;
        }

        int industryIndex = text.indexOf(industry);
        if (industryIndex == -1) {
            return results;
        }

        // 从行业关键词后开始查找组织类型
        String remaining = text.substring(industryIndex + industry.length());
        List<String> orgTypes = orgTypeExtractor.getOrgTypes();

        for (String orgType : orgTypes) {
            int orgTypeIndex = remaining.indexOf(orgType);
            if (orgTypeIndex >= 0) {
                int endIndex = industryIndex + industry.length() + orgTypeIndex + orgType.length();
                String org = text.substring(industryIndex, endIndex);

                // 验证长度
                if (org.length() >= SmartKeywordSensitiveConstant.MIN_ORG_LENGTH && org.length() <= SmartKeywordSensitiveConstant.MAX_ORG_LENGTH) {
                    // 去重
                    if (recognized.add(org)) {
                        results.add(new RecognizeResult(org, industryIndex, endIndex, 0.85, "INDUSTRY"));
                        log.debug("Industry recognizer found: {} at [{}, {})", org, industryIndex, endIndex);
                    }
                }
            }
        }

        return results;
    }

    /**
     * 基于组织类型后缀识别
     */
    private List<RecognizeResult> recognizeByOrgType(String text, Set<String> recognized, List<String> orgTypes) {
        List<RecognizeResult> results = new ArrayList<>();

        for (String orgType : orgTypes) {
            int index = 0;
            while ((index = text.indexOf(orgType, index)) != -1) {
                int endIndex = index + orgType.length();

                // 向前扩展，找到完整的组织名
                int startIndex = findOrgStart(text, index);

                if (startIndex < index) {
                    String org = text.substring(startIndex, endIndex);

                    // 继续查找后面的括号内容
                    int bracketEndIndex = findBracketEnd(text, endIndex);
                    if (bracketEndIndex > endIndex) {
                        org = text.substring(startIndex, bracketEndIndex);
                        endIndex = bracketEndIndex;
                    }

                    // 继续查找后面的组织类型后缀（处理子公司、分公司等）
                    int extendedEndIndex = findExtendedOrgType(text, endIndex, orgTypes);
                    if (extendedEndIndex > endIndex) {
                        org = text.substring(startIndex, extendedEndIndex);
                        endIndex = extendedEndIndex;
                    }

                    // 验证长度
                    if (org.length() >= SmartKeywordSensitiveConstant.MIN_ORG_LENGTH && org.length() <= SmartKeywordSensitiveConstant.MAX_ORG_LENGTH) {
                        // 去重
                        if (recognized.add(org)) {
                            results.add(new RecognizeResult(org, startIndex, org.length() + startIndex, 0.8, "RULE"));
                            log.debug("Rule recognizer found: {} at [{}, {})", org, startIndex, org.length() + startIndex);
                        }
                    }
                }

                index = endIndex;
            }
        }

        return results;
    }

    /**
     * 基于地域前缀 + 组织类型后缀识别
     */
    private List<RecognizeResult> recognizeByRegionAndOrgType(String text, Set<String> recognized,
                                                              List<String> regions, List<String> orgTypes) {
        List<RecognizeResult> results = new ArrayList<>();

        for (String region : regions) {
            int index = 0;
            while ((index = text.indexOf(region, index)) != -1) {
                int startIndex = index;

                // 从地域后开始查找组织类型
                String remaining = text.substring(startIndex);

                for (String orgType : orgTypes) {
                    int orgTypeIndex = remaining.indexOf(orgType);
                    if (orgTypeIndex > 0) {
                        int endIndex = startIndex + orgTypeIndex + orgType.length();
                        String org = text.substring(startIndex, endIndex);

                        // 验证长度
                        if (org.length() >= SmartKeywordSensitiveConstant.MIN_ORG_LENGTH && org.length() <= SmartKeywordSensitiveConstant.MAX_ORG_LENGTH) {
                            // 去重
                            if (recognized.add(org)) {
                                results.add(new RecognizeResult(org, startIndex, endIndex, 0.9, "RULE"));
                                log.debug("Rule recognizer found (region+type): {} at [{}, {})",
                                        org, startIndex, endIndex);
                            }
                        }
                    }
                }

                index++;
            }
        }

        return results;
    }

    /**
     * 向前查找组织名的开始位置
     */
    private int findOrgStart(String text, int endIndex) {
        int start = endIndex - 1;

        // 向前查找连续的中文字符、数字、字母、括号
        while (start >= 0) {
            char c = text.charAt(start);
            if (isOrgChar(c)) {
                start--;
            } else {
                break;
            }
        }

        return start + 1;
    }

    /**
     * 查找括号的结束位置
     * 如果当前位置后面跟着括号，返回所有括号的结束位置，否则返回原位置
     * 支持查找多个连续的括号内容
     * 支持跳过空格和其他分隔符
     */
    private int findBracketEnd(String text, int startIndex) {
        if (startIndex >= text.length()) {
            return startIndex;
        }

        int currentIndex = startIndex;

        // 跳过空格和其他分隔符
        while (currentIndex < text.length()) {
            char c = text.charAt(currentIndex);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                currentIndex++;
            } else {
                break;
            }
        }

        while (currentIndex < text.length()) {
            char nextChar = text.charAt(currentIndex);
            if (BracketType.isLeftBracket(nextChar)) {
                // 查找对应的右括号
                String rightBracketStr = BracketType.getRightBracket(nextChar);
                if (rightBracketStr == null) {
                    break;
                }
                char leftBracket = nextChar;
                char rightBracket = rightBracketStr.charAt(0);

                int bracketCount = 1;
                boolean found = false;
                for (int i = currentIndex + 1; i < text.length(); i++) {
                    char c = text.charAt(i);
                    if (c == leftBracket) {
                        bracketCount++;
                    } else if (c == rightBracket) {
                        bracketCount--;
                        if (bracketCount == 0) {
                            currentIndex = i + 1;
                            found = true;
                            break;
                        }
                    }
                }

                if (!found) {
                    break;
                }

                // 跳过括号后的空格和其他分隔符
                while (currentIndex < text.length()) {
                    char c = text.charAt(currentIndex);
                    if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                        currentIndex++;
                    } else {
                        break;
                    }
                }
            } else {
                break;
            }
        }

        return currentIndex;
    }

    /**
     * 查找扩展的组织类型后缀
     * 在找到组织名后，继续查找后面是否还有组织类型后缀（如"煤矿"、"事业部"等）
     */
    private int findExtendedOrgType(String text, int startIndex, List<String> orgTypes) {
        if (startIndex >= text.length()) {
            return startIndex;
        }

        int currentIndex = startIndex;
        int maxEndIndex = startIndex;

        // 检查当前位置是否是连接符（如"-"、"·"等）
        if (currentIndex < text.length()) {
            char c = text.charAt(currentIndex);
            if (c == '-' || c == '·' || c == '_' || c == ' ') {
                currentIndex++;
            }
        }

        // 查找后面的组织类型后缀
        String remaining = text.substring(currentIndex);
        for (String orgType : orgTypes) {
            int orgTypeIndex = remaining.indexOf(orgType);
            if (orgTypeIndex >= 0) {
                int endIndex = currentIndex + orgTypeIndex + orgType.length();
                if (endIndex > maxEndIndex) {
                    maxEndIndex = endIndex;
                }
            }
        }

        return maxEndIndex;
    }

    /**
     * 判断字符是否属于组织名的一部分
     */
    private boolean isOrgChar(char c) {
        return Character.isLetterOrDigit(c)
                || BracketType.isBracket(c)
                || c == '·' || c == '-' || c == '_';
    }
}
