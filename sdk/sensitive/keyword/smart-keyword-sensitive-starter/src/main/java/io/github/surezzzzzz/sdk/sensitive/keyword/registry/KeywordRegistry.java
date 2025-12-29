package io.github.surezzzzzz.sdk.sensitive.keyword.registry;

import io.github.surezzzzzz.sdk.sensitive.keyword.annotation.SmartKeywordSensitiveComponent;
import io.github.surezzzzzz.sdk.sensitive.keyword.configuration.SmartKeywordSensitiveProperties;
import io.github.surezzzzzz.sdk.sensitive.keyword.extractor.MetaInfo;
import io.github.surezzzzzz.sdk.sensitive.keyword.matcher.KeywordMatcher;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keyword Registry
 * 管理关键词、策略、元信息等
 *
 * @author surezzzzzz
 */
@Slf4j
@SmartKeywordSensitiveComponent
@RequiredArgsConstructor
public class KeywordRegistry {

    private final SmartKeywordSensitiveProperties properties;

    /**
     * 关键词匹配器
     */
    @Getter
    private KeywordMatcher matcher;

    /**
     * 关键词到策略配置的映射
     */
    private final Map<String, SmartKeywordSensitiveProperties.RuntimeStrategy> keywordStrategyMap = new ConcurrentHashMap<>();

    /**
     * 关键词到元信息的映射（用户预定义）
     */
    private final Map<String, MetaInfo> keywordMetaMap = new ConcurrentHashMap<>();

    /**
     * 模式到关键词的映射
     */
    private final Map<String, String> patternToKeywordMap = new ConcurrentHashMap<>();

    /**
     * 默认策略配置
     */
    @Getter
    private SmartKeywordSensitiveProperties.RuntimeStrategy defaultStrategy;

    /**
     * 兜底策略配置
     */
    @Getter
    private SmartKeywordSensitiveProperties.RuntimeStrategy fallbackStrategy;

    @PostConstruct
    public void init() {
        log.info("Initializing KeywordRegistry...");

        // 1. 初始化默认策略
        this.defaultStrategy = properties.getDefaultStrategy().toRuntimeStrategy();

        // 2. 初始化兜底策略
        this.fallbackStrategy = properties.getDefaultStrategy().toFallbackRuntimeStrategy();

        // 3. 加载用户配置的关键词
        loadKeywords();

        // 4. 构建关键词匹配器
        buildMatcher();

        log.info("KeywordRegistry initialized successfully with {} keywords", keywordStrategyMap.size());
    }

    /**
     * 加载关键词配置
     */
    private void loadKeywords() {
        if (CollectionUtils.isEmpty(properties.getKeywords())) {
            log.warn("No keywords configured");
            return;
        }

        for (SmartKeywordSensitiveProperties.Keyword config : properties.getKeywords()) {
            String keyword = config.getKeyword();

            // 1. 保存策略配置（始终保存，用于标记关键词存在）
            SmartKeywordSensitiveProperties.RuntimeStrategy strategy = null;
            if (config.getStrategy() != null) {
                strategy = config.getStrategy().toRuntimeStrategy();
                // 与默认策略合并
                strategy = strategy.mergeWithDefault(defaultStrategy);
            } else {
                // 没有自定义策略，使用默认策略
                strategy = defaultStrategy;
            }
            keywordStrategyMap.put(keyword, strategy);

            // 2. 保存元信息（如果用户预定义）
            if (config.getMeta() != null) {
                MetaInfo meta = config.getMeta().toMetaInfo(keyword);
                keywordMetaMap.put(keyword, meta);
            }

            // 3. 保存模式映射(patterns共享主keyword的meta和strategy)
            if (!CollectionUtils.isEmpty(config.getPatterns())) {
                for (String pattern : config.getPatterns()) {
                    patternToKeywordMap.put(pattern, keyword);
                    // 为pattern也存储strategy和meta,确保pattern匹配时使用主keyword的配置
                    keywordStrategyMap.put(pattern, strategy);
                    if (config.getMeta() != null) {
                        MetaInfo meta = config.getMeta().toMetaInfo(keyword);
                        keywordMetaMap.put(pattern, meta);
                    }
                }
            }

            log.debug("Loaded keyword: {}, has strategy: {}, has meta: {}",
                    keyword,
                    config.getStrategy() != null,
                    config.getMeta() != null);
        }
    }

    /**
     * 构建关键词匹配器
     */
    private void buildMatcher() {
        Set<String> keywords = keywordStrategyMap.keySet();
        this.matcher = new KeywordMatcher(keywords, patternToKeywordMap);
        log.info("KeywordMatcher built with {} keywords and {} patterns",
                keywords.size(), patternToKeywordMap.size());
    }

    /**
     * 获取关键词的策略配置
     *
     * @param keyword 关键词
     * @return 策略配置，未配置返回默认策略
     */
    public SmartKeywordSensitiveProperties.RuntimeStrategy getStrategy(String keyword) {
        SmartKeywordSensitiveProperties.RuntimeStrategy strategy = keywordStrategyMap.get(keyword);
        return strategy != null ? strategy : defaultStrategy;
    }

    /**
     * 获取关键词的元信息（用户预定义）
     *
     * @param keyword 关键词
     * @return 元信息，未配置返回null
     */
    public MetaInfo getMetaInfo(String keyword) {
        return keywordMetaMap.get(keyword);
    }

    /**
     * 判断关键词是否已配置
     *
     * @param keyword 关键词
     * @return true表示已配置
     */
    public boolean contains(String keyword) {
        return keywordStrategyMap.containsKey(keyword);
    }

    /**
     * 动态添加关键词
     * <p>
     * 线程安全：使用synchronized保证原子性，确保map更新和matcher更新的一致性
     *
     * @param keyword  关键词
     * @param strategy 策略配置（可选）
     */
    public synchronized void addKeyword(String keyword, SmartKeywordSensitiveProperties.RuntimeStrategy strategy) {
        if (strategy != null) {
            strategy = strategy.mergeWithDefault(defaultStrategy);
        } else {
            strategy = defaultStrategy;
        }
        keywordStrategyMap.put(keyword, strategy);

        // 更新匹配器
        matcher.addKeyword(keyword);

        log.info("Added keyword dynamically: {}", keyword);
    }

    /**
     * 动态添加模式映射
     * <p>
     * 线程安全：使用synchronized保证原子性，确保map更新和matcher更新的一致性
     *
     * @param pattern 模式
     * @param keyword 关键词
     */
    public synchronized void addPattern(String pattern, String keyword) {
        patternToKeywordMap.put(pattern, keyword);
        matcher.addPattern(pattern, keyword);

        log.info("Added pattern mapping dynamically: {} -> {}", pattern, keyword);
    }

    /**
     * 获取所有关键词
     *
     * @return 关键词集合
     */
    public Set<String> getAllKeywords() {
        return Collections.unmodifiableSet(keywordStrategyMap.keySet());
    }

    /**
     * 获取关键词数量
     *
     * @return 关键词数量
     */
    public int size() {
        return keywordStrategyMap.size();
    }
}
