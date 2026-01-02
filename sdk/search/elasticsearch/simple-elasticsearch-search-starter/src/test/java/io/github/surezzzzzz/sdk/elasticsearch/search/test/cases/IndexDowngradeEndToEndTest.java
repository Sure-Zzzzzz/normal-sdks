package io.github.surezzzzzz.sdk.elasticsearch.search.test.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.test.SimpleElasticsearchSearchTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 索引降级端到端测试
 * <p>
 * 测试场景：
 * 1. 日粒度索引降级（LEVEL_0 -> LEVEL_1 月级通配符）
 * 2. 预估降级触发（通过索引数量阈值）
 * 3. 查询降级（Query API）
 * 4. 聚合降级（Agg API）
 * 5. 降级配置验证
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchSearchTestApplication.class)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IndexDowngradeEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SimpleElasticsearchRouteRegistry registry;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String DOWNGRADE_LOG_INDEX_PREFIX = "test_downgrade_log--";
    private static final String DEFAULT_DATASOURCE = "primary";

    /**
     * 将对象转换为JSON字符串
     */
    private String toJson(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    @BeforeAll
    static void setupAll(@Autowired SimpleElasticsearchRouteRegistry registry) throws Exception {
        log.info("========== 开始准备降级测试数据 ==========");

        RestHighLevelClient primaryClient = registry.getHighLevelClient(DEFAULT_DATASOURCE);

        // 创建15个月的日粒度索引（模拟跨年查询场景）
        // 2024年1月 - 2025年3月，共15个月，每月31天，约465个索引
        // 这样可以触发降级：LEVEL_0 (465个索引) -> LEVEL_1 (15个月级通配符)
        createMultiMonthLogIndices(primaryClient);

        log.info("========== 降级测试数据准备完成 ==========");
    }

    /**
     * 创建多个月的日粒度索引
     */
    private static void createMultiMonthLogIndices(RestHighLevelClient client) throws Exception {
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2025, 3, 31);

        LocalDate currentDate = startDate;
        int totalIndices = 0;

        while (!currentDate.isAfter(endDate)) {
            String indexName = DOWNGRADE_LOG_INDEX_PREFIX + currentDate.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
            createLogIndexForDate(client, indexName, currentDate);
            currentDate = currentDate.plusDays(1);
            totalIndices++;
        }

        log.info("✓ 共创建 {} 个日粒度索引", totalIndices);
    }

    /**
     * 创建指定日期的日志索引
     */
    private static void createLogIndexForDate(RestHighLevelClient client, String indexName, LocalDate date) throws Exception {
        // 删除旧索引
        if (client.indices().exists(new GetIndexRequest(indexName), RequestOptions.DEFAULT)) {
            client.indices().delete(new DeleteIndexRequest(indexName), RequestOptions.DEFAULT);
        }

        // 创建索引
        CreateIndexRequest request = new CreateIndexRequest(indexName);
        request.mapping(
                "{" +
                        "  \"properties\": {" +
                        "    \"user_id\": {\"type\": \"keyword\"}," +
                        "    \"action\": {\"type\": \"keyword\"}," +
                        "    \"message\": {\"type\": \"text\"}," +
                        "    \"timestamp\": {\"type\": \"date\"}" +
                        "  }" +
                        "}",
                org.elasticsearch.xcontent.XContentType.JSON
        );
        client.indices().create(request, RequestOptions.DEFAULT);

        // 插入少量测试数据（每个索引1条，减少数据量）
        Map<String, Object> logData = new HashMap<>();
        logData.put("user_id", "user" + (date.getDayOfMonth() % 10));
        logData.put("action", date.getDayOfMonth() % 3 == 0 ? "login" : (date.getDayOfMonth() % 3 == 1 ? "logout" : "view"));
        logData.put("message", "Test log for " + date);
        logData.put("timestamp", date.atStartOfDay());

        IndexRequest indexRequest = new IndexRequest(indexName)
                .id("1")
                .source(logData);
        client.index(indexRequest, RequestOptions.DEFAULT);

        // 每10个索引打印一次日志
        if (date.getDayOfMonth() % 10 == 0) {
            log.info("✓ 已创建索引: {} (总计约 {} 个)", indexName, date.getDayOfYear());
        }
    }

    @AfterAll
    static void cleanupAll(@Autowired SimpleElasticsearchRouteRegistry registry) throws Exception {
        log.info("========== 开始清理降级测试数据 ==========");

        try {
            RestHighLevelClient primaryClient = registry.getHighLevelClient(DEFAULT_DATASOURCE);

            // 删除所有 test_downgrade_log--* 索引
            LocalDate startDate = LocalDate.of(2024, 1, 1);
            LocalDate endDate = LocalDate.of(2025, 3, 31);

            LocalDate currentDate = startDate;
            int deletedCount = 0;

            while (!currentDate.isAfter(endDate)) {
                String indexName = DOWNGRADE_LOG_INDEX_PREFIX + currentDate.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
                if (primaryClient.indices().exists(new GetIndexRequest(indexName), RequestOptions.DEFAULT)) {
                    primaryClient.indices().delete(new DeleteIndexRequest(indexName), RequestOptions.DEFAULT);
                    deletedCount++;
                }
                currentDate = currentDate.plusDays(1);
            }

            log.info("✓ 已删除 {} 个测试索引", deletedCount);

        } catch (Exception e) {
            log.warn("清理索引失败", e);
        }

        log.info("========== 降级测试数据清理完成 ==========");
    }

    // ==================== 降级功能测试 ====================

    @Test
    @Order(1)
    @DisplayName("1.1 大范围查询 - 触发预估降级（查询15个月数据）")
    void testLargeRangeQueryWithDowngrade() throws Exception {
        log.info("========== 测试：大范围查询触发降级 ==========");

        // 查询 2024-01-01 到 2025-03-31（15个月，约455个索引）
        // 预估触发条件：索引数量 > 200（配置的阈值）
        // 预期：自动降级到 LEVEL_1（月级通配符，15个通配符）
        QueryRequest request = QueryRequest.builder()
                .index("test_downgrade_log--*")
                .dateRange(QueryRequest.DateRange.builder()
                        .from("2024-01-01")
                        .to("2025-03-31")
                        .build())
                .pagination(PaginationInfo.builder()
                        .size(10)
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.total").exists())
                .andDo(result -> {
                    log.info("✓ 大范围查询成功（触发降级）");
                    log.info("✓ 查询15个月数据（约455个索引）自动降级到月级通配符");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(2)
    @DisplayName("1.2 跨年查询 - 验证降级结果准确性")
    void testCrossYearQueryWithDowngrade() throws Exception {
        log.info("========== 测试：跨年查询降级结果准确性 ==========");

        // 查询跨年数据：2024-12-01 到 2025-02-28
        // 预期：降级到月级通配符（2024.12.*, 2025.01.*, 2025.02.*）
        QueryRequest request = QueryRequest.builder()
                .index("test_downgrade_log--*")
                .dateRange(QueryRequest.DateRange.builder()
                        .from("2024-12-01")
                        .to("2025-02-28")
                        .build())
                .query(QueryCondition.builder()
                        .field("action")
                        .op("EQ")
                        .value("login")
                        .build())
                .pagination(PaginationInfo.builder()
                        .size(50)
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[*].action").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.equalTo("login")
                )))
                .andDo(result -> {
                    log.info("✓ 跨年查询降级成功，结果准确");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(3)
    @DisplayName("1.3 聚合查询 - 大范围降级")
    void testAggregationWithDowngrade() throws Exception {
        log.info("========== 测试：聚合查询降级 ==========");

        // 查询15个月数据的聚合
        AggRequest request = AggRequest.builder()
                .index("test_downgrade_log--*")
                .query(QueryCondition.builder()
                        .field("timestamp")
                        .op("BETWEEN")
                        .values(Arrays.asList("2024-01-01", "2025-03-31"))
                        .build())
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("by_action")
                                .type("TERMS")
                                .field("action")
                                .size(10)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/agg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aggregations.by_action").isArray())
                .andExpect(jsonPath("$.data.aggregations.by_action.length()").value(3))  // login, logout, view
                .andDo(result -> {
                    log.info("✓ 聚合查询降级成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(4)
    @DisplayName("1.4 单月查询 - 不触发降级")
    void testSingleMonthQueryNoDowngrade() throws Exception {
        log.info("========== 测试：单月查询不降级 ==========");

        // 查询单月数据（31个索引），不会触发降级
        QueryRequest request = QueryRequest.builder()
                .index("test_downgrade_log--*")
                .dateRange(QueryRequest.DateRange.builder()
                        .from("2024-01-01")
                        .to("2024-01-31")
                        .build())
                .pagination(PaginationInfo.builder()
                        .size(50)
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.total").value(31))  // 31天的数据
                .andDo(result -> {
                    log.info("✓ 单月查询成功（未触发降级）");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(5)
    @DisplayName("1.5 整年查询 - 验证 LEVEL_2 降级（年级通配符）")
    void testFullYearQueryLevel2Downgrade() throws Exception {
        log.info("========== 测试：整年查询触发 LEVEL_2 降级 ==========");

        // 查询整年数据（365个索引）
        // 预期：降级到月级通配符（12个通配符）
        QueryRequest request = QueryRequest.builder()
                .index("test_downgrade_log--*")
                .dateRange(QueryRequest.DateRange.builder()
                        .from("2024-01-01")
                        .to("2024-12-31")
                        .build())
                .query(QueryCondition.builder()
                        .field("action")
                        .op("IN")
                        .values(Arrays.asList("login", "logout"))
                        .build())
                .pagination(PaginationInfo.builder()
                        .size(10)
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[*].action").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.anyOf(
                                org.hamcrest.Matchers.equalTo("login"),
                                org.hamcrest.Matchers.equalTo("logout")
                        )
                )))
                .andDo(result -> {
                    log.info("✓ 整年查询降级成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(6)
    @DisplayName("1.6 分页查询 - 降级场景")
    void testPaginationWithDowngrade() throws Exception {
        log.info("========== 测试：分页查询 + 降级 ==========");

        // 查询大范围数据并分页
        QueryRequest request = QueryRequest.builder()
                .index("test_downgrade_log--*")
                .dateRange(QueryRequest.DateRange.builder()
                        .from("2024-01-01")
                        .to("2025-03-31")
                        .build())
                .pagination(PaginationInfo.builder()
                        .type("offset")
                        .page(1)
                        .size(20)
                        .sort(Collections.singletonList(
                                PaginationInfo.SortField.builder()
                                        .field("timestamp")
                                        .order("DESC")
                                        .build()
                        ))
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(20))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andDo(result -> {
                    log.info("✓ 分页查询降级成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(7)
    @DisplayName("1.7 嵌套聚合 - 降级场景")
    void testNestedAggregationWithDowngrade() throws Exception {
        log.info("========== 测试：嵌套聚合 + 降级 ==========");

        AggRequest request = AggRequest.builder()
                .index("test_downgrade_log--*")
                .query(QueryCondition.builder()
                        .field("timestamp")
                        .op("BETWEEN")
                        .values(Arrays.asList("2024-01-01", "2025-03-31"))
                        .build())
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("by_action")
                                .type("TERMS")
                                .field("action")
                                .size(10)
                                .aggs(Collections.singletonList(
                                        AggDefinition.builder()
                                                .name("user_count")
                                                .type("CARDINALITY")
                                                .field("user_id")
                                                .build()
                                ))
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/agg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aggregations.by_action").isArray())
                .andExpect(jsonPath("$.data.aggregations.by_action[0].user_count").exists())
                .andDo(result -> {
                    log.info("✓ 嵌套聚合降级成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(8)
    @DisplayName("1.8 多条件查询 - 降级场景")
    void testMultiConditionQueryWithDowngrade() throws Exception {
        log.info("========== 测试：多条件查询 + 降级 ==========");

        QueryRequest request = QueryRequest.builder()
                .index("test_downgrade_log--*")
                .query(QueryCondition.builder()
                        .logic("AND")
                        .conditions(Arrays.asList(
                                QueryCondition.builder()
                                        .field("timestamp")
                                        .op("BETWEEN")
                                        .values(Arrays.asList("2024-01-01", "2025-03-31"))
                                        .build(),
                                QueryCondition.builder()
                                        .field("action")
                                        .op("EQ")
                                        .value("login")
                                        .build()
                        ))
                        .build())
                .pagination(PaginationInfo.builder()
                        .size(10)
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[*].action").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.equalTo("login")
                )))
                .andDo(result -> {
                    log.info("✓ 多条件查询降级成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }
}
