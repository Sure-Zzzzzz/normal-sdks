package io.github.surezzzzzz.sdk.elasticsearch.search.test.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.ApiResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.test.SimpleElasticsearchSearchTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.xcontent.XContentType;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 自然语言集成测试
 * <p>
 * 测试 NL → QueryRequest/AggRequest 转换链
 * 1. 调用 /api/nl/dsl 获取 DSL 对象
 * 2. 将 DSL 对象发送到 /api/query 或 /api/agg 执行
 * 3. 验证执行结果
 *
 * <p>
 * 测试索引：test_nl_user_index（11条用户数据）
 * 字段：name, age(年龄), city(城市), status(状态), points(积分), createTime(创建时间)
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchSearchTestApplication.class)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NLIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String NL_USER_INDEX = "test_nl_user_index";

    @BeforeAll
    static void setupAll(@Autowired RestHighLevelClient client) throws Exception {
        log.info("========== 准备 NL 集成测试数据 ==========");
        createNlUserIndex(client);
        insertNlUserData(client);
        Thread.sleep(1000);
        log.info("========== NL 集成测试数据准备完成 ==========");
    }

    private static void createNlUserIndex(RestHighLevelClient client) throws Exception {
        if (client.indices().exists(new GetIndexRequest(NL_USER_INDEX), RequestOptions.DEFAULT)) {
            client.indices().delete(new DeleteIndexRequest(NL_USER_INDEX), RequestOptions.DEFAULT);
        }
        CreateIndexRequest request = new CreateIndexRequest(NL_USER_INDEX);
        request.mapping(
                "{" +
                        "  \"properties\": {" +
                        "    \"name\":    {\"type\": \"keyword\"}," +
                        "    \"age\":     {\"type\": \"integer\"}," +
                        "    \"city\":    {\"type\": \"keyword\"}," +
                        "    \"status\":  {\"type\": \"keyword\"}," +
                        "    \"points\":  {\"type\": \"integer\"}," +
                        "    \"createTime\": {\"type\": \"date\"}" +
                        "  }" +
                        "}",
                XContentType.JSON
        );
        client.indices().create(request, RequestOptions.DEFAULT);
        log.info("✓ 已创建索引: {}", NL_USER_INDEX);
    }

    private static void insertNlUserData(RestHighLevelClient client) throws Exception {
        List<Map<String, Object>> users = Arrays.asList(
                Map.of("name", "张三", "age", 25, "city", "北京", "status", "active", "points", 100, "createTime", "2025-01-01T10:00:00"),
                Map.of("name", "李四", "age", 30, "city", "上海", "status", "active", "points", 200, "createTime", "2025-01-02T10:00:00"),
                Map.of("name", "王五", "age", 22, "city", "北京", "status", "inactive", "points", 150, "createTime", "2025-01-03T10:00:00"),
                Map.of("name", "赵六", "age", 28, "city", "深圳", "status", "active", "points", 300, "createTime", "2025-01-04T10:00:00"),
                Map.of("name", "钱七", "age", 35, "city", "北京", "status", "active", "points", 250, "createTime", "2025-01-05T10:00:00"),
                Map.of("name", "孙八", "age", 27, "city", "上海", "status", "inactive", "points", 80, "createTime", "2025-01-06T10:00:00"),
                Map.of("name", "周九", "age", 32, "city", "深圳", "status", "active", "points", 180, "createTime", "2025-01-07T10:00:00"),
                Map.of("name", "吴十", "age", 24, "city", "北京", "status", "active", "points", 120, "createTime", "2025-01-08T10:00:00"),
                Map.of("name", "郑十一", "age", 29, "city", "上海", "status", "active", "points", 220, "createTime", "2025-01-09T10:00:00"),
                Map.of("name", "郑十二", "age", 26, "city", "北京", "status", "active", "points", 160, "createTime", "2025-01-10T10:00:00"),
                Map.of("name", "林黛玉", "age", 31, "city", "深圳", "status", "inactive", "points", 50, "createTime", "2025-01-11T10:00:00")
        );
        for (int i = 0; i < users.size(); i++) {
            Map<String, Object> user = users.get(i);
            IndexRequest indexRequest = new IndexRequest(NL_USER_INDEX)
                    .id(String.valueOf(i + 1))
                    .source(user);
            client.index(indexRequest, RequestOptions.DEFAULT);
        }
        log.info("✓ 已插入 {} 条测试数据到 {}", users.size(), NL_USER_INDEX);
    }

    private QueryResponse executeQuery(String nl, String index) throws Exception {
        // 1. NL → DSL
        MvcResult dslResult = mockMvc.perform(get("/api/nl/dsl")
                        .param("text", nl)
                        .param("index", index)
                        .characterEncoding("UTF-8"))
                .andExpect(status().isOk())
                .andReturn();

        String dslJson = dslResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        log.info("NL: {}, DSL:\n{}", nl, dslJson);

        ApiResponse<?> dslApiResponse = objectMapper.readValue(dslJson, ApiResponse.class);
        QueryRequest queryRequest = objectMapper.convertValue(dslApiResponse.getData(), QueryRequest.class);

        // 2. 执行查询
        MvcResult queryResult = mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(objectMapper.writeValueAsString(queryRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String queryJson = queryResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        log.debug("Query Response:\n{}", queryJson);

        ApiResponse<QueryResponse> queryApiResponse = objectMapper.readValue(queryJson,
                objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, QueryResponse.class));
        return queryApiResponse.getData();
    }

    // ==================== 2. 基础条件查询 ====================

    @Test
    @Order(10)
    @DisplayName("2.1 多条件AND：状态等于active并且年龄大于等于25")
    void testMultiConditionAnd() throws Exception {
        QueryResponse response = executeQuery("状态等于active并且年龄大于等于25", NL_USER_INDEX);
        List<Map<String, Object>> items = response.getItems();
        log.info("查询结果: {} 条", items.size());
        for (Map<String, Object> item : items) {
            log.info("  {} - age={}, city={}, status={}", item.get("name"), item.get("age"), item.get("city"), item.get("status"));
        }
        Assertions.assertTrue(items.size() >= 1, "应该有匹配结果");
    }

    @Test
    @Order(11)
    @DisplayName("2.2 多条件AND：城市等于北京并且状态等于active并且积分大于100")
    void testTripleConditionAnd() throws Exception {
        QueryResponse response = executeQuery("城市等于北京并且状态等于active并且积分大于100", NL_USER_INDEX);
        List<Map<String, Object>> items = response.getItems();
        log.info("查询结果: {} 条", items.size());
        for (Map<String, Object> item : items) {
            log.info("  {} - age={}, city={}, points={}", item.get("name"), item.get("age"), item.get("city"), item.get("points"));
        }
        Assertions.assertTrue(items.size() >= 1, "应该有匹配结果");
    }

    @Test
    @Order(12)
    @DisplayName("3.1 IN查询：城市在北京、上海、深圳中")
    void testInCondition() throws Exception {
        QueryResponse response = executeQuery("城市在北京、上海、深圳中", NL_USER_INDEX);
        List<Map<String, Object>> items = response.getItems();
        log.info("查询结果: {} 条", items.size());
        for (Map<String, Object> item : items) {
            log.info("  {} - city={}", item.get("name"), item.get("city"));
        }
        Assertions.assertTrue(items.size() >= 1, "应该有匹配结果");
        // 验证所有结果的 city 都在列表中
        for (Map<String, Object> item : items) {
            String city = (String) item.get("city");
            Assertions.assertTrue(
                    "北京".equals(city) || "上海".equals(city) || "深圳".equals(city),
                    "city 应该在 ['北京','上海','深圳'] 中，实际: " + city);
        }
    }

    @Test
    @Order(13)
    @DisplayName("3.2 IN + AND：城市在北京、上海、深圳中并且年龄大于25")
    void testInWithAnd() throws Exception {
        QueryResponse response = executeQuery("城市在北京、上海、深圳中并且年龄大于25", NL_USER_INDEX);
        List<Map<String, Object>> items = response.getItems();
        log.info("查询结果: {} 条", items.size());
        for (Map<String, Object> item : items) {
            log.info("  {} - age={}, city={}", item.get("name"), item.get("age"), item.get("city"));
        }
        Assertions.assertTrue(items.size() >= 1, "应该有匹配结果");
        for (Map<String, Object> item : items) {
            Integer age = (Integer) item.get("age");
            Assertions.assertTrue(age > 25, "年龄应该大于25");
        }
    }

    @Test
    @Order(14)
    @DisplayName("4.1 嵌套OR：年龄小于25或城市等于深圳")
    void testOrCondition() throws Exception {
        QueryResponse response = executeQuery("年龄小于25或城市等于深圳", NL_USER_INDEX);
        List<Map<String, Object>> items = response.getItems();
        log.info("查询结果: {} 条", items.size());
        for (Map<String, Object> item : items) {
            log.info("  {} - age={}, city={}", item.get("name"), item.get("age"), item.get("city"));
        }
        Assertions.assertTrue(items.size() >= 1, "应该有匹配结果");
        for (Map<String, Object> item : items) {
            Integer age = (Integer) item.get("age");
            String city = (String) item.get("city");
            Assertions.assertTrue(age < 25 || "深圳".equals(city), "age<25 或 city=深圳");
        }
    }

    @Test
    @Order(15)
    @DisplayName("4.2 复杂嵌套：展平OR写法（active且age>30或city=北京）")
    void testComplexNestedLogic() throws Exception {
        QueryResponse response = executeQuery("状态等于active并且年龄大于30或者状态等于active并且城市等于北京", NL_USER_INDEX);
        List<Map<String, Object>> items = response.getItems();
        log.info("查询结果: {} 条", items.size());
        for (Map<String, Object> item : items) {
            log.info("  {} - age={}, city={}, status={}", item.get("name"), item.get("age"), item.get("city"), item.get("status"));
        }
        Assertions.assertTrue(items.size() >= 1, "应该有匹配结果");
    }

    @Test
    @Order(16)
    @DisplayName("10.1 BETWEEN：年龄在18到30之间")
    void testBetweenCondition() throws Exception {
        QueryResponse response = executeQuery("年龄在18到30之间", NL_USER_INDEX);
        List<Map<String, Object>> items = response.getItems();
        log.info("查询结果: {} 条", items.size());
        for (Map<String, Object> item : items) {
            log.info("  {} - age={}", item.get("name"), item.get("age"));
        }
        Assertions.assertTrue(items.size() >= 1, "应该有匹配结果");
        for (Map<String, Object> item : items) {
            Integer age = (Integer) item.get("age");
            Assertions.assertTrue(age >= 18 && age <= 30, "年龄应该在18-30之间");
        }
    }

    @Test
    @Order(17)
    @DisplayName("9.1 字段绑定：中文字段名 → ES字段名")
    void testFieldBinding() throws Exception {
        QueryResponse response = executeQuery("年龄大于25", NL_USER_INDEX);
        List<Map<String, Object>> items = response.getItems();
        log.info("查询结果: {} 条", items.size());
        for (Map<String, Object> item : items) {
            log.info("  {} - age={}", item.get("name"), item.get("age"));
        }
        Assertions.assertTrue(items.size() >= 1, "应该有匹配结果");
        for (Map<String, Object> item : items) {
            Integer age = (Integer) item.get("age");
            Assertions.assertTrue(age > 25, "年龄应该大于25，实际: " + age);
        }
    }
}
