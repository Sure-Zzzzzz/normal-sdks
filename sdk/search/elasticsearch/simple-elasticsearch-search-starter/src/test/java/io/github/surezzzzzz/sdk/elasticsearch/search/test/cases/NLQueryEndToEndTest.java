package io.github.surezzzzzz.sdk.elasticsearch.search.test.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
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

    @BeforeAll
    static void setupAll(@Autowired SimpleElasticsearchRouteRegistry registry) throws Exception {
        log.info("========== 准备 NL 查询 E2E 测试数据 ==========");
        RestHighLevelClient client = registry.getHighLevelClient("primary");
        createUserIndex(client);
        Thread.sleep(2000);
        log.info("========== NL 查询 E2E 测试数据准备完成 ==========");
    }

    private static void createUserIndex(RestHighLevelClient client) throws Exception {
        if (client.indices().exists(new GetIndexRequest(USER_INDEX), RequestOptions.DEFAULT)) {
            client.indices().delete(new DeleteIndexRequest(USER_INDEX), RequestOptions.DEFAULT);
        }
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
        log.info("已创建索引: {}", USER_INDEX);

        // 测试数据：张三(25,北京), 李四(30,上海), 王五(28,北京), 赵六(22,深圳), 钱七(35,上海)
        List<Map<String, Object>> users = new ArrayList<Map<String, Object>>();
        users.add(userMap("alice", "张三", 25, "北京", "13800138000", "password123"));
        users.add(userMap("bob", "李四", 30, "上海", "13900139000", "password456"));
        users.add(userMap("charlie", "王五", 28, "北京", "13700137000", "password789"));
        users.add(userMap("david", "赵六", 22, "深圳", "13600136000", "password000"));
        users.add(userMap("eve", "钱七", 35, "上海", "13500135000", "password111"));

        for (int i = 0; i < users.size(); i++) {
            Map<String, Object> user = users.get(i);
            IndexRequest indexRequest = new IndexRequest(USER_INDEX)
                    .id(String.valueOf(i + 1))
                    .source(user);
            client.index(indexRequest, RequestOptions.DEFAULT);
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
                .andExpect(jsonPath("$.data.aggregations.terms_city[0].avg_age").exists())
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
        // 密码字段 FORBIDDEN，查询条件使用密码 → 400
        String nl = "查询test_user索引，密码等于password123";
        String body = "{\"nl\":\"" + nl + "\"}";
        log.info("请求: POST /api/query/nl  body={}", body);

        // 敏感字段查询应被拒绝
        mockMvc.perform(post("/api/query/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(body))
                .andExpect(status().isBadRequest())
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
}