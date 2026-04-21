package io.github.surezzzzzz.sdk.elasticsearch.search.query.validator;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import org.springframework.core.annotation.Order;

/**
 * 分页默认值填充（必须在 PaginationSizeValidator 之前执行，先填默认值再校验上限）
 *
 * @author surezzzzzz
 */
@Order(30)
@SimpleElasticsearchSearchComponent
public class PaginationDefaultsValidator implements QueryRequestValidator {

    @Override
    public void validate(QueryRequest request, SimpleElasticsearchSearchProperties properties) {
        PaginationInfo pagination = request.getPagination();
        if (pagination == null) {
            pagination = PaginationInfo.builder()
                    .type(SimpleElasticsearchSearchConstant.PAGINATION_TYPE_OFFSET)
                    .page(1)
                    .size(properties.getQueryLimits().getDefaultSize())
                    .build();
            request.setPagination(pagination);
            return;
        }
        if (pagination.getSize() == null) {
            pagination.setSize(properties.getQueryLimits().getDefaultSize());
        }
        if (pagination.isOffsetPagination() && pagination.getPage() == null) {
            pagination.setPage(1);
        }
    }
}
