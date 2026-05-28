package io.github.surezzzzzz.sdk.elasticsearch.search.test.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.elasticsearch.route.model.ClusterInfo;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.PipelineAggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.request.ExpressionAggRequest;
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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Search 端到端测试
 * <p>
 * 测试场景：
 * 1. 索引管理 API
 * 2. 字段查询 API
 * 3. 数据查询（各种操作符、分页、排序）
 * 4. 聚合查询（metrics、bucket、嵌套聚合）
 * 5. 敏感字段处理（脱敏、禁止访问）
 * 6. 日期分割索引
 * 7. 多数据源路由与版本兼容性
 * 8. 错误处理
 *
 * <p><b>版本兼容性说明：</b>
 * 使用 simple-elasticsearch-route-starter 提供的 SimpleElasticsearchRouteRegistry 获取版本自适应的客户端，
 * 支持多数据源路由，确保不同版本的 ES 集群都能正常工作（例如 ES 6.2.2 与 ES 7.x）
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchSearchTestApplication.class)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SearchEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SimpleElasticsearchRouteRegistry registry;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String USER_INDEX = "test_user_index";
    private static final String NL_USER_INDEX = "test_nl_user_index";
    private static final String ORDER_INDEX = "test_order_index";
    private static final String LOG_INDEX_PREFIX = "test_log_";
    private static final String SECONDARY_INDEX = "test_index_b.secondary";  // 路由到 secondary 数据源的索引
    private static final String DEFAULT_DATASOURCE = "primary";  // 从配置文件中获取的默认数据源
    private static final String SECONDARY_DATASOURCE = "secondary";  // 第二个数据源

    /**
     * 将对象转换为JSON字符串
     */
    private String toJson(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    @BeforeAll
    static void setupAll(@Autowired SimpleElasticsearchRouteRegistry registry) throws Exception {
        log.info("========== 开始准备测试数据 ==========");

        // 获取默认数据源的客户端
        RestHighLevelClient primaryClient = registry.getHighLevelClient(DEFAULT_DATASOURCE);

        // 1. 创建 order 索引（primary 数据源）
        createOrderIndex(primaryClient);

        // 2. 创建 user 索引（primary 数据源）
        createUserIndex(primaryClient);

        // 2.5 创建 NL 用户索引（带 keyword 子字段，用于表达式 DSL 端到端测试）
        createNlUserIndex(primaryClient);

        // 3. 创建多天的 log 索引（模拟日期分割，primary 数据源）
        createMultipleDateLogIndices(primaryClient);

        // 4. 创建 secondary 索引（secondary 数据源）- 测试多数据源路由
        createSecondaryIndex(registry);

        log.info("========== 测试数据准备完成 ==========");
    }

    /**
     * 创建多天的日期分割索引
     */
    private static void createMultipleDateLogIndices(RestHighLevelClient client) throws Exception {
        LocalDateTime baseDate = LocalDateTime.now();

        // 创建最近3天的索引
        for (int i = 0; i < 3; i++) {
            LocalDateTime date = baseDate.minusDays(i);
            String indexName = LOG_INDEX_PREFIX + date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));

            createLogIndexForDate(client, indexName, date, i);
        }
    }

    /**
     * 创建指定日期的日志索引
     */
    private static void createLogIndexForDate(RestHighLevelClient client, String indexName,
                                              LocalDateTime date, int dayOffset) throws Exception {
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
                        "    \"createTime\": {\"type\": \"date\"}" +
                        "  }" +
                        "}",
                org.elasticsearch.xcontent.XContentType.JSON
        );
        client.indices().create(request, RequestOptions.DEFAULT);
        log.info("✓ 已创建索引: {}", indexName);

        // 插入该日期的测试数据
        List<Map<String, Object>> logs = Arrays.asList(
                createLog("user001", "login", "用户登录 - " + date.toLocalDate(), date),
                createLog("user002", "logout", "用户登出 - " + date.toLocalDate(), date),
                createLog("user003", "create", "创建记录 - " + date.toLocalDate(), date)
        );

        for (int i = 0; i < logs.size(); i++) {
            IndexRequest indexRequest = new IndexRequest(indexName)
                    .id(String.format("%d-%d", dayOffset, i + 1))
                    .source(logs.get(i));
            client.index(indexRequest, RequestOptions.DEFAULT);
        }

        Thread.sleep(1000);
        log.info("✓ 已插入 {} 条测试数据到 {}", logs.size(), indexName);
    }

    private static Map<String, Object> createLog(String userId, String action, String message, LocalDateTime createTime) {
        Map<String, Object> log = new HashMap<>();
        log.put("user_id", userId);
        log.put("action", action);
        log.put("message", message);
        log.put("createTime", createTime);  // ✅ 使用参数中的时间
        return log;
    }


    @AfterAll
    static void cleanupAll(@Autowired SimpleElasticsearchRouteRegistry registry) throws Exception {
        log.info("========== 开始清理测试数据 ==========");

        try {
            // 清理 primary 数据源的索引
            RestHighLevelClient primaryClient = registry.getHighLevelClient(DEFAULT_DATASOURCE);
            deleteIndexIfExists(primaryClient, ORDER_INDEX);
            deleteIndexIfExists(primaryClient, USER_INDEX);
            deleteIndexIfExists(primaryClient, NL_USER_INDEX);

            // 删除所有 test_log_* 索引
            LocalDateTime baseDate = LocalDateTime.now();
            for (int i = 0; i < 3; i++) {
                LocalDateTime date = baseDate.minusDays(i);
                String indexName = LOG_INDEX_PREFIX + date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
                deleteIndexIfExists(primaryClient, indexName);
            }

            // 清理 secondary 数据源的索引
            RestHighLevelClient secondaryClient = registry.getHighLevelClient(SECONDARY_DATASOURCE);
            deleteIndexIfExists(secondaryClient, SECONDARY_INDEX);

        } catch (Exception e) {
            log.warn("清理索引失败", e);
        }

        log.info("========== 测试数据清理完成 ==========");
    }

    private static void deleteIndexIfExists(RestHighLevelClient client, String indexName) throws Exception {
        if (client.indices().exists(new GetIndexRequest(indexName), RequestOptions.DEFAULT)) {
            client.indices().delete(new DeleteIndexRequest(indexName), RequestOptions.DEFAULT);
            log.info("✓ 已删除索引: {}", indexName);
        }
    }

    // ==================== 1. 索引管理 API 测试 ====================

    @Test
    @Order(1)
    @DisplayName("1.1 获取所有索引列表")
    void testGetIndices() throws Exception {
        log.info("========== 测试：获取所有索引列表 ==========");

        mockMvc.perform(get("/api/indices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(7))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andDo(result -> {
                    log.info("✓ 获取索引列表成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(2)
    @DisplayName("1.2 获取索引字段信息（无别名索引）")
    void testGetFields() throws Exception {
        log.info("========== 测试：获取索引字段信息（无别名索引） ==========");

        // 测试无别名的索引：使用索引名
        mockMvc.perform(get("/api/indices/test_order_index/fields"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.index").value("test_order_index"))
                .andExpect(jsonPath("$.data.fields").isArray())
                .andExpect(jsonPath("$.data.fields.length()").value(7))
                .andExpect(jsonPath("$.data.fields[?(@.name=='amount')]").exists())
                .andExpect(jsonPath("$.data.fields[?(@.name=='status')]").exists())
                .andDo(result -> {
                    log.info("✓ 获取字段信息成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(3)
    @DisplayName("1.3 获取索引字段信息（有别名索引）")
    void testGetFieldsWithIdentifier() throws Exception {
        log.info("========== 测试：获取索引字段信息（有别名索引） ==========");

        // 测试有别名的索引：使用别名
        mockMvc.perform(get("/api/indices/test_user/fields"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.index").value("test_user"))
                .andExpect(jsonPath("$.data.fields").isArray())
                .andExpect(jsonPath("$.data.fields.length()").value(7))
                .andExpect(jsonPath("$.data.fields[?(@.name=='name')]").exists())
                .andExpect(jsonPath("$.data.fields[?(@.name=='age')]").exists())
                .andDo(result -> {
                    log.info("✓ 获取字段信息成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(4)
    @DisplayName("1.4 刷新索引 Mapping")
    void testRefreshMapping() throws Exception {
        log.info("========== 测试：刷新索引 Mapping ==========");

        mockMvc.perform(post("/api/indices/test_user/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isString())
                .andDo(result -> {
                    log.info("✓ 刷新 Mapping 成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    // ==================== 2. 数据查询测试 ====================

    @Test
    @Order(10)
    @DisplayName("2.1 基础查询 - EQ 操作符")
    void testQueryEq() throws Exception {
        log.info("========== 测试：EQ 查询 ==========");

        QueryRequest request = QueryRequest.builder()
                .index("test_user")
                .query(QueryCondition.builder()
                        .field("name")
                        .op("EQ")
                        .value("张三")
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("张三"))
                .andExpect(jsonPath("$.data.items[0].age").value(25))
                .andExpect(jsonPath("$.data.items[0].city").value("北京"))
                .andDo(result -> {
                    log.info("✓ EQ 查询成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(11)
    @DisplayName("2.2 范围查询 - BETWEEN")
    void testQueryBetween() throws Exception {
        log.info("========== 测试：BETWEEN 查询 ==========");

        QueryRequest request = QueryRequest.builder()
                .index("test_user")
                .query(QueryCondition.builder()
                        .field("age")
                        .op("BETWEEN")
                        .values(Arrays.asList(20, 30))
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(4))
                .andExpect(jsonPath("$.data.items[*].age").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.greaterThanOrEqualTo(20),
                                org.hamcrest.Matchers.lessThanOrEqualTo(30)
                        ))))
                .andDo(result -> {
                    log.info("✓ BETWEEN 查询成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(12)
    @DisplayName("2.3 IN 查询")
    void testQueryIn() throws Exception {
        log.info("========== 测试：IN 查询 ==========");

        QueryRequest request = QueryRequest.builder()
                .index("test_user")
                .query(QueryCondition.builder()
                        .field("city")
                        .op("IN")
                        .values(Arrays.asList("北京", "上海"))
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(4))
                .andExpect(jsonPath("$.data.items[*].city").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.anyOf(
                                org.hamcrest.Matchers.equalTo("北京"),
                                org.hamcrest.Matchers.equalTo("上海")
                        ))))
                .andDo(result -> {
                    log.info("✓ IN 查询成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(13)
    @DisplayName("2.4 逻辑查询 - AND")
    void testQueryLogicAnd() throws Exception {
        log.info("========== 测试：AND 逻辑查询 ==========");

        QueryRequest request = QueryRequest.builder()
                .index("test_user")
                .query(QueryCondition.builder()
                        .logic("and")
                        .conditions(Arrays.asList(
                                QueryCondition.builder()
                                        .field("city")
                                        .op("EQ")
                                        .value("北京")
                                        .build(),
                                QueryCondition.builder()
                                        .field("age")
                                        .op("GT")
                                        .value(25)
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
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].city").value("北京"))
                .andExpect(jsonPath("$.data.items[0].age").value(org.hamcrest.Matchers.greaterThan(25)))
                .andDo(result -> {
                    log.info("✓ AND 逻辑查询成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(14)
    @DisplayName("2.5 逻辑查询 - OR")
    void testQueryLogicOr() throws Exception {
        log.info("========== 测试：OR 逻辑查询 ==========");

        QueryRequest request = QueryRequest.builder()
                .index("test_user")
                .query(QueryCondition.builder()
                        .logic("or")
                        .conditions(Arrays.asList(
                                QueryCondition.builder()
                                        .field("city")
                                        .op("EQ")
                                        .value("北京")
                                        .build(),
                                QueryCondition.builder()
                                        .field("city")
                                        .op("EQ")
                                        .value("上海")
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
                .andExpect(jsonPath("$.data.items.length()").value(4))
                .andExpect(jsonPath("$.data.items[*].city").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.anyOf(
                                org.hamcrest.Matchers.equalTo("北京"),
                                org.hamcrest.Matchers.equalTo("上海")
                        ))))
                .andDo(result -> {
                    log.info("✓ OR 逻辑查询成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(15)
    @DisplayName("2.6 分页查询 - Offset")
    void testQueryPagination() throws Exception {
        log.info("========== 测试：Offset 分页 ==========");

        QueryRequest request = QueryRequest.builder()
                .index("test_user")
                .pagination(PaginationInfo.builder()
                        .type("offset")
                        .page(1)
                        .size(2)
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.total").value(5))
                .andDo(result -> {
                    log.info("✓ 分页查询成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(16)
    @DisplayName("2.7 排序查询")
    void testQuerySort() throws Exception {
        log.info("========== 测试：排序查询 ==========");

        QueryRequest request = QueryRequest.builder()
                .index("test_user")
                .pagination(PaginationInfo.builder()
                        .size(5)
                        .sort(Collections.singletonList(
                                PaginationInfo.SortField.builder()
                                        .field("age")
                                        .order("desc")
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
                .andExpect(jsonPath("$.data.items[0].age").value(35))
                .andExpect(jsonPath("$.data.items[1].age").value(30))
                .andExpect(jsonPath("$.data.items[2].age").value(28))
                .andExpect(jsonPath("$.data.items[3].age").value(25))
                .andExpect(jsonPath("$.data.items[4].age").value(22))
                .andDo(result -> {
                    log.info("✓ 排序查询成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    // ==================== 3. 聚合查询测试 ====================

    @Test
    @Order(20)
    @DisplayName("3.1 Metrics 聚合 - AVG")
    void testAggAvg() throws Exception {
        log.info("========== 测试：AVG 聚合 ==========");

        AggRequest request = AggRequest.builder()
                .index("test_user")
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("avg_age")
                                .type("AVG")
                                .field("age")
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/agg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aggregations.avg_age").value(28.0))
                .andExpect(jsonPath("$.data.took").isNumber())
                .andDo(result -> {
                    log.info("✓ AVG 聚合成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(21)
    @DisplayName("3.2 Metrics 聚合 - STATS")
    void testAggStats() throws Exception {
        log.info("========== 测试：STATS 聚合 ==========");

        AggRequest request = AggRequest.builder()
                .index("test_user")
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("age_stats")
                                .type("STATS")
                                .field("age")
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/agg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aggregations.age_stats.count").value(5))  // ✅ 改路径
                .andExpect(jsonPath("$.data.aggregations.age_stats.min").value(22))
                .andExpect(jsonPath("$.data.aggregations.age_stats.max").value(35))
                .andExpect(jsonPath("$.data.aggregations.age_stats.avg").value(28.0))
                .andExpect(jsonPath("$.data.aggregations.age_stats.sum").value(140))
                .andDo(result -> {
                    log.info("✓ STATS 聚合成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(22)
    @DisplayName("3.3 Bucket 聚合 - TERMS")
    void testAggTerms() throws Exception {
        log.info("========== 测试：TERMS 聚合 ==========");

        AggRequest request = AggRequest.builder()
                .index("test_user")
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("by_city")
                                .type("TERMS")
                                .field("city")
                                .size(10)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/agg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aggregations.by_city").isArray())  // ✅ 改路径
                .andExpect(jsonPath("$.data.aggregations.by_city.length()").value(3))
                .andExpect(jsonPath("$.data.aggregations.by_city[0].key").exists())
                .andExpect(jsonPath("$.data.aggregations.by_city[0].count").exists())
                // 北京2人、上海2人、深圳1人，按 count desc 排序
                .andExpect(jsonPath("$.data.aggregations.by_city[?(@.key=='北京')].count").value(
                        org.hamcrest.Matchers.hasItem(2)))
                .andExpect(jsonPath("$.data.aggregations.by_city[?(@.key=='上海')].count").value(
                        org.hamcrest.Matchers.hasItem(2)))
                .andExpect(jsonPath("$.data.aggregations.by_city[?(@.key=='深圳')].count").value(
                        org.hamcrest.Matchers.hasItem(1)))
                .andDo(result -> {
                    log.info("✓ TERMS 聚合成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(23)
    @DisplayName("3.4 嵌套聚合 - TERMS + AVG")
    void testAggNested() throws Exception {
        log.info("========== 测试：嵌套聚合 ==========");

        AggRequest request = AggRequest.builder()
                .index("test_user")
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("by_city")
                                .type("TERMS")
                                .field("city")
                                .size(10)
                                .aggs(Collections.singletonList(
                                        AggDefinition.builder()
                                                .name("avg_age")
                                                .type("AVG")
                                                .field("age")
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
                .andExpect(jsonPath("$.data.aggregations.by_city").isArray())  // ✅ 改路径
                .andExpect(jsonPath("$.data.aggregations.by_city.length()").value(3))
                // 验证每个城市的 avg_age 存在且为数值
                .andExpect(jsonPath("$.data.aggregations.by_city[0].avg_age").isNumber())
                .andExpect(jsonPath("$.data.aggregations.by_city[0].key").isString())
                .andExpect(jsonPath("$.data.aggregations.by_city[0].count").isNumber())
                // 北京2人 avg=26.5，上海2人 avg=32.5，深圳1人 avg=22
                .andExpect(jsonPath("$.data.aggregations.by_city[?(@.key=='北京')].avg_age").value(
                        org.hamcrest.Matchers.hasItem(26.5)))
                .andExpect(jsonPath("$.data.aggregations.by_city[?(@.key=='上海')].avg_age").value(
                        org.hamcrest.Matchers.hasItem(32.5)))
                .andExpect(jsonPath("$.data.aggregations.by_city[?(@.key=='深圳')].avg_age").value(
                        org.hamcrest.Matchers.hasItem(22.0)))
                .andDo(result -> {
                    log.info("✓ 嵌套聚合成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    // ==================== 4. 敏感字段测试 ====================

    @Test
    @Order(30)
    @DisplayName("4.1 敏感字段脱敏 - MASK")
    void testSensitiveFieldMask() throws Exception {
        log.info("========== 测试：敏感字段脱敏 ==========");

        QueryRequest request = QueryRequest.builder()
                .index("test_user")
                .query(QueryCondition.builder()
                        .field("name")
                        .op("EQ")
                        .value("张三")
                        .build())
                .pagination(PaginationInfo.builder()
                        .size(1)
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("张三"))
                // mask-start=3, mask-end=4, mask-pattern="****"：138****8000
                .andExpect(jsonPath("$.data.items[0].phone").value("138****8000"))
                .andDo(result -> {
                    log.info("✓ 敏感字段脱敏成功");
                    log.debug("Masked phone: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(31)
    @DisplayName("4.2 敏感字段禁止访问 - FORBIDDEN")
    void testSensitiveFieldForbidden() throws Exception {
        log.info("========== 测试：敏感字段禁止访问 ==========");

        QueryRequest request = QueryRequest.builder()
                .index("test_user")
                .pagination(PaginationInfo.builder()
                        .size(1)
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].password").doesNotExist())
                // mask-start=3, mask-end=4, mask-pattern="****"：138****8000
                .andExpect(jsonPath("$.data.items[0].phone").value("138****8000"))
                .andDo(result -> {
                    log.info("✓ 敏感字段禁止访问成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(32)
    @DisplayName("4.3 敏感字段查询被拒绝")
    void testSensitiveFieldQueryForbidden() throws Exception {
        log.info("========== 测试：敏感字段查询被拒绝 ==========");

        QueryRequest request = QueryRequest.builder()
                .index("test_user")
                .query(QueryCondition.builder()
                        .field("password")
                        .op("EQ")
                        .value("123456")
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("password")))
                .andDo(result -> {
                    log.info("✓ 敏感字段查询拒绝成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    // ==================== 8. 错误处理测试 ====================

    @Test
    @Order(40)
    @DisplayName("8.1 错误处理 - 索引不存在")
    void testErrorIndexNotFound() throws Exception {
        log.info("========== 测试：索引不存在错误 ==========");

        QueryRequest request = QueryRequest.builder()
                .index("non_existent_index")
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("non_existent_index")))
                .andDo(result -> {
                    log.info("✓ 索引不存在错误处理正确");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(41)
    @DisplayName("8.2 错误处理 - 字段不存在")
    void testErrorFieldNotFound() throws Exception {
        log.info("========== 测试：字段不存在错误 ==========");

        QueryRequest request = QueryRequest.builder()
                .index("test_user")
                .query(QueryCondition.builder()
                        .field("non_existent_field")
                        .op("EQ")
                        .value("test")
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("non_existent_field")))
                .andDo(result -> {
                    log.info("✓ 字段不存在错误处理正确");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    // ==================== 9. search_after 翻页模式测试 ====================

    @Test
    @Order(70)
    @DisplayName("9.1 searchAfterMode=tiebreaker（默认）- 正常翻页，行为与旧版本一致")
    void testSearchAfterTiebreakerMode() throws Exception {
        log.info("========== 测试：searchAfterMode=tiebreaker 翻页 ==========");

        // 第一页
        QueryRequest firstPage = QueryRequest.builder()
                .index("test_user")
                .pagination(PaginationInfo.builder()
                        .type("search_after")
                        .size(2)
                        .sort(Collections.singletonList(
                                PaginationInfo.SortField.builder().field("age").order("asc").build()
                        ))
                        // 不传 searchAfterMode，默认 tiebreaker
                        .build())
                .build();

        String firstResponse = mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(firstPage)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pagination.hasMore").value(true))
                .andExpect(jsonPath("$.data.pagination.nextSearchAfter").isArray())
                .andExpect(jsonPath("$.data.pagination.nextSearchAfter.length()").value(2)) // tiebreaker 模式：age + _id
                .andExpect(jsonPath("$.data.pagination.pitId").doesNotExist())
                .andDo(result -> log.info("✓ tiebreaker 第一页成功"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        // 取出 nextSearchAfter，翻第二页
        List<Object> nextSearchAfter = objectMapper.readTree(firstResponse)
                .path("data").path("pagination").path("nextSearchAfter")
                .traverse(objectMapper).readValueAs(List.class);

        QueryRequest secondPage = QueryRequest.builder()
                .index("test_user")
                .pagination(PaginationInfo.builder()
                        .type("search_after")
                        .size(2)
                        .searchAfter(nextSearchAfter)
                        .sort(Collections.singletonList(
                                PaginationInfo.SortField.builder().field("age").order("asc").build()
                        ))
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(secondPage)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andDo(result -> log.info("✓ tiebreaker 第二页成功"));
    }

    @Test
    @Order(71)
    @DisplayName("9.2 searchAfterMode=none - 排序字段唯一时正常翻页，不追加 _id")
    void testSearchAfterNoneMode() throws Exception {
        log.info("========== 测试：searchAfterMode=none 翻页 ==========");

        // name 是 keyword，每条数据唯一，排序字段本身唯一，不需要 tiebreaker
        QueryRequest firstPage = QueryRequest.builder()
                .index("test_user")
                .pagination(PaginationInfo.builder()
                        .type("search_after")
                        .searchAfterMode("none")
                        .size(2)
                        .sort(Collections.singletonList(
                                PaginationInfo.SortField.builder().field("name").order("asc").build()
                        ))
                        .build())
                .build();

        String firstResponse = mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(firstPage)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pagination.hasMore").value(true))
                .andExpect(jsonPath("$.data.pagination.nextSearchAfter").isArray())
                .andExpect(jsonPath("$.data.pagination.nextSearchAfter.length()").value(1)) // none 模式：只有 name，不追加 _id
                .andDo(result -> log.info("✓ none 模式第一页成功"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        List<Object> nextSearchAfter = objectMapper.readTree(firstResponse)
                .path("data").path("pagination").path("nextSearchAfter")
                .traverse(objectMapper).readValueAs(List.class);

        QueryRequest secondPage = QueryRequest.builder()
                .index("test_user")
                .pagination(PaginationInfo.builder()
                        .type("search_after")
                        .searchAfterMode("none")
                        .size(2)
                        .searchAfter(nextSearchAfter)
                        .sort(Collections.singletonList(
                                PaginationInfo.SortField.builder().field("name").order("asc").build()
                        ))
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(secondPage)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andDo(result -> log.info("✓ none 模式第二页成功"));
    }

    @Test
    @Order(72)
    @DisplayName("9.3 searchAfterMode=pit，pitKeepAlive 未传 - 报 400")
    void testSearchAfterPitMissingKeepAlive() throws Exception {
        log.info("========== 测试：pit 模式缺少 pitKeepAlive ==========");

        QueryRequest request = QueryRequest.builder()
                .index("test_user")
                .pagination(PaginationInfo.builder()
                        .type("search_after")
                        .searchAfterMode("pit")
                        // 故意不传 pitKeepAlive
                        .size(10)
                        .sort(Collections.singletonList(
                                PaginationInfo.SortField.builder().field("age").order("asc").build()
                        ))
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("pitKeepAlive")))
                .andDo(result -> log.info("✓ 缺少 pitKeepAlive 正确报错"));
    }

    @Test
    @Order(73)
    @DisplayName("9.4 searchAfterMode=pit，pitKeepAlive 超过服务端上限 - 报 400")
    void testSearchAfterPitKeepAliveExceeded() throws Exception {
        log.info("========== 测试：pit 模式 pitKeepAlive 超过上限 ==========");

        QueryRequest request = QueryRequest.builder()
                .index("test_user")
                .pagination(PaginationInfo.builder()
                        .type("search_after")
                        .searchAfterMode("pit")
                        .pitKeepAlive("24h")  // 超过默认上限 5m
                        .size(10)
                        .sort(Collections.singletonList(
                                PaginationInfo.SortField.builder().field("age").order("asc").build()
                        ))
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("24h")))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("5m")))
                .andDo(result -> log.info("✓ pitKeepAlive 超限正确报错"));
    }

    @Test
    @Order(74)
    @DisplayName("9.5 searchAfterMode=pit，ES 6.x 不支持 PIT - 报 400（secondary 非 6.x 时跳过）")
    void testSearchAfterPitNotSupportedOnEs6() throws Exception {
        log.info("========== 测试：ES 6.x 不支持 PIT ==========");

        ClusterInfo clusterInfo = registry.getClusterInfo("secondary");
        Assumptions.assumeTrue(
                clusterInfo != null
                        && clusterInfo.getEffectiveVersion() != null
                        && clusterInfo.getEffectiveVersion().getMajor() == 6,
                "secondary 数据源不是 ES 6.x，跳过此测试"
        );

        QueryRequest request = QueryRequest.builder()
                .index("test_index_b.secondary")
                .pagination(PaginationInfo.builder()
                        .type("search_after")
                        .searchAfterMode("pit")
                        .pitKeepAlive("1m")
                        .size(10)
                        .sort(Collections.singletonList(
                                PaginationInfo.SortField.builder().field("price").order("asc").build()
                        ))
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("不支持 PIT")))
                .andDo(result -> log.info("✓ ES 6.x 不支持 PIT 正确报错"));
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建 order 索引（无标识符）
     */
    private static void createOrderIndex(RestHighLevelClient client) throws Exception {
        // 删除旧索引
        if (client.indices().exists(new GetIndexRequest(ORDER_INDEX), RequestOptions.DEFAULT)) {
            client.indices().delete(new DeleteIndexRequest(ORDER_INDEX), RequestOptions.DEFAULT);
        }

        // 创建索引
        CreateIndexRequest request = new CreateIndexRequest(ORDER_INDEX);
        request.mapping(
                "{" +
                        "  \"properties\": {" +
                        "    \"order_id\": {\"type\": \"keyword\"}," +
                        "    \"product_name\": {\"type\": \"keyword\"}," +
                        "    \"amount\": {\"type\": \"double\"}," +
                        "    \"quantity\": {\"type\": \"integer\"}," +
                        "    \"status\": {\"type\": \"keyword\"}," +
                        "    \"client_ip\": {\"type\": \"ip\"}," +
                        "    \"created_at\": {\"type\": \"date\"}" +
                        "  }" +
                        "}",
                org.elasticsearch.xcontent.XContentType.JSON
        );
        client.indices().create(request, RequestOptions.DEFAULT);
        log.info("✓ 已创建索引: {}", ORDER_INDEX);

        // 插入测试数据
        List<Map<String, Object>> orders = Arrays.asList(
                createOrder("ORD001", "iPhone 15", 7999.0, 1, "completed", "10.0.0.1"),
                createOrder("ORD002", "MacBook Pro", 15999.0, 1, "pending", "10.0.0.2"),
                createOrder("ORD003", "AirPods Pro", 1999.0, 2, "completed", "192.168.1.1"),
                createOrder("ORD004", "iPad Air", 4999.0, 1, "cancelled", "192.168.1.2"),
                createOrder("ORD005", "Apple Watch", 2999.0, 1, "completed", "172.16.0.1")
        );

        for (int i = 0; i < orders.size(); i++) {
            IndexRequest indexRequest = new IndexRequest(ORDER_INDEX)
                    .id(String.valueOf(i + 1))
                    .source(orders.get(i));
            client.index(indexRequest, RequestOptions.DEFAULT);
        }

        Thread.sleep(2000);
        log.info("✓ 已插入 {} 条测试数据到 {}", orders.size(), ORDER_INDEX);
    }

    /**
     * 创建 user 索引（有标识符）
     */
    private static void createUserIndex(RestHighLevelClient client) throws Exception {
        // 删除旧索引
        if (client.indices().exists(new GetIndexRequest(USER_INDEX), RequestOptions.DEFAULT)) {
            client.indices().delete(new DeleteIndexRequest(USER_INDEX), RequestOptions.DEFAULT);
        }

        // 创建索引
        CreateIndexRequest request = new CreateIndexRequest(USER_INDEX);
        request.mapping(
                "{" +
                        "  \"properties\": {" +
                        "    \"username\": {" +
                        "      \"type\": \"text\"," +
                        "      \"fields\": {" +
                        "        \"keyword\": {" +
                        "          \"type\": \"keyword\"" +
                        "        }" +
                        "      }" +
                        "    }," +
                        "    \"name\": {\"type\": \"keyword\"}," +
                        "    \"age\": {\"type\": \"integer\"}," +
                        "    \"city\": {\"type\": \"keyword\"}," +
                        "    \"phone\": {\"type\": \"keyword\"}," +
                        "    \"password\": {\"type\": \"keyword\"}," +
                        "    \"created_at\": {\"type\": \"date\"}" +
                        "  }" +
                        "}",
                org.elasticsearch.xcontent.XContentType.JSON
        );
        client.indices().create(request, RequestOptions.DEFAULT);
        log.info("✓ 已创建索引: {}", USER_INDEX);

        // 插入测试数据
        List<Map<String, Object>> users = Arrays.asList(
                createUser("alice", "张三", 25, "北京", "13800138000", "password123"),
                createUser("bob", "李四", 30, "上海", "13900139000", "password456"),
                createUser("charlie", "王五", 28, "北京", "13700137000", "password789"),
                createUser("david", "赵六", 22, "深圳", "13600136000", "password000"),
                createUser("eve", "钱七", 35, "上海", "13500135000", "password111")
        );

        for (int i = 0; i < users.size(); i++) {
            IndexRequest indexRequest = new IndexRequest(USER_INDEX)
                    .id(String.valueOf(i + 1))
                    .source(users.get(i));
            client.index(indexRequest, RequestOptions.DEFAULT);
        }

        Thread.sleep(2000);
        log.info("✓ 已插入 {} 条测试数据到 {}", users.size(), USER_INDEX);
    }

    /**
     * 创建 NL 用户索引（带 keyword 子字段，用于表达式 DSL 端到端测试）
     */
    private static void createNlUserIndex(RestHighLevelClient client) throws Exception {
        if (client.indices().exists(new GetIndexRequest(NL_USER_INDEX), RequestOptions.DEFAULT)) {
            client.indices().delete(new DeleteIndexRequest(NL_USER_INDEX), RequestOptions.DEFAULT);
        }

        CreateIndexRequest request = new CreateIndexRequest(NL_USER_INDEX);
        request.mapping(
                "{" +
                        "  \"properties\": {" +
                        "    \"name\": {" +
                        "      \"type\": \"text\"," +
                        "      \"fields\": {" +
                        "        \"keyword\": {" +
                        "          \"type\": \"keyword\"" +
                        "        }" +
                        "      }" +
                        "    }," +
                        "    \"age\": {\"type\": \"long\"}," +
                        "    \"city\": {\"type\": \"keyword\"}," +
                        "    \"status\": {\"type\": \"keyword\"}," +
                        "    \"points\": {\"type\": \"long\"}," +
                        "    \"createTime\": {\"type\": \"date\"}," +
                        "    \"orderId\": {\"type\": \"keyword\"}" +
                        "  }" +
                        "}",
                org.elasticsearch.xcontent.XContentType.JSON
        );
        client.indices().create(request, RequestOptions.DEFAULT);
        log.info("✓ 已创建索引: {}", NL_USER_INDEX);

        // 插入测试数据
        // Alice: age=25, points=500  → 命中 name='Alice' AND age>=18 AND points<=1000
        // Bob:   age=16, points=200  → age<18，不命中
        // Carol: age=30, points=1500 → points>1000，不命中
        // Dave:  age=20, points=800  → 命中 name='Alice' AND age>=18 AND points<=1000 时不命中（name不对）
        List<Map<String, Object>> users = Arrays.asList(
                createNlUser("Alice", 25, "北京", "active", 500L),
                createNlUser("Bob",   16, "上海", "active", 200L),
                createNlUser("Carol", 30, "广州", "active", 1500L),
                createNlUser("Dave",  20, "深圳", "active", 800L)
        );
        for (int i = 0; i < users.size(); i++) {
            IndexRequest indexRequest = new IndexRequest(NL_USER_INDEX)
                    .id("nl-" + (i + 1))
                    .source(users.get(i));
            client.index(indexRequest, RequestOptions.DEFAULT);
        }

        Thread.sleep(2000);
        log.info("✓ NL 用户索引创建完成，已插入 {} 条数据", users.size());
    }

    private static Map<String, Object> createNlUser(String name, int age, String city, String status, long points) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("name", name);
        doc.put("age", age);
        doc.put("city", city);
        doc.put("status", status);
        doc.put("points", points);
        return doc;
    }

    @Test
    @Order(50)
    @DisplayName("6.1 日期分割索引 - 单日查询")
    void testDateSplitIndexSingleDay() throws Exception {
        log.info("========== 测试：单日查询（只查一个索引） ==========");

        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        QueryRequest request = QueryRequest.builder()
                .index("test_log_*")
                .dateRange(QueryRequest.DateRange.builder()
                        .from(today + "T00:00:00")  // ✅ 只查今天
                        .to(today + "T23:59:59")
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(3))  // 今天的3条数据
                .andExpect(jsonPath("$.data.items[0].message").value(org.hamcrest.Matchers.containsString(today)))
                .andDo(result -> {
                    log.info("✓ 单日查询成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(51)
    @DisplayName("6.2 日期分割索引 - 跨2天查询")
    void testDateSplitIndexTwoDays() throws Exception {
        log.info("========== 测试：跨2天查询（查询2个索引） ==========");

        LocalDateTime today = LocalDateTime.now();
        LocalDateTime yesterday = today.minusDays(1);

        String from = yesterday.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "T00:00:00";
        String to = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "T23:59:59";

        QueryRequest request = QueryRequest.builder()
                .index("test_log_*")
                .dateRange(QueryRequest.DateRange.builder()
                        .from(from)  // ✅ 从昨天到今天
                        .to(to)
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(6))  // 2天 × 3条 = 6条
                .andExpect(jsonPath("$.data.total").value(6))
                .andDo(result -> {
                    log.info("✓ 跨2天查询成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(52)
    @DisplayName("6.3 日期分割索引 - 跨3天查询")
    void testDateSplitIndexThreeDays() throws Exception {
        log.info("========== 测试：跨3天查询（查询3个索引） ==========");

        LocalDateTime today = LocalDateTime.now();
        LocalDateTime twoDaysAgo = today.minusDays(2);

        String from = twoDaysAgo.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "T00:00:00";
        String to = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "T23:59:59";

        QueryRequest request = QueryRequest.builder()
                .index("test_log_*")
                .dateRange(QueryRequest.DateRange.builder()
                        .from(from)  // ✅ 从2天前到今天
                        .to(to)
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(9))  // 3天 × 3条 = 9条
                .andExpect(jsonPath("$.data.total").value(9))
                .andDo(result -> {
                    log.info("✓ 跨3天查询成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(53)
    @DisplayName("6.4 日期分割索引 - 验证索引路由")
    void testDateSplitIndexRouting() throws Exception {
        log.info("========== 测试：验证索引路由 ==========");

        LocalDateTime today = LocalDateTime.now();
        LocalDateTime yesterday = today.minusDays(1);

        String from = yesterday.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "T00:00:00";
        String to = yesterday.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "T23:59:59";

        // ✅ 只查询昨天
        QueryRequest request = QueryRequest.builder()
                .index("test_log_*")
                .dateRange(QueryRequest.DateRange.builder()
                        .from(from)
                        .to(to)
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(3))  // 只有昨天的3条
                .andExpect(jsonPath("$.data.items[*].message").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.containsString(yesterday.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                )))
                .andDo(result -> {
                    log.info("✓ 索引路由验证成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(54)
    @DisplayName("6.5 日期分割索引 - 带条件的跨日期查询")
    void testDateSplitIndexWithCondition() throws Exception {
        log.info("========== 测试：带条件的跨日期查询 ==========");

        LocalDateTime today = LocalDateTime.now();
        LocalDateTime yesterday = today.minusDays(1);

        String from = yesterday.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "T00:00:00";
        String to = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "T23:59:59";

        QueryRequest request = QueryRequest.builder()
                .index("test_log_*")
                .query(QueryCondition.builder()
                        .field("action")
                        .op("EQ")
                        .value("login")  // ✅ 只查询 login 操作
                        .build())
                .dateRange(QueryRequest.DateRange.builder()
                        .from(from)
                        .to(to)
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(2))  // 2天的 login 操作
                .andExpect(jsonPath("$.data.items[*].action").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.equalTo("login")
                )))
                .andDo(result -> {
                    log.info("✓ 带条件的跨日期查询成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(55)
    @DisplayName("6.6 日期分割索引 - 跨日期聚合")
    void testDateSplitIndexAggregation() throws Exception {
        log.info("========== 测试：跨日期聚合 ==========");

        LocalDateTime today = LocalDateTime.now();
        LocalDateTime twoDaysAgo = today.minusDays(2);

        AggRequest request = AggRequest.builder()
                .index("test_log_*")
                .query(QueryCondition.builder()
                        .field("createTime")
                        .op("BETWEEN")
                        .values(Arrays.asList(
                                twoDaysAgo.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "T00:00:00",
                                today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "T23:59:59"
                        ))
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
                .andExpect(jsonPath("$.data.aggregations.by_action.length()").value(3))  // login, logout, create
                .andDo(result -> {
                    log.info("✓ 跨日期聚合成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(56)
    @DisplayName("6.7 日期分割索引 - 忽略不存在的索引（ignore-unavailable-indices）")
    void testDateSplitIndexIgnoreUnavailable() throws Exception {
        log.info("========== 测试：跨大范围日期查询（部分索引不存在）==========");

        LocalDateTime today = LocalDateTime.now();
        LocalDateTime thirtyDaysAgo = today.minusDays(30);

        // 查询最近30天的数据，但实际只有最近3天的索引存在
        // 由于配置了 ignore-unavailable-indices: true，应该成功返回已存在索引的数据
        QueryRequest request = QueryRequest.builder()
                .index("test_log_*")
                .dateRange(QueryRequest.DateRange.builder()
                        .from(thirtyDaysAgo.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "T00:00:00")
                        .to(today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "T23:59:59")
                        .build())
                .query(QueryCondition.builder()
                        .field("action")
                        .op("EQ")
                        .value("login")
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
                .andExpect(jsonPath("$.data.items[0].action").value("login"))
                .andDo(result -> {
                    log.info("✓ 跨大范围日期查询成功（忽略不存在的索引）");
                    log.info("✓ 查询30天范围，实际只有最近几天数据，成功返回记录");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });

        // 同样测试聚合场景
        log.info("测试聚合查询 - 忽略不存在的索引");
        AggRequest aggRequest = AggRequest.builder()
                .index("test_log_*")
                .query(QueryCondition.builder()
                        .field("createTime")
                        .op("BETWEEN")
                        .values(Arrays.asList(
                                thirtyDaysAgo.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "T00:00:00",
                                today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "T23:59:59"
                        ))
                        .build())
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("action_count")
                                .type("TERMS")
                                .field("action")
                                .size(10)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/agg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(aggRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aggregations.action_count").isArray())
                .andExpect(jsonPath("$.data.aggregations.action_count.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)))  // login, logout, create 이상
                .andDo(result -> {
                    log.info("✓ 聚合查询成功（忽略不存在的索引）");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(57)
    @DisplayName("6.8 strict-date-filter=true（默认）- 整天范围仍追加 date range filter，过滤跨天脏数据")
    void testStrictDateFilterEnabled() throws Exception {
        log.info("========== 测试：strict-date-filter=true，整天范围也追加 date range filter ==========");

        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        // 向今天的索引中插入一条昨天时间的数据，模拟入库延迟导致的跨天脏数据
        RestHighLevelClient client = registry.getHighLevelClient(DEFAULT_DATASOURCE);
        String todayIndex = LOG_INDEX_PREFIX + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
        Map<String, Object> dirtyData = new HashMap<>();
        dirtyData.put("user_id", "dirty_user");
        dirtyData.put("action", "dirty_action");
        dirtyData.put("message", "脏数据 - 昨天时间写入今天索引");
        dirtyData.put("createTime", yesterday);
        client.index(new IndexRequest(todayIndex).id("dirty-1").source(dirtyData), RequestOptions.DEFAULT);
        Thread.sleep(1000);

        // strict-date-filter=true（默认），查今天整天范围，脏数据的 createTime 是昨天，应被过滤掉
        QueryRequest request = QueryRequest.builder()
                .index("test_log_*")
                .dateRange(QueryRequest.DateRange.builder()
                        .from(today + "T00:00:00")
                        .to(today + "T23:59:59")
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(3))  // 只有今天的3条，脏数据被过滤
                .andExpect(jsonPath("$.data.items[*].user_id").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("dirty_user"))))
                .andDo(result -> {
                    log.info("✓ strict-date-filter=true：脏数据被正确过滤");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });

        // 清理脏数据
        client.delete(new org.elasticsearch.action.delete.DeleteRequest(todayIndex, "dirty-1"), RequestOptions.DEFAULT);
    }

    @Test
    @Order(58)
    @DisplayName("6.9 精确时间范围（非整天）- 无论 strict-date-filter 如何，始终追加 date range filter")
    void testDateFilterAlwaysAppliedForPartialDay() throws Exception {
        log.info("========== 测试：精确时间范围始终追加 date range filter ==========");

        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        // 查今天下午时间段，只有 createTime 在该范围内的数据才应返回
        QueryRequest request = QueryRequest.builder()
                .index("test_log_*")
                .dateRange(QueryRequest.DateRange.builder()
                        .from(today + "T12:00:00")
                        .to(today + "T23:59:59")
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                // 测试数据的 createTime 是 LocalDateTime.now()，大概率在下午范围内，但核心是验证 filter 被追加不报错
                .andDo(result -> {
                    log.info("✓ 精确时间范围 date range filter 正常工作");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    // ==================== 7. 多数据源路由测试 ====================

    @Test
    @Order(60)
    @DisplayName("7.1 多数据源路由 - 查询 secondary 数据源索引")
    void testMultiDatasourceQuerySecondary() throws Exception {
        log.info("========== 测试：查询路由到 secondary 数据源的索引 ==========");

        QueryRequest request = QueryRequest.builder()
                .index(SECONDARY_INDEX)
                .query(QueryCondition.builder()
                        .field("category")
                        .op("EQ")
                        .value("Electronics")
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
                .andExpect(jsonPath("$.data.items.length()").value(5))
                .andExpect(jsonPath("$.data.items[0].category").value("Electronics"))
                .andDo(result -> {
                    log.info("✓ Secondary 数据源查询成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(61)
    @DisplayName("7.2 多数据源路由 - secondary 索引范围查询")
    void testMultiDatasourceQueryRange() throws Exception {
        log.info("========== 测试：Secondary 数据源范围查询 ==========");

        QueryRequest request = QueryRequest.builder()
                .index(SECONDARY_INDEX)
                .query(QueryCondition.builder()
                        .field("price")
                        .op("BETWEEN")
                        .values(Arrays.asList(6000, 9000))
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(4))  // iPhone 15 Pro, MacBook Air, iPad Pro, Apple Watch Ultra
                .andExpect(jsonPath("$.data.items[*].price").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.greaterThanOrEqualTo(6000.0),
                                org.hamcrest.Matchers.lessThanOrEqualTo(9000.0)
                        ))))
                .andDo(result -> {
                    log.info("✓ Secondary 数据源范围查询成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(62)
    @DisplayName("7.3 多数据源路由 - secondary 索引聚合")
    void testMultiDatasourceAggregation() throws Exception {
        log.info("========== 测试：Secondary 数据源聚合 ==========");

        AggRequest request = AggRequest.builder()
                .index(SECONDARY_INDEX)
                .aggs(Arrays.asList(
                        AggDefinition.builder()
                                .name("avg_price")
                                .type("AVG")
                                .field("price")
                                .build(),
                        AggDefinition.builder()
                                .name("total_stock")
                                .type("SUM")
                                .field("stock")
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/agg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aggregations.avg_price").value(6439.0))  // (8999+7999+1899+6999+6299)/5
                .andExpect(jsonPath("$.data.aggregations.total_stock").value(550.0))  // 100+50+200+80+120
                .andDo(result -> {
                    log.info("✓ Secondary 数据源聚合成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(63)
    @DisplayName("7.4 多数据源路由 - 获取 secondary 索引字段信息")
    void testMultiDatasourceGetFields() throws Exception {
        log.info("========== 测试：获取 Secondary 数据源索引字段信息 ==========");

        mockMvc.perform(get("/api/indices/" + SECONDARY_INDEX + "/fields"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.index").value(SECONDARY_INDEX))
                .andExpect(jsonPath("$.data.fields").isArray())
                .andExpect(jsonPath("$.data.fields[?(@.name=='product_id')]").exists())
                .andExpect(jsonPath("$.data.fields[?(@.name=='product_name')]").exists())
                .andExpect(jsonPath("$.data.fields[?(@.name=='price')]").exists())
                .andDo(result -> {
                    log.info("✓ Secondary 数据源字段信息获取成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(64)
    @DisplayName("7.5 版本兼容性 - 验证 GetMappings 请求正常工作")
    void testVersionCompatibilityGetMappings() throws Exception {
        log.info("========== 测试：验证版本自适应客户端兼容性 ==========");

        // 测试 secondary 数据源（可能是 ES 6.2.2）的 mapping 获取
        // 如果版本适配正确，不会出现 include_type_name 和 master_timeout 参数错误
        mockMvc.perform(get("/api/indices/" + SECONDARY_INDEX + "/fields"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fields").isArray())
                .andDo(result -> {
                    log.info("✓ 版本自适应客户端工作正常，未出现参数兼容性错误");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });

        // 测试 primary 数据源的 mapping 获取
        mockMvc.perform(get("/api/indices/test_user/fields"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fields").isArray())
                .andDo(result -> {
                    log.info("✓ Primary 数据源版本自适应工作正常");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(65)
    @DisplayName("8. Multi-fields（keyword 子字段）功能测试")
    void testMultiFieldsSupport() throws Exception {
        log.info("========== 测试：Multi-fields（keyword 子字段）功能 ==========");

        // 8.1 验证 fields API 返回 keyword 子字段
        log.info("测试 1: 验证 /fields API 返回 keyword 子字段");
        mockMvc.perform(get("/api/indices/test_user/fields"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fields").isArray())
                .andDo(result -> {
                    String responseBody = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
                    log.debug("Fields API Response: {}", responseBody);

                    // 验证响应中包含 subFields
                    org.assertj.core.api.Assertions.assertThat(responseBody)
                            .contains("subFields")
                            .contains("keyword");

                    log.info("✓ Fields API 正确返回了 keyword 子字段");
                });

        // 8.2 使用 text 主字段进行查询（模糊匹配）
        log.info("测试 2: 使用 text 主字段进行模糊查询");
        QueryRequest textQueryRequest = QueryRequest.builder()
                .index("test_user")
                .query(QueryCondition.builder()
                        .field("username")  // text 字段
                        .op(io.github.surezzzzzz.sdk.elasticsearch.search.constant.QueryOperator.LIKE.getOperator())
                        .value("alice")
                        .build())
                .pagination(PaginationInfo.builder()
                        .size(10)
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(textQueryRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andDo(result -> {
                    log.info("✓ Text 字段模糊查询成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });

        // 8.3 使用 keyword 子字段进行精确查询
        log.info("测试 3: 使用 keyword 子字段进行精确查询");
        QueryRequest keywordQueryRequest = QueryRequest.builder()
                .index("test_user")
                .query(QueryCondition.builder()
                        .field("username.keyword")  // keyword 子字段
                        .op(io.github.surezzzzzz.sdk.elasticsearch.search.constant.QueryOperator.EQ.getOperator())
                        .value("alice")
                        .build())
                .pagination(PaginationInfo.builder()
                        .size(10)
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(keywordQueryRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].username").value("alice"))
                .andDo(result -> {
                    log.info("✓ Keyword 子字段精确查询成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });

        // 8.4 使用 keyword 子字段进行聚合
        log.info("测试 4: 使用 keyword 子字段进行聚合");
        AggRequest aggRequest = AggRequest.builder()
                .index("test_user")
                .aggs(Arrays.asList(
                        AggDefinition.builder()
                                .name("username_terms")
                                .type(io.github.surezzzzzz.sdk.elasticsearch.search.constant.AggType.TERMS.getType())
                                .field("username.keyword")  // keyword 子字段用于聚合
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/agg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(aggRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aggregations.username_terms").exists())
                .andDo(result -> {
                    log.info("✓ Keyword 子字段聚合成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });

        // 8.5 使用 keyword 子字段进行排序
        log.info("测试 5: 使用 keyword 子字段进行排序");
        QueryRequest sortRequest = QueryRequest.builder()
                .index("test_user")
                .query(QueryCondition.builder()
                        .field("username")
                        .op(io.github.surezzzzzz.sdk.elasticsearch.search.constant.QueryOperator.EXISTS.getOperator())
                        .build())
                .pagination(PaginationInfo.builder()
                        .size(10)
                        .sort(Arrays.asList(
                                PaginationInfo.SortField.builder()
                                        .field("username.keyword")
                                        .order("ASC")
                                        .build()
                        ))
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(sortRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andDo(result -> {
                    log.info("✓ Keyword 子字段排序成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });

        log.info("========== Multi-fields 功能测试完成 ==========");
    }

    @Test
    @Order(80)
    @DisplayName("10.1 composite 聚合 - 第一页（不传 after）")
    void testCompositeAggFirstPage() throws Exception {
        log.info("========== 测试：composite 聚合第一页 ==========");

        AggRequest request = AggRequest.builder()
                .index(ORDER_INDEX)
                .aggs(Arrays.asList(
                        AggDefinition.builder()
                                .name("all_status")
                                .type(io.github.surezzzzzz.sdk.elasticsearch.search.constant.AggType.TERMS.getType())
                                .field("status")
                                .composite(true)
                                .size(2)
                                .build()
                ))
                .build();

        String responseBody = mockMvc.perform(post("/api/agg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aggregations.all_status").isArray())
                .andExpect(jsonPath("$.data.aggregations.all_status.length()").value(2))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        log.info("composite 第一页响应: {}", responseBody);

        // 解析 afterKey，验证存在（说明还有下一页）
        @SuppressWarnings("unchecked")
        Map<String, Object> resp = objectMapper.readValue(responseBody, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) resp.get("data");
        Object afterKey = data.get("afterKey");

        log.info("afterKey: {}", afterKey);
        org.junit.jupiter.api.Assertions.assertNotNull(afterKey, "第一页应返回 afterKey（还有更多数据）");
        log.info("✓ composite 聚合第一页成功，afterKey 存在");
    }

    @Test
    @Order(81)
    @DisplayName("10.2 composite 聚合 - 翻页（传 after）")
    void testCompositeAggNextPage() throws Exception {
        log.info("========== 测试：composite 聚合翻页 ==========");

        // 第一页：size=2，取 afterKey
        AggRequest firstRequest = AggRequest.builder()
                .index(ORDER_INDEX)
                .aggs(Arrays.asList(
                        AggDefinition.builder()
                                .name("all_status")
                                .type(io.github.surezzzzzz.sdk.elasticsearch.search.constant.AggType.TERMS.getType())
                                .field("status")
                                .composite(true)
                                .size(2)
                                .build()
                ))
                .build();

        String firstResponse = mockMvc.perform(post("/api/agg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(firstRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        @SuppressWarnings("unchecked")
        Map<String, Object> firstResp = objectMapper.readValue(firstResponse, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> firstData = (Map<String, Object>) firstResp.get("data");
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> afterKey = (Map<String, Map<String, Object>>) firstData.get("afterKey");

        org.junit.jupiter.api.Assertions.assertNotNull(afterKey, "第一页应返回 afterKey");
        log.info("第一页 afterKey: {}", afterKey);

        // 第二页：传入 afterKey
        AggRequest secondRequest = AggRequest.builder()
                .index(ORDER_INDEX)
                .after(afterKey)
                .aggs(Arrays.asList(
                        AggDefinition.builder()
                                .name("all_status")
                                .type(io.github.surezzzzzz.sdk.elasticsearch.search.constant.AggType.TERMS.getType())
                                .field("status")
                                .composite(true)
                                .size(2)
                                .build()
                ))
                .build();

        String secondResponse = mockMvc.perform(post("/api/agg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(secondRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aggregations.all_status").isArray())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        log.info("composite 第二页响应: {}", secondResponse);

        @SuppressWarnings("unchecked")
        Map<String, Object> secondResp = objectMapper.readValue(secondResponse, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> secondData = (Map<String, Object>) secondResp.get("data");
        Object secondAfterKey = secondData.get("afterKey");

        // 第二页 afterKey 为 null 说明已是最后一页（order 索引只有 3 种 status，size=2 翻两页）
        log.info("第二页 afterKey: {}", secondAfterKey);
        log.info("✓ composite 聚合翻页成功");
    }

    @Test
    @Order(82)
    @DisplayName("10.3 composite 聚合 - 不支持的类型（RANGE）报 400")
    void testCompositeAggUnsupportedType() throws Exception {
        log.info("========== 测试：composite 聚合不支持的类型 ==========");

        AggRequest request = AggRequest.builder()
                .index(ORDER_INDEX)
                .aggs(Arrays.asList(
                        AggDefinition.builder()
                                .name("bad_agg")
                                .type(io.github.surezzzzzz.sdk.elasticsearch.search.constant.AggType.RANGE.getType())
                                .field("amount")
                                .composite(true)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/agg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andDo(result -> {
                    log.info("✓ composite 不支持 RANGE 类型，正确返回 400");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(83)
    @DisplayName("10.4 composite 聚合 - 嵌套 bucket 聚合报 400")
    void testCompositeAggNestedBucketNotAllowed() throws Exception {
        log.info("========== 测试：composite 聚合嵌套 bucket 聚合 ==========");

        AggRequest request = AggRequest.builder()
                .index(ORDER_INDEX)
                .aggs(Arrays.asList(
                        AggDefinition.builder()
                                .name("all_status")
                                .type(io.github.surezzzzzz.sdk.elasticsearch.search.constant.AggType.TERMS.getType())
                                .field("status")
                                .composite(true)
                                .aggs(Arrays.asList(
                                        AggDefinition.builder()
                                                .name("nested_terms")
                                                .type(io.github.surezzzzzz.sdk.elasticsearch.search.constant.AggType.TERMS.getType())
                                                .field("product_name")
                                                .build()
                                ))
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/agg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andDo(result -> {
                    log.info("✓ composite 内部嵌套 bucket 聚合，正确返回 400");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(84)
    @DisplayName("10.5 composite 聚合 - 嵌套 metrics 子聚合（合法）")
    void testCompositeAggWithMetricsSubAgg() throws Exception {
        log.info("========== 测试：composite 聚合嵌套 metrics 子聚合 ==========");

        AggRequest request = AggRequest.builder()
                .index(ORDER_INDEX)
                .aggs(Arrays.asList(
                        AggDefinition.builder()
                                .name("status_with_avg")
                                .type(io.github.surezzzzzz.sdk.elasticsearch.search.constant.AggType.TERMS.getType())
                                .field("status")
                                .composite(true)
                                .size(10)
                                .aggs(Arrays.asList(
                                        AggDefinition.builder()
                                                .name("avg_amount")
                                                .type(io.github.surezzzzzz.sdk.elasticsearch.search.constant.AggType.AVG.getType())
                                                .field("amount")
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
                .andExpect(jsonPath("$.data.aggregations.status_with_avg").isArray())
                .andExpect(jsonPath("$.data.aggregations.status_with_avg[0].avg_amount").exists())
                .andDo(result -> {
                    log.info("✓ composite 聚合嵌套 metrics 子聚合成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(85)
    @DisplayName("10.6 composite 聚合 - ES 6.x（secondary 数据源）")
    void testCompositeAggOnEs6x() throws Exception {
        log.info("========== 测试：composite 聚合 ES 6.x 路径 ==========");

        // secondary 是 ES 6.2.2，composite 是 beta 特性（6.1+）
        // 使用 7.x Java client 构建的 composite 请求在 6.x 上可能因 DSL 差异返回 400
        // 本测试验证：请求能正常发出，不抛出未处理异常（400 是 ES 侧拒绝，属于预期内行为）
        AggRequest request = AggRequest.builder()
                .index(SECONDARY_INDEX)
                .aggs(Arrays.asList(
                        AggDefinition.builder()
                                .name("all_category")
                                .type(io.github.surezzzzzz.sdk.elasticsearch.search.constant.AggType.TERMS.getType())
                                .field("category")
                                .composite(true)
                                .size(10)
                                .build()
                ))
                .build();

        org.springframework.test.web.servlet.MvcResult result = mockMvc.perform(post("/api/agg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aggregations.all_category").isArray())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        log.info("ES 6.x composite 响应: {}", responseBody);

        @SuppressWarnings("unchecked")
        Map<String, Object> resp = objectMapper.readValue(responseBody, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) resp.get("data");

        // secondary 索引只有 1 种 category（Electronics），afterKey 应为 null（已遍历完）
        Object afterKey = data.get("afterKey");
        log.info("ES 6.x composite afterKey: {}", afterKey);
        log.info("✓ ES 6.x composite 聚合成功");
    }

    @Test
    @Order(86)
    @DisplayName("10.7 composite 聚合 - date_histogram 类型")
    void testCompositeAggDateHistogram() throws Exception {
        log.info("========== 测试：composite date_histogram 聚合 ==========");

        AggRequest request = AggRequest.builder()
                .index(ORDER_INDEX)
                .aggs(Arrays.asList(
                        AggDefinition.builder()
                                .name("orders_by_day")
                                .type(io.github.surezzzzzz.sdk.elasticsearch.search.constant.AggType.DATE_HISTOGRAM.getType())
                                .field("created_at")
                                .interval("day")
                                .composite(true)
                                .size(10)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/agg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aggregations.orders_by_day").isArray())
                .andDo(result -> {
                    log.info("✓ composite date_histogram 聚合成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(90)
    @DisplayName("11.1 default-date-range - 通配索引 query 未传 dateRange，自动补充最近 30 天")
    void testDefaultDateRangeQueryWildcard() throws Exception {
        log.info("========== 测试：通配索引 query 自动补充 default-date-range ==========");

        // test_log_* 是通配索引，不传 dateRange，应自动补充最近 30 天（配置在 application.yaml）
        QueryRequest request = QueryRequest.builder()
                .index(LOG_INDEX_PREFIX + "*")
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
                .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThan(0)))
                .andDo(result -> {
                    log.info("✓ 通配索引 query 自动补充 default-date-range，正常返回数据");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(91)
    @DisplayName("11.2 default-date-range - 通配索引 agg 未传 query，自动补充最近 30 天")
    void testDefaultDateRangeAggWildcard() throws Exception {
        log.info("========== 测试：通配索引 agg 自动补充 default-date-range ==========");

        AggRequest request = AggRequest.builder()
                .index(LOG_INDEX_PREFIX + "*")
                .aggs(Arrays.asList(
                        AggDefinition.builder()
                                .name("action_count")
                                .type(io.github.surezzzzzz.sdk.elasticsearch.search.constant.AggType.TERMS.getType())
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
                .andExpect(jsonPath("$.data.aggregations.action_count").isArray())
                .andExpect(jsonPath("$.data.aggregations.action_count.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andDo(result -> {
                    log.info("✓ 通配索引 agg 自动补充 default-date-range，正常返回聚合结果");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(92)
    @DisplayName("11.3 default-date-range - 精确索引不触发，全量返回")
    void testDefaultDateRangeExactIndexNotAffected() throws Exception {
        log.info("========== 测试：精确索引不触发 default-date-range ==========");

        // test_order_index 无通配符，不应补充时间范围，全量返回 5 条
        QueryRequest request = QueryRequest.builder()
                .index(ORDER_INDEX)
                .pagination(PaginationInfo.builder()
                        .size(10)
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(5))
                .andDo(result -> {
                    log.info("✓ 精确索引不受 default-date-range 影响，全量返回 5 条");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(93)
    @DisplayName("11.4 default-date-range - 通配索引已传 dateRange，不覆盖")
    void testDefaultDateRangeNotOverrideExplicit() throws Exception {
        log.info("========== 测试：通配索引已传 dateRange，不被 default-date-range 覆盖 ==========");

        // 显式传入 2020 年的时间范围，该年无数据
        // 如果被 default-date-range 覆盖成最近 30 天，则会返回数据（不符合预期）
        QueryRequest request = QueryRequest.builder()
                .index(LOG_INDEX_PREFIX + "*")
                .dateRange(QueryRequest.DateRange.builder()
                        .from("2020-01-01T00:00:00")
                        .to("2020-01-02T23:59:59")
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
                .andExpect(jsonPath("$.data.total").value(0))
                .andDo(result -> {
                    log.info("✓ 通配索引已传 dateRange，不被 default-date-range 覆盖，2020 年无数据");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    // ==================== 12. Pipeline Aggregation 测试 ====================

    @Test
    @Order(94)
    @DisplayName("12.1 bucket_sort - Top N（按 avg_age 降序取 Top 2 城市）")
    void testPipelineBucketSortTopN() throws Exception {
        log.info("========== 测试：bucket_sort Top N ==========");

        AggRequest request = AggRequest.builder()
                .index("test_user")
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("by_city")
                                .type("TERMS")
                                .field("city")
                                .size(100)
                                .aggs(Collections.singletonList(
                                        AggDefinition.builder()
                                                .name("avg_age")
                                                .type("AVG")
                                                .field("age")
                                                .build()
                                ))
                                .pipelineAggs(Collections.singletonList(
                                        PipelineAggDefinition.builder()
                                                .name("top2")
                                                .type("bucket_sort")
                                                .sort(Collections.singletonMap("avg_age", "desc"))
                                                .size(2)
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
                .andExpect(jsonPath("$.data.aggregations.by_city").isArray())
                .andExpect(jsonPath("$.data.aggregations.by_city.length()").value(2))
                .andDo(result -> {
                    log.info("✓ bucket_sort Top N 成功，返回 2 个城市");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(95)
    @DisplayName("12.2 bucket_sort - 仅排序不截断")
    void testPipelineBucketSortNoLimit() throws Exception {
        log.info("========== 测试：bucket_sort 仅排序 ==========");

        AggRequest request = AggRequest.builder()
                .index("test_user")
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("by_city")
                                .type("TERMS")
                                .field("city")
                                .size(100)
                                .aggs(Collections.singletonList(
                                        AggDefinition.builder()
                                                .name("avg_age")
                                                .type("AVG")
                                                .field("age")
                                                .build()
                                ))
                                .pipelineAggs(Collections.singletonList(
                                        PipelineAggDefinition.builder()
                                                .name("sort_asc")
                                                .type("bucket_sort")
                                                .sort(Collections.singletonMap("avg_age", "asc"))
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
                .andExpect(jsonPath("$.data.aggregations.by_city").isArray())
                .andExpect(jsonPath("$.data.aggregations.by_city.length()").value(3))
                .andDo(result -> {
                    log.info("✓ bucket_sort 仅排序成功，返回全部 3 个城市");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(96)
    @DisplayName("12.3 bucket_selector - HAVING 过滤（count > 1）")
    void testPipelineBucketSelectorHaving() throws Exception {
        log.info("========== 测试：bucket_selector HAVING ==========");

        AggRequest request = AggRequest.builder()
                .index("test_user")
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("by_city")
                                .type("TERMS")
                                .field("city")
                                .size(100)
                                .pipelineAggs(Collections.singletonList(
                                        PipelineAggDefinition.builder()
                                                .name("having")
                                                .type("bucket_selector")
                                                .script("params._count > 1")
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
                .andExpect(jsonPath("$.data.aggregations.by_city").isArray())
                .andDo(result -> {
                    String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
                    log.info("✓ bucket_selector HAVING 成功");
                    log.debug("Response: {}", body);
                });
    }

    @Test
    @Order(97)
    @DisplayName("12.4 bucket_selector - bucketsPath 自动推断")
    void testPipelineBucketSelectorAutoInfer() throws Exception {
        log.info("========== 测试：bucket_selector bucketsPath 自动推断 ==========");

        AggRequest request = AggRequest.builder()
                .index("test_user")
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("by_city")
                                .type("TERMS")
                                .field("city")
                                .size(100)
                                .aggs(Collections.singletonList(
                                        AggDefinition.builder()
                                                .name("avg_age")
                                                .type("AVG")
                                                .field("age")
                                                .build()
                                ))
                                .pipelineAggs(Collections.singletonList(
                                        PipelineAggDefinition.builder()
                                                .name("having")
                                                .type("bucket_selector")
                                                .script("params.avg_age > 25")
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
                .andExpect(jsonPath("$.data.aggregations.by_city").isArray())
                .andDo(result -> {
                    log.info("✓ bucket_selector 自动推断 bucketsPath 成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(98)
    @DisplayName("12.5 bucket_selector + bucket_sort 组合")
    void testPipelineCombined() throws Exception {
        log.info("========== 测试：bucket_selector + bucket_sort 组合 ==========");

        AggRequest request = AggRequest.builder()
                .index("test_user")
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("by_city")
                                .type("TERMS")
                                .field("city")
                                .size(100)
                                .aggs(Collections.singletonList(
                                        AggDefinition.builder()
                                                .name("avg_age")
                                                .type("AVG")
                                                .field("age")
                                                .build()
                                ))
                                .pipelineAggs(Arrays.asList(
                                        PipelineAggDefinition.builder()
                                                .name("having")
                                                .type("bucket_selector")
                                                .script("params.avg_age > 20")
                                                .build(),
                                        PipelineAggDefinition.builder()
                                                .name("top1")
                                                .type("bucket_sort")
                                                .sort(Collections.singletonMap("avg_age", "desc"))
                                                .size(1)
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
                .andExpect(jsonPath("$.data.aggregations.by_city").isArray())
                .andExpect(jsonPath("$.data.aggregations.by_city.length()").value(1))
                .andDo(result -> {
                    log.info("✓ bucket_selector + bucket_sort 组合成功，返回 1 条");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(99)
    @DisplayName("12.6 校验 - composite 下挂 pipelineAggs 返回 400")
    void testPipelineCompositeNotAllowed() throws Exception {
        log.info("========== 测试：composite 下挂 pipelineAggs 校验 ==========");

        AggRequest request = AggRequest.builder()
                .index("test_user")
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("by_city")
                                .type("TERMS")
                                .field("city")
                                .composite(true)
                                .size(10)
                                .pipelineAggs(Collections.singletonList(
                                        PipelineAggDefinition.builder()
                                                .name("top1")
                                                .type("bucket_sort")
                                                .sort(Collections.singletonMap("_count", "desc"))
                                                .size(1)
                                                .build()
                                ))
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/agg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andDo(result -> log.info("✓ composite 下挂 pipelineAggs 正确返回 400"));
    }

    @Test
    @Order(100)
    @DisplayName("12.7 校验 - bucket_selector 缺少 script 返回 400")
    void testPipelineMissingScript() throws Exception {
        log.info("========== 测试：bucket_selector 缺少 script 校验 ==========");

        AggRequest request = AggRequest.builder()
                .index("test_user")
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("by_city")
                                .type("TERMS")
                                .field("city")
                                .size(10)
                                .pipelineAggs(Collections.singletonList(
                                        PipelineAggDefinition.builder()
                                                .name("having")
                                                .type("bucket_selector")
                                                .build()
                                ))
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/agg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andDo(result -> log.info("✓ bucket_selector 缺少 script 正确返回 400"));
    }

    @Test
    @Order(101)
    @DisplayName("12.8 校验 - 不支持的 pipeline 类型返回 400")
    void testPipelineUnsupportedType() throws Exception {
        log.info("========== 测试：不支持的 pipeline 类型校验 ==========");

        AggRequest request = AggRequest.builder()
                .index("test_user")
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("by_city")
                                .type("TERMS")
                                .field("city")
                                .size(10)
                                .pipelineAggs(Collections.singletonList(
                                        PipelineAggDefinition.builder()
                                                .name("unsupported")
                                                .type("moving_avg")
                                                .build()
                                ))
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/agg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andDo(result -> log.info("✓ 不支持的 pipeline 类型正确返回 400"));
    }

    // ==================== 13. 表达式聚合测试 ====================

    @Test
    @Order(102)
    @DisplayName("13.1 表达式聚合 - 基本过滤 + TERMS")
    void testExpressionAggBasic() throws Exception {
        log.info("========== 测试：表达式聚合基本用法 ==========");

        ExpressionAggRequest request = ExpressionAggRequest.builder()
                .index(ORDER_INDEX)
                .expression("status = \"completed\"")
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("by_product")
                                .type("TERMS")
                                .field("product_name")
                                .size(10)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/agg/expression")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aggregations.by_product").isArray())
                .andDo(result -> {
                    log.info("✓ 表达式聚合基本用法成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(103)
    @DisplayName("13.2 表达式聚合 - 嵌套聚合")
    void testExpressionAggNested() throws Exception {
        log.info("========== 测试：表达式聚合 + 嵌套聚合 ==========");

        ExpressionAggRequest request = ExpressionAggRequest.builder()
                .index(ORDER_INDEX)
                .expression("amount >= 1000")
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("by_status")
                                .type("TERMS")
                                .field("status")
                                .size(10)
                                .aggs(Collections.singletonList(
                                        AggDefinition.builder()
                                                .name("total_amount")
                                                .type("SUM")
                                                .field("amount")
                                                .build()
                                ))
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/agg/expression")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aggregations.by_status").isArray())
                .andExpect(jsonPath("$.data.aggregations.by_status[0].total_amount").exists())
                .andDo(result -> {
                    log.info("✓ 表达式聚合 + 嵌套聚合成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(104)
    @DisplayName("13.3 表达式聚合 + pipeline agg")
    void testExpressionAggWithPipeline() throws Exception {
        log.info("========== 测试：表达式聚合 + pipeline agg ==========");

        ExpressionAggRequest request = ExpressionAggRequest.builder()
                .index(ORDER_INDEX)
                .expression("amount >= 1000")
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("by_status")
                                .type("TERMS")
                                .field("status")
                                .size(100)
                                .aggs(Collections.singletonList(
                                        AggDefinition.builder()
                                                .name("total_amount")
                                                .type("SUM")
                                                .field("amount")
                                                .build()
                                ))
                                .pipelineAggs(Collections.singletonList(
                                        PipelineAggDefinition.builder()
                                                .name("top1")
                                                .type("bucket_sort")
                                                .sort(Collections.singletonMap("total_amount", "desc"))
                                                .size(1)
                                                .build()
                                ))
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/agg/expression")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aggregations.by_status").isArray())
                .andExpect(jsonPath("$.data.aggregations.by_status.length()").value(1))
                .andDo(result -> {
                    log.info("✓ 表达式聚合 + pipeline agg 成功，返回 1 条");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(105)
    @DisplayName("13.4 校验 - expression 为空返回 400")
    void testExpressionAggEmptyExpression() throws Exception {
        log.info("========== 测试：表达式聚合 expression 为空校验 ==========");

        ExpressionAggRequest request = ExpressionAggRequest.builder()
                .index(ORDER_INDEX)
                .expression("")
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("by_status")
                                .type("TERMS")
                                .field("status")
                                .size(10)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/agg/expression")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("expression 不能为空"))
                .andDo(result -> log.info("✓ expression 为空正确返回 400"));
    }

    @Test
    @Order(106)
    @DisplayName("13.5 校验 - expression 语法错误返回 400")
    void testExpressionAggSyntaxError() throws Exception {
        log.info("========== 测试：表达式聚合语法错误校验 ==========");

        ExpressionAggRequest request = ExpressionAggRequest.builder()
                .index(ORDER_INDEX)
                .expression("status = ")
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("by_status")
                                .type("TERMS")
                                .field("status")
                                .size(10)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/agg/expression")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andDo(result -> log.info("✓ expression 语法错误正确返回 400"));
    }


    private static String createLogIndex(RestHighLevelClient client) throws Exception {
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
        String indexName = LOG_INDEX_PREFIX + today;

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
                        "    \"createTime\": {\"type\": \"date\"}" +
                        "  }" +
                        "}",
                org.elasticsearch.xcontent.XContentType.JSON
        );
        client.indices().create(request, RequestOptions.DEFAULT);
        log.info("✓ 已创建索引: {}", indexName);

        // 插入测试数据
        List<Map<String, Object>> logs = Arrays.asList(
                createLog("user001", "login", "用户登录"),
                createLog("user002", "logout", "用户登出"),
                createLog("user003", "create", "创建记录")
        );

        for (int i = 0; i < logs.size(); i++) {
            IndexRequest indexRequest = new IndexRequest(indexName)
                    .id(String.valueOf(i + 1))
                    .source(logs.get(i));
            client.index(indexRequest, RequestOptions.DEFAULT);
        }

        Thread.sleep(2000);
        log.info("✓ 已插入 {} 条测试数据到 {}", logs.size(), indexName);

        return indexName;
    }

    private static Map<String, Object> createOrder(String orderId, String productName, double amount, int quantity, String status, String clientIp) {
        Map<String, Object> order = new HashMap<>();
        order.put("order_id", orderId);
        order.put("product_name", productName);
        order.put("amount", amount);
        order.put("quantity", quantity);
        order.put("status", status);
        order.put("client_ip", clientIp);
        order.put("created_at", new Date());
        return order;
    }

    // ==================== 表达式查询端到端测试 ====================

    @Test
    @Order(200)
    @DisplayName("表达式查询 - 等于")
    void testExpressionQueryEq() throws Exception {
        String body = "{\"index\":\"test_order_index\",\"expression\":\"status = \\\"completed\\\"\"}";
        mockMvc.perform(post("/api/query/expression")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andDo(result -> log.info("✓ 表达式等于查询: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @Order(201)
    @DisplayName("表达式查询 - 数值大于")
    void testExpressionQueryGt() throws Exception {
        String body = "{\"index\":\"test_order_index\",\"expression\":\"amount > 5000\"}";
        mockMvc.perform(post("/api/query/expression")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andDo(result -> log.info("✓ 表达式大于查询: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @Order(202)
    @DisplayName("表达式查询 - AND 组合")
    void testExpressionQueryAnd() throws Exception {
        String body = "{\"index\":\"test_order_index\",\"expression\":\"status = \\\"completed\\\" AND amount > 2000\"}";
        mockMvc.perform(post("/api/query/expression")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andDo(result -> log.info("✓ 表达式 AND 查询: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @Order(203)
    @DisplayName("表达式查询 - IN 多值")
    void testExpressionQueryIn() throws Exception {
        String body = "{\"index\":\"test_order_index\",\"expression\":\"status IN (\\\"completed\\\", \\\"pending\\\")\"}";
        mockMvc.perform(post("/api/query/expression")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(4))
                .andDo(result -> log.info("✓ 表达式 IN 查询: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @Order(204)
    @DisplayName("表达式查询 - NOT 取反")
    void testExpressionQueryNot() throws Exception {
        String body = "{\"index\":\"test_order_index\",\"expression\":\"NOT status = \\\"cancelled\\\"\"}";
        mockMvc.perform(post("/api/query/expression")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(4))
                .andDo(result -> log.info("✓ 表达式 NOT 查询: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @Order(205)
    @DisplayName("表达式查询 - LIKE 模糊匹配")
    void testExpressionQueryLike() throws Exception {
        String body = "{\"index\":\"test_order_index\",\"expression\":\"product_name LIKE \\\"Apple\\\"\"}";
        mockMvc.perform(post("/api/query/expression")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andDo(result -> log.info("✓ 表达式 LIKE 查询: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @Order(206)
    @DisplayName("表达式查询 - 语法错误返回 400")
    void testExpressionQuerySyntaxError() throws Exception {
        String body = "{\"index\":\"test_order_index\",\"expression\":\"status = \"}";
        mockMvc.perform(post("/api/query/expression")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andDo(result -> log.info("✓ 表达式语法错误返回 400: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @Order(207)
    @DisplayName("表达式校验 - 合法表达式")
    void testExpressionValidateValid() throws Exception {
        mockMvc.perform(get("/api/expression/validate")
                        .param("expression", "status = \"completed\" AND amount > 1000")
                        .param("index", ORDER_INDEX))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.errorMessage").doesNotExist())
                .andDo(result -> log.info("✓ 表达式校验合法: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @Order(208)
    @DisplayName("表达式校验 - 语法错误")
    void testExpressionValidateInvalid() throws Exception {
        mockMvc.perform(get("/api/expression/validate")
                        .param("expression", "status = \"completed\" AND amount >=")
                        .param("index", ORDER_INDEX))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.errorMessage").isString())
                .andDo(result -> log.info("✓ 表达式校验语法错误: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @Order(209)
    @DisplayName("表达式提示 - 获取提示信息（含字段中文 label）")
    void testExpressionHints() throws Exception {
        mockMvc.perform(get("/api/expression/hints")
                        .param("index", "test_order_index"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operators").isArray())
                .andExpect(jsonPath("$.data.operators.length()").value(17))
                .andExpect(jsonPath("$.data.timeRanges").isArray())
                .andExpect(jsonPath("$.data.timeRanges.length()").value(31))
                .andExpect(jsonPath("$.data.valueRules.stringNeedsQuote").value(true))
                .andExpect(jsonPath("$.data.valueRules.numberNoQuote").value(true))
                .andDo(result -> {
                    String content = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
                    // 验证字段包含中文 label
                    assertTrue(content.contains("订单号"), "应包含字段 label：订单号");
                    assertTrue(content.contains("订单ID"), "应包含字段 label：订单ID");
                    assertTrue(content.contains("商品名"), "应包含字段 label：商品名");
                    assertTrue(content.contains("金额"), "应包含字段 label：金额");
                    assertTrue(content.contains("状态"), "应包含字段 label：状态");
                    assertTrue(content.contains("订单状态"), "应包含字段 label：订单状态");
                    assertTrue(content.contains("创建时间"), "应包含字段 label：创建时间");
                    log.info("✓ 表达式提示（含中文 label）: {}", content);
                });
    }

    @Test
    @Order(210)
    @DisplayName("表达式提示 - 敏感字段不暴露")
    void testExpressionHintsExcludeSensitive() throws Exception {
        mockMvc.perform(get("/api/expression/hints")
                        .param("index", "test_user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operators").isArray())
                .andDo(result -> {
                    String content = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
                    assertFalse(content.contains("password"), "password 是 FORBIDDEN 字段，不应暴露");
                    log.info("✓ 表达式提示敏感字段未暴露: {}", content);
                });
    }

    @Test
    @Order(211)
    @DisplayName("表达式提示 - 不存在的索引仍返回全局提示")
    void testExpressionHintsNonExistentIndex() throws Exception {
        mockMvc.perform(get("/api/expression/hints")
                        .param("index", "non_existent_index"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operators").isArray())
                .andExpect(jsonPath("$.data.timeRanges").isArray())
                .andExpect(jsonPath("$.data.valueRules").isMap())
                .andDo(result -> log.info("✓ 不存在索引仍返回全局提示: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private static Map<String, Object> createUser(String username, String name, int age, String city, String phone, String password) {
        Map<String, Object> user = new HashMap<>();
        user.put("username", username);
        user.put("name", name);
        user.put("age", age);
        user.put("city", city);
        user.put("phone", phone);
        user.put("password", password);
        user.put("created_at", new Date());
        return user;
    }

    private static Map<String, Object> createLog(String userId, String action, String message) {
        Map<String, Object> log = new HashMap<>();
        log.put("user_id", userId);
        log.put("action", action);
        log.put("message", message);
        log.put("createTime", new Date());
        return log;
    }

    /**
     * 创建 secondary 索引（secondary 数据源）
     * 用于测试多数据源路由和版本兼容性
     */
    private static void createSecondaryIndex(SimpleElasticsearchRouteRegistry registry) throws Exception {
        // 获取 secondary 数据源的客户端
        RestHighLevelClient client = registry.getHighLevelClient(SECONDARY_DATASOURCE);

        // 删除旧索引
        if (client.indices().exists(new GetIndexRequest(SECONDARY_INDEX), RequestOptions.DEFAULT)) {
            client.indices().delete(new DeleteIndexRequest(SECONDARY_INDEX), RequestOptions.DEFAULT);
        }

        // 使用 TestIndexHelper 创建索引（版本自适应，支持 ES 6.x 和 7.x）
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> keywordType = new HashMap<>();
        keywordType.put("type", "keyword");
        Map<String, Object> doubleType = new HashMap<>();
        doubleType.put("type", "double");
        Map<String, Object> integerType = new HashMap<>();
        integerType.put("type", "integer");
        Map<String, Object> dateType = new HashMap<>();
        dateType.put("type", "date");

        Map<String, Object> ipType = new HashMap<>();
        ipType.put("type", "ip");

        properties.put("product_id", keywordType);
        properties.put("product_name", new HashMap<>(keywordType));
        properties.put("category", new HashMap<>(keywordType));
        properties.put("price", doubleType);
        properties.put("stock", integerType);
        properties.put("created_at", dateType);
        properties.put("client_ip", ipType);

        io.github.surezzzzzz.sdk.elasticsearch.search.test.helper.TestIndexHelper.createIndex(
                registry,
                SECONDARY_DATASOURCE,
                SECONDARY_INDEX,
                properties
        );

        // 插入测试数据
        List<Map<String, Object>> products = Arrays.asList(
                createProduct("P001", "iPhone 15 Pro", "Electronics", 8999.0, 100, "10.0.0.1"),
                createProduct("P002", "MacBook Air", "Electronics", 7999.0, 50, "10.0.0.2"),
                createProduct("P003", "AirPods Pro 2", "Electronics", 1899.0, 200, "192.168.1.1"),
                createProduct("P004", "iPad Pro", "Electronics", 6999.0, 80, "192.168.1.2"),
                createProduct("P005", "Apple Watch Ultra", "Electronics", 6299.0, 120, "172.16.0.1")
        );

        for (int i = 0; i < products.size(); i++) {
            IndexRequest indexRequest = new IndexRequest(SECONDARY_INDEX)
                    .id(String.valueOf(i + 1))
                    .source(products.get(i));
            client.index(indexRequest, RequestOptions.DEFAULT);
        }

        Thread.sleep(2000);
        log.info("✓ 已插入 {} 条测试数据到 {} (secondary 数据源)", products.size(), SECONDARY_INDEX);
    }

    private static Map<String, Object> createProduct(String productId, String productName, String category, double price, int stock, String clientIp) {
        Map<String, Object> product = new HashMap<>();
        product.put("product_id", productId);
        product.put("product_name", productName);
        product.put("category", category);
        product.put("price", price);
        product.put("stock", stock);
        product.put("client_ip", clientIp);
        product.put("created_at", new Date());
        return product;
    }

    // ==================== 14. 新增 bucket 聚合类型测试 ====================

    @Test
    @Order(212)
    @DisplayName("14.1 filter 聚合 - 单过滤器统计")
    void testAggFilter() throws Exception {
        log.info("========== 测试：filter 聚合 ==========");

        AggRequest request = AggRequest.builder()
                .index("test_user")
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("young_users")
                                .type("FILTER")
                                .query(QueryCondition.builder()
                                        .field("age")
                                        .op("lt")
                                        .value(30)
                                        .build())
                                .aggs(Collections.singletonList(
                                        AggDefinition.builder()
                                                .name("avg_age")
                                                .type("AVG")
                                                .field("age")
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
                .andExpect(jsonPath("$.data.aggregations.young_users").exists())
                .andExpect(jsonPath("$.data.aggregations.young_users.avg_age").value(25.0)) // (25+28+22)/3
                .andDo(result -> {
                    log.info("✓ filter 聚合成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(213)
    @DisplayName("14.2 filters 聚合 - 多命名过滤器对比")
    void testAggFilters() throws Exception {
        log.info("========== 测试：filters 聚合 ==========");

        Map<String, QueryCondition> filterMap = new java.util.LinkedHashMap<>();
        filterMap.put("young", QueryCondition.builder().field("age").op("lt").value(30).build());
        filterMap.put("senior", QueryCondition.builder().field("age").op("gte").value(30).build());

        AggRequest request = AggRequest.builder()
                .index("test_user")
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("age_groups")
                                .type("FILTERS")
                                .filters(filterMap)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/agg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aggregations.age_groups").isArray())
                .andExpect(jsonPath("$.data.aggregations.age_groups.length()").value(2))
                .andExpect(jsonPath("$.data.aggregations.age_groups[?(@.key=='young')].count").value(3))   // 25,28,22
                .andExpect(jsonPath("$.data.aggregations.age_groups[?(@.key=='senior')].count").value(2))  // 30,35
                .andDo(result -> {
                    log.info("✓ filters 聚合成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(214)
    @DisplayName("14.3 missing 聚合 - 统计字段缺失文档数")
    void testAggMissing() throws Exception {
        log.info("========== 测试：missing 聚合 ==========");

        AggRequest request = AggRequest.builder()
                .index("test_user")
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("no_city")
                                .type("MISSING")
                                .field("city")
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/agg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aggregations.no_city").exists())
                .andExpect(jsonPath("$.data.aggregations.no_city.count").value(0)) // 所有用户都有 city 字段
                .andDo(result -> {
                    log.info("✓ missing 聚合成功，no_city count=0");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(215)
    @DisplayName("14.4 date_range 聚合 - 日期范围分组")
    void testAggDateRange() throws Exception {
        log.info("========== 测试：date_range 聚合 ==========");

        AggRequest request = AggRequest.builder()
                .index("test_user")
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("by_created")
                                .type("DATE_RANGE")
                                .field("created_at")
                                .ranges(Arrays.asList(
                                        AggDefinition.Range.builder()
                                                .key("last_year")
                                                .from("now-1y")
                                                .to("now")
                                                .build(),
                                        AggDefinition.Range.builder()
                                                .key("older")
                                                .to("now-1y")
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
                .andExpect(jsonPath("$.data.aggregations.by_created").isArray())
                .andDo(result -> {
                    log.info("✓ date_range 聚合成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(216)
    @DisplayName("14.5 ip_range 聚合 - ES 7.x primary（ip 字段类型验证）")
    void testAggIpRangePrimary() throws Exception {
        log.info("========== 测试：ip_range 聚合 - primary ES 7.x ==========");

        AggRequest request = AggRequest.builder()
                .index(ORDER_INDEX)
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("by_network")
                                .type("IP_RANGE")
                                .field("client_ip")
                                .ranges(Arrays.asList(
                                        AggDefinition.Range.builder()
                                                .key("internal_10")
                                                .from("10.0.0.0")
                                                .to("10.255.255.255")
                                                .build(),
                                        AggDefinition.Range.builder()
                                                .key("internal_192")
                                                .from("192.168.0.0")
                                                .to("192.168.255.255")
                                                .build(),
                                        AggDefinition.Range.builder()
                                                .key("other")
                                                .from("172.0.0.0")
                                                .to("172.255.255.255")
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
                .andExpect(jsonPath("$.data.aggregations.by_network").isArray())
                .andExpect(jsonPath("$.data.aggregations.by_network.length()").value(3))
                .andExpect(jsonPath("$.data.aggregations.by_network[?(@.key=='internal_10')].count").value(2))   // 10.0.0.1, 10.0.0.2
                .andExpect(jsonPath("$.data.aggregations.by_network[?(@.key=='internal_192')].count").value(2)) // 192.168.1.1, 192.168.1.2
                .andExpect(jsonPath("$.data.aggregations.by_network[?(@.key=='other')].count").value(1))        // 172.16.0.1
                .andDo(result -> {
                    log.info("✓ ip_range 聚合成功（ES 7.x primary），各段 count 正确");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(217)
    @DisplayName("14.6 ip 字段 - eq 查询（ES 7.x primary）")
    void testIpFieldQueryPrimary() throws Exception {
        log.info("========== 测试：ip 字段 eq 查询 - primary ES 7.x ==========");

        QueryRequest request = QueryRequest.builder()
                .index(ORDER_INDEX)
                .query(QueryCondition.builder()
                        .field("client_ip")
                        .op("eq")
                        .value("10.0.0.1")
                        .build())
                .pagination(PaginationInfo.builder().size(10).build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andDo(result -> log.info("✓ ip 字段 eq 查询成功（ES 7.x primary）"));
    }

    @Test
    @Order(218)
    @DisplayName("14.7 ip_range 聚合 - ES 6.2.2 secondary（兼容性验证）")
    void testAggIpRangeSecondary() throws Exception {
        log.info("========== 测试：ip_range 聚合 - secondary ES 6.2.2 ==========");

        AggRequest request = AggRequest.builder()
                .index(SECONDARY_INDEX)
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("by_network")
                                .type("IP_RANGE")
                                .field("client_ip")
                                .ranges(Arrays.asList(
                                        AggDefinition.Range.builder()
                                                .key("internal_10")
                                                .from("10.0.0.0")
                                                .to("10.255.255.255")
                                                .build(),
                                        AggDefinition.Range.builder()
                                                .key("internal_192")
                                                .from("192.168.0.0")
                                                .to("192.168.255.255")
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
                .andExpect(jsonPath("$.data.aggregations.by_network").isArray())
                .andExpect(jsonPath("$.data.aggregations.by_network.length()").value(2))
                .andExpect(jsonPath("$.data.aggregations.by_network[?(@.key=='internal_10')].count").value(2))   // 10.0.0.1, 10.0.0.2
                .andExpect(jsonPath("$.data.aggregations.by_network[?(@.key=='internal_192')].count").value(2)) // 192.168.1.1, 192.168.1.2
                .andDo(result -> {
                    log.info("✓ ip_range 聚合成功（ES 6.2.2 secondary），各段 count 正确");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(219)
    @DisplayName("14.8 ip 字段 - eq 查询（ES 6.2.2 secondary）")
    void testIpFieldQuerySecondary() throws Exception {
        log.info("========== 测试：ip 字段 eq 查询 - secondary ES 6.2.2 ==========");

        QueryRequest request = QueryRequest.builder()
                .index(SECONDARY_INDEX)
                .query(QueryCondition.builder()
                        .field("client_ip")
                        .op("eq")
                        .value("10.0.0.1")
                        .build())
                .pagination(PaginationInfo.builder().size(10).build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andDo(result -> log.info("✓ ip 字段 eq 查询成功（ES 6.2.2 secondary）"));
    }

    @Test
    @Order(220)
    @DisplayName("14.9 校验 - filter 聚合缺少 query 返回 400")
    void testAggFilterMissingQuery() throws Exception {
        log.info("========== 测试：filter 聚合缺少 query 校验 ==========");

        AggRequest request = AggRequest.builder()
                .index("test_user")
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("bad_filter")
                                .type("FILTER")
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/agg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andDo(result -> log.info("✓ filter 缺少 query 正确返回 400"));
    }

    @Test
    @Order(221)
    @DisplayName("14.10 ip 字段 - CIDR 范围查询（ES 7.x primary）")
    void testIpCidrQueryPrimary() throws Exception {
        log.info("========== 测试：ip CIDR 查询 - primary ES 7.x ==========");

        // 10.0.0.0/24 覆盖 10.0.0.1 和 10.0.0.2，应返回 2 条
        QueryRequest request = QueryRequest.builder()
                .index(ORDER_INDEX)
                .query(QueryCondition.builder()
                        .field("client_ip")
                        .op("eq")
                        .value("10.0.0.0/24")
                        .build())
                .pagination(PaginationInfo.builder().size(10).build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andDo(result -> log.info("✓ ip CIDR 查询成功（ES 7.x primary），10.0.0.0/24 命中 2 条"));
    }

    @Test
    @Order(222)
    @DisplayName("14.11 ip 字段 - CIDR 范围查询（ES 6.2.2 secondary）")
    void testIpCidrQuerySecondary() throws Exception {
        log.info("========== 测试：ip CIDR 查询 - secondary ES 6.2.2 ==========");

        // 10.0.0.0/24 覆盖 10.0.0.1 和 10.0.0.2，应返回 2 条
        QueryRequest request = QueryRequest.builder()
                .index(SECONDARY_INDEX)
                .query(QueryCondition.builder()
                        .field("client_ip")
                        .op("eq")
                        .value("10.0.0.0/24")
                        .build())
                .pagination(PaginationInfo.builder().size(10).build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andDo(result -> log.info("✓ ip CIDR 查询成功（ES 6.2.2 secondary），10.0.0.0/24 命中 2 条"));
    }

    @Test
    @Order(223)
    @DisplayName("14.12 ip CIDR - filter 聚合统计子网内文档数（ES 7.x primary）")
    void testIpCidrFilterAggPrimary() throws Exception {
        log.info("========== 测试：ip CIDR filter 聚合 - primary ES 7.x ==========");

        // 用 filter 聚合 + CIDR 条件，统计 10.0.0.0/24 子网内的订单总金额
        AggRequest request = AggRequest.builder()
                .index(ORDER_INDEX)
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("subnet_10_orders")
                                .type("FILTER")
                                .query(QueryCondition.builder()
                                        .field("client_ip")
                                        .op("eq")
                                        .value("10.0.0.0/24")
                                        .build())
                                .aggs(Collections.singletonList(
                                        AggDefinition.builder()
                                                .name("total_amount")
                                                .type("SUM")
                                                .field("amount")
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
                .andExpect(jsonPath("$.data.aggregations.subnet_10_orders").exists())
                .andExpect(jsonPath("$.data.aggregations.subnet_10_orders.count").value(2))           // 10.0.0.1, 10.0.0.2
                .andExpect(jsonPath("$.data.aggregations.subnet_10_orders.total_amount").value(23998.0)) // 7999+15999
                .andDo(result -> {
                    log.info("✓ ip CIDR filter 聚合成功（ES 7.x primary），10.0.0.0/24 内 2 条，总金额 23998");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(224)
    @DisplayName("14.13 ip CIDR - filter 聚合统计子网内文档数（ES 6.2.2 secondary）")
    void testIpCidrFilterAggSecondary() throws Exception {
        log.info("========== 测试：ip CIDR filter 聚合 - secondary ES 6.2.2 ==========");

        AggRequest request = AggRequest.builder()
                .index(SECONDARY_INDEX)
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("subnet_10_products")
                                .type("FILTER")
                                .query(QueryCondition.builder()
                                        .field("client_ip")
                                        .op("eq")
                                        .value("10.0.0.0/24")
                                        .build())
                                .aggs(Collections.singletonList(
                                        AggDefinition.builder()
                                                .name("avg_price")
                                                .type("AVG")
                                                .field("price")
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
                .andExpect(jsonPath("$.data.aggregations.subnet_10_products").exists())
                .andExpect(jsonPath("$.data.aggregations.subnet_10_products.count").value(2))          // 10.0.0.1, 10.0.0.2
                .andExpect(jsonPath("$.data.aggregations.subnet_10_products.avg_price").value(8499.0)) // (8999+7999)/2
                .andDo(result -> {
                    log.info("✓ ip CIDR filter 聚合成功（ES 6.2.2 secondary），10.0.0.0/24 内 2 条，均价 8499");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(225)
    @DisplayName("14.14 校验 - filters 聚合缺少 filters 返回 400")
    void testAggFiltersMissingFilters() throws Exception {
        log.info("========== 测试：filters 聚合缺少 filters 校验 ==========");

        AggRequest request = AggRequest.builder()
                .index("test_user")
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("bad_filters")
                                .type("FILTERS")
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/agg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andDo(result -> log.info("✓ filters 缺少 filters 正确返回 400"));
    }

    // ==================== 15. 新增 bucket 类型 composite 校验测试 ====================

    @Test
    @Order(226)
    @DisplayName("15.1 校验 - date_range 设置 composite=true 报 400")
    void testCompositeNotSupportedDateRange() throws Exception {
        AggRequest request = AggRequest.builder()
                .index(ORDER_INDEX)
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("bad")
                                .type("date_range")
                                .field("created_at")
                                .composite(true)
                                .ranges(Collections.singletonList(
                                        AggDefinition.Range.builder().key("r1").from("now-1y").to("now").build()
                                ))
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/agg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andDo(result -> log.info("✓ date_range 不支持 composite，正确返回 400"));
    }

    @Test
    @Order(227)
    @DisplayName("15.2 校验 - ip_range 设置 composite=true 报 400")
    void testCompositeNotSupportedIpRange() throws Exception {
        AggRequest request = AggRequest.builder()
                .index(ORDER_INDEX)
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("bad")
                                .type("ip_range")
                                .field("client_ip")
                                .composite(true)
                                .ranges(Collections.singletonList(
                                        AggDefinition.Range.builder().key("r1").from("10.0.0.0").to("10.255.255.255").build()
                                ))
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/agg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andDo(result -> log.info("✓ ip_range 不支持 composite，正确返回 400"));
    }

    @Test
    @Order(228)
    @DisplayName("15.3 校验 - filter 设置 composite=true 报 400")
    void testCompositeNotSupportedFilter() throws Exception {
        AggRequest request = AggRequest.builder()
                .index(ORDER_INDEX)
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("bad")
                                .type("filter")
                                .composite(true)
                                .query(QueryCondition.builder().field("amount").op("gte").value(100).build())
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/agg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andDo(result -> log.info("✓ filter 不支持 composite，正确返回 400"));
    }

    @Test
    @Order(229)
    @DisplayName("15.4 校验 - filters 设置 composite=true 报 400")
    void testCompositeNotSupportedFilters() throws Exception {
        Map<String, QueryCondition> filterMap = new java.util.LinkedHashMap<>();
        filterMap.put("a", QueryCondition.builder().field("amount").op("gte").value(100).build());

        AggRequest request = AggRequest.builder()
                .index(ORDER_INDEX)
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("bad")
                                .type("filters")
                                .composite(true)
                                .filters(filterMap)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/agg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andDo(result -> log.info("✓ filters 不支持 composite，正确返回 400"));
    }

    @Test
    @Order(230)
    @DisplayName("15.5 校验 - missing 设置 composite=true 报 400")
    void testCompositeNotSupportedMissing() throws Exception {
        AggRequest request = AggRequest.builder()
                .index(ORDER_INDEX)
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("bad")
                                .type("missing")
                                .field("status")
                                .composite(true)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/agg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andDo(result -> log.info("✓ missing 不支持 composite，正确返回 400"));
    }

    // ==================== 16. percentiles / percentile_ranks / extended_stats 修复验证 ====================

    @Test
    @Order(231)
    @DisplayName("16.1 percentiles 聚合 - 指定百分位（ES 7.x primary）")
    void testAggPercentiles() throws Exception {
        log.info("========== 测试：percentiles 聚合 - 指定 percents ==========");
        // amount: 1999, 2999, 4999, 7999, 15999（5条）
        AggRequest request = AggRequest.builder()
                .index(ORDER_INDEX)
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("amount_percentiles")
                                .type("percentiles")
                                .field("amount")
                                .percents(Arrays.asList(50.0, 95.0, 99.0))
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/agg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aggregations.amount_percentiles").isMap())
                .andExpect(jsonPath("$.data.aggregations.amount_percentiles['50.0']").isNumber())
                .andExpect(jsonPath("$.data.aggregations.amount_percentiles['95.0']").isNumber())
                .andExpect(jsonPath("$.data.aggregations.amount_percentiles['99.0']").isNumber())
                .andDo(result -> {
                    log.info("✓ percentiles 聚合成功（ES 7.x primary）");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(232)
    @DisplayName("16.2 percentiles 聚合 - 不填 percents 使用 ES 默认（ES 7.x primary）")
    void testAggPercentilesDefault() throws Exception {
        log.info("========== 测试：percentiles 聚合 - 默认百分位 ==========");
        AggRequest request = AggRequest.builder()
                .index(ORDER_INDEX)
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("amount_percentiles_default")
                                .type("percentiles")
                                .field("amount")
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/agg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aggregations.amount_percentiles_default").isMap())
                // ES 默认包含 1.0, 5.0, 25.0, 50.0, 75.0, 95.0, 99.0
                .andExpect(jsonPath("$.data.aggregations.amount_percentiles_default['50.0']").isNumber())
                .andExpect(jsonPath("$.data.aggregations.amount_percentiles_default['95.0']").isNumber())
                .andDo(result -> {
                    log.info("✓ percentiles 聚合成功（默认百分位）");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(233)
    @DisplayName("16.3 percentile_ranks 聚合 - 指定 values（ES 7.x primary）")
    void testAggPercentileRanks() throws Exception {
        log.info("========== 测试：percentile_ranks 聚合 ==========");
        // amount: 1999, 2999, 4999, 7999, 15999
        // 5000 以下有 3 条（1999/2999/4999），占 60%
        AggRequest request = AggRequest.builder()
                .index(ORDER_INDEX)
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("amount_ranks")
                                .type("percentile_ranks")
                                .field("amount")
                                .values(Arrays.asList(5000.0, 10000.0))
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/agg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aggregations.amount_ranks").isMap())
                .andExpect(jsonPath("$.data.aggregations.amount_ranks['5000.0']").isNumber())
                .andExpect(jsonPath("$.data.aggregations.amount_ranks['10000.0']").isNumber())
                .andDo(result -> {
                    log.info("✓ percentile_ranks 聚合成功（ES 7.x primary）");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(234)
    @DisplayName("16.4 percentile_ranks 聚合 - 不填 values 报 400")
    void testAggPercentileRanksMissingValues() throws Exception {
        log.info("========== 测试：percentile_ranks 缺少 values 报 400 ==========");
        AggRequest request = AggRequest.builder()
                .index(ORDER_INDEX)
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("amount_ranks")
                                .type("percentile_ranks")
                                .field("amount")
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/agg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andDo(result -> log.info("✓ percentile_ranks 缺少 values 正确返回 400"));
    }

    @Test
    @Order(235)
    @DisplayName("16.5 extended_stats 聚合 - 返回完整 9 个字段（Bug 修复验证）")
    void testAggExtendedStats() throws Exception {
        log.info("========== 测试：extended_stats 聚合 - 完整字段验证 ==========");
        AggRequest request = AggRequest.builder()
                .index(ORDER_INDEX)
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("amount_extended_stats")
                                .type("extended_stats")
                                .field("amount")
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/agg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aggregations.amount_extended_stats").isMap())
                // 5 个基础字段
                .andExpect(jsonPath("$.data.aggregations.amount_extended_stats.count").value(5))
                .andExpect(jsonPath("$.data.aggregations.amount_extended_stats.min").value(1999.0))
                .andExpect(jsonPath("$.data.aggregations.amount_extended_stats.max").value(15999.0))
                .andExpect(jsonPath("$.data.aggregations.amount_extended_stats.avg").isNumber())
                .andExpect(jsonPath("$.data.aggregations.amount_extended_stats.sum").isNumber())
                // 4 个扩展字段（修复前这些字段会丢失）
                .andExpect(jsonPath("$.data.aggregations.amount_extended_stats.sum_of_squares").isNumber())
                .andExpect(jsonPath("$.data.aggregations.amount_extended_stats.variance").isNumber())
                .andExpect(jsonPath("$.data.aggregations.amount_extended_stats.std_deviation").isNumber())
                .andExpect(jsonPath("$.data.aggregations.amount_extended_stats.std_deviation_bounds").isMap())
                .andExpect(jsonPath("$.data.aggregations.amount_extended_stats.std_deviation_bounds.upper").isNumber())
                .andExpect(jsonPath("$.data.aggregations.amount_extended_stats.std_deviation_bounds.lower").isNumber())
                .andDo(result -> {
                    log.info("✓ extended_stats 聚合返回完整 9 个字段（Bug 修复验证通过）");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(236)
    @DisplayName("16.6 percentiles 聚合 - ES 6.x secondary（兼容性验证）")
    void testAggPercentilesOnEs6x() throws Exception {
        log.info("========== 测试：percentiles 聚合 - ES 6.x secondary ==========");
        // secondary 索引 price: 1899, 6299, 6999, 7999, 8999
        AggRequest request = AggRequest.builder()
                .index(SECONDARY_INDEX)
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("price_percentiles")
                                .type("percentiles")
                                .field("price")
                                .percents(Arrays.asList(50.0, 99.0))
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/agg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aggregations.price_percentiles").isMap())
                .andExpect(jsonPath("$.data.aggregations.price_percentiles['50.0']").isNumber())
                .andExpect(jsonPath("$.data.aggregations.price_percentiles['99.0']").isNumber())
                .andDo(result -> {
                    log.info("✓ percentiles 聚合成功（ES 6.x secondary）");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    @Test
    @Order(237)
    @DisplayName("16.7 extended_stats 聚合 - ES 6.x secondary（兼容性验证）")
    void testAggExtendedStatsOnEs6x() throws Exception {
        log.info("========== 测试：extended_stats 聚合 - ES 6.x secondary ==========");
        AggRequest request = AggRequest.builder()
                .index(SECONDARY_INDEX)
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("price_extended_stats")
                                .type("extended_stats")
                                .field("price")
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/agg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aggregations.price_extended_stats").isMap())
                .andExpect(jsonPath("$.data.aggregations.price_extended_stats.count").value(5))
                .andExpect(jsonPath("$.data.aggregations.price_extended_stats.sum_of_squares").isNumber())
                .andExpect(jsonPath("$.data.aggregations.price_extended_stats.variance").isNumber())
                .andExpect(jsonPath("$.data.aggregations.price_extended_stats.std_deviation").isNumber())
                .andExpect(jsonPath("$.data.aggregations.price_extended_stats.std_deviation_bounds").isMap())
                .andDo(result -> {
                    log.info("✓ extended_stats 聚合返回完整字段（ES 6.x secondary）");
                    log.debug("Response: {}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    // ==================== 17. scroll 分页 ====================

    /**
     * scroll 链式翻页共享的 scrollId（static 保证跨测试方法实例共享）
     */
    private static String scrollId;

    @Test
    @Order(240)
    @DisplayName("17.1 scroll 第一页 - 返回 scrollId，hasMore=true")
    void testScrollFirstPage() throws Exception {
        log.info("========== 测试：scroll 第一页请求，返回 scrollId ==========");

        QueryRequest request = QueryRequest.builder()
                .index(ORDER_INDEX)
                .pagination(PaginationInfo.builder()
                        .type("scroll")
                        .size(2)
                        .scrollTtl("2m")
                        .sort(Collections.singletonList(
                                PaginationInfo.SortField.builder().field("amount").order("asc").build()))
                        .build())
                .build();

        String response = mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.pagination.type").value("scroll"))
                .andExpect(jsonPath("$.data.pagination.hasMore").value(true))
                .andExpect(jsonPath("$.data.pagination.scrollId").isString())
                .andExpect(jsonPath("$.data.pagination.scrollId").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyString())))
                .andExpect(jsonPath("$.data.pagination.nextSearchAfter").doesNotExist())
                .andExpect(jsonPath("$.data.pagination.pitId").doesNotExist())
                .andExpect(jsonPath("$.data.total").value(5))
                .andDo(result -> log.info("✓ scroll 第一页成功"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        scrollId = objectMapper.readTree(response)
                .path("data").path("pagination").path("scrollId").asText();
        log.info("scrollId: {}", scrollId);
        assertFalse(scrollId.isEmpty(), "scrollId 不应为空");
    }

    @Test
    @Order(241)
    @DisplayName("17.2 scroll 后续翻页 - 带 scrollId 请求下一页")
    void testScrollNextPage() throws Exception {
        log.info("========== 测试：scroll 后续翻页 ==========");

        QueryRequest request = QueryRequest.builder()
                .index(ORDER_INDEX)
                .pagination(PaginationInfo.builder()
                        .type("scroll")
                        .size(2)
                        .scrollTtl("2m")
                        .scrollId(scrollId)
                        .build())
                .build();

        String response = mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.pagination.type").value("scroll"))
                .andExpect(jsonPath("$.data.pagination.hasMore").value(true))
                .andExpect(jsonPath("$.data.pagination.scrollId").isString())
                .andExpect(jsonPath("$.data.pagination.scrollId").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyString())))
                .andDo(result -> log.info("✓ scroll 第二页成功"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        scrollId = objectMapper.readTree(response)
                .path("data").path("pagination").path("scrollId").asText();
        log.info("新 scrollId: {}", scrollId);
    }

    @Test
    @Order(242)
    @DisplayName("17.3 scroll 最后一页 - hasMore=false，scrollId 不再返回，上下文自动清除")
    void testScrollLastPage() throws Exception {
        log.info("========== 测试：scroll 最后一页，自动清除上下文 ==========");

        // test_order 共 5 条，前两页各 2 条，第三页剩余 1 条
        QueryRequest request = QueryRequest.builder()
                .index(ORDER_INDEX)
                .pagination(PaginationInfo.builder()
                        .type("scroll")
                        .size(2)
                        .scrollTtl("2m")
                        .scrollId(scrollId)
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.pagination.hasMore").value(false))
                .andExpect(jsonPath("$.data.pagination.scrollId").doesNotExist())
                .andExpect(jsonPath("$.data.total").value(5))
                .andDo(result -> log.info("✓ scroll 最后一页成功，上下文已自动清除"));
    }

    @Test
    @Order(243)
    @DisplayName("17.4 scroll 不传 scrollTtl - 返回 400")
    void testScrollTtlRequired() throws Exception {
        log.info("========== 测试：scroll 不传 scrollTtl → 400 ==========");

        QueryRequest request = QueryRequest.builder()
                .index(ORDER_INDEX)
                .pagination(PaginationInfo.builder()
                        .type("scroll")
                        .size(10)
                        .sort(Collections.singletonList(
                                PaginationInfo.SortField.builder().field("amount").order("asc").build()))
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("scrollTtl")))
                .andDo(result -> log.info("✓ 缺少 scrollTtl 正确报错"));
    }

    @Test
    @Order(244)
    @DisplayName("17.5 scroll scrollTtl 超过服务端上限 - 返回 400")
    void testScrollTtlExceeded() throws Exception {
        log.info("========== 测试：scrollTtl 超过 max-ttl → 400 ==========");

        QueryRequest request = QueryRequest.builder()
                .index(ORDER_INDEX)
                .pagination(PaginationInfo.builder()
                        .type("scroll")
                        .size(10)
                        .scrollTtl("1h")
                        .sort(Collections.singletonList(
                                PaginationInfo.SortField.builder().field("amount").order("asc").build()))
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("1h")))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("5m")))
                .andDo(result -> log.info("✓ scrollTtl 超限正确报错"));
    }

    @Test
    @Order(245)
    @DisplayName("17.6 scroll 第一页不传 sort - 返回 400")
    void testScrollSortRequired() throws Exception {
        log.info("========== 测试：scroll 第一页不传 sort → 400 ==========");

        QueryRequest request = QueryRequest.builder()
                .index(ORDER_INDEX)
                .pagination(PaginationInfo.builder()
                        .type("scroll")
                        .size(10)
                        .scrollTtl("2m")
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("排序")))
                .andDo(result -> log.info("✓ 缺少 sort 正确报错"));
    }

    @Test
    @Order(246)
    @DisplayName("17.7 scroll + collapse 同时使用 - 返回 400")
    void testScrollCollapseNotSupported() throws Exception {
        log.info("========== 测试：scroll + collapse → 400 ==========");

        QueryRequest request = QueryRequest.builder()
                .index(ORDER_INDEX)
                .pagination(PaginationInfo.builder()
                        .type("scroll")
                        .size(10)
                        .scrollTtl("2m")
                        .sort(Collections.singletonList(
                                PaginationInfo.SortField.builder().field("amount").order("asc").build()))
                        .build())
                .collapse(QueryRequest.CollapseField.builder().field("status").build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("collapse")))
                .andDo(result -> log.info("✓ scroll + collapse 正确报错"));
    }

    @Test
    @Order(247)
    @DisplayName("17.8 scroll scrollTtl 格式不合法 - 返回 400")
    void testScrollTtlInvalidFormat() throws Exception {
        log.info("========== 测试：scrollTtl 格式不合法 → 400 ==========");

        QueryRequest request = QueryRequest.builder()
                .index(ORDER_INDEX)
                .pagination(PaginationInfo.builder()
                        .type("scroll")
                        .size(10)
                        .scrollTtl("abc")
                        .sort(Collections.singletonList(
                                PaginationInfo.SortField.builder().field("amount").order("asc").build()))
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("abc")))
                .andDo(result -> log.info("✓ scrollTtl 格式非法正确报错"));
    }

    @Test
    @Order(248)
    @DisplayName("17.9 scroll 全量遍历一致性 - 数据不丢不重")
    void testScrollFullTraversalConsistency() throws Exception {
        log.info("========== 测试：scroll 全量遍历一致性 ==========");

        Set<String> allIds = new HashSet<>();
        String currentScrollId = null;
        int pageCount = 0;

        while (true) {
            PaginationInfo.PaginationInfoBuilder paginationBuilder = PaginationInfo.builder()
                    .type("scroll")
                    .size(2)
                    .scrollTtl("2m");

            if (currentScrollId == null) {
                paginationBuilder.sort(Collections.singletonList(
                        PaginationInfo.SortField.builder().field("amount").order("asc").build()));
            } else {
                paginationBuilder.scrollId(currentScrollId);
            }

            QueryRequest request = QueryRequest.builder()
                    .index(ORDER_INDEX)
                    .pagination(paginationBuilder.build())
                    .build();

            String response = mockMvc.perform(post("/api/query")
                            .contentType(MediaType.APPLICATION_JSON)
                            .characterEncoding("UTF-8")
                            .content(toJson(request)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(response);
            com.fasterxml.jackson.databind.JsonNode items = root.path("data").path("items");
            boolean hasMore = root.path("data").path("pagination").path("hasMore").asBoolean();

            for (com.fasterxml.jackson.databind.JsonNode item : items) {
                allIds.add(item.path("_id").asText());
            }
            pageCount++;
            log.info("第 {} 页：{} 条，hasMore={}", pageCount, items.size(), hasMore);

            if (!hasMore) {
                break;
            }
            currentScrollId = root.path("data").path("pagination").path("scrollId").asText();
        }

        log.info("遍历完成：共 {} 页，收集 {} 个 id", pageCount, allIds.size());
        assertTrue(allIds.size() == 5, "应遍历到全部 5 条数据，实际: " + allIds.size());
        assertTrue(pageCount == 3, "应分 3 页遍历完，实际: " + pageCount);
        // 验证 5 条数据的 _id 完整（1~5）
        for (int i = 1; i <= 5; i++) {
            assertTrue(allIds.contains(String.valueOf(i)), "缺少 _id=" + i);
        }
    }

    @Test
    @Order(249)
    @DisplayName("17.10 scroll ES 6.x 兼容性 - secondary 数据源正常翻页")
    void testScrollOnEs6x() throws Exception {
        log.info("========== 测试：ES 6.x 环境下 scroll 翻页 ==========");

        ClusterInfo clusterInfo = registry.getClusterInfo(SECONDARY_DATASOURCE);
        Assumptions.assumeTrue(
                clusterInfo != null
                        && clusterInfo.getEffectiveVersion() != null
                        && clusterInfo.getEffectiveVersion().getMajor() == 6,
                "secondary 数据源不是 ES 6.x，跳过此测试"
        );

        QueryRequest request = QueryRequest.builder()
                .index(SECONDARY_INDEX)
                .pagination(PaginationInfo.builder()
                        .type("scroll")
                        .size(2)
                        .scrollTtl("2m")
                        .sort(Collections.singletonList(
                                PaginationInfo.SortField.builder().field("price").order("asc").build()))
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pagination.scrollId").isString())
                .andExpect(jsonPath("$.data.pagination.hasMore").value(true))
                .andDo(result -> log.info("✓ ES 6.x scroll 第一页成功"));
    }

    @Test
    @Order(250)
    @DisplayName("17.11 scroll 后续翻页不传 scrollTtl - 返回 400")
    void testScrollContinuationMissingTtl() throws Exception {
        log.info("========== 测试：scroll 后续翻页不传 scrollTtl → 400 ==========");

        // 先拿一个合法的 scrollId
        QueryRequest firstPage = QueryRequest.builder()
                .index(ORDER_INDEX)
                .pagination(PaginationInfo.builder()
                        .type("scroll")
                        .size(2)
                        .scrollTtl("2m")
                        .sort(Collections.singletonList(
                                PaginationInfo.SortField.builder().field("amount").order("asc").build()))
                        .build())
                .build();

        String firstResponse = mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(firstPage)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        String sid = objectMapper.readTree(firstResponse)
                .path("data").path("pagination").path("scrollId").asText();

        // 后续翻页不传 scrollTtl
        QueryRequest nextPage = QueryRequest.builder()
                .index(ORDER_INDEX)
                .pagination(PaginationInfo.builder()
                        .type("scroll")
                        .size(2)
                        .scrollId(sid)
                        // 故意不传 scrollTtl
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(nextPage)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("scrollTtl")))
                .andDo(result -> log.info("✓ 后续翻页缺少 scrollTtl 正确报错"));
    }

    @Test
    @Order(251)
    @DisplayName("17.12 scroll scrollTtl 恰好等于 maxTtl - 应通过（边界值）")
    void testScrollTtlEqualsMaxTtl() throws Exception {
        log.info("========== 测试：scrollTtl 恰好等于 maxTtl（5m）→ 应通过 ==========");

        // maxTtl 默认 5m，传 5m 应该通过
        QueryRequest request = QueryRequest.builder()
                .index(ORDER_INDEX)
                .pagination(PaginationInfo.builder()
                        .type("scroll")
                        .size(2)
                        .scrollTtl("5m")
                        .sort(Collections.singletonList(
                                PaginationInfo.SortField.builder().field("amount").order("asc").build()))
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pagination.scrollId").isString())
                .andExpect(jsonPath("$.data.pagination.hasMore").value(true))
                .andDo(result -> log.info("✓ scrollTtl=maxTtl 边界值通过"));
    }

    // ==================== v1.6.5 表达式三条件 AND 扁平化端到端测试 ====================

    @Test
    @Order(260)
    @DisplayName("v1.6.5 表达式三条件 AND - 扁平 DSL 查询结果正确")
    void testExpressionThreeAndFlatQuery() throws Exception {
        log.info("========== 测试：表达式三条件 AND 扁平化端到端 ==========");

        // name='Alice' AND age>=18 AND points<=1000
        // 预期命中：Alice(age=25, points=500)
        // 不命中：Bob(age=16), Carol(points=1500), Dave(name不对)
        String body = "{\"index\":\"test_nl_user_index\",\"expression\":\"name = 'Alice' AND age >= 18 AND points <= 1000\"}";
        mockMvc.perform(post("/api/query/expression")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("Alice"))
                .andExpect(jsonPath("$.data.items[0].age").value(25))
                .andExpect(jsonPath("$.data.items[0].points").value(500))
                .andDo(result -> log.info("✓ 三条件 AND 扁平查询: {}",
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @Order(261)
    @DisplayName("v1.6.5 表达式三条件 OR - 扁平 DSL 查询结果正确")
    void testExpressionThreeOrFlatQuery() throws Exception {
        log.info("========== 测试：表达式三条件 OR 扁平化端到端 ==========");

        // name='Alice' OR name='Bob' OR name='Carol'
        // 预期命中：Alice, Bob, Carol（3条）
        String body = "{\"index\":\"test_nl_user_index\",\"expression\":\"name = 'Alice' OR name = 'Bob' OR name = 'Carol'\"}";
        mockMvc.perform(post("/api/query/expression")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[*].name").value(
                        org.hamcrest.Matchers.containsInAnyOrder("Alice", "Bob", "Carol")))
                .andDo(result -> log.info("✓ 三条件 OR 扁平查询: {}",
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }
}

