package io.github.surezzzzzz.sdk.elasticsearch.orm.test.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.elasticsearch.orm.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.orm.agg.model.AggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.orm.query.model.PaginationInfo;
import io.github.surezzzzzz.sdk.elasticsearch.orm.query.model.QueryCondition;
import io.github.surezzzzzz.sdk.elasticsearch.orm.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.orm.test.SimpleElasticsearchOrmTestApplication;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ORM 端到端测试
 * <p>
 * 测试场景：
 * 1. 索引管理 API
 * 2. 字段查询 API
 * 3. 数据查询（各种操作符、分页、排序）
 * 4. 聚合查询（metrics、bucket、嵌套聚合）
 * 5. 敏感字段处理（脱敏、禁止访问）
 * 6. 日期分割索引
 * 7. 错误处理
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchOrmTestApplication.class)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrmEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String USER_INDEX = "test_user_index";
    private static final String ORDER_INDEX = "test_order_index";
    private static final String LOG_INDEX_PREFIX = "test_log_";

    /**
     * 将对象转换为JSON字符串
     */
    private String toJson(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    @BeforeAll
    static void setupAll(@Autowired RestHighLevelClient client) throws Exception {
        log.info("========== 开始准备测试数据 ==========");

        // 1. 创建 order 索引
        createOrderIndex(client);

        // 2. 创建 user 索引
        createUserIndex(client);

        // 3. 创建多天的 log 索引（模拟日期分割）
        createMultipleDateLogIndices(client);

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
    static void cleanupAll(@Autowired RestHighLevelClient client) throws Exception {
        log.info("========== 开始清理测试数据 ==========");

        try {
            // 删除 order 和 user 索引
            deleteIndexIfExists(client, ORDER_INDEX);
            deleteIndexIfExists(client, USER_INDEX);

            // 删除所有 test_log_* 索引
            LocalDateTime baseDate = LocalDateTime.now();
            for (int i = 0; i < 3; i++) {
                LocalDateTime date = baseDate.minusDays(i);
                String indexName = LOG_INDEX_PREFIX + date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
                deleteIndexIfExists(client, indexName);
            }
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
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andDo(result -> {
                    log.info("✓ 获取索引列表成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString());
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
                .andDo(result -> {
                    log.info("✓ 获取字段信息成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString());
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
                .andDo(result -> {
                    log.info("✓ 获取字段信息成功");
                    log.debug("Response: {}", result.getResponse().getContentAsString());
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
                .andDo(result -> log.info("✓ 刷新 Mapping 成功"));
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
                .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThan(0)))
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
                .andExpect(jsonPath("$.data.aggregations.avg_age").exists())  // ✅ 改路径
                .andExpect(jsonPath("$.data.aggregations.avg_age").value(28.0))
                .andExpect(jsonPath("$.data.took").exists())
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
                .andExpect(jsonPath("$.data.aggregations.by_city[0].avg_age").exists())
                .andExpect(jsonPath("$.data.aggregations.by_city[0].key").exists())
                .andExpect(jsonPath("$.data.aggregations.by_city[0].count").exists())
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
                .andExpect(jsonPath("$.data.items[0].phone").value(org.hamcrest.Matchers.containsString("****")))
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
                .andExpect(jsonPath("$.data.items[0].phone").value(org.hamcrest.Matchers.containsString("****")))
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

    // ==================== 5. 错误处理测试 ====================

    @Test
    @Order(40)
    @DisplayName("5.1 错误处理 - 索引不存在")
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
    @DisplayName("5.2 错误处理 - 字段不存在")
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
                        "    \"created_at\": {\"type\": \"date\"}" +
                        "  }" +
                        "}",
                org.elasticsearch.xcontent.XContentType.JSON
        );
        client.indices().create(request, RequestOptions.DEFAULT);
        log.info("✓ 已创建索引: {}", ORDER_INDEX);

        // 插入测试数据
        List<Map<String, Object>> orders = Arrays.asList(
                createOrder("ORD001", "iPhone 15", 7999.0, 1, "completed"),
                createOrder("ORD002", "MacBook Pro", 15999.0, 1, "pending"),
                createOrder("ORD003", "AirPods Pro", 1999.0, 2, "completed"),
                createOrder("ORD004", "iPad Air", 4999.0, 1, "cancelled"),
                createOrder("ORD005", "Apple Watch", 2999.0, 1, "completed")
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
                createUser("张三", 25, "北京", "13800138000", "password123"),
                createUser("李四", 30, "上海", "13900139000", "password456"),
                createUser("王五", 28, "北京", "13700137000", "password789"),
                createUser("赵六", 22, "深圳", "13600136000", "password000"),
                createUser("钱七", 35, "上海", "13500135000", "password111")
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

    private static Map<String, Object> createOrder(String orderId, String productName, double amount, int quantity, String status) {
        Map<String, Object> order = new HashMap<>();
        order.put("order_id", orderId);
        order.put("product_name", productName);
        order.put("amount", amount);
        order.put("quantity", quantity);
        order.put("status", status);
        order.put("created_at", new Date());
        return order;
    }

    private static Map<String, Object> createUser(String name, int age, String city, String phone, String password) {
        Map<String, Object> user = new HashMap<>();
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
}
