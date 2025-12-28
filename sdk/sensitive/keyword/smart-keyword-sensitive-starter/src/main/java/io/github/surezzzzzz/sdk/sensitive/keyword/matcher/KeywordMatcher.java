package io.github.surezzzzzz.sdk.sensitive.keyword.matcher;

import com.hankcs.algorithm.AhoCorasickDoubleArrayTrie;
import io.github.surezzzzzz.sdk.sensitive.keyword.constant.ErrorCode;
import io.github.surezzzzzz.sdk.sensitive.keyword.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.sensitive.keyword.exception.MaskException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Keyword Matcher based on Aho-Corasick Double Array Trie Algorithm
 * <p>
 * 线程安全说明：
 * - 初始化后的只读操作（match、contains等）是线程安全的
 * - 动态添加操作（addKeyword、addPattern等）使用synchronized保证线程安全
 * - trie字段使用volatile保证可见性
 *
 * @author surezzzzzz
 */
@Slf4j
public class KeywordMatcher {

    /**
     * AC自动机 Double Array Trie
     * 使用volatile确保多线程可见性
     */
    private volatile AhoCorasickDoubleArrayTrie<String> trie;

    /**
     * 关键词集合（用于快速查询）
     * 使用ConcurrentHashMap.newKeySet()保证线程安全
     */
    private final Set<String> keywords;

    /**
     * 模式到关键词的映射（支持多个模式匹配到同一个关键词）
     * 使用ConcurrentHashMap保证线程安全
     */
    private final Map<String, String> patternToKeyword;

    /**
     * 构造函数
     *
     * @param keywords 关键词列表
     */
    public KeywordMatcher(Collection<String> keywords) {
        this.keywords = ConcurrentHashMap.newKeySet();
        this.patternToKeyword = new ConcurrentHashMap<>();

        if (CollectionUtils.isEmpty(keywords)) {
            log.warn("Keywords is empty, matcher will not match anything");
            this.trie = new AhoCorasickDoubleArrayTrie<>();
            this.trie.build(new HashMap<>());
            return;
        }

        this.keywords.addAll(keywords);
        buildTrie(keywords, null);
    }

    /**
     * 构造函数（支持关键词和模式映射）
     *
     * @param keywords            关键词列表
     * @param patternToKeywordMap 模式到关键词的映射
     */
    public KeywordMatcher(Collection<String> keywords, Map<String, String> patternToKeywordMap) {
        this.keywords = ConcurrentHashMap.newKeySet();
        this.patternToKeyword = new ConcurrentHashMap<>();

        if (CollectionUtils.isEmpty(keywords)) {
            log.warn("Keywords is empty, matcher will not match anything");
            this.trie = new AhoCorasickDoubleArrayTrie<>();
            this.trie.build(new HashMap<>());
            return;
        }

        this.keywords.addAll(keywords);
        if (patternToKeywordMap != null) {
            this.patternToKeyword.putAll(patternToKeywordMap);
        }
        buildTrie(keywords, patternToKeywordMap);
    }

    /**
     * 构建AC自动机Trie树
     * <p>
     * 使用synchronized确保重建Trie时的线程安全
     *
     * @param keywords            关键词集合
     * @param patternToKeywordMap 模式到关键词的映射
     */
    private synchronized void buildTrie(Collection<String> keywords, Map<String, String> patternToKeywordMap) {
        try {
            Map<String, String> trieMap = new TreeMap<>();

            // 1. 添加所有关键词（key=value=keyword）
            for (String keyword : keywords) {
                if (StringUtils.hasText(keyword)) {
                    trieMap.put(keyword, keyword);
                }
            }

            // 2. 添加所有模式（key=pattern, value=keyword）
            if (!CollectionUtils.isEmpty(patternToKeywordMap)) {
                for (Map.Entry<String, String> entry : patternToKeywordMap.entrySet()) {
                    if (StringUtils.hasText(entry.getKey())) {
                        trieMap.put(entry.getKey(), entry.getValue());
                    }
                }
            }

            // 3. 构建Trie树
            AhoCorasickDoubleArrayTrie<String> newTrie = new AhoCorasickDoubleArrayTrie<>();
            newTrie.build(trieMap);

            // 4. 原子性地替换trie（volatile保证可见性）
            this.trie = newTrie;

            log.info("AC Double Array Trie built successfully with {} patterns", trieMap.size());

        } catch (Exception e) {
            throw new MaskException(ErrorCode.MASK_MATCHER_INIT_FAILED,
                    ErrorMessage.MASK_MATCHER_INIT_FAILED, e);
        }
    }

    /**
     * 匹配文本中的关键词
     *
     * @param text 待匹配文本
     * @return 匹配结果列表
     */
    public List<MatchResult> match(String text) {
        if (!StringUtils.hasText(text)) {
            return new ArrayList<>();
        }

        try {
            List<AhoCorasickDoubleArrayTrie.Hit<String>> hits = trie.parseText(text);

            return hits.stream()
                    .map(hit -> convertToMatchResult(hit, text))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Failed to match text: {}", text, e);
            return new ArrayList<>();
        }
    }

    /**
     * 转换为MatchResult
     *
     * @param hit  AC自动机匹配结果
     * @param text 原文本
     * @return MatchResult
     */
    private MatchResult convertToMatchResult(AhoCorasickDoubleArrayTrie.Hit<String> hit, String text) {
        // hit.value 就是关键词（或对应的关键词）
        String keyword = hit.value;
        int start = hit.begin;
        int end = hit.end;
        String matchedText = text.substring(start, end);

        return new MatchResult(
                keyword,
                start,
                end,
                matchedText
        );
    }

    /**
     * 判断文本是否包含关键词
     *
     * @param text 待匹配文本
     * @return true表示包含关键词
     */
    public boolean contains(String text) {
        return !match(text).isEmpty();
    }

    /**
     * 获取第一个匹配结果
     *
     * @param text 待匹配文本
     * @return 第一个匹配结果，未匹配返回null
     */
    public MatchResult findFirst(String text) {
        List<MatchResult> results = match(text);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * 获取所有匹配的关键词（去重）
     *
     * @param text 待匹配文本
     * @return 关键词集合
     */
    public Set<String> findAllKeywords(String text) {
        return match(text).stream()
                .map(MatchResult::getKeyword)
                .collect(Collectors.toSet());
    }

    /**
     * 统计匹配次数
     *
     * @param text 待匹配文本
     * @return 匹配次数
     */
    public int countMatches(String text) {
        return match(text).size();
    }

    /**
     * 添加关键词（动态添加）
     * <p>
     * 线程安全：使用synchronized保证线程安全
     * 注意：动态添加会重建整个Trie树，性能开销较大，建议批量添加
     *
     * @param keyword 关键词
     */
    public synchronized void addKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }

        if (keywords.contains(keyword)) {
            log.debug("Keyword already exists: {}", keyword);
            return;
        }

        keywords.add(keyword);

        // 重建Trie树
        buildTrie(keywords, patternToKeyword);

        log.info("Added keyword: {}, total keywords: {}", keyword, keywords.size());
    }

    /**
     * 添加模式映射
     * <p>
     * 线程安全：使用synchronized保证线程安全
     *
     * @param pattern 模式
     * @param keyword 对应的关键词
     */
    public synchronized void addPattern(String pattern, String keyword) {
        if (!StringUtils.hasText(pattern) || !StringUtils.hasText(keyword)) {
            return;
        }

        patternToKeyword.put(pattern, keyword);

        // 重建Trie树
        buildTrie(keywords, patternToKeyword);

        log.info("Added pattern mapping: {} -> {}", pattern, keyword);
    }

    /**
     * 批量添加关键词
     * <p>
     * 线程安全：使用synchronized保证线程安全
     * 相比单个添加，批量添加只重建一次Trie树，性能更好
     *
     * @param newKeywords 关键词列表
     */
    public synchronized void addKeywords(Collection<String> newKeywords) {
        if (CollectionUtils.isEmpty(newKeywords)) {
            return;
        }

        keywords.addAll(newKeywords);

        // 重建Trie树
        buildTrie(keywords, patternToKeyword);

        log.info("Added {} keywords, total keywords: {}", newKeywords.size(), keywords.size());
    }

    /**
     * 获取关键词数量
     *
     * @return 关键词数量
     */
    public int size() {
        return keywords.size();
    }

    /**
     * 判断是否为空
     *
     * @return true表示没有关键词
     */
    public boolean isEmpty() {
        return keywords.isEmpty();
    }

    /**
     * 获取所有关键词
     *
     * @return 关键词集合（只读）
     */
    public Set<String> getKeywords() {
        return Collections.unmodifiableSet(keywords);
    }
}
