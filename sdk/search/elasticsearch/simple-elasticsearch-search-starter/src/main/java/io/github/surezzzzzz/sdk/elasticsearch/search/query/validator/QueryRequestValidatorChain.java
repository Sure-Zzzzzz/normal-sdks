package io.github.surezzzzzz.sdk.elasticsearch.search.query.validator;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 查询请求校验责任链
 * validators 通过 Spring 自动注入，顺序由各 Validator 的 {@code @Order} 控制
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchSearchComponent
@RequiredArgsConstructor
public class QueryRequestValidatorChain {

    private final List<QueryRequestValidator> validators;

    /**
     * 按顺序执行所有校验器，任一校验不通过时抛异常终止
     *
     * @param request    查询请求
     * @param properties 配置
     */
    public void validate(QueryRequest request, SimpleElasticsearchSearchProperties properties) {
        for (QueryRequestValidator validator : validators) {
            validator.validate(request, properties);
        }
    }
}
