package io.github.surezzzzzz.sdk.elasticsearch.search.query.validator;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.QueryException;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.pagination.PaginationStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.pagination.PaginationStrategyRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;

/**
 * search_after / scroll 排序必填校验，并委托给具体策略做额外校验
 *
 * @author surezzzzzz
 */
@Order(60)
@SimpleElasticsearchSearchComponent
@RequiredArgsConstructor
public class SearchAfterSortValidator implements QueryRequestValidator {

    private final PaginationStrategyRegistry paginationStrategyRegistry;

    @Override
    public void validate(QueryRequest request, SimpleElasticsearchSearchProperties properties) {
        PaginationInfo pagination = request.getPagination();

        // scroll 分页：委托给 ScrollPaginationStrategy 做完整校验
        if (pagination.isScrollPagination()) {
            PaginationStrategy strategy = paginationStrategyRegistry.resolve(pagination);
            strategy.validate(request, pagination);
            return;
        }

        if (!pagination.isSearchAfterPagination()) {
            return;
        }
        if (pagination.getSort() == null || pagination.getSort().isEmpty()) {
            throw new QueryException(ErrorCode.SEARCH_AFTER_SORT_REQUIRED,
                    ErrorMessage.SEARCH_AFTER_SORT_REQUIRED);
        }
        // 委托给具体策略做额外校验（如 PIT 版本校验、keepAlive 校验）
        PaginationStrategy strategy = paginationStrategyRegistry.resolve(pagination);
        strategy.validate(request, pagination);
    }
}
