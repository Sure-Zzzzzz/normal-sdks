package io.github.surezzzzzz.sdk.sensitive.keyword.extractor;

import io.github.surezzzzzz.sdk.sensitive.keyword.annotation.SmartKeywordSensitiveComponent;
import io.github.surezzzzzz.sdk.sensitive.keyword.constant.SmartKeywordSensitiveConstant;
import io.github.surezzzzzz.sdk.sensitive.keyword.support.ResourceLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * Organization Type Extractor
 *
 * @author surezzzzzz
 */
@Slf4j
@SmartKeywordSensitiveComponent
public class OrgTypeExtractor {

    private List<String> orgTypes;
    private List<String> sortedOrgTypes;

    @PostConstruct
    public void init() {
        // 从ORG_TYPE_CATEGORY_MAP中提取所有组织类型（keys）
        this.orgTypes = new ArrayList<>(SmartKeywordSensitiveConstant.ORG_TYPE_CATEGORY_MAP.keySet());
        // 按长度从长到短排序，确保优先匹配长后缀（如"股份有限公司"优先于"有限公司"）
        this.sortedOrgTypes = new ArrayList<>(orgTypes);
        this.sortedOrgTypes.sort((a, b) -> Integer.compare(b.length(), a.length()));
        log.info("OrgTypeExtractor initialized with {} org types (sorted by length desc)", orgTypes.size());
    }

    /**
     * 获取所有组织类型列表（供识别器使用）
     *
     * @return 组织类型列表
     */
    public List<String> getOrgTypes() {
        return orgTypes != null ? orgTypes : new ArrayList<>();
    }

    /**
     * 提取组织类型
     * 使用endsWith匹配，从末尾提取组织类型后缀
     * 对于子公司结构（如"XX有限责任公司YY分公司"），提取末尾的后缀（"分公司"）
     *
     * @param text 文本
     * @return 组织类型，未找到返回null
     */
    public String extract(String text) {
        if (!StringUtils.hasText(text) || sortedOrgTypes == null) {
            return null;
        }

        // 使用预排序的列表，按长度从长到短匹配
        // 确保最小长度为MIN_ORG_TYPE_LENGTH，避免匹配到单个字符
        for (String orgType : sortedOrgTypes) {
            if (orgType.length() < SmartKeywordSensitiveConstant.MIN_ORG_TYPE_LENGTH) {
                continue;
            }
            if (text.endsWith(orgType)) {
                log.debug("Extracted org type: {} from text: {}", orgType, text);
                return orgType;
            }
        }

        return null;
    }

    /**
     * 判断文本是否以组织类型结尾
     *
     * @param text 文本
     * @return true表示以组织类型结尾
     */
    public boolean endsWithOrgType(String text) {
        return extract(text) != null;
    }
}
