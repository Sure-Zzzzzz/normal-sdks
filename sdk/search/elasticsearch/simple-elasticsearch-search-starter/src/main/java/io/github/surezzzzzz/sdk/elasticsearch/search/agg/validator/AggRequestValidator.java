package io.github.surezzzzzz.sdk.elasticsearch.search.agg.validator;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;

/**
 * 聚合请求校验器接口
 *
 * @author surezzzzzz
 */
public interface AggRequestValidator {

    /**
     * 校验请求，不通过时抛异常
     *
     * @param request    聚合请求
     * @param properties 配置
     */
    void validate(AggRequest request, SimpleElasticsearchSearchProperties properties);
}
