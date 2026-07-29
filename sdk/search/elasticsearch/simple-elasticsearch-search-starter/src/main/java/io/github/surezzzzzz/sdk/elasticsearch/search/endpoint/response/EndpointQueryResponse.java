package io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;

/**
 * 查询接口响应
 *
 * @author surezzzzzz
 */
public class EndpointQueryResponse extends QueryResponse {

    /**
     * 转换查询响应
     *
     * @param source 查询响应
     * @return 查询接口响应
     */
    public static QueryResponse from(QueryResponse source) {
        EndpointQueryResponse response = new EndpointQueryResponse();
        response.setTotal(source.getTotal());
        response.setPage(source.getPage());
        response.setSize(source.getSize());
        response.setItems(source.getItems());
        response.setTook(source.getTook());
        response.setPagination(PaginationResponse.from(source.getPagination()));
        return response;
    }

    /**
     * 查询接口分页响应
     */
    public static class PaginationResponse extends QueryResponse.PaginationResult {

        /**
         * 转换分页响应
         *
         * @param source 分页响应
         * @return 查询接口分页响应
         */
        private static PaginationResponse from(QueryResponse.PaginationResult source) {
            if (source == null) {
                return null;
            }
            PaginationResponse response = new PaginationResponse();
            response.setType(source.getType());
            response.setHasMore(source.getHasMore());
            response.setNextSearchAfter(source.getNextSearchAfter());
            response.setPitId(source.getPitId());
            response.setScrollId(source.getScrollId());
            return response;
        }

        @Override
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String getScrollId() {
            return super.getScrollId();
        }
    }
}
