package io.github.surezzzzzz.sdk.sensitive.keyword.support;

import io.github.surezzzzzz.sdk.sensitive.keyword.constant.ErrorCode;
import io.github.surezzzzzz.sdk.sensitive.keyword.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.sensitive.keyword.constant.SmartKeywordSensitiveConstant;
import io.github.surezzzzzz.sdk.sensitive.keyword.exception.ConfigurationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Resource Loader Support
 *
 * @author surezzzzzz
 */
@Slf4j
public final class ResourceLoader {

    private ResourceLoader() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 加载内置词典文件
     *
     * @param filename 文件名
     * @return 词典列表（去重）
     */
    public static List<String> loadBuiltInDictionary(String filename) {
        String resourcePath = SmartKeywordSensitiveConstant.BUILT_IN_RESOURCE_PATH + filename;
        return loadDictionary(resourcePath, true);
    }

    /**
     * 加载词典文件
     *
     * @param resourcePath  资源路径
     * @param deduplication 是否去重
     * @return 词典列表
     */
    public static List<String> loadDictionary(String resourcePath, boolean deduplication) {
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            if (!resource.exists()) {
                log.warn("Dictionary file not found: {}, returning empty list", resourcePath);
                return new ArrayList<>();
            }

            Set<String> uniqueWords = new LinkedHashSet<>();
            List<String> words = new ArrayList<>();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                int lineNumber = 0;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;

                    // 去除注释和空白
                    line = removeComment(line).trim();

                    if (!StringUtils.hasText(line)) {
                        continue;
                    }

                    // 去重处理
                    if (deduplication) {
                        if (uniqueWords.add(line)) {
                            words.add(line);
                        } else {
                            log.debug("Duplicate word found at line {}: {}", lineNumber, line);
                        }
                    } else {
                        words.add(line);
                    }
                }
            }

            log.info("Loaded {} words from {}", words.size(), resourcePath);
            return words;

        } catch (IOException e) {
            throw new ConfigurationException(ErrorCode.RESOURCE_LOAD_FAILED,
                    String.format(ErrorMessage.RESOURCE_LOAD_FAILED, resourcePath), e);
        }
    }

    /**
     * 移除注释（支持 # 和 // 两种格式）
     *
     * @param line 原始行
     * @return 移除注释后的内容
     */
    private static String removeComment(String line) {
        if (line == null) {
            return "";
        }

        // 处理 # 注释
        int hashIndex = line.indexOf('#');
        if (hashIndex >= 0) {
            line = line.substring(0, hashIndex);
        }

        // 处理 // 注释
        int slashIndex = line.indexOf("//");
        if (slashIndex >= 0) {
            line = line.substring(0, slashIndex);
        }

        return line;
    }

    /**
     * 加载地域词典
     *
     * @return 地域列表
     */
    public static List<String> loadRegions() {
        return loadBuiltInDictionary(SmartKeywordSensitiveConstant.REGION_DICT_FILE);
    }

    /**
     * 加载行业关键词Map（键值对格式：关键词=行业名称）
     *
     * @return 行业关键词映射表
     */
    public static Map<String, String> loadIndustryMap() {
        return loadKeyValueMap(SmartKeywordSensitiveConstant.BUILT_IN_RESOURCE_PATH + SmartKeywordSensitiveConstant.INDUSTRY_DICT_FILE);
    }

    /**
     * 加载品牌Map（键值对格式：品牌=行业）
     *
     * @return 品牌映射表
     */
    public static Map<String, String> loadBrandMap() {
        return loadKeyValueMap(SmartKeywordSensitiveConstant.BUILT_IN_RESOURCE_PATH + SmartKeywordSensitiveConstant.BRAND_DICT_FILE);
    }

    /**
     * 加载键值对格式的词典文件
     *
     * @param resourcePath 资源路径
     * @return 键值对映射表
     */
    private static Map<String, String> loadKeyValueMap(String resourcePath) {
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            if (!resource.exists()) {
                log.warn("Dictionary file not found: {}, returning empty map", resourcePath);
                return new HashMap<>();
            }

            Map<String, String> map = new HashMap<>();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                int lineNumber = 0;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;

                    // 去除注释和空白
                    line = removeComment(line).trim();

                    if (!StringUtils.hasText(line)) {
                        continue;
                    }

                    // 解析键值对（格式：key=value）
                    int equalsIndex = line.indexOf('=');
                    if (equalsIndex > 0 && equalsIndex < line.length() - 1) {
                        String key = line.substring(0, equalsIndex).trim();
                        String value = line.substring(equalsIndex + 1).trim();

                        if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
                            if (map.containsKey(key)) {
                                log.debug("Duplicate key found at line {}: {}, overwriting value", lineNumber, key);
                            }
                            map.put(key, value);
                        }
                    } else {
                        log.debug("Invalid key-value format at line {}: {}", lineNumber, line);
                    }
                }
            }

            log.info("Loaded {} key-value pairs from {}", map.size(), resourcePath);
            return map;

        } catch (IOException e) {
            throw new ConfigurationException(ErrorCode.RESOURCE_LOAD_FAILED,
                    String.format(ErrorMessage.RESOURCE_LOAD_FAILED, resourcePath), e);
        }
    }

    /**
     * 加载组织类型分类Map
     * 支持两种格式：
     * 1. key=value 格式：组织类型=分类标签
     * 2. key 格式：仅组织类型（value为null，表示无分类，会fallback到contains判断）
     *
     * @return 组织类型分类Map，key=组织类型，value=分类标签（可能为null）
     */
    public static Map<String, String> loadOrgTypeCategoryMap() {
        String resourcePath = SmartKeywordSensitiveConstant.BUILT_IN_RESOURCE_PATH + SmartKeywordSensitiveConstant.ORG_TYPE_DICT_FILE;
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            if (!resource.exists()) {
                log.warn("Org types file not found: {}, returning empty map", resourcePath);
                return new HashMap<>();
            }

            Map<String, String> map = new LinkedHashMap<>();  // 保持顺序（长度排序）

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                int lineNumber = 0;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;

                    // 去除注释和空白
                    line = removeComment(line).trim();

                    if (!StringUtils.hasText(line)) {
                        continue;
                    }

                    // 解析：支持 key=value 或纯 key 两种格式
                    int equalsIndex = line.indexOf('=');
                    if (equalsIndex > 0) {
                        // 格式1: key=value
                        String key = line.substring(0, equalsIndex).trim();
                        String value = line.substring(equalsIndex + 1).trim();
                        if (StringUtils.hasText(key)) {
                            map.put(key, StringUtils.hasText(value) ? value : null);
                        }
                    } else {
                        // 格式2: 纯key（无分类，value为null）
                        map.put(line, null);
                    }
                }
            }

            log.info("Loaded {} org type categories from {}", map.size(), resourcePath);
            return map;

        } catch (IOException e) {
            throw new ConfigurationException(ErrorCode.RESOURCE_LOAD_FAILED,
                    String.format(ErrorMessage.RESOURCE_LOAD_FAILED, resourcePath), e);
        }
    }

    /**
     * 从industry-keywords.txt提取指定行业的所有关键词
     *
     * @param industries 行业名称（支持多个，如"金融", "银行"等）
     * @return 匹配的关键词集合
     */
    public static Set<String> extractKeywordsByIndustry(String... industries) {
        if (industries == null || industries.length == 0) {
            return Collections.emptySet();
        }

        Map<String, String> industryMap = loadIndustryMap();
        Set<String> result = new LinkedHashSet<>();

        for (Map.Entry<String, String> entry : industryMap.entrySet()) {
            String keyword = entry.getKey();
            String industry = entry.getValue();

            // 检查是否匹配任何一个指定的行业
            for (String targetIndustry : industries) {
                if (industry != null && industry.contains(targetIndustry)) {
                    result.add(keyword);
                    break;  // 匹配到一个就够了，避免重复添加
                }
            }
        }

        log.debug("Extracted {} keywords for industries: {}", result.size(), Arrays.toString(industries));
        return result;
    }
}
