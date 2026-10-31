package io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.resttemplate;

import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.SmartRedisLimiterPolicyMutationClient;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.model.PolicyLimit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.model.PolicyMutationResult;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.model.PolicyRuleView;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.model.PolicyUpsertCommand;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.resttemplate.configuration.SmartRedisLimiterPolicyMutationClientProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.support.JacksonPolicyMutationJsonCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * 管理面变更客户端传输 wire 契约测试：端点/方法/请求体/409 分类/幂等编排全链
 *
 * @author surezzzzzz
 */
class RestTemplateSmartRedisLimiterPolicyMutationClientTest {

    private static final String BASE = "https://limiter.example.test/api";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private RestTemplateSmartRedisLimiterPolicyMutationClient client;

    private static PolicyUpsertCommand command(boolean enabled, List<PolicyLimit> limits) {
        return PolicyUpsertCommand.builder()
                .serviceCode("aksk").resourceCode("oauth2-token")
                .dimension("CUSTOMER").selector("EXACT")
                .namespace("customer").objectId("cust-001")
                .enabled(enabled).limits(limits)
                .build();
    }

    private static List<PolicyLimit> limits(int count) {
        return Collections.singletonList(new PolicyLimit(count, 1L, "MINUTES"));
    }

    private static String ruleJson(long id, long rowVersion, int count, boolean enabled) {
        // createdAt/updatedAt 为服务端真响应字段（v2 TypedRuleResponse），wire 必须容忍解析
        return "{\"id\":" + id + ",\"rowVersion\":" + rowVersion
                + ",\"serviceCode\":\"aksk\",\"resourceCode\":\"oauth2-token\""
                + ",\"dimension\":\"CUSTOMER\",\"selector\":\"EXACT\",\"namespace\":\"customer\""
                + ",\"objectId\":\"cust-001\",\"enabled\":" + enabled
                + ",\"createdAt\":\"2026-10-14T10:00:00Z\",\"updatedAt\":\"2026-10-14T11:00:00Z\""
                + ",\"limits\":[{\"count\":" + count + ",\"window\":1,\"unit\":\"MINUTES\"}]}";
    }

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        SmartRedisLimiterPolicyMutationClientProperties properties = new SmartRedisLimiterPolicyMutationClientProperties();
        properties.setBaseUrl(BASE);
        client = new RestTemplateSmartRedisLimiterPolicyMutationClient(
                properties, new JacksonPolicyMutationJsonCodec(), restTemplate);
    }

    @Test
    void findByKeyQueriesIdentityAndReturnsUniqueView() {
        server.expect(requestTo(BASE + "/v2/policy/rules?serviceCode=aksk&resourceCode=oauth2-token"
                        + "&dimension=CUSTOMER&selector=EXACT&namespace=customer&customType=&objectId=cust-001&page=1&size=100"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"items\":[" + ruleJson(7L, 3L, 100, true) + "],\"page\":1,\"size\":20,\"total\":1}",
                        MediaType.APPLICATION_JSON));
        PolicyRuleView view = client.findByKey(command(true, limits(100)));
        assertEquals(7L, view.getRuleId());
        assertEquals(3L, view.getRowVersion());
        assertTrue(view.isEnabled());
        assertEquals(100, view.getLimits().get(0).getCount());
        server.verify();
    }

    @Test
    void findByKeyMultipleMatchesRejected() {
        server.expect(requestTo(BASE + "/v2/policy/rules?serviceCode=aksk&resourceCode=oauth2-token"
                        + "&dimension=CUSTOMER&selector=EXACT&namespace=customer&customType=&objectId=cust-001&page=1&size=100"))
                .andRespond(withSuccess(
                        "{\"items\":[" + ruleJson(7L, 3L, 100, true) + "," + ruleJson(8L, 0L, 100, true)
                                + "],\"page\":1,\"size\":20,\"total\":2}",
                        MediaType.APPLICATION_JSON));
        assertThrows(SmartRedisLimiterException.class, () -> client.findByKey(command(true, limits(100))));
    }

    @Test
    void createPostsFullIdentityBodyAndParsesMutation() {
        server.expect(requestTo(BASE + "/v2/policy/rule"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.serviceCode").value("aksk"))
                .andExpect(jsonPath("$.dimension").value("CUSTOMER"))
                .andExpect(jsonPath("$.selector").value("EXACT"))
                .andExpect(jsonPath("$.objectId").value("cust-001"))
                .andExpect(jsonPath("$.limits[0].count").value(100))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"rule\":" + ruleJson(12L, 0L, 100, true) + ",\"revision\":5}"));
        PolicyMutationResult result = client.create(command(true, limits(100)));
        assertEquals(12L, result.getRuleId());
        assertEquals(5L, result.getRevision());
        assertTrue(result.hasNewRevision());
        server.verify();
    }

    @Test
    void conflictSurfacesAsMutationConflictException() {
        server.expect(requestTo(BASE + "/v2/policy/rule"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CONFLICT));
        assertThrows(SmartRedisLimiterPolicyMutationClient.PolicyMutationConflictException.class,
                () -> client.create(command(true, limits(100))));
        server.verify();
    }

    @Test
    void updateLimitsSendsCasBody() {
        server.expect(requestTo(BASE + "/v2/policy/rule/7"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.expectedRowVersion").value(3))
                .andExpect(jsonPath("$.limits[0].count").value(30))
                .andRespond(withStatus(org.springframework.http.HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"rule\":" + ruleJson(7L, 4L, 30, true) + ",\"revision\":6}"));
        PolicyMutationResult result = client.updateLimits(7L, 3L, limits(30));
        assertEquals(4L, result.getRowVersion());
        assertEquals(6L, result.getRevision());
        server.verify();
    }

    @Test
    void upsertFullChainCreatesWhenAbsent() {
        server.expect(requestTo(BASE + "/v2/policy/rules?serviceCode=aksk&resourceCode=oauth2-token"
                        + "&dimension=CUSTOMER&selector=EXACT&namespace=customer&customType=&objectId=cust-001&page=1&size=100"))
                .andRespond(withSuccess("{\"items\":[],\"page\":1,\"size\":20,\"total\":0}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/v2/policy/rule"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"rule\":" + ruleJson(12L, 0L, 100, true) + ",\"revision\":5}"));
        PolicyMutationResult result = client.upsert(command(true, limits(100)));
        assertEquals(12L, result.getRuleId());
        assertTrue(result.hasNewRevision());
        server.verify();
    }

    @Test
    void upsertIdenticalContentSkipsWrite() {
        server.expect(requestTo(BASE + "/v2/policy/rules?serviceCode=aksk&resourceCode=oauth2-token"
                        + "&dimension=CUSTOMER&selector=EXACT&namespace=customer&customType=&objectId=cust-001&page=1&size=100"))
                .andRespond(withSuccess(
                        "{\"items\":[" + ruleJson(7L, 3L, 100, true) + "],\"page\":1,\"size\":20,\"total\":1}",
                        MediaType.APPLICATION_JSON));
        PolicyMutationResult result = client.upsert(command(true, limits(100)));
        assertEquals(PolicyMutationResult.NO_REVISION, result.getRevision());
        server.verify();
    }

    @Test
    void unknownResponseFieldRejected() {
        server.expect(requestTo(BASE + "/v2/policy/rules?serviceCode=aksk&resourceCode=oauth2-token"
                        + "&dimension=CUSTOMER&selector=EXACT&namespace=customer&customType=&objectId=cust-001&page=1&size=100"))
                .andRespond(withSuccess(
                        "{\"items\":[],\"page\":1,\"size\":20,\"total\":0,\"unexpected\":\"x\"}",
                        MediaType.APPLICATION_JSON));
        assertThrows(SmartRedisLimiterException.class, () -> client.findByKey(command(true, limits(100))));
        server.verify();
    }

    @Test
    void findByKeySendsNamespaceAndCustomTypeFilters() {
        server.expect(requestTo(BASE + "/v2/policy/rules?serviceCode=aksk&resourceCode=oauth2-token"
                        + "&dimension=CUSTOMER&selector=EXACT&namespace=customer&customType=&objectId=cust-001"
                        + "&page=1&size=100"))
                .andRespond(withSuccess("{\"items\":[],\"page\":1,\"size\":100,\"total\":0}",
                        MediaType.APPLICATION_JSON));
        assertNull(client.findByKey(command(true, limits(100))));
        server.verify();
    }

    @Test
    void findByIdentityRowMaps404ToNull() {
        server.expect(requestTo(BASE + "/v2/policy/rule/7"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));
        assertNull(client.findByIdentityRow(7L));
        server.verify();
    }

    @Test
    void findByKeyPaginatesBeyondFirstPage() {
        // 目标规则落在第二页：首页 100 条填充 + 次页命中，证明翻页不停留在首页
        StringBuilder filler = new StringBuilder();
        for (long i = 1; i <= 100; i++) {
            if (i > 1) {
                filler.append(",");
            }
            filler.append(ruleJson(i, 0L, 10, true)
                    .replace("\"objectId\":\"cust-001\"", "\"objectId\":\"filler-" + i + "\""));
        }
        server.expect(requestTo(BASE + "/v2/policy/rules?serviceCode=aksk&resourceCode=oauth2-token"
                        + "&dimension=CUSTOMER&selector=EXACT&namespace=customer&customType=&objectId=cust-001"
                        + "&page=1&size=100"))
                .andRespond(withSuccess("{\"items\":[" + filler + "],\"page\":1,\"size\":100,\"total\":101}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/v2/policy/rules?serviceCode=aksk&resourceCode=oauth2-token"
                        + "&dimension=CUSTOMER&selector=EXACT&namespace=customer&customType=&objectId=cust-001"
                        + "&page=2&size=100"))
                .andRespond(withSuccess(
                        "{\"items\":[" + ruleJson(201L, 5L, 100, true) + "],\"page\":2,\"size\":100,\"total\":101}",
                        MediaType.APPLICATION_JSON));
        PolicyRuleView view = client.findByKey(command(true, limits(100)));
        assertEquals(201L, view.getRuleId());
        server.verify();
    }

    @Test
    void findByKeySurvivesServerSidePageSizeClamp() {
        // 宿主配置 page.max-size=50 钳制请求：服务端每页只回 50 条（size 回传 50）——
        // 终止判定必须按回传页大小而非请求值，否则首页 50<100 误判"无下一页"漏检
        StringBuilder first = new StringBuilder();
        for (long i = 1; i <= 50; i++) {
            if (i > 1) {
                first.append(",");
            }
            first.append(ruleJson(i, 0L, 10, true)
                    .replace("\"objectId\":\"cust-001\"", "\"objectId\":\"filler-" + i + "\""));
        }
        server.expect(requestTo(BASE + "/v2/policy/rules?serviceCode=aksk&resourceCode=oauth2-token"
                        + "&dimension=CUSTOMER&selector=EXACT&namespace=customer&customType=&objectId=cust-001"
                        + "&page=1&size=100"))
                .andRespond(withSuccess("{\"items\":[" + first + "],\"page\":1,\"size\":50,\"total\":51}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/v2/policy/rules?serviceCode=aksk&resourceCode=oauth2-token"
                        + "&dimension=CUSTOMER&selector=EXACT&namespace=customer&customType=&objectId=cust-001"
                        + "&page=2&size=100"))
                .andRespond(withSuccess(
                        "{\"items\":[" + ruleJson(99L, 5L, 100, true) + "],\"page\":2,\"size\":50,\"total\":51}",
                        MediaType.APPLICATION_JSON));
        PolicyRuleView view = client.findByKey(command(true, limits(100)));
        assertEquals(99L, view.getRuleId());
        server.verify();
    }

    @Test
    void missingBaseUrlFailsFast() {
        SmartRedisLimiterPolicyMutationClientProperties properties =
                new SmartRedisLimiterPolicyMutationClientProperties();
        RestTemplateSmartRedisLimiterPolicyMutationClient bare =
                new RestTemplateSmartRedisLimiterPolicyMutationClient(
                        properties, new JacksonPolicyMutationJsonCodec(), new RestTemplate());
        assertThrows(SmartRedisLimiterException.class, () -> bare.findByKey(command(true, limits(100))));
    }
}
