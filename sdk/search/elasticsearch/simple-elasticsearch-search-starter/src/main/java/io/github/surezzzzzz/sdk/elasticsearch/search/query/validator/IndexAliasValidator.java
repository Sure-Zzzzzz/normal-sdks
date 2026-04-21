package io.github.surezzzzzz.sdk.elasticsearch.search.query.validator;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.QueryException;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import org.springframework.core.annotation.Order;

/**
 * 索引别名非空校验（最先执行，后续校验依赖 index 非空）
 *
 * @author surezzzzzz
 */
@Order(10)
@SimpleElasticsearchSearchComponent
public class IndexAliasValidator implements QueryRequestValidator {

    @Override
    public void validate(QueryRequest request, SimpleElasticsearchSearchProperties properties) {
        if (request.getIndex() == null || request.getIndex().trim().isEmpty()) {
            throw new QueryException(ErrorCode.INDEX_ALIAS_REQUIRED, ErrorMessage.INDEX_ALIAS_REQUIRED);
        }
    }
}
