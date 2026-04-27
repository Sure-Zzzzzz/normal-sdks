package io.github.surezzzzzz.sdk.elasticsearch.search.agg.validator;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 聚合请求校验责任链
 * validators 通过 Spring 自动注入，顺序由各 Validator 的 {@code @Order} 控制
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchSearchComponent
@RequiredArgsConstructor
public class AggRequestValidatorChain {

    private final List<AggRequestValidator> validators;

    /**
     * 按顺序执行所有校验器，任一校验不通过时抛异常终止
     *
     * @param request    聚合请求
     * @param properties 配置
     */
    public void validate(AggRequest request, SimpleElasticsearchSearchProperties properties) {
        for (AggRequestValidator validator : validators) {
            validator.validate(request, properties);
        }
    }
}
