package io.github.surezzzzzz.sdk.elasticsearch.search.query.validator;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.TimeRangeHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;

/**
 * 通配索引默认时间范围注入
 * 必须在 PaginationDefaultsValidator 之前执行（注入 dateRange 后分页才能正确处理）
 *
 * @author surezzzzzz
 */
@Slf4j
@Order(20)
@SimpleElasticsearchSearchComponent
public class DefaultDateRangeValidator implements QueryRequestValidator {

    @Override
    public void validate(QueryRequest request, SimpleElasticsearchSearchProperties properties) {
        String defaultDateRange = properties.getQueryLimits().getDefaultDateRange();
        if (StringUtils.hasText(defaultDateRange)
                && request.getDateRange() == null
                && isWildcardIndex(request.getIndex())) {
            request.setDateRange(TimeRangeHelper.buildRecentRange(defaultDateRange));
            log.debug("Applied default date range [{}] for wildcard index [{}]",
                    defaultDateRange, request.getIndex());
        }
    }

    private boolean isWildcardIndex(String index) {
        return index != null && (index.contains(SimpleElasticsearchSearchConstant.WILDCARD_STAR)
                || index.contains(SimpleElasticsearchSearchConstant.WILDCARD_QUESTION));
    }
}
