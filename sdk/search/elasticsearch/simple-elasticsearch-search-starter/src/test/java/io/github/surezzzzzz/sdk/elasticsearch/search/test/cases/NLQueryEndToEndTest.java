package io.github.surezzzzzz.sdk.elasticsearch.search.test.cases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.elasticsearch.route.model.ClusterInfo;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
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
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NL 查询端到端测试
 * <p>
 * 将 SearchEndToEndTest 中所有 query/agg 测试改用 NL API：
 * - 查询类：POST /api/query/nl  body: {"nl": "...", "dataSource": "primary"}
 * - 聚合类：POST /api/agg/nl    body: {"nl": "...", "dataSource": "primary"}
 * <p>
 * 断言与 SearchEndToEndTest 完全一致，确保 NL 语义解析结果的正确性。
 * <p>
 * 测试索引：test_user_index（别名 test_user）
 * 字段：username(text+keyword), name, age, city, phone, password, created_at
 * 测试数据：
 * 1. 张三(25,北京)  2. 李四(30,上海)  3. 王五(28,北京)
 * 4. 赵六(22,深圳)  5. 钱七(35,上海)
 *
 * @author surezzzzzz
 */
@Slf4j
@ActiveProfiles(resolver = SearchTestProfilesResolver.class)
@SpringBootTest(classes = SimpleElasticsearchSearchTestApplication.class)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NLQueryEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SimpleElasticsearchRouteRegistry registry;

    private static final String USER_INDEX = "test_user_index";
    private static final String USER_ALIAS = "test_user";

    private boolean primarySupportsPit() {
        ClusterInfo clusterInfo = registry.getClusterInfo("primary");
        return clusterInfo != null
                && clusterInfo.getEffectiveVersion() != null
                && (clusterInfo.getEffectiveVersion().getMajor() > 7
                || (clusterInfo.getEffectiveVersion().getMajor() == 7
                && clusterInfo.getEffectiveVersion().getMinor() >= 10));
    }

    @BeforeAll
    static void setupAll(@Autowired SimpleElasticsearchRouteRegistry registry) throws Exception {
        log.info("========== 准备 NL 查询 E2E 测试数据 ==========");
        createUserIndex(registry);
        log.info("========== NL 查询 E2E 测试数据准备完成 ==========");
    }

    private static void createUserIndex(SimpleElasticsearchRouteRegistry registry) {
        EsApiHelper.deleteIndex(registry, "primary", USER_INDEX);
        EsApiHelper.createIndex(registry, "primary", USER_INDEX,
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
                        "}");
        log.info("已创建索引: {}", USER_INDEX);

        // 测试数据：张三(25,北京), 李四(30,上海), 王五(28,北京), 赵六(22,深圳), 钱七(35,上海)
        List<Map<String, Object>> users = new ArrayList<Map<String, Object>>();
        users.add(userMap("alice", "张三", 25, "北京", "13800138000", "password123"));
        users.add(userMap("bob", "李四", 30, "上海", "13900139000", "password456"));
        users.add(userMap("charlie", "王五", 28, "北京", "13700137000", "password789"));
        users.add(userMap("david", "赵六", 22, "深圳", "13600136000", "password000"));
        users.add(userMap("eve", "钱七", 35, "上海", "13500135000", "password111"));

        for (int i = 0; i < users.size(); i++) {
            EsApiHelper.indexDoc(registry, "primary", USER_INDEX, String.valueOf(i + 1), users.get(i));
        }
        log.info("已插入 {} 条测试数据到 {}", users.size(), USER_INDEX);
    }

    private static Map<String, Object> userMap(String username, String name, int age,
                                               String city, String phone, String password) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("username", username);
        map.put("name", name);
        map.put("age", age);
        map.put("city", city);
        map.put("phone", phone);
        map.put("password", password);
        return map;
    }

    // ==================== 2. 数据查询测试（NL API） ====================

    @Test
    @Order(10)
    void testQueryEq() throws Exception {
        log.info("========== NL 查询：EQ ==========");
        String nl = "查询test_user索引，姓名等于张三";   // 索引提示 + "等于" → EQ 操作符
        String body = "{\"nl\":\"" + nl + "\"}";
        log.info("请求: POST /api/query/nl  body={}", body);

        mockMvc.perform(post("/api/query/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("张三"))
                .andExpect(jsonPath("$.data.items[0].age").value(25))
                .andExpect(jsonPath("$.data.items[0].city").value("北京"))
                .andDo(result -> log.info("响应: status={}, body={}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @Order(11)
    void testQueryBetween() throws Exception {
        log.info("========== NL 查询：BETWEEN ==========");
        String nl = "查询test_user索引，年龄在20到30之间";
        String body = "{\"nl\":\"" + nl + "\"}";
        log.info("请求: POST /api/query/nl  body={}", body);

        mockMvc.perform(post("/api/query/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(4))
                .andExpect(jsonPath("$.data.items[*].age").value(everyItem(
                        allOf(greaterThanOrEqualTo(20), lessThanOrEqualTo(30))
                )))
                .andDo(result -> log.info("响应: status={}, body={}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @Order(12)
    void testQueryIn() throws Exception {
        log.info("========== NL 查询：IN ==========");
        String nl = "查询test_user索引，城市在北京、上海中";   // 逗号分隔，末尾"中"被nl-parser忽略
        String body = "{\"nl\":\"" + nl + "\"}";
        log.info("请求: POST /api/query/nl  body={}", body);

        mockMvc.perform(post("/api/query/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(4))
                .andExpect(jsonPath("$.data.items[*].city").value(everyItem(
                        anyOf(equalTo("北京"), equalTo("上海"))
                )))
                .andDo(result -> log.info("响应: status={}, body={}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @Order(13)
    void testQueryLogicAnd() throws Exception {
        log.info("========== NL 查询：AND ==========");
        String nl = "年龄大于25并且城市等于北京";
        String body = "{\"nl\":\"" + nl + "\",\"dataSource\":\"test_user\"}";
        log.info("请求: POST /api/query/nl  body={}", body);

        mockMvc.perform(post("/api/query/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].city").value("北京"))
                .andExpect(jsonPath("$.data.items[0].age").value(greaterThan(25)))
                .andDo(result -> log.info("响应: status={}, body={}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @Order(14)
    void testQueryLogicOr() throws Exception {
        log.info("========== NL 查询：OR ==========");
        String nl = "查询test_user索引，城市等于北京或者城市等于上海";
        String body = "{\"nl\":\"" + nl + "\"}";
        log.info("请求: POST /api/query/nl  body={}", body);

        mockMvc.perform(post("/api/query/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(4))
                .andExpect(jsonPath("$.data.items[*].city").value(everyItem(
                        anyOf(equalTo("北京"), equalTo("上海"))
                )))
                .andDo(result -> log.info("响应: status={}, body={}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @Order(15)
    void testQueryPagination() throws Exception {
        log.info("========== NL 查询：分页 ==========");
        String nl = "查询test_user索引，第1页每页2条";
        String body = "{\"nl\":\"" + nl + "\"}";
        log.info("请求: POST /api/query/nl  body={}", body);

        mockMvc.perform(post("/api/query/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.total").value(5))
                .andDo(result -> log.info("响应: status={}, body={}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @Order(16)
    void testQuerySort() throws Exception {
        log.info("========== NL 查询：排序 ==========");
        String nl = "查询test_user索引，按年龄降序";
        String body = "{\"nl\":\"" + nl + "\"}";
        log.info("请求: POST /api/query/nl  body={}", body);

        mockMvc.perform(post("/api/query/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].age").value(35))
                .andExpect(jsonPath("$.data.items[1].age").value(30))
                .andExpect(jsonPath("$.data.items[2].age").value(28))
                .andExpect(jsonPath("$.data.items[3].age").value(25))
                .andExpect(jsonPath("$.data.items[4].age").value(22))
                .andDo(result -> log.info("响应: status={}, body={}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    // ==================== 3. 聚合查询测试（NL API） ====================

    @Test
    @Order(20)
    void testAggAvg() throws Exception {
        log.info("========== NL 聚合：AVG ==========");
        String nl = "查询test_user索引，统计平均年龄";
        String body = "{\"nl\":\"" + nl + "\"}";
        log.info("请求: POST /api/agg/nl  body={}", body);

        mockMvc.perform(post("/api/agg/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aggregations.avg_age").value(28.0))
                .andExpect(jsonPath("$.data.took").isNumber())
                .andDo(result -> log.info("响应: status={}, body={}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @Order(21)
    void testAggStats() throws Exception {
        log.info("========== NL 聚合：STATS ==========");
        String nl = "年龄stats";
        String body = "{\"nl\":\"" + nl + "\",\"dataSource\":\"test_user\"}";
        log.info("请求: POST /api/agg/nl  body={}", body);

        mockMvc.perform(post("/api/agg/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aggregations.stats_age.count").value(5))
                .andExpect(jsonPath("$.data.aggregations.stats_age.min").value(22))
                .andExpect(jsonPath("$.data.aggregations.stats_age.max").value(35))
                .andExpect(jsonPath("$.data.aggregations.stats_age.avg").value(28.0))
                .andExpect(jsonPath("$.data.aggregations.stats_age.sum").value(140))
                .andDo(result -> log.info("响应: status={}, body={}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @Order(22)
    void testAggTerms() throws Exception {
        log.info("========== NL 聚合：TERMS ==========");
        String nl = "查询test_user索引，按城市分组前10个";
        String body = "{\"nl\":\"" + nl + "\"}";
        log.info("请求: POST /api/agg/nl  body={}", body);

        mockMvc.perform(post("/api/agg/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aggregations.terms_city").isArray())
                .andExpect(jsonPath("$.data.aggregations.terms_city.length()").value(3))
                // 北京2人、上海2人、深圳1人
                .andExpect(jsonPath("$.data.aggregations.terms_city[?(@.key=='北京')].count").value(2))
                .andExpect(jsonPath("$.data.aggregations.terms_city[?(@.key=='上海')].count").value(2))
                .andExpect(jsonPath("$.data.aggregations.terms_city[?(@.key=='深圳')].count").value(1))
                .andDo(result -> log.info("响应: status={}, body={}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @Order(23)
    void testAggNested() throws Exception {
        log.info("========== NL 聚合：嵌套 TERMS + AVG ==========");
        String nl = "查询test_user索引，按城市分组前10个统计平均年龄";
        String body = "{\"nl\":\"" + nl + "\"}";
        log.info("请求: POST /api/agg/nl  body={}", body);

        mockMvc.perform(post("/api/agg/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aggregations.terms_city").isArray())
                .andExpect(jsonPath("$.data.aggregations.terms_city.length()").value(3))
                .andExpect(jsonPath("$.data.aggregations.terms_city[0].avg_age").isNumber())
                .andExpect(jsonPath("$.data.aggregations.terms_city[0].key").isString())
                .andExpect(jsonPath("$.data.aggregations.terms_city[0].count").isNumber())
                // 北京2人 avg=26.5，上海2人 avg=32.5，深圳1人 avg=22
                .andExpect(jsonPath("$.data.aggregations.terms_city[?(@.key=='北京')].avg_age").value(hasItem(26.5)))
                .andExpect(jsonPath("$.data.aggregations.terms_city[?(@.key=='上海')].avg_age").value(hasItem(32.5)))
                .andExpect(jsonPath("$.data.aggregations.terms_city[?(@.key=='深圳')].avg_age").value(hasItem(22.0)))
                .andDo(result -> log.info("响应: status={}, body={}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    // ==================== 4. 敏感字段（NL API） ====================

    @Test
    @Order(30)
    void testSensitiveFieldMask() throws Exception {
        log.info("========== NL 查询：敏感字段 MASK ==========");
        String nl = "查询test_user索引，姓名等于张三";
        String body = "{\"nl\":\"" + nl + "\"}";
        log.info("请求: POST /api/query/nl  body={}", body);

        mockMvc.perform(post("/api/query/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("张三"))
                // phone: 138****8000 (mask-start=3, mask-end=4, mask-pattern="****")
                .andExpect(jsonPath("$.data.items[0].phone").value("138****8000"))
                .andExpect(jsonPath("$.data.items[0].password").doesNotExist())
                .andDo(result -> log.info("响应: status={}, body={}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @Order(31)
    void testSensitiveFieldForbidden() throws Exception {
        log.info("========== NL 查询：敏感字段 FORBIDDEN ==========");
        // 密码字段 FORBIDDEN，固定查询张三，避免不同 ES 版本默认排序导致首条记录不一致
        // 验证：password 不返回、phone 被 mask
        String nl = "查询test_user索引，姓名等于张三";
        String body = "{\"nl\":\"" + nl + "\"}";
        log.info("请求: POST /api/query/nl  body={}", body);

        mockMvc.perform(post("/api/query/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                // password FORBIDDEN 字段不应出现在返回结果中
                .andExpect(jsonPath("$.data.items[0].password").doesNotExist())
                // phone: mask-start=3, mask-end=4, mask-pattern="****" → 138****8000
                .andExpect(jsonPath("$.data.items[0].phone").value("138****8000"))
                .andDo(result -> log.info("响应: status={}, body={}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    // ==================== 5. 错误处理（NL API） ====================

    @Test
    @Order(40)
    void testQueryIndexNotExists() throws Exception {
        log.info("========== NL 查询错误：索引不存在 ==========");
        String nl = "年龄大于18";   // 无索引，translateToRequest 抛 NLDslTranslationException
        String body = "{\"nl\":\"" + nl + "\"}";
        log.info("请求: POST /api/query/nl  body={}", body);

        mockMvc.perform(post("/api/query/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andDo(result -> log.info("响应: status={}, body={}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @Order(41)
    void testQueryFieldNotExists() throws Exception {
        log.info("========== NL 查询错误：字段不存在 ==========");
        String nl = "不存在字段大于18";
        String body = "{\"nl\":\"" + nl + "\"}";
        log.info("请求: POST /api/query/nl  body={}", body);

        mockMvc.perform(post("/api/query/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andDo(result -> log.info("响应: status={}, body={}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @Order(42)
    void testQueryEmptyNl() throws Exception {
        log.info("========== NL 查询错误：空 NL ==========");
        String body = "{\"nl\":\"\"}";
        log.info("请求: POST /api/query/nl  body={}", body);

        mockMvc.perform(post("/api/query/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andDo(result -> log.info("响应: status={}, body={}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    // ==================== 6. NL → DSL 转换接口 ====================

    @Test
    @Order(50)
    void testNlToQueryRequest() throws Exception {
        log.info("========== NL → DSL：查询请求 ==========");
        String nl = "年龄大于25";
        log.info("请求: GET /api/nl/dsl  text={}, index={}", nl, USER_ALIAS);

        mockMvc.perform(get("/api/nl/dsl")
                        .param("text", nl)
                        .param("index", USER_ALIAS)
                        .characterEncoding("UTF-8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists())
                .andDo(result -> log.info("响应: status={}, body={}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @Order(51)
    void testNlToAggRequest() throws Exception {
        log.info("========== NL → DSL：聚合请求 ==========");
        String nl = "按城市分组前10统计平均年龄";
        log.info("请求: GET /api/nl/dsl  text={}, index={}", nl, USER_ALIAS);

        mockMvc.perform(get("/api/nl/dsl")
                        .param("text", nl)
                        .param("index", USER_ALIAS)
                        .characterEncoding("UTF-8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists())
                .andDo(result -> log.info("响应: status={}, body={}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    // ==================== 7. pagination 覆盖 + scroll 续页 ====================

    @Test
    @Order(60)
    void testNlQueryWithPaginationOverride() throws Exception {
        log.info("========== NL 查询：pagination 覆盖（scroll 首页） ==========");
        String body = "{" +
                "\"nl\":\"查询test_user索引，按年龄升序\"," +
                "\"pagination\":{\"type\":\"scroll\",\"size\":2,\"scrollTtl\":\"1m\"," +
                "\"sort\":[{\"field\":\"age\",\"order\":\"asc\"}]}" +
                "}";
        log.info("请求: POST /api/query/nl  body={}", body);

        mockMvc.perform(post("/api/query/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.pagination.type").value("scroll"))
                .andExpect(jsonPath("$.data.pagination.hasMore").value(true))
                .andExpect(jsonPath("$.data.pagination.scrollId").isNotEmpty())
                .andDo(result -> {
                    String responseBody = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
                    log.info("响应: status={}, body={}", result.getResponse().getStatus(), responseBody);

                    // 提取 scrollId 做续页测试
                    com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(responseBody);
                    String scrollId = node.path("data").path("pagination").path("scrollId").asText();

                    log.info("========== NL 查询：scroll 续页（nl 为空，dataSource 必填） ==========");
                    String continuationBody = "{" +
                            "\"dataSource\":\"test_user\"," +
                            "\"pagination\":{\"type\":\"scroll\",\"size\":2,\"scrollTtl\":\"1m\"," +
                            "\"scrollId\":\"" + scrollId + "\"}" +
                            "}";
                    log.info("请求: POST /api/query/nl  body={}", continuationBody);

                    mockMvc.perform(post("/api/query/nl")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .characterEncoding("UTF-8")
                                    .content(continuationBody))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.data.items").isArray())
                            .andExpect(jsonPath("$.data.items.length()").value(2))
                            .andExpect(jsonPath("$.data.pagination.type").value("scroll"))
                            .andDo(r -> log.info("续页响应: status={}, body={}",
                                    r.getResponse().getStatus(),
                                    r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
                });
    }

    @Test
    @Order(61)
    void testNlQueryWithFieldsOverride() throws Exception {
        log.info("========== NL 查询：fields 字段投影覆盖 ==========");
        String body = "{" +
                "\"nl\":\"查询test_user索引，姓名等于张三\"," +
                "\"fields\":[\"name\",\"age\"]" +
                "}";
        log.info("请求: POST /api/query/nl  body={}", body);

        mockMvc.perform(post("/api/query/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("张三"))
                .andExpect(jsonPath("$.data.items[0].age").value(25))
                // city 不在 fields 里，不应返回
                .andExpect(jsonPath("$.data.items[0].city").doesNotExist())
                .andDo(result -> log.info("响应: status={}, body={}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @Order(62)
    void testScrollContinuationWithoutDataSource() throws Exception {
        log.info("========== NL 查询错误：scroll 续页缺少 dataSource ==========");
        // scroll 续页时 dataSource 必填
        String body = "{" +
                "\"pagination\":{\"type\":\"scroll\",\"size\":2,\"scrollTtl\":\"1m\"," +
                "\"scrollId\":\"some_scroll_id\"}" +
                "}";
        log.info("请求: POST /api/query/nl  body={}", body);

        mockMvc.perform(post("/api/query/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andDo(result -> log.info("响应: status={}, body={}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    // ==================== 8. search_after 覆盖 ====================

    @Test
    @Order(63)
    void testNlQueryWithSearchAfter() throws Exception {
        log.info("========== NL 查询：search_after 首页覆盖 ==========");
        String body = "{" +
                "\"nl\":\"查询test_user索引，城市等于北京\"," +
                "\"pagination\":{\"type\":\"search_after\",\"size\":2,\"searchAfterMode\":\"tiebreaker\"," +
                "\"sort\":[{\"field\":\"age\",\"order\":\"asc\"}]}" +
                "}";
        log.info("请求: POST /api/query/nl  body={}", body);

        mockMvc.perform(post("/api/query/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.pagination.type").value("search_after"))
                .andExpect(jsonPath("$.data.pagination.hasMore").value(true))
                .andExpect(jsonPath("$.data.pagination.nextSearchAfter").isArray())
                .andDo(result -> log.info("响应: status={}, body={}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    // ==================== 9. search_after 续页 ====================

    @Test
    @Order(64)
    void testNlQueryWithSearchAfterContinuation() throws Exception {
        log.info("========== NL 查询：search_after 续页 ==========");
        // 先发首页拿到 nextSearchAfter
        String firstBody = "{" +
                "\"nl\":\"查询test_user索引，城市等于北京\"," +
                "\"pagination\":{\"type\":\"search_after\",\"size\":1,\"searchAfterMode\":\"tiebreaker\"," +
                "\"sort\":[{\"field\":\"age\",\"order\":\"asc\"}]}" +
                "}";
        MvcResult firstResult = mockMvc.perform(post("/api/query/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(firstBody))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode node = objectMapper.readTree(firstResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        JsonNode nextSearchAfter = node.path("data").path("pagination").path("nextSearchAfter");

        log.info("========== NL 查询：search_after 续页（使用 nextSearchAfter） ==========");
        String contBody = "{" +
                "\"nl\":\"查询test_user索引，城市等于北京\"," +
                "\"pagination\":{\"type\":\"search_after\",\"size\":1,\"searchAfterMode\":\"tiebreaker\"," +
                "\"searchAfter\":" + nextSearchAfter + "," +
                "\"sort\":[{\"field\":\"age\",\"order\":\"asc\"}]}" +
                "}";
        log.info("请求: POST /api/query/nl  body={}", contBody);

        mockMvc.perform(post("/api/query/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(contBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andDo(result -> log.info("续页响应: status={}, body={}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    // ==================== 10. collapse 覆盖 ====================

    @Test
    @Order(65)
    void testNlQueryWithCollapse() throws Exception {
        log.info("========== NL 查询：collapse 字段折叠覆盖 ==========");
        String body = "{" +
                "\"nl\":\"查询test_user索引\"," +
                "\"pagination\":{\"type\":\"offset\",\"page\":1,\"size\":5," +
                "\"sort\":[{\"field\":\"age\",\"order\":\"asc\"}]}," +
                "\"collapse\":{\"field\":\"city\"}" +
                "}";
        log.info("请求: POST /api/query/nl  body={}", body);

        mockMvc.perform(post("/api/query/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                // 3 个城市（北京、上海、深圳），collapse 后最多 3 条
                .andExpect(jsonPath("$.data.items.length()").value(3))
                // 每条 city 不重复
                .andExpect(jsonPath("$.data.items[*].city").value(everyItem(
                        anyOf(equalTo("北京"), equalTo("上海"), equalTo("深圳"))
                )))
                .andDo(result -> log.info("响应: status={}, body={}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    // ==================== 11. PIT 首页（ES 7.10+） ====================
    // PIT API 已支持 alias：resolvePhysicalIndex 将 alias 转换为物理索引名

    @Test
    @Order(66)
    void testNlQueryWithPit() throws Exception {
        Assumptions.assumeTrue(primarySupportsPit(), "ES < 7.10 不支持 PIT，跳过 PIT 首页测试");
        log.info("========== NL 查询：PIT 首页覆盖（ES 7.10+，使用 alias） ==========");
        String body = "{" +
                "\"nl\":\"查询test_user索引，城市等于北京\"," +
                "\"pagination\":{\"type\":\"search_after\",\"searchAfterMode\":\"pit\",\"pitKeepAlive\":\"1m\"," +
                "\"size\":2,\"sort\":[{\"field\":\"age\",\"order\":\"asc\"}]}" +
                "}";
        log.info("请求: POST /api/query/nl  body={}", body);

        mockMvc.perform(post("/api/query/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.pagination.type").value("search_after"))
                .andExpect(jsonPath("$.data.pagination.pitId").isNotEmpty())
                .andExpect(jsonPath("$.data.pagination.nextSearchAfter").isArray())
                .andDo(result -> log.info("响应: status={}, body={}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    // ==================== 12. PIT 续页 ====================

    @Test
    @Order(67)
    void testNlQueryWithPitContinuation() throws Exception {
        Assumptions.assumeTrue(primarySupportsPit(), "ES < 7.10 不支持 PIT，跳过 PIT 续页测试");
        log.info("========== NL 查询：PIT 续页 ==========");
        // 先发 PIT 首页
        String firstBody = "{" +
                "\"nl\":\"查询test_user索引，城市等于北京\"," +
                "\"pagination\":{\"type\":\"search_after\",\"searchAfterMode\":\"pit\",\"pitKeepAlive\":\"1m\"," +
                "\"size\":1,\"sort\":[{\"field\":\"age\",\"order\":\"asc\"}]}" +
                "}";

        MvcResult firstResult = mockMvc.perform(post("/api/query/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(firstBody))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode firstNode = objectMapper.readTree(firstResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        JsonNode pitId = firstNode.path("data").path("pagination").path("pitId");
        JsonNode nextSearchAfter = firstNode.path("data").path("pagination").path("nextSearchAfter");

        // 续页：带回 pitId + nextSearchAfter
        log.info("========== NL 查询：PIT 续页 ==========");
        String contBody = "{" +
                "\"nl\":\"查询test_user索引，城市等于北京\"," +
                "\"pagination\":{\"type\":\"search_after\",\"searchAfterMode\":\"pit\",\"pitKeepAlive\":\"1m\"," +
                "\"pitId\":" + pitId + "," +
                "\"searchAfter\":" + nextSearchAfter + "," +
                "\"size\":1,\"sort\":[{\"field\":\"age\",\"order\":\"asc\"}]}" +
                "}";
        log.info("请求: POST /api/query/nl  body={}", contBody);

        mockMvc.perform(post("/api/query/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(contBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.pagination.type").value("search_after"))
                .andExpect(jsonPath("$.data.pagination.pitId").isNotEmpty())
                .andDo(result -> log.info("续页响应: status={}, body={}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    // ==================== 13. NL 聚合 + dateRange ====================

    @Test
    @Order(68)
    void testNlAggWithDateRange() throws Exception {
        log.info("========== NL 聚合：dateRange 覆盖 ==========");
        String body = "{" +
                "\"nl\":\"按城市分组\"," +
                "\"dataSource\":\"test_user\"," +
                "\"dateRange\":{\"from\":\"2020-01-01T00:00:00\",\"to\":\"2026-12-31T23:59:59\"}" +
                "}";
        log.info("请求: POST /api/agg/nl  body={}", body);

        mockMvc.perform(post("/api/agg/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aggregations.terms_city").isArray())
                .andExpect(jsonPath("$.data.aggregations.terms_city.length()").value(3))
                .andExpect(jsonPath("$.data.took").isNumber())
                .andDo(result -> log.info("响应: status={}, body={}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    // ==================== 14. NL 聚合 + after ====================

    @Test
    @Order(69)
    void testNlAggWithAfterKeyContinuation() throws Exception {
        log.info("========== NL 聚合：NLAggRequest.after 续页 ==========");
        // 第一页：按城市分组
        String firstBody = "{" +
                "\"nl\":\"按城市分组\"," +
                "\"dataSource\":\"test_user\"" +
                "}";
        log.info("请求: POST /api/agg/nl  firstBody={}", firstBody);

        MvcResult firstResult = mockMvc.perform(post("/api/agg/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(firstBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aggregations.terms_city").isArray())
                .andExpect(jsonPath("$.data.aggregations.terms_city.length()").value(3))
                .andReturn();

        JsonNode node = objectMapper.readTree(firstResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        JsonNode afterKey = node.path("data").path("afterKey");

        // 续页：用 after 字段
        log.info("========== NL 聚合：after 续页 ==========");
        String contBody = "{" +
                "\"nl\":\"按城市分组\"," +
                "\"dataSource\":\"test_user\"," +
                "\"after\":{\"by_city\":" + afterKey.get("by_city") + "}" +
                "}";
        log.info("请求: POST /api/agg/nl  contBody={}", contBody);

        mockMvc.perform(post("/api/agg/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(contBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aggregations").exists())
                .andExpect(jsonPath("$.data.took").isNumber())
                .andDo(result -> log.info("续页响应: status={}, body={}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    // ==================== 15. scroll 校验测试 ====================

    @Test
    @Order(70)
    void testNlScrollTtlRequired() throws Exception {
        log.info("========== NL 查询错误：scroll 不传 scrollTtl → 400 ==========");
        String body = "{" +
                "\"nl\":\"查询test_user索引\"," +
                "\"pagination\":{\"type\":\"scroll\",\"size\":10," +
                "\"sort\":[{\"field\":\"age\",\"order\":\"asc\"}]}" +
                "}";
        log.info("请求: POST /api/query/nl  body={}", body);

        mockMvc.perform(post("/api/query/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("scrollTtl")))
                .andDo(result -> log.info("响应: status={}, body={}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @Order(71)
    void testNlScrollTtlExceeded() throws Exception {
        log.info("========== NL 查询错误：scrollTtl 超过 max-ttl → 400 ==========");
        String body = "{" +
                "\"nl\":\"查询test_user索引\"," +
                "\"pagination\":{\"type\":\"scroll\",\"size\":10,\"scrollTtl\":\"1h\"," +
                "\"sort\":[{\"field\":\"age\",\"order\":\"asc\"}]}" +
                "}";
        log.info("请求: POST /api/query/nl  body={}", body);

        mockMvc.perform(post("/api/query/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("1h")))
                .andExpect(jsonPath("$.error").value(containsString("5m")))
                .andDo(result -> log.info("响应: status={}, body={}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @Order(72)
    void testNlScrollSortRequired() throws Exception {
        log.info("========== NL 查询错误：scroll 不传 sort → 400 ==========");
        String body = "{" +
                "\"nl\":\"查询test_user索引\"," +
                "\"pagination\":{\"type\":\"scroll\",\"size\":10,\"scrollTtl\":\"1m\"}" +
                "}";
        log.info("请求: POST /api/query/nl  body={}", body);

        mockMvc.perform(post("/api/query/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("排序")))
                .andDo(result -> log.info("响应: status={}, body={}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @Order(73)
    void testNlScrollCollapseNotSupported() throws Exception {
        log.info("========== NL 查询错误：scroll + collapse → 400 ==========");
        String body = "{" +
                "\"nl\":\"查询test_user索引\"," +
                "\"pagination\":{\"type\":\"scroll\",\"size\":10,\"scrollTtl\":\"1m\"," +
                "\"sort\":[{\"field\":\"age\",\"order\":\"asc\"}]}," +
                "\"collapse\":{\"field\":\"city\"}" +
                "}";
        log.info("请求: POST /api/query/nl  body={}", body);

        mockMvc.perform(post("/api/query/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("collapse")))
                .andDo(result -> log.info("响应: status={}, body={}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    // ==================== 16. PIT 校验测试 ====================

    @Test
    @Order(74)
    void testNlPitKeepAliveRequired() throws Exception {
        Assumptions.assumeTrue(primarySupportsPit(), "ES < 7.10 不支持 PIT，跳过 pitKeepAlive 参数校验");
        log.info("========== NL 查询错误：PIT 缺少 pitKeepAlive → 400 ==========");
        String body = "{" +
                "\"nl\":\"查询test_user索引\"," +
                "\"pagination\":{\"type\":\"search_after\",\"searchAfterMode\":\"pit\"," +
                "\"size\":10,\"sort\":[{\"field\":\"age\",\"order\":\"asc\"}]}" +
                "}";
        log.info("请求: POST /api/query/nl  body={}", body);

        mockMvc.perform(post("/api/query/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("pitKeepAlive")))
                .andDo(result -> log.info("响应: status={}, body={}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @Order(75)
    void testNlPitKeepAliveExceeded() throws Exception {
        Assumptions.assumeTrue(primarySupportsPit(), "ES < 7.10 不支持 PIT，跳过 pitKeepAlive 上限校验");
        log.info("========== NL 查询错误：PIT pitKeepAlive 超过上限 → 400 ==========");
        String body = "{" +
                "\"nl\":\"查询test_user索引\"," +
                "\"pagination\":{\"type\":\"search_after\",\"searchAfterMode\":\"pit\"," +
                "\"pitKeepAlive\":\"24h\"," +
                "\"size\":10,\"sort\":[{\"field\":\"age\",\"order\":\"asc\"}]}" +
                "}";
        log.info("请求: POST /api/query/nl  body={}", body);

        mockMvc.perform(post("/api/query/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("24h")))
                .andExpect(jsonPath("$.error").value(containsString("5m")))
                .andDo(result -> log.info("响应: status={}, body={}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    // ==================== 17. search_after 模式完整测试 ====================

    @Test
    @Order(76)
    void testNlSearchAfterTiebreakerFull() throws Exception {
        log.info("========== NL 查询：search_after tiebreaker 完整翻页 ==========");
        // 首页：size=2
        String firstBody = "{" +
                "\"nl\":\"查询test_user索引\"," +
                "\"pagination\":{\"type\":\"search_after\",\"searchAfterMode\":\"tiebreaker\"," +
                "\"size\":2,\"sort\":[{\"field\":\"age\",\"order\":\"asc\"}]}" +
                "}";
        MvcResult firstResult = mockMvc.perform(post("/api/query/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(firstBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.pagination.type").value("search_after"))
                .andExpect(jsonPath("$.data.pagination.hasMore").value(true))
                .andExpect(jsonPath("$.data.pagination.nextSearchAfter").isArray())
                .andExpect(jsonPath("$.data.pagination.pitId").doesNotExist())
                .andReturn();

        JsonNode node = objectMapper.readTree(firstResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        JsonNode nextSearchAfter = node.path("data").path("pagination").path("nextSearchAfter");

        // 续页
        String contBody = "{" +
                "\"nl\":\"查询test_user索引\"," +
                "\"pagination\":{\"type\":\"search_after\",\"searchAfterMode\":\"tiebreaker\"," +
                "\"size\":2,\"sort\":[{\"field\":\"age\",\"order\":\"asc\"}]," +
                "\"searchAfter\":" + nextSearchAfter + "}" +
                "}";
        mockMvc.perform(post("/api/query/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(contBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(greaterThanOrEqualTo(1)))
                .andDo(result -> log.info("续页响应: status={}, body={}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @Order(77)
    void testNlSearchAfterNoneMode() throws Exception {
        log.info("========== NL 查询：search_after none 模式 ==========");
        String body = "{" +
                "\"nl\":\"查询test_user索引\"," +
                "\"pagination\":{\"type\":\"search_after\",\"searchAfterMode\":\"none\"," +
                "\"size\":2,\"sort\":[{\"field\":\"age\",\"order\":\"asc\"}]}" +
                "}";
        mockMvc.perform(post("/api/query/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.pagination.type").value("search_after"))
                .andExpect(jsonPath("$.data.pagination.hasMore").value(true))
                .andExpect(jsonPath("$.data.pagination.nextSearchAfter").isArray())
                .andExpect(jsonPath("$.data.pagination.nextSearchAfter.length()").value(1))
                .andDo(result -> log.info("响应: status={}, body={}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    // ==================== 18. scroll 完整翻页一致性 ====================

    @Test
    @Order(78)
    void testNlScrollFullTraversalConsistency() throws Exception {
        log.info("========== NL 查询：scroll 完整翻页一致性（5 条数据、每页 2 条） ==========");
        java.util.Set<String> allIds = new java.util.HashSet<String>();
        int pageCount = 0;

        // 首页
        String firstBody = "{" +
                "\"nl\":\"查询test_user索引\"," +
                "\"pagination\":{\"type\":\"scroll\",\"size\":2,\"scrollTtl\":\"1m\"," +
                "\"sort\":[{\"field\":\"age\",\"order\":\"asc\"}]}" +
                "}";
        MvcResult firstResult = mockMvc.perform(post("/api/query/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(firstBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.pagination.hasMore").value(true))
                .andReturn();

        JsonNode firstNode = objectMapper.readTree(firstResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        String scrollId = firstNode.path("data").path("pagination").path("scrollId").asText();
        for (JsonNode item : firstNode.path("data").path("items")) {
            allIds.add(item.path("_id").asText());
        }
        pageCount++;

        // 续页直到完成
        while (scrollId != null && !scrollId.isEmpty()) {
            String contBody = "{" +
                    "\"dataSource\":\"test_user\"," +
                    "\"pagination\":{\"type\":\"scroll\",\"size\":2,\"scrollTtl\":\"1m\"," +
                    "\"scrollId\":\"" + scrollId + "\"}" +
                    "}";
            MvcResult contResult = mockMvc.perform(post("/api/query/nl")
                            .contentType(MediaType.APPLICATION_JSON)
                            .characterEncoding("UTF-8")
                            .content(contBody))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode contNode = objectMapper.readTree(contResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
            JsonNode items = contNode.path("data").path("items");
            boolean hasMore = contNode.path("data").path("pagination").path("hasMore").asBoolean();

            for (JsonNode item : items) {
                allIds.add(item.path("_id").asText());
            }
            pageCount++;

            if (!hasMore) {
                break;
            }
            scrollId = contNode.path("data").path("pagination").path("scrollId").asText();
        }

        // 5 条数据，3 页，5 个唯一 ID
        org.junit.jupiter.api.Assertions.assertEquals(5, allIds.size(), "scroll 翻页应无丢失无重复");
        org.junit.jupiter.api.Assertions.assertEquals(3, pageCount, "5 条数据每页 2 条应有 3 页");
        log.info("✓ scroll 完整翻页一致性验证通过：5 条数据、3 页、5 个唯一 ID");
    }
}