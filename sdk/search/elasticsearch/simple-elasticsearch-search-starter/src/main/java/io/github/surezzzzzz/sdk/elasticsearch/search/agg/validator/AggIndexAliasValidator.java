package io.github.surezzzzzz.sdk.elasticsearch.search.agg.validator;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.AggregationException;
import org.springframework.core.annotation.Order;

/**
 * 索引别名非空校验（最先执行，后续校验依赖 index 非空）
 *
 * @author surezzzzzz
 */
@Order(10)
@SimpleElasticsearchSearchComponent
public class AggIndexAliasValidator implements AggRequestValidator {

    @Override
    public void validate(AggRequest request, SimpleElasticsearchSearchProperties properties) {
        if (request.getIndex() == null || request.getIndex().trim().isEmpty()) {
            throw new AggregationException(ErrorCode.INDEX_ALIAS_REQUIRED, ErrorMessage.INDEX_ALIAS_REQUIRED);
        }
    }
}
