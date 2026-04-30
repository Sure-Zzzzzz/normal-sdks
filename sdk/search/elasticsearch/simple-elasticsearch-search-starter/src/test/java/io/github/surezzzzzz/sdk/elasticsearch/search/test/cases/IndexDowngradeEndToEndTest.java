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
 * 1. 大范围查询触发降级（LEVEL_0 → LEVEL_1 月级通配符）
 * 2. 跨年查询降级结果准确性
 * 3. 聚合查询降级
 * 4. 单月查询不触发降级
 * 5. 分页查询 + 降级
 * 6. 嵌套聚合 + 降级
 * 7. 多条件查询 + 降级
 * <p>
 * 数据规模：3 个月（2024-01 ~ 2024-03），共 91 个日粒度索引
 * 降级阈值：35（测试配置），单月 31 天不触发，2 个月 60 天触发
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchSearchTestApplication.class)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IndexDowngradeEndToEndTest {

    private static final String DOWNGRADE_LOG_INDEX_PREFIX = "test_downgrade_log--";
    private static final String DEFAULT_DATASOURCE = "primary";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SimpleElasticsearchRouteRegistry registry;

    @Autowired
    private ObjectMapper objectMapper;

    private String toJson(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    @BeforeAll
    static void setupAll(@Autowired SimpleElasticsearchRouteRegistry registry) throws Exception {
        log.info("========== 开始准备降级测试数据 ==========");
        RestHighLevelClient client = registry.getHighLevelClient(DEFAULT_DATASOURCE);
        createLogIndices(client, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31));
        log.info("========== 降级测试数据准备完成 ==========");
    }

    @AfterAll
    static void cleanupAll(@Autowired SimpleElasticsearchRouteRegistry registry) throws Exception {
        log.info("========== 开始清理降级测试数据 ==========");
        try {
            RestHighLevelClient client = registry.getHighLevelClient(DEFAULT_DATASOURCE);
            org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest deleteRequest =
                    new org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest(DOWNGRADE_LOG_INDEX_PREFIX + "*");
            client.indices().delete(deleteRequest, RequestOptions.DEFAULT);
            log.info("✓ 已清理所有 {} 索引", DOWNGRADE_LOG_INDEX_PREFIX + "*");
        } catch (Exception e) {
            log.warn("清理索引失败: {}", e.getMessage());
        }
        log.info("========== 降级测试数据清理完成 ==========");
    }

    private static void createLogIndices(RestHighLevelClient client,
                                         LocalDate from, LocalDate to) throws Exception {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
        int count = 0;
        LocalDate current = from;
        while (!current.isAfter(to)) {
            String indexName = DOWNGRADE_LOG_INDEX_PREFIX + current.format(formatter);
            if (!client.indices().exists(new GetIndexRequest(indexName), RequestOptions.DEFAULT)) {
                CreateIndexRequest createRequest = new CreateIndexRequest(indexName);
                createRequest.mapping(
                        "{\"properties\":{" +
                                "\"user_id\":{\"type\":\"keyword\"}," +
                                "\"action\":{\"type\":\"keyword\"}," +
                                "\"message\":{\"type\":\"text\"}," +
                                "\"timestamp\":{\"type\":\"date\"}" +
                                "}}",
                        org.elasticsearch.xcontent.XContentType.JSON
                );
                client.indices().create(createRequest, RequestOptions.DEFAULT);

                Map<String, Object> doc = new HashMap<>();
                doc.put("user_id", "user" + (current.getDayOfMonth() % 10));
                doc.put("action", current.getDayOfMonth() % 3 == 0 ? "login"
                        : current.getDayOfMonth() % 3 == 1 ? "logout" : "view");
                doc.put("message", "Test log for " + current);
                doc.put("timestamp", current.atStartOfDay().toString());
                client.index(new IndexRequest(indexName).id("1").source(doc), RequestOptions.DEFAULT);
            }
            current = current.plusDays(1);
            count++;
        }
        log.info("✓ 共创建 {} 个日粒度索引（{} ~ {}）", count, from, to);
    }

    // ==================== 降级功能测试 ====================

    @Test
    @Order(1)
    @DisplayName("1.1 大范围查询 - 触发降级（2个月，60个索引 > 阈值35）")
    void testLargeRangeQueryWithDowngrade() throws Exception {
        log.info("========== 测试：大范围查询触发降级 ==========");

        QueryRequest request = QueryRequest.builder()
                .index("test_downgrade_log--*")
                .dateRange(QueryRequest.DateRange.builder()
                        .from("2024-01-01")
                        .to("2024-02-29")
                        .build())
                .pagination(PaginationInfo.builder().size(10).build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.total").value(60))  // 2024-01（31天）+ 2024-02（29天）= 60条
                .andDo(result -> log.info("✓ 大范围查询降级成功，response: {}",
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @Order(2)
    @DisplayName("1.2 跨月查询 - 验证降级结果准确性")
    void testCrossMonthQueryWithDowngrade() throws Exception {
        log.info("========== 测试：跨月查询降级结果准确性 ==========");

        QueryRequest request = QueryRequest.builder()
                .index("test_downgrade_log--*")
                .dateRange(QueryRequest.DateRange.builder()
                        .from("2024-01-15")
                        .to("2024-03-15")
                        .build())
                .query(QueryCondition.builder()
                        .field("action")
                        .op("EQ")
                        .value("login")
                        .build())
                .pagination(PaginationInfo.builder().size(50).build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[*].action").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.equalTo("login"))))
                .andDo(result -> log.info("✓ 跨月查询降级成功，结果准确"));
    }

    @Test
    @Order(3)
    @DisplayName("1.3 聚合查询 - 大范围降级")
    void testAggregationWithDowngrade() throws Exception {
        log.info("========== 测试：聚合查询降级 ==========");

        AggRequest request = AggRequest.builder()
                .index("test_downgrade_log--*")
                .query(QueryCondition.builder()
                        .field("timestamp")
                        .op("BETWEEN")
                        .values(Arrays.asList("2024-01-01", "2024-03-31"))
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
                .andExpect(jsonPath("$.data.aggregations.by_action.length()").value(3))
                .andDo(result -> log.info("✓ 聚合查询降级成功"));
    }

    @Test
    @Order(4)
    @DisplayName("1.4 单月查询 - 不触发降级（31个索引 < 阈值35）")
    void testSingleMonthQueryNoDowngrade() throws Exception {
        log.info("========== 测试：单月查询不降级 ==========");

        QueryRequest request = QueryRequest.builder()
                .index("test_downgrade_log--*")
                .dateRange(QueryRequest.DateRange.builder()
                        .from("2024-01-01")
                        .to("2024-01-31")
                        .build())
                .pagination(PaginationInfo.builder().size(50).build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.total").value(31))
                .andDo(result -> log.info("✓ 单月查询成功（未触发降级）"));
    }

    @Test
    @Order(5)
    @DisplayName("1.5 分页查询 - 降级场景")
    void testPaginationWithDowngrade() throws Exception {
        log.info("========== 测试：分页查询 + 降级 ==========");

        QueryRequest request = QueryRequest.builder()
                .index("test_downgrade_log--*")
                .dateRange(QueryRequest.DateRange.builder()
                        .from("2024-01-01")
                        .to("2024-03-31")
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
                .andDo(result -> log.info("✓ 分页查询降级成功"));
    }

    @Test
    @Order(6)
    @DisplayName("1.6 嵌套聚合 - 降级场景")
    void testNestedAggregationWithDowngrade() throws Exception {
        log.info("========== 测试：嵌套聚合 + 降级 ==========");

        AggRequest request = AggRequest.builder()
                .index("test_downgrade_log--*")
                .query(QueryCondition.builder()
                        .field("timestamp")
                        .op("BETWEEN")
                        .values(Arrays.asList("2024-01-01", "2024-03-31"))
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
                .andExpect(jsonPath("$.data.aggregations.by_action[0].user_count").isNumber())
                .andDo(result -> log.info("✓ 嵌套聚合降级成功"));
    }

    @Test
    @Order(7)
    @DisplayName("1.7 多条件查询 - 降级场景")
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
                                        .values(Arrays.asList("2024-01-01", "2024-03-31"))
                                        .build(),
                                QueryCondition.builder()
                                        .field("action")
                                        .op("EQ")
                                        .value("login")
                                        .build()
                        ))
                        .build())
                .pagination(PaginationInfo.builder().size(10).build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[*].action").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.equalTo("login"))))
                .andDo(result -> log.info("✓ 多条件查询降级成功"));
    }
}
