package io.github.surezzzzzz.sdk.elasticsearch.search.query.pagination;

import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;

/**
 * 翻页策略接口
 *
 * <p>每种翻页方式对应一个实现，通过 {@link PaginationStrategyRegistry} 注册和查找。
 * 内置策略：
 * <ul>
 *   <li>{@code offset} - from + size 偏移分页</li>
 *   <li>{@code search_after:tiebreaker} - 游标分页，追加 _id ASC 保证稳定排序</li>
 *   <li>{@code search_after:none} - 游标分页，不追加 tiebreaker</li>
 *   <li>{@code search_after:pit} - 游标分页，使用 PIT 快照</li>
 * </ul>
 *
 * @author surezzzzzz
 */
public interface PaginationStrategy {

    /**
     * 将排序字段应用到请求构建器（默认实现，各策略按需调用）
     *
     * @param sourceBuilder ES 请求构建器
     * @param pagination    分页信息
     */
    default void applySortFields(SearchSourceBuilder sourceBuilder, PaginationInfo pagination) {
        if (pagination.getSort() == null || pagination.getSort().isEmpty()) {
            return;
        }
        for (PaginationInfo.SortField sortField : pagination.getSort()) {
            SortOrder order = SimpleElasticsearchSearchConstant.SORT_ORDER_DESC.equalsIgnoreCase(sortField.getOrder())
                    ? SortOrder.DESC : SortOrder.ASC;
            sourceBuilder.sort(sortField.getField(), order);
        }
    }

    /**
     * 校验分页参数（默认空实现，有特殊校验需求的策略自行重写）
     *
     * @param request    原始查询请求
     * @param pagination 分页信息
     */
    default void validate(QueryRequest request, PaginationInfo pagination) {
    }

    /**
     * 将翻页参数应用到请求构建器（设置 from/size/sort/pit 等）
     *
     * @param sourceBuilder ES 请求构建器
     * @param searchRequest ES 搜索请求（PIT 模式需要设置 pointInTimeBuilder）
     * @param pagination    分页信息
     * @param request       原始查询请求
     */
    void applyToRequest(SearchSourceBuilder sourceBuilder,
                        SearchRequest searchRequest,
                        PaginationInfo pagination,
                        QueryRequest request);

    /**
     * 从响应中提取翻页结果（hasMore/nextSearchAfter/pitId/scrollId 等）
     *
     * @param searchResponse ES 响应
     * @param pagination     分页信息
     * @param request        原始查询请求（供需要管理上下文生命周期的策略使用，如 PIT）
     * @return 分页结果
     */
    QueryResponse.PaginationResult buildResult(SearchResponse searchResponse,
                                               PaginationInfo pagination,
                                               QueryRequest request);
}
