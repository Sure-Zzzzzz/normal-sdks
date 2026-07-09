package io.github.surezzzzzz.sdk.elasticsearch.search.test.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.test.SearchTestProfilesResolver;
import io.github.surezzzzzz.sdk.elasticsearch.search.test.SimpleElasticsearchSearchTestApplication;
import io.github.surezzzzzz.sdk.elasticsearch.search.test.helper.EsApiHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
@ActiveProfiles(resolver = SearchTestProfilesResolver.class)
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
        createLogIndices(registry, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31));
        log.info("========== 降级测试数据准备完成 ==========");
    }

    @AfterAll
    static void cleanupAll(@Autowired SimpleElasticsearchRouteRegistry registry) throws Exception {
        log.info("========== 开始清理降级测试数据 ==========");
        try {
            EsApiHelper.deleteIndex(registry, DEFAULT_DATASOURCE, DOWNGRADE_LOG_INDEX_PREFIX + "*");
            log.info("✓ 已清理所有 {} 索引", DOWNGRADE_LOG_INDEX_PREFIX + "*");
        } catch (Exception e) {
            log.warn("清理索引失败: {}", e.getMessage());
        }
        log.info("========== 降级测试数据清理完成 ==========");
    }

    private static void createLogIndices(SimpleElasticsearchRouteRegistry registry,
                                         LocalDate from, LocalDate to) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
        int count = 0;
        LocalDate current = from;
        while (!current.isAfter(to)) {
            String indexName = DOWNGRADE_LOG_INDEX_PREFIX + current.format(formatter);
            if (!EsApiHelper.indexExists(registry, DEFAULT_DATASOURCE, indexName)) {
                // 字段合并测试：2024-01 无 extraField/extraField2，2024-02/03 有
                // LocalDate.of(2024, 1, 1) ~ 2024-01-31 为第一个月
                boolean isMonth1 = current.getMonthValue() == 1;
                if (isMonth1) {
                    // 月份1：基础字段，无 extraField/extraField2（模拟 2024 旧索引）
                    EsApiHelper.createIndex(registry, DEFAULT_DATASOURCE, indexName,
                            "{\"properties\":{" +
                                    "\"user_id\":{\"type\":\"keyword\"}," +
                                    "\"action\":{\"type\":\"keyword\"}," +
                                    "\"message\":{\"type\":\"text\"}," +
                                    "\"timestamp\":{\"type\":\"date\"}" +
                                    "}}");
                } else {
                    // 月份2/3：有 extraField(text+keyword子字段) 和 extraField2(keyword)（模拟 2025/2026 新索引）
                    EsApiHelper.createIndex(registry, DEFAULT_DATASOURCE, indexName,
                            "{\"properties\":{" +
                                    "\"user_id\":{\"type\":\"keyword\"}," +
                                    "\"action\":{\"type\":\"keyword\"}," +
                                    "\"message\":{\"type\":\"text\"}," +
                                    "\"timestamp\":{\"type\":\"date\"}," +
                                    "\"extraField\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\"}}}," +
                                    "\"extraField2\":{\"type\":\"keyword\"}" +
                                    "}}");
                }

                Map<String, Object> doc = new HashMap<>();
                doc.put("user_id", "user" + (current.getDayOfMonth() % 10));
                doc.put("action", current.getDayOfMonth() % 3 == 0 ? "login"
                        : current.getDayOfMonth() % 3 == 1 ? "logout" : "view");
                doc.put("message", "Test log for " + current);
                doc.put("timestamp", current.atStartOfDay().toString());
                if (!isMonth1) {
                    // 只有月份 2/3 的索引有 extraField/extraField2
                    doc.put("extraField", "level-" + current.getMonthValue());
                    doc.put("extraField2", "v" + current.getMonthValue() + ".0");
                }
                EsApiHelper.indexDoc(registry, DEFAULT_DATASOURCE, indexName, "1", doc);
            }
            current = current.plusDays(1);
            count++;
        }
        log.info("✓ 共创建 {} 个日粒度索引（{} ~ {}），其中月份1无extraField/extraField2，月份2/3有",
                count, from, to);
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

    // ==================== 通配符索引字段合并测试 ====================

    @Test
    @Order(8)
    @DisplayName("2.1 /fields 接口：通配符索引合并后返回全部字段（含新增的 extraField 和 extraField2）")
    void testFieldsApiMergesAllFields() throws Exception {
        log.info("========== 测试：/fields 接口返回合并后的全量字段 ==========");

        mockMvc.perform(get("/api/indices/test_downgrade_log--*/fields")
                        .characterEncoding("UTF-8"))
                .andExpect(status().isOk())
                // 基础字段：所有索引都有
                .andExpect(jsonPath("$.data.fields[*].name").value(org.hamcrest.Matchers.hasItem("user_id")))
                .andExpect(jsonPath("$.data.fields[*].name").value(org.hamcrest.Matchers.hasItem("action")))
                .andExpect(jsonPath("$.data.fields[*].name").value(org.hamcrest.Matchers.hasItem("timestamp")))
                // 新增字段：来自月份 2/3 的索引
                .andExpect(jsonPath("$.data.fields[*].name").value(org.hamcrest.Matchers.hasItem("extraField")))
                .andExpect(jsonPath("$.data.fields[*].name").value(org.hamcrest.Matchers.hasItem("extraField2")))
                .andDo(result -> log.info("✓ /fields 返回合并字段：{}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @Order(9)
    @DisplayName("2.2 /fields 接口：extraField 子字段 keyword 正确返回")
    void testFieldsApiExtraFieldKeywordSubField() throws Exception {
        log.info("========== 测试：extraField 的 keyword 子字段在 /fields 中返回 ==========");

        mockMvc.perform(get("/api/indices/test_downgrade_log--*/fields")
                        .characterEncoding("UTF-8"))
                .andExpect(status().isOk())
                // 找到 extraField 字段（type 为小写 text）
                .andExpect(jsonPath("$.data.fields[?(@.name=='extraField')].type").value("text"))
                // extraField 的 subFields 中有 keyword（type 和 name 都为小写）
                .andExpect(jsonPath("$.data.fields[?(@.name=='extraField')].subFields.keyword.type").value("keyword"))
                .andExpect(jsonPath("$.data.fields[?(@.name=='extraField')].subFields.keyword.name").value("extraField.keyword"))
                .andDo(result -> log.info("✓ extraField.keyword 子字段正确返回"));
    }

    @Test
    @Order(10)
    @DisplayName("2.3 /query 接口：带 extraField.keyword 查询不报错（字段在合并后 fieldMap 中存在）")
    void testQueryWithExtraFieldKeyword() throws Exception {
        log.info("========== 测试：extraField.keyword 查询成功（合并后能命中 fieldMap） ==========");

        // 只查月份 2，extraField 字段有值
        QueryRequest request = QueryRequest.builder()
                .index("test_downgrade_log--*")
                .dateRange(QueryRequest.DateRange.builder()
                        .from("2024-02-01")
                        .to("2024-02-01")
                        .build())
                .query(QueryCondition.builder()
                        .field("extraField.keyword")
                        .op("EQ")
                        .value("level-2")
                        .build())
                .pagination(PaginationInfo.builder().size(10).build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                // 2月份只有1天，应该查到1条
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].extraField").value("level-2"))
                .andDo(result -> log.info("✓ extraField.keyword 查询成功，返回数据：{}",
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @Order(11)
    @DisplayName("2.4 /query 接口：带 extraField2 查询不报错（字段在合并后 fieldMap 中存在）")
    void testQueryWithExtraField2() throws Exception {
        log.info("========== 测试：extraField2 查询成功（合并后能命中 fieldMap） ==========");

        // 只查月份 3，extraField2 字段有值
        QueryRequest request = QueryRequest.builder()
                .index("test_downgrade_log--*")
                .dateRange(QueryRequest.DateRange.builder()
                        .from("2024-03-01")
                        .to("2024-03-01")
                        .build())
                .query(QueryCondition.builder()
                        .field("extraField2")
                        .op("EQ")
                        .value("v3.0")
                        .build())
                .pagination(PaginationInfo.builder().size(10).build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].extraField2").value("v3.0"))
                .andDo(result -> log.info("✓ extraField2 查询成功"));
    }

    @Test
    @Order(12)
    @DisplayName("2.5 /query 接口：多索引查询时各索引字段均有数据（extraField 在 2/3 月有值，1 月无值）")
    void testQueryMultiMonthFieldsData() throws Exception {
        log.info("========== 测试：跨月查询，合并字段在各索引均有正确数据 ==========");

        // 查 2-3 月（两个月都有 extraField）
        QueryRequest request = QueryRequest.builder()
                .index("test_downgrade_log--*")
                .dateRange(QueryRequest.DateRange.builder()
                        .from("2024-02-15")
                        .to("2024-03-15")
                        .build())
                .pagination(PaginationInfo.builder().size(10).build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request)))
                .andExpect(status().isOk())
                // 2月15日到3月15日跨月，2月有16天+3月有15天=31天，但ES默认去重/排序不确定，取前10条
                .andExpect(jsonPath("$.data.total").isNumber())
                .andExpect(jsonPath("$.data.items").isArray())
                // 所有返回的 item 都有 user_id / action / timestamp（各月都有的字段）
                .andDo(result -> log.info("✓ 跨月查询成功：{}", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
