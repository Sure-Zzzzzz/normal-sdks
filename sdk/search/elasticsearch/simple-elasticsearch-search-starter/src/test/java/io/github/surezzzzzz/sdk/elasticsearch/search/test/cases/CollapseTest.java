package io.github.surezzzzzz.sdk.elasticsearch.search.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.executor.QueryExecutor;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Collapse（字段折叠/去重）功能测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchSearchTestApplication.class)
public class CollapseTest {

    @Autowired
    private QueryExecutor queryExecutor;

    @BeforeAll
    static void setupTestData(@Autowired SimpleElasticsearchRouteRegistry registry) throws Exception {
        log.info("========== 开始准备 Collapse 测试数据 ==========");

        RestHighLevelClient client = registry.getHighLevelClient("primary");
        LocalDateTime baseDate = LocalDateTime.now();

        // 创建最近3天的索引，每天10个不同的action，确保有足够的数据测试翻页
        String[] actions = {"login", "logout", "create", "update", "delete", "read", "write", "execute", "download", "upload"};

        for (int i = 0; i < 3; i++) {
            LocalDateTime date = baseDate.minusDays(i);
            String indexName = "test_log_" + date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));

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

            // 插入该日期的测试数据，每个action创建一条记录
            for (int j = 0; j < actions.length; j++) {
                Map<String, Object> log = createLog("user" + String.format("%03d", j + 1), actions[j],
                        actions[j] + " 操作 - " + date.toLocalDate(), date);
                IndexRequest indexRequest = new IndexRequest(indexName)
                        .id(String.format("%d-%d", i, j + 1))
                        .source(log);
                client.index(indexRequest, RequestOptions.DEFAULT);
            }
        }

        // 刷新索引确保数据可见
        client.indices().refresh(new org.elasticsearch.action.admin.indices.refresh.RefreshRequest("test_log_*"), RequestOptions.DEFAULT);

        log.info("========== Collapse 测试数据准备完成：3天 × 10条 = 30条数据，10个不同的action ==========");
    }

    private static Map<String, Object> createLog(String userId, String action, String message, LocalDateTime createTime) {
        Map<String, Object> log = new HashMap<>();
        log.put("user_id", userId);
        log.put("action", action);
        log.put("message", message);
        log.put("createTime", createTime.toString());
        return log;
    }

    /**
     * 测试基础 collapse 功能
     * 场景：按字段去重，每个值只返回一条记录
     */
    @Test
    public void testBasicCollapse() {
        QueryRequest request = QueryRequest.builder()
                .index("test_log_*")
                .fields(Arrays.asList("action", "createTime"))
                .collapse(QueryRequest.CollapseField.builder()
                        .field("action")
                        .build())
                .pagination(PaginationInfo.builder()
                        .type("offset")
                        .page(1)
                        .size(100)
                        .sort(Arrays.asList(
                                PaginationInfo.SortField.builder()
                                        .field("createTime")
                                        .order("desc")
                                        .build()
                        ))
                        .build())
                .build();

        QueryResponse response = queryExecutor.execute(request);

        assertNotNull(response);
        assertTrue(response.getTotal() > 0, "应该有查询结果");

        // 验证去重：所有返回的字段值应该不重复
        List<Map<String, Object>> items = response.getItems();
        assertNotNull(items);
        assertTrue(items.size() <= 100, "返回数量不应超过 size");

        long distinctCount = items.stream()
                .map(item -> item.get("action"))
                .distinct()
                .count();

        assertEquals(items.size(), distinctCount, "所有action应该是唯一的（已去重）");

        log.info("✅ Collapse 测试通过：返回 {} 条记录，{} 个不同的action值",
                items.size(), distinctCount);
    }

    /**
     * 测试 collapse + search_after 深度分页
     * 场景：去重后的深度分页
     */
    @Test
    public void testCollapseWithSearchAfter() {
        // 第一页：只取2条，确保能翻页
        QueryRequest firstPageRequest = QueryRequest.builder()
                .index("test_log_*")
                .fields(Arrays.asList("action", "createTime"))
                .collapse(QueryRequest.CollapseField.builder()
                        .field("action")
                        .build())
                .pagination(PaginationInfo.builder()
                        .type("search_after")
                        .size(2)  // 只取2条，测试数据有10个不同的action，肯定能翻页
                        .sort(Arrays.asList(
                                PaginationInfo.SortField.builder()
                                        .field("action")
                                        .order("asc")
                                        .build()
                        ))
                        .build())
                .build();

        QueryResponse firstPageResponse = queryExecutor.execute(firstPageRequest);
        assertNotNull(firstPageResponse);
        assertTrue(firstPageResponse.getItems().size() > 0, "第一页应该有数据");
        assertEquals(2, firstPageResponse.getItems().size(), "第一页应该返回2条数据");

        // 获取第一页的最后一个 sort 值
        List<Object> nextSearchAfter = firstPageResponse.getPagination().getNextSearchAfter();
        assertNotNull(nextSearchAfter, "search_after 应该返回 nextSearchAfter");

        String firstPageLastAction = (String) firstPageResponse.getItems()
                .get(firstPageResponse.getItems().size() - 1)
                .get("action");

        log.info("✅ 第一页返回 {} 条记录，最后一个action: {}，nextSearchAfter: {}",
                firstPageResponse.getItems().size(), firstPageLastAction, nextSearchAfter);

        // 第二页：继续取2条
        QueryRequest secondPageRequest = QueryRequest.builder()
                .index("test_log_*")
                .fields(Arrays.asList("action", "createTime"))
                .collapse(QueryRequest.CollapseField.builder()
                        .field("action")
                        .build())
                .pagination(PaginationInfo.builder()
                        .type("search_after")
                        .size(2)
                        .searchAfter(nextSearchAfter)
                        .sort(Arrays.asList(
                                PaginationInfo.SortField.builder()
                                        .field("action")
                                        .order("asc")
                                        .build()
                        ))
                        .build())
                .build();

        QueryResponse secondPageResponse = queryExecutor.execute(secondPageRequest);
        assertNotNull(secondPageResponse);
        assertTrue(secondPageResponse.getItems().size() > 0, "第二页应该有数据");

        // 验证第二页的数据与第一页不重复
        String secondPageFirstAction = (String) secondPageResponse.getItems()
                .get(0)
                .get("action");

        assertNotEquals(firstPageLastAction, secondPageFirstAction,
                "第二页的第一个action应该与第一页的最后一个不同（已按action asc排序）");

        // 验证第二页的action按字母顺序在第一页最后一个之后
        assertTrue(secondPageFirstAction.compareTo(firstPageLastAction) > 0,
                String.format("第二页第一个action [%s] 应该在第一页最后一个action [%s] 之后",
                        secondPageFirstAction, firstPageLastAction));

        log.info("✅ 第二页返回 {} 条记录，第一个action: {}，翻页成功",
                secondPageResponse.getItems().size(), secondPageFirstAction);
    }

    /**
     * 测试 collapse 必须有排序的验证
     * 场景：使用 collapse 但不指定排序应该报错
     */
    @Test
    public void testCollapseRequiresSort() {
        QueryRequest request = QueryRequest.builder()
                .index("test_log_*")
                .collapse(QueryRequest.CollapseField.builder()
                        .field("action")
                        .build())
                .pagination(PaginationInfo.builder()
                        .type("offset")
                        .page(1)
                        .size(10)
                        // 故意不设置 sort
                        .build())
                .build();

        assertThrows(Exception.class, () -> {
            queryExecutor.execute(request);
        }, "使用 collapse 但不指定排序应该抛出异常");

        log.info("✅ Collapse 排序验证测试通过");
    }

    /**
     * 测试 collapse + 字段投影
     * 场景：去重后只返回指定字段
     */
    @Test
    public void testCollapseWithFieldProjection() {
        QueryRequest request = QueryRequest.builder()
                .index("test_log_*")
                .fields(Arrays.asList("action"))  // 只返回action字段
                .collapse(QueryRequest.CollapseField.builder()
                        .field("action")
                        .build())
                .pagination(PaginationInfo.builder()
                        .type("offset")
                        .page(1)
                        .size(50)
                        .sort(Arrays.asList(
                                PaginationInfo.SortField.builder()
                                        .field("action")
                                        .order("asc")
                                        .build()
                        ))
                        .build())
                .build();

        QueryResponse response = queryExecutor.execute(request);

        assertNotNull(response);
        assertTrue(response.getItems().size() > 0);

        // 验证每条记录只有action字段
        for (Map<String, Object> item : response.getItems()) {
            assertTrue(item.containsKey("action"), "应该包含action字段");
            // 注意：可能还有 _id 等元数据字段
        }

        log.info("✅ Collapse + 字段投影测试通过：返回 {} 条记录",
                response.getItems().size());
    }
}
