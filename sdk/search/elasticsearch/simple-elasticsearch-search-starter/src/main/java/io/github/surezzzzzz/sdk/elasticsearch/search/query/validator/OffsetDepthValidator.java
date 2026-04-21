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
 * offset 分页深度校验
 *
 * @author surezzzzzz
 */
@Order(50)
@SimpleElasticsearchSearchComponent
public class OffsetDepthValidator implements QueryRequestValidator {

    @Override
    public void validate(QueryRequest request, SimpleElasticsearchSearchProperties properties) {
        PaginationInfo pagination = request.getPagination();
        if (!pagination.isOffsetPagination()) {
            return;
        }
        int from = (pagination.getPage() - 1) * pagination.getSize();
        if (from + pagination.getSize() > properties.getQueryLimits().getMaxOffset()) {
            throw new QueryException(ErrorCode.OFFSET_PAGINATION_EXCEEDED,
                    String.format(ErrorMessage.OFFSET_PAGINATION_EXCEEDED,
                            properties.getQueryLimits().getMaxOffset()));
        }
    }
}
