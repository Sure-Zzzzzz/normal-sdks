package io.github.surezzzzzz.sdk.elasticsearch.search.agg.validator;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.AggregationException;
import org.springframework.core.annotation.Order;

/**
 * 聚合定义非空校验
 *
 * @author surezzzzzz
 */
@Order(20)
@SimpleElasticsearchSearchComponent
public class AggDefinitionValidator implements AggRequestValidator {

    @Override
    public void validate(AggRequest request, SimpleElasticsearchSearchProperties properties) {
        if (request.getAggs() == null || request.getAggs().isEmpty()) {
            throw new AggregationException(ErrorCode.AGG_DEFINITION_REQUIRED, ErrorMessage.AGG_DEFINITION_REQUIRED);
        }
    }
}
