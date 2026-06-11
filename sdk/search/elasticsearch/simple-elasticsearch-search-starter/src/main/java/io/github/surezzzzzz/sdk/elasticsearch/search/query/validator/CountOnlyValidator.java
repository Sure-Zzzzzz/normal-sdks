package io.github.surezzzzzz.sdk.elasticsearch.search.query.validator;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SearchAfterMode;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.QueryException;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;

/**
 * 校验 countOnly 模式下不支持的分页方式
 *
 * @author surezzzzzz
 * @since 1.6.6
 */
@SimpleElasticsearchSearchComponent
public class CountOnlyValidator implements QueryRequestValidator {

    @Override
    public void validate(QueryRequest request, SimpleElasticsearchSearchProperties properties) {
        if (!Boolean.TRUE.equals(request.getCountOnly())) {
            return;
        }
        PaginationInfo pagination = request.getPagination();
        if (pagination != null
                && pagination.isSearchAfterPagination()
                && pagination.getSearchAfterModeEnum() == SearchAfterMode.PIT) {
            throw new QueryException(ErrorCode.COUNT_ONLY_PIT_NOT_SUPPORTED,
                    ErrorMessage.COUNT_ONLY_PIT_NOT_SUPPORTED);
        }
    }
}
