package io.github.surezzzzzz.sdk.elasticsearch.route.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.route.test.DocumentIndexHelper;
import io.github.surezzzzzz.sdk.elasticsearch.route.test.EsApiHelper;
import io.github.surezzzzzz.sdk.elasticsearch.route.test.SimpleElasticsearchRouteTestApplication;
import io.github.surezzzzzz.sdk.elasticsearch.route.test.document.TestDocumentB;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 路由测试 - 验证能连接到不同的 ES 集群
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchRouteTestApplication.class)
public class RoutingTest {

    @Autowired
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    @Autowired
    private SimpleElasticsearchRouteRegistry registry;

    private static final String HISTORY_FLAG_KEY =
            DocumentIndexHelper.class.getCanonicalName() + ".access.history";


    @Test
    public void testRouteToTwoDifferentDataSources() throws Exception {
        RestClient primaryLow = registry.getLowLevelClient("primary");
        RestClient secondaryLow = registry.getLowLevelClient("secondary");
        log.info("========== 准备 primary 索引 test_index_a ==========");
        Response headA = primaryLow.performRequest(new Request("HEAD", "/test_index_a"));
        if (headA.getStatusLine().getStatusCode() != 200) {
            primaryLow.performRequest(new Request("PUT", "/test_index_a"));
            log.info("索引 test_index_a 已创建");
        }

        String primaryDocId = "e2e-primary-" + System.currentTimeMillis();
        log.info("========== 写入 primary 文档 id=[{}] =========", primaryDocId);
        Request putPrimary = new Request("PUT", "/test_index_a/_doc/" + primaryDocId + "?refresh=true");
        putPrimary.setJsonEntity("{\"code\":\"primary-only\",\"content\":\"written-to-primary\"}");
        primaryLow.performRequest(putPrimary);

        Response getPrimary = primaryLow.performRequest(new Request("GET", "/test_index_a/_doc/" + primaryDocId));
        String primaryBody = EntityUtils.toString(getPrimary.getEntity());
        log.info("primary 读回: {}", primaryBody);
        assertEquals(200, getPrimary.getStatusLine().getStatusCode(), "primary 写入后应能读回文档");
        assertTrue(primaryBody.contains("primary-only"), "primary 读回的文档应包含写入的字段值");

        log.info("========== 验证 secondary 无法读到 primary 的数据（路由隔离）==========");
        int secondaryIsolationStatus;
        try {
            Response isolationResp = secondaryLow.performRequest(
                    new Request("GET", "/test_index_a/_doc/" + primaryDocId));
            secondaryIsolationStatus = isolationResp.getStatusLine().getStatusCode();
        } catch (ResponseException e) {
            secondaryIsolationStatus = e.getResponse().getStatusLine().getStatusCode();
        }
        log.info("secondary 查 primary 数据，状态码={}", secondaryIsolationStatus);
        assertNotEquals(200, secondaryIsolationStatus, "secondary 不应能读到 primary 写入的数据，路由隔离失败");

        String indexB = "test_index_b.secondary";
        log.info("========== 准备 secondary 索引 {} =========", indexB);
        RestClient secondaryLowForSetup = registry.getLowLevelClient("secondary");
        if (!EsApiHelper.indexExists(secondaryLowForSetup, indexB)) {
            EsApiHelper.createIndex(secondaryLowForSetup, indexB);
            log.info("索引 {} 已创建", indexB);
        }

        String secondaryDocId = "e2e-secondary-" + System.currentTimeMillis();
        TestDocumentB docB = new TestDocumentB(secondaryDocId, "secondary-only", "written-to-secondary");
        log.info("========== 写入 secondary 文档 id=[{}] =========", secondaryDocId);
        Object saved = EsApiHelper.save(elasticsearchRestTemplate, docB);

        log.info("secondary 保存结果: {}", saved);
        assertNotNull(saved, "save() 不应返回 null");
        assertEquals(secondaryDocId, ((TestDocumentB) saved).getId(), "保存后的文档 ID 应匹配");

        Thread.sleep(500);
        Response getSecondary = secondaryLow.performRequest(new Request("GET", "/" + indexB + "/_doc/" + secondaryDocId));
        String secondaryBody = EntityUtils.toString(getSecondary.getEntity());
        log.info("secondary 读回: {}", secondaryBody);
        assertEquals(200, getSecondary.getStatusLine().getStatusCode(), "secondary 写入后应能读回文档");
        assertTrue(secondaryBody.contains("secondary-only"), "secondary 读回的 code 字段应匹配写入值");

        log.info("========== 验证 primary 无法读到 secondary 的数据（路由隔离）==========");
        int primaryIsolationStatus;
        try {
            Response primaryIsolationResp = primaryLow.performRequest(
                    new Request("GET", "/test_index_b.secondary/_doc/" + secondaryDocId));
            primaryIsolationStatus = primaryIsolationResp.getStatusLine().getStatusCode();
        } catch (ResponseException e) {
            primaryIsolationStatus = e.getResponse().getStatusLine().getStatusCode();
        }
        log.info("primary 查 secondary 数据，状态码={}", primaryIsolationStatus);
        assertNotEquals(200, primaryIsolationStatus, "primary 不应能读到 secondary 写入的数据，路由隔离失败");
    }

    @Test
    public void testRouteToHistory() throws Exception {
        log.info("========== 测试路由到 history 数据源 ==========");
        MockHttpServletRequest request = new MockHttpServletRequest();
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);
        attributes.setAttribute(HISTORY_FLAG_KEY, Boolean.TRUE, RequestAttributes.SCOPE_REQUEST);
        RequestContextHolder.setRequestAttributes(attributes);
        try {
            String historyIndex = "test_index.history";
            RestClient secondaryLow = registry.getLowLevelClient("secondary");
            boolean exists = EsApiHelper.indexExists(secondaryLow, historyIndex);
            log.info("索引 [{}] 是否存在: {}", historyIndex, exists);
            if (!exists) {
                EsApiHelper.createIndex(secondaryLow, historyIndex);
                log.info("索引 [{}] 创建成功 (history 数据源)", historyIndex);
            }
            Thread.sleep(1000);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
