package io.github.surezzzzzz.sdk.sensitive.keyword.extractor;

import io.github.surezzzzzz.sdk.sensitive.keyword.annotation.SmartKeywordSensitiveComponent;
import io.github.surezzzzzz.sdk.sensitive.keyword.support.ResourceLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Brand Extractor
 * 品牌提取器
 *
 * @author surezzzzzz
 */
@Slf4j
@SmartKeywordSensitiveComponent
public class BrandExtractor {

    private Map<String, String> brandMap;

    @PostConstruct
    public void init() {
        this.brandMap = ResourceLoader.loadBrandMap();
        log.info("BrandExtractor initialized with {} brands", brandMap.size());
    }

    /**
     * 获取所有品牌列表（供识别器使用）
     *
     * @return 品牌列表
     */
    public List<String> getBrands() {
        return brandMap != null ? new ArrayList<>(brandMap.keySet()) : new ArrayList<>();
    }

    /**
     * 获取品牌映射
     *
     * @return 品牌映射
     */
    public Map<String, String> getBrandMap() {
        return brandMap != null ? brandMap : new HashMap<>();
    }

    /**
     * 提取品牌
     *
     * @param text 文本
     * @return 品牌名称，未找到返回null
     */
    public String extract(String text) {
        if (!StringUtils.hasText(text) || brandMap == null) {
            return null;
        }

        return findMatchingBrand(text, true);
    }

    /**
     * 提取品牌对应的行业
     *
     * @param text 文本
     * @return 行业名称，未找到返回null
     */
    public String extractIndustry(String text) {
        if (!StringUtils.hasText(text) || brandMap == null) {
            return null;
        }

        return findMatchingBrand(text, false);
    }

    /**
     * 查找匹配的品牌或行业
     *
     * @param text        文本
     * @param returnBrand true返回品牌名称，false返回行业名称
     * @return 品牌或行业名称，未找到返回null
     */
    private String findMatchingBrand(String text, boolean returnBrand) {
        for (Map.Entry<String, String> entry : brandMap.entrySet()) {
            String brand = entry.getKey();
            if (text.startsWith(brand)) {
                String industry = entry.getValue();
                if (returnBrand) {
                    log.debug("Extracted brand: {} from text: {}", brand, text);
                    return brand;
                } else {
                    log.debug("Extracted brand: {} with industry: {} from text: {}", brand, industry, text);
                    return industry;
                }
            }
        }
        return null;
    }

    /**
     * 判断文本是否以品牌开头
     *
     * @param text 文本
     * @return true表示以品牌开头
     */
    public boolean startsWithBrand(String text) {
        return extract(text) != null;
    }
}
