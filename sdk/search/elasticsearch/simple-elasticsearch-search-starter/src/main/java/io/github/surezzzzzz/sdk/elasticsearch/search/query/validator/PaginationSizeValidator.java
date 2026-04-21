package io.github.surezzzzzz.sdk.elasticsearch.search.query.validator;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.QueryException;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import org.springframework.core.annotation.Order;

/**
 * 分页 size 上限校验
 *
 * @author surezzzzzz
 */
@Order(40)
@SimpleElasticsearchSearchComponent
public class PaginationSizeValidator implements QueryRequestValidator {

    @Override
    public void validate(QueryRequest request, SimpleElasticsearchSearchProperties properties) {
        PaginationInfo pagination = request.getPagination();
        if (pagination.getSize() > properties.getQueryLimits().getMaxSize()) {
            throw new QueryException(ErrorCode.QUERY_SIZE_EXCEEDED,
                    String.format(ErrorMessage.QUERY_SIZE_EXCEEDED, properties.getQueryLimits().getMaxSize()));
        }
    }
}
