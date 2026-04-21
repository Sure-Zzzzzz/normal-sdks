package io.github.surezzzzzz.sdk.elasticsearch.search.query.validator;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.QueryException;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import org.springframework.core.annotation.Order;

/**
 * collapse 字段折叠排序必填校验
 *
 * @author surezzzzzz
 */
@Order(70)
@SimpleElasticsearchSearchComponent
public class CollapseSortValidator implements QueryRequestValidator {

    @Override
    public void validate(QueryRequest request, SimpleElasticsearchSearchProperties properties) {
        if (request.getCollapse() == null || request.getCollapse().getField() == null) {
            return;
        }
        PaginationInfo pagination = request.getPagination();
        if (pagination.getSort() == null || pagination.getSort().isEmpty()) {
            throw new QueryException(ErrorCode.COLLAPSE_SORT_REQUIRED,
                    "使用字段折叠（collapse）时必须指定排序字段");
        }
    }
}
