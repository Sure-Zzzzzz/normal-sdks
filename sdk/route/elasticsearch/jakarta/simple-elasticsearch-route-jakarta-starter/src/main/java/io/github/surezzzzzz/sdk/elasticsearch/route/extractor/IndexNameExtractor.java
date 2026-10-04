package io.github.surezzzzzz.sdk.elasticsearch.route.extractor;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/**
 * 索引名称提取器接口 (责任链模式)
 *
 * <p>用于从方法调用参数中提取 Elasticsearch 索引名称。</p>
 * <p>支持责任链模式,多个提取器按优先级顺序尝试提取。</p>
 *
 * @author surezzzzzz
 * @since 1.0.6
 */
public interface IndexNameExtractor {

    /**
     * 尝试从方法参数中提取索引名称
     *
     * @param method 被调用的方法
     * @param args   方法参数数组
     * @return 提取到的索引名称,如果无法提取则返回 null
     */
    String extract(Method method, Object[] args);

    /**
     * 提取本次调用涉及的全部索引名。
     *
     * <p>旧接口只返回第一个索引，无法识别 {@code IndexCoordinates.of("a", "b")}、
     * 批量实体或批量 query 中的跨数据源请求。默认实现保留旧扩展点的兼容性；能够识别
     * 集合参数的提取器应覆盖此方法并返回全部候选索引。</p>
     *
     * @param method 被调用的方法
     * @param args   方法参数数组
     * @return 已提取的索引名，无法提取时返回空列表
     */
    default List<String> extractAll(Method method, Object[] args) {
        String indexName = extract(method, args);
        return indexName == null ? Collections.emptyList() : Collections.singletonList(indexName);
    }

    /**
     * 判断此提取器是否支持处理给定的参数
     *
     * @param arg 方法参数
     * @return 如果支持处理返回 true,否则返回 false
     */
    boolean supports(Object arg);
}
