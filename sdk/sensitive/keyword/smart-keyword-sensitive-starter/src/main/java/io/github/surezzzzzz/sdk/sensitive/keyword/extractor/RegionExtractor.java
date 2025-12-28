package io.github.surezzzzzz.sdk.sensitive.keyword.extractor;

import io.github.surezzzzzz.sdk.sensitive.keyword.annotation.SmartKeywordSensitiveComponent;
import io.github.surezzzzzz.sdk.sensitive.keyword.configuration.SmartKeywordSensitiveProperties;
import io.github.surezzzzzz.sdk.sensitive.keyword.support.ResourceLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Region Extractor
 *
 * @author surezzzzzz
 */
@Slf4j
@SmartKeywordSensitiveComponent
public class RegionExtractor {

    private final SmartKeywordSensitiveProperties properties;
    private List<String> regions;

    /**
     * 括号正则表达式，用于提取括号内的地域
     */
    private static final Pattern BRACKET_PATTERN = Pattern.compile("[（(]([^）)]+)[）)]");

    @Autowired
    public RegionExtractor(SmartKeywordSensitiveProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        this.regions = ResourceLoader.loadRegions();
        // 按长度从长到短排序，确保优先匹配长地名（如"龙游县"优先于"龙游"）
        this.regions.sort(Comparator.comparingInt(String::length).reversed());
        log.info("RegionExtractor initialized with {} regions (sorted by length desc)", regions.size());
    }

    /**
     * 获取所有地域列表（供识别器使用）
     *
     * @return 地域列表
     */
    public List<String> getRegions() {
        return regions != null ? regions : new ArrayList<>();
    }

    /**
     * 提取地域信息
     * 优先级规则：
     * 1. 括号内的标准地域（如"（江苏）" → "江苏"）
     * 2. 前缀地域，按长度从长到短匹配
     * 3. 跳过黑名单中的非地域词
     *
     * @param text 文本
     * @return 地域信息，未找到返回null
     */
    public String extract(String text) {
        if (!StringUtils.hasText(text) || regions == null) {
            return null;
        }

        // 1. 优先提取括号内的地域（如"招商局金陵船舶（江苏）有限公司" → "江苏"）
        Matcher matcher = BRACKET_PATTERN.matcher(text);
        if (matcher.find()) {
            String bracketContent = matcher.group(1);
            // 检查括号内是否包含标准地域
            for (String region : regions) {
                if (bracketContent.equals(region) || bracketContent.startsWith(region)) {
                    log.debug("Extracted region from bracket: {} from text: {}", region, text);
                    return region;
                }
            }
        }

        // 2. 提取前缀地域,按长度从长到短匹配
        for (String region : regions) {
            if (text.startsWith(region)) {
                // 检查是否在黑名单中
                Set<String> nonRegionBlacklist = properties.getKeywordSets().getNonRegionBlacklist();
                if (nonRegionBlacklist != null && nonRegionBlacklist.contains(region)) {
                    log.debug("Skipped blacklisted region: {} from text: {}", region, text);
                    continue;
                }

                // 额外检查：如果匹配的是"海城"，但文本以"上海"开头，跳过
                // 这是为了处理"上海城投"被错误识别为"海城"的情况
                if (region.equals("海城") && text.startsWith("上海")) {
                    log.debug("Skipped region '海城' because text starts with '上海'");
                    continue;
                }

                // 额外检查：如果匹配的是"林县"，但文本以"上林县"开头，跳过
                if (region.equals("林县") && text.startsWith("上林县")) {
                    log.debug("Skipped region '林县' because text starts with '上林县'");
                    continue;
                }

                log.debug("Extracted region: {} from text: {}", region, text);
                return region;
            }
        }

        return null;
    }

    /**
     * 判断文本是否以地域开头
     *
     * @param text 文本
     * @return true表示以地域开头
     */
    public boolean startsWithRegion(String text) {
        return extract(text) != null;
    }
}
