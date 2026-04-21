package io.github.surezzzzzz.sdk.elasticsearch.search.query.validator;

import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;

/**
 * 查询请求校验器接口
 *
 * @author surezzzzzz
 */
public interface QueryRequestValidator {

    /**
     * 校验请求，不通过时抛异常
     *
     * @param request    查询请求
     * @param properties 配置
     */
    void validate(QueryRequest request, SimpleElasticsearchSearchProperties properties);
}
