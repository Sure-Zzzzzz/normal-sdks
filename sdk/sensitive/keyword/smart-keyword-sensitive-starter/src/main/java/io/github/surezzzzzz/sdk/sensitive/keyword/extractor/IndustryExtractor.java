package io.github.surezzzzzz.sdk.sensitive.keyword.extractor;

import io.github.surezzzzzz.sdk.sensitive.keyword.annotation.SmartKeywordSensitiveComponent;
import io.github.surezzzzzz.sdk.sensitive.keyword.support.ResourceLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Industry Extractor
 *
 * @author surezzzzzz
 */
@Slf4j
@SmartKeywordSensitiveComponent
public class IndustryExtractor {

    private Map<String, String> industryMap;

    @PostConstruct
    public void init() {
        this.industryMap = ResourceLoader.loadIndustryMap();
        log.info("IndustryExtractor initialized with {} industry keywords", industryMap.size());
    }

    /**
     * 提取行业信息
     *
     * @param text 文本
     * @return 行业关键词，未找到返回null
     */
    public String extract(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }

        // 查找包含的行业关键词，返回原始关键词而不是行业名称
        for (Map.Entry<String, String> entry : industryMap.entrySet()) {
            if (text.contains(entry.getKey())) {
                log.debug("Extracted industry keyword: {} (industry: {}) from text: {}",
                        entry.getKey(), entry.getValue(), text);
                return entry.getKey();
            }
        }

        return null;
    }

    /**
     * 判断文本是否包含行业关键词
     *
     * @param text 文本
     * @return true表示包含行业关键词
     */
    public boolean containsIndustry(String text) {
        return extract(text) != null;
    }
}
