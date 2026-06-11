package io.github.surezzzzzz.sdk.elasticsearch.search.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.QueryException;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.validator.CountOnlyValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CountOnlyValidator 单元测试
 *
 * @author surezzzzzz
 * @since 1.6.6
 */
@Slf4j
class CountOnlyValidatorTest {

    private final CountOnlyValidator validator = new CountOnlyValidator();

    @Test
    @DisplayName("countOnly=true + PIT 分页 - 抛出异常")
    void testCountOnlyWithPit() {
        QueryRequest request = QueryRequest.builder()
                .index("test")
                .countOnly(true)
                .pagination(PaginationInfo.builder()
                        .type("search_after")
                        .searchAfterMode("pit")
                        .build())
                .build();

        log.info("测试: countOnly=true + PIT, 期望抛出 QueryException");

        QueryException ex = assertThrows(QueryException.class, () -> validator.validate(request, null));
        log.info("捕获异常: errorCode={}, message={}", ex.getErrorCode(), ex.getMessage());
        assertEquals(ErrorCode.COUNT_ONLY_PIT_NOT_SUPPORTED, ex.getErrorCode(),
                "异常错误码应为 SEARCH_QUERY_011");
    }

    @Test
    @DisplayName("countOnly=true + search_after 非PIT - 通过校验")
    void testCountOnlyWithSearchAfterNotPit() {
        QueryRequest request = QueryRequest.builder()
                .index("test")
                .countOnly(true)
                .pagination(PaginationInfo.builder()
                        .type("search_after")
                        .searchAfterMode("tiebreaker")
                        .build())
                .build();

        log.info("测试: countOnly=true + search_after 非 PIT, 期望通过校验");
        assertDoesNotThrow(() -> validator.validate(request, null));
        log.info("校验通过");
    }

    @Test
    @DisplayName("countOnly=true + offset 分页 - 通过校验")
    void testCountOnlyWithOffset() {
        QueryRequest request = QueryRequest.builder()
                .index("test")
                .countOnly(true)
                .pagination(PaginationInfo.builder()
                        .page(1)
                        .size(10)
                        .build())
                .build();

        log.info("测试: countOnly=true + offset 分页, 期望通过校验");
        assertDoesNotThrow(() -> validator.validate(request, null));
        log.info("校验通过");
    }

    @Test
    @DisplayName("countOnly=true + 无分页 - 通过校验")
    void testCountOnlyNoPagination() {
        QueryRequest request = QueryRequest.builder()
                .index("test")
                .countOnly(true)
                .build();

        log.info("测试: countOnly=true + 无分页, 期望通过校验");
        assertDoesNotThrow(() -> validator.validate(request, null));
        log.info("校验通过");
    }

    @Test
    @DisplayName("countOnly=false + PIT 分页 - 通过校验（不走 CountExecutor）")
    void testNotCountOnlyWithPit() {
        QueryRequest request = QueryRequest.builder()
                .index("test")
                .countOnly(false)
                .pagination(PaginationInfo.builder()
                        .type("search_after")
                        .searchAfterMode("pit")
                        .build())
                .build();

        log.info("测试: countOnly=false + PIT, 期望通过校验");
        assertDoesNotThrow(() -> validator.validate(request, null));
        log.info("校验通过");
    }

    @Test
    @DisplayName("countOnly=null + PIT 分页 - 通过校验")
    void testCountOnlyNullWithPit() {
        QueryRequest request = QueryRequest.builder()
                .index("test")
                .pagination(PaginationInfo.builder()
                        .type("search_after")
                        .searchAfterMode("pit")
                        .build())
                .build();

        log.info("测试: countOnly=null + PIT, 期望通过校验");
        assertDoesNotThrow(() -> validator.validate(request, null));
        log.info("校验通过");
    }

    @Test
    @DisplayName("countOnly=true + scroll 分页 - 通过校验（_count 不走 scroll，scrollId 静默忽略）")
    void testCountOnlyWithScroll() {
        QueryRequest request = QueryRequest.builder()
                .index("test")
                .countOnly(true)
                .pagination(PaginationInfo.builder()
                        .scrollId("dummy-scroll-id")
                        .scrollTtl("1m")
                        .build())
                .build();

        log.info("测试: countOnly=true + scroll, 期望通过校验（scroll 静默忽略）");
        assertDoesNotThrow(() -> validator.validate(request, null));
        log.info("校验通过");
    }

    @Test
    @DisplayName("countOnly=true + 有分页但无 searchAfterMode - 通过校验")
    void testCountOnlyWithPaginationNoSearchAfterMode() {
        QueryRequest request = QueryRequest.builder()
                .index("test")
                .countOnly(true)
                .pagination(PaginationInfo.builder()
                        .page(1)
                        .size(10)
                        .build())
                .build();

        log.info("测试: countOnly=true + 有分页无 searchAfterMode, 期望通过校验");
        assertDoesNotThrow(() -> validator.validate(request, null));
        log.info("校验通过");
    }
}
