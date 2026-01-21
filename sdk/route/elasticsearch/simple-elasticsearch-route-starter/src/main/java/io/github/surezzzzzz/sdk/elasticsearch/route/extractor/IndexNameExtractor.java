package io.github.surezzzzzz.sdk.elasticsearch.route.extractor;

import java.lang.reflect.Method;

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
     * 判断此提取器是否支持处理给定的参数
     *
     * @param arg 方法参数
     * @return 如果支持处理返回 true,否则返回 false
     */
    boolean supports(Object arg);
}
