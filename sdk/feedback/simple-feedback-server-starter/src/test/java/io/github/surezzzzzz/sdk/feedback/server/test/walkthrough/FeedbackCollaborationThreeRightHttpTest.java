package io.github.surezzzzzz.sdk.feedback.server.test.walkthrough;

import com.sun.net.httpserver.HttpServer;
import io.github.surezzzzzz.sdk.feedback.server.model.SubmissionContactHint;
import io.github.surezzzzzz.sdk.feedback.server.spi.SubmitterProfileProvider;
import io.github.surezzzzzz.sdk.feedback.server.test.SimpleFeedbackServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 协作链 HTTP 三权测试（collaboration tag：日常 test 恒排除，本地 init 单独执行）。
 *
 * <p>形态：RANDOM_PORT 真实 HTTP × 真实公共资源链（resource-server+iam-resource）×
 * 受控 verify 桩（JDK HttpServer 按 token 返回三种投影——钉的是 IAM verify 响应 wire 契约，
 * KMS walkthrough 同型分工）。三账号三权矩阵：全权/受限（DATA IN）/无码。</p>
 *
 * <p>非直观约束：verify 桩端口经 @DynamicPropertySource 注入（端口动态，属性源而非 profile）；
 * schema 重置由本类 @BeforeAll 作为唯一受控入口执行一次。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@Tag("collaboration")
// classes 显式引用时内嵌 @TestConfiguration 不自动检测（坑7），桩配置须显式列出
@SpringBootTest(classes = {SimpleFeedbackServerTestApplication.class,
        FeedbackCollaborationThreeRightHttpTest.SubmitterProfileStubConfiguration.class},
        properties = "io.github.surezzzzzz.sdk.cache.enabled=false",
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FeedbackCollaborationThreeRightHttpTest {

    // 桩令牌为 JWT 形态：header 带 kid（iam/xxx）——公共层凭据解析器只认 kid 前缀来源标识，
    // 纯字符串 token 解析不出来源会直接 401；payload 段承载桩身份标识，不参与验签
    private static final String TOKEN_FULL = jwtStub("collab-token-full");
    private static final String TOKEN_SCOPED = jwtStub("collab-token-scoped");
    private static final String TOKEN_NOCODES = jwtStub("collab-token-nocodes");
    private static HttpServer verifyStub;
    @LocalServerPort
    private int port;
    @Autowired
    private org.springframework.boot.test.web.client.TestRestTemplate rest;

    private static String jwtStub(String identity) {
        String header = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"kid\":\"iam/stub-key-1\"}".getBytes(StandardCharsets.UTF_8));
        return header + "." + identity + ".stub-signature";
    }

    @DynamicPropertySource
    static void stubProperties(DynamicPropertyRegistry registry) throws IOException {
        verifyStub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        verifyStub.createContext("/iam/resource/tokens/verify", exchange -> {
            String body = readBody(exchange);
            String token = extractToken(body);
            String projection = projectionFor(token);
            log.debug("verify 桩完成响应 status={}", projection.isEmpty() ? 401 : 200);
            byte[] out = projection.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            if (projection.isEmpty()) {
                exchange.sendResponseHeaders(401, -1);
            } else {
                exchange.sendResponseHeaders(200, out.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(out);
                }
            }
        });
        verifyStub.start();
        int stubPort = verifyStub.getAddress().getPort();
        registry.add("io.github.surezzzzzz.sdk.auth.iam.resource.server.verification-endpoint",
                () -> "http://127.0.0.1:" + stubPort + "/iam/resource/tokens/verify");
        registry.add("io.github.surezzzzzz.sdk.auth.iam.resource.server.client-id", () -> "stub-verify");
        registry.add("io.github.surezzzzzz.sdk.auth.iam.resource.server.client-secret", () -> "fixture-value");
        registry.add("io.github.surezzzzzz.sdk.auth.resource.server.security.protected-paths[0]",
                () -> "/api/feedback/**");
    }

    @BeforeAll
    static void resetSchema(@Autowired DataSource dataSource) {
        io.github.surezzzzzz.sdk.feedback.server.test.support.FeedbackTestSchemaHelper.reset(dataSource);
    }

    @AfterAll
    static void stopStub() {
        if (verifyStub != null) {
            verifyStub.stop(0);
        }
    }

    private static String readBody(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        java.io.InputStream input = exchange.getRequestBody();
        int read = input.read(buffer);
        while (read != -1) {
            out.write(buffer, 0, read);
            read = input.read(buffer);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String extractToken(String body) {
        int idx = body.indexOf("\"token\"");
        if (idx < 0) {
            return "";
        }
        int start = body.indexOf('"', idx + 8) + 1;
        int end = body.indexOf('"', start);
        String jwt = body.substring(start, end);
        // JWT 形态桩令牌：取 payload 段作为身份标识（header 段承载 kid）
        String[] segments = jwt.split("\\.");
        return segments.length == 3 ? segments[1] : jwt;
    }

    /**
     * 三账号投影（wire 形态照 IAM verify 真实响应结构：两键 sub+iam_authorization）。
     *
     * @param token 桩令牌
     * @return 投影 JSON；未知 token 返回空串（桩回 401=token 无效）
     */
    private static String projectionFor(String token) {
        String grantsTemplate = "{\"protocol\":\"simple-data-permission\",\"version\":\"1.0\",\"grants\":[%s]}";
        String allGrants = String.format(grantsTemplate,
                "{\"resource\":\"feedback:submission\",\"actions\":[\"read\",\"manage\"],\"all\":true,"
                        + "\"constraints\":[]},{\"resource\":\"feedback:application\","
                        + "\"actions\":[\"read\",\"manage\"],\"all\":true,\"constraints\":[]}");
        String scopedGrants = String.format(grantsTemplate,
                "{\"resource\":\"feedback:submission\",\"actions\":[\"read\",\"manage\"],\"all\":false,"
                        + "\"constraints\":[{\"dimension\":\"application\",\"operator\":\"IN\",\"values\":[\"app-a\"]}]},"
                        + "{\"resource\":\"feedback:application\",\"actions\":[\"read\",\"manage\"],"
                        + "\"all\":false,\"constraints\":[{\"dimension\":\"application\",\"operator\":\"IN\",\"values\":[\"app-a\"]}]}");
        String noGrants = "null";
        String codes = "\"feedback.submission.create\",\"feedback.submission.read\","
                + "\"feedback.submission.manage\",\"feedback.submission.mine\",\"feedback.submission.hint\","
                + "\"feedback.application.list\",\"feedback.application.read\","
                + "\"feedback.application.manage\"";
        // 投影字段照 IAM verify 真实响应逐字段对齐（缺 applicationCode/subjectId 等会被适配器映射拒绝）
        String authBody = "\"protocol\":\"simple-application-authorization\",\"version\":\"1.0\","
                + "\"subjectType\":\"HUMAN\",\"subjectId\":\"%s\",\"applicationCode\":\"simple-feedback\","
                + "\"admitted\":true,\"roles\":[],\"pagePermissions\":[],\"apiPermissions\":[%s],"
                + "\"dataGrantDocument\":%s,\"authorizationVersion\":1,\"manifestVersion\":\"1.0\","
                + "\"manifestDigest\":\"stub-digest\",\"issuedAt\":1760000000,"
                + "\"expiresAt\":1823000000";
        String identity = token == null ? "" : token;
        if (identity.equals("collab-token-full")) {
            return "{\"sub\":\"collab-full\",\"iam_authorization\":{"
                    + String.format(authBody, "collab-full", codes, allGrants) + "}}";
        }
        if (identity.equals("collab-token-scoped")) {
            return "{\"sub\":\"collab-scoped\",\"iam_authorization\":{"
                    + String.format(authBody, "collab-scoped", codes, scopedGrants) + "}}";
        }
        if (identity.equals("collab-token-nocodes")) {
            return "{\"sub\":\"collab-nocodes\",\"iam_authorization\":{"
                    + String.format(authBody, "collab-nocodes", "", noGrants) + "}}";
        }
        return "";
    }

    private ResponseEntity<String> submitMultipart(String token, String submissionJson, String idempotencyKey) {
        HttpHeaders headers = bearer(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("Idempotency-Key", idempotencyKey);
        org.springframework.util.LinkedMultiValueMap<String, Object> form =
                new org.springframework.util.LinkedMultiValueMap<>();
        form.add("submission", new org.springframework.core.io.ByteArrayResource(
                submissionJson.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "submission.json";
            }
        });
        return rest.exchange(baseUrl() + "/submissions", HttpMethod.POST,
                new HttpEntity<>(form, headers), String.class);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + port + "/api/feedback";
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private HttpEntity<String> jsonEntity(String token, String body) {
        HttpHeaders headers = bearer(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    @Test
    @DisplayName("三权矩阵：无token=401；无码=403；全权=创建/提交/处理 2xx；受限=范围外 404 且列表遮蔽")
    void testThreeRightMatrix() {
        ResponseEntity<String> anonymous = rest.getForEntity(baseUrl() + "/submissions", String.class);
        assertEquals(401, anonymous.getStatusCodeValue());
        log.info("输出: 无token=401");

        // ② 有身份无 API 码：403
        ResponseEntity<String> nocodes = rest.exchange(baseUrl() + "/submissions", HttpMethod.GET,
                new HttpEntity<>(bearer(TOKEN_NOCODES)), String.class);
        assertEquals(403, nocodes.getStatusCodeValue());
        log.info("输出: 无码=403");

        // ③ 全权：登记两个应用
        HttpHeaders postHeaders = bearer(TOKEN_FULL);
        postHeaders.setContentType(MediaType.APPLICATION_JSON);
        assertEquals(201, rest.exchange(baseUrl() + "/applications", HttpMethod.POST,
                new HttpEntity<>("{\"applicationCode\":\"app-a\",\"displayName\":\"甲\"}", postHeaders),
                String.class).getStatusCodeValue());
        assertEquals(201, rest.exchange(baseUrl() + "/applications", HttpMethod.POST,
                new HttpEntity<>("{\"applicationCode\":\"app-b\",\"displayName\":\"乙\"}", postHeaders),
                String.class).getStatusCodeValue());

        // ④ 全权：提交两条反馈（app-a 与 app-b）
        String submitBody = "{\"applicationCode\":\"%s\",\"kind\":\"proactive\",\"type\":\"bug\",\"score\":5,"
                + "\"content\":\"协作链走查-%s\"}";
        String keyA = UUID.randomUUID().toString();
        ResponseEntity<String> createdA = submitMultipart(TOKEN_FULL, String.format(submitBody, "app-a", "a"),
                keyA);
        assertEquals(201, createdA.getStatusCodeValue());
        // ④" 提交人展示名随行快照（聚形态档案桩生效；回访展示不再回退裸 subjectId）
        assertEquals("桩展示名-collab-full", io.github.surezzzzzz.sdk.feedback.server.support.JsonHelper.stringField(
                createdA.getBody(), "submitterDisplayName"), "创建响应应带提交人展示名快照");
        // ④' 幂等重放：同键同内容再发=201 且同 id（首响应快照与首成功一致）
        String idA = io.github.surezzzzzz.sdk.feedback.server.support.JsonHelper.stringField(
                createdA.getBody(), "id");
        ResponseEntity<String> replayA = submitMultipart(TOKEN_FULL, String.format(submitBody, "app-a", "a"),
                keyA);
        assertEquals(201, replayA.getStatusCodeValue());
        assertEquals(idA, io.github.surezzzzzz.sdk.feedback.server.support.JsonHelper.stringField(
                replayA.getBody(), "id"));
        assertEquals("submitted", io.github.surezzzzzz.sdk.feedback.server.support.JsonHelper.stringField(
                replayA.getBody(), "status"), "重放返回首成功快照（处理前状态）");
        log.info("输出: 全权提交 201 且同键重放同 id（快照一致）");
        // app-b 由受限身份提交（独立 owner 频控窗——同 owner 短窗第二条新键会被自家频控 429）
        assertEquals(201, submitMultipart(TOKEN_SCOPED, String.format(submitBody, "app-b", "b"),
                UUID.randomUUID().toString()).getStatusCodeValue());

        // ⑤ 受限（DATA IN app-a）：管理列表只含 app-a 行
        ResponseEntity<String> scopedList = rest.exchange(baseUrl() + "/submissions", HttpMethod.GET,
                new HttpEntity<>(bearer(TOKEN_SCOPED)), String.class);
        assertEquals(200, scopedList.getStatusCodeValue());
        assertTrue(scopedList.getBody().contains("app-a"), "受限列表应含 app-a 行");
        assertTrue(!scopedList.getBody().contains("app-b"), "受限列表不应含 app-b 行");
        assertTrue(scopedList.getBody().contains("桩展示名-collab-full"), "管理列表行应带提交人展示名快照");
        log.info("输出: 受限列表 DATA 遮蔽生效（只见 app-a）且行带提交人展示名");

        // ⑥ 受限：app-b 提交 id 详情=安全 404
        ResponseEntity<String> scopedDenied = rest.exchange(baseUrl() + "/submissions?application=app-b",
                HttpMethod.GET, new HttpEntity<>(bearer(TOKEN_SCOPED)), String.class);
        assertEquals(200, scopedDenied.getStatusCodeValue());
        assertEquals(0L, io.github.surezzzzzz.sdk.feedback.server.support.JsonHelper
                .longField(scopedDenied.getBody(), "total"), "范围外筛选应为空集");
        log.info("输出: 范围外筛选=空列表（安全遮蔽不泄露存在性）");

        // ⑦ 全权：处理 app-a 反馈（resolved）
        String handleBody = "{\"targetStatus\":\"resolved\",\"handlerNote\":\"已处理\","
                + "\"reply\":\"协作链回复\",\"expectedRowVersion\":0}";
        ResponseEntity<String> handled = rest.exchange(baseUrl() + "/submissions/" + idA + "/handling",
                HttpMethod.PUT, jsonEntity(TOKEN_FULL, handleBody), String.class);
        assertEquals(200, handled.getStatusCodeValue());
        assertTrue(handled.getBody().contains("resolved"));
        log.info("输出: 全权处理 200 resolved");

        // ⑧ 我的反馈（owner 等值）：全权身份自见
        ResponseEntity<String> mine = rest.exchange(baseUrl() + "/me/submissions", HttpMethod.GET,
                new HttpEntity<>(bearer(TOKEN_FULL)), String.class);
        assertEquals(200, mine.getStatusCodeValue());
        assertTrue(mine.getBody().contains("协作链走查-a"), "我的反馈应含本人提交内容");
        log.info("输出: 我的反馈 owner 等值可见");

        // ⑨ 受限处理范围外行=404（DATA 进 UPDATE WHERE）
        String deniedHandle = "{\"targetStatus\":\"resolved\",\"handlerNote\":\"x\","
                + "\"reply\":null,\"expectedRowVersion\":0}";
        // 先找 app-b 的 id（全权查列表）
        ResponseEntity<String> fullList = rest.exchange(baseUrl() + "/submissions?application=app-b",
                HttpMethod.GET, new HttpEntity<>(bearer(TOKEN_FULL)), String.class);
        String idB = io.github.surezzzzzz.sdk.feedback.server.support.JsonHelper.stringField(
                fullList.getBody(), "id");
        ResponseEntity<String> scopedHandle = rest.exchange(baseUrl() + "/submissions/" + idB + "/handling",
                HttpMethod.PUT, jsonEntity(TOKEN_SCOPED, deniedHandle), String.class);
        assertEquals(404, scopedHandle.getStatusCodeValue());
        log.info("输出: 受限处理范围外行=404（写路径 DATA 进 SQL）");

        // ⑩ hint：全员端点 200（交集降级为注册表全量）
        ResponseEntity<String> hint = rest.exchange(baseUrl() + "/submission-hints", HttpMethod.GET,
                new HttpEntity<>(bearer(TOKEN_NOCODES)), String.class);
        assertEquals(403, hint.getStatusCodeValue());
        ResponseEntity<String> hintFull = rest.exchange(baseUrl() + "/submission-hints", HttpMethod.GET,
                new HttpEntity<>(bearer(TOKEN_FULL)), String.class);
        assertEquals(200, hintFull.getStatusCodeValue());
        assertTrue(hintFull.getBody().contains("app-a") && hintFull.getBody().contains("app-b"));
        log.info("输出: hint 需码（无码 403），有码 200 返回注册表全量（散形态降级）");
    }

    @Test
    @DisplayName("注册表来源：IAM 行徽标+登记抄录+同源锁+重同步；本表行全可编辑")
    void testRegistrySourceLock() {
        // ① 同源行：手滑名被 IAM 官方名抄录纠正，source=IAM
        ResponseEntity<String> createdIam = rest.exchange(baseUrl() + "/applications", HttpMethod.POST,
                jsonEntity(TOKEN_FULL, "{\"applicationCode\":\"src-iam\",\"displayName\":\"手滑名\",\"description\":\"d\"}"),
                String.class);
        assertEquals(201, createdIam.getStatusCodeValue());
        assertEquals("IAM官方名-同源", io.github.surezzzzzz.sdk.feedback.server.support.JsonHelper.stringField(
                createdIam.getBody(), "displayName"), "同源行登记应自动抄录 IAM 官方名");
        assertEquals("IAM", io.github.surezzzzzz.sdk.feedback.server.support.JsonHelper.stringField(
                createdIam.getBody(), "source"));
        // ② 本表行：提交值原样落库，source=MANUAL
        ResponseEntity<String> createdManual = rest.exchange(baseUrl() + "/applications", HttpMethod.POST,
                jsonEntity(TOKEN_FULL, "{\"applicationCode\":\"src-manual\",\"displayName\":\"本表名\",\"description\":\"d\"}"),
                String.class);
        assertEquals(201, createdManual.getStatusCodeValue());
        assertEquals("本表名", io.github.surezzzzzz.sdk.feedback.server.support.JsonHelper.stringField(
                createdManual.getBody(), "displayName"));
        assertEquals("MANUAL", io.github.surezzzzzz.sdk.feedback.server.support.JsonHelper.stringField(
                createdManual.getBody(), "source"));
        log.info("输出: 同源行登记抄录官方名+source=IAM；本表行 source=MANUAL");
        // ③ 列表徽标：src-iam=IAM / src-manual=MANUAL
        ResponseEntity<String> list = rest.exchange(baseUrl() + "/applications", HttpMethod.GET,
                new HttpEntity<>(bearer(TOKEN_FULL)), String.class);
        assertEquals(200, list.getStatusCodeValue());
        assertTrue(list.getBody().contains("\"source\":\"IAM\"") && list.getBody().contains("\"source\":\"MANUAL\""),
                "列表应同时携带 IAM 与 MANUAL 来源徽标");
        // ④ 同源锁：改 src-iam 展示名=409 SOURCE_001
        ResponseEntity<String> locked = rest.exchange(baseUrl() + "/applications/src-iam", HttpMethod.PUT,
                jsonEntity(TOKEN_FULL, "{\"displayName\":\"想改名\",\"expectedRowVersion\":0}"), String.class);
        assertEquals(409, locked.getStatusCodeValue());
        assertTrue(locked.getBody().contains("以 IAM 为准不可改写"),
                "拒绝响应应携带同源锁提示文案（错误契约=HTTP 状态+message，无错误码字段）");
        // ⑤ 受理门不受锁：停用 src-iam=200；重同步=200 且为官方名
        ResponseEntity<String> toggle = rest.exchange(baseUrl() + "/applications/src-iam", HttpMethod.PUT,
                jsonEntity(TOKEN_FULL, "{\"enabled\":false,\"expectedRowVersion\":0}"), String.class);
        assertEquals(200, toggle.getStatusCodeValue());
        assertEquals(false, io.github.surezzzzzz.sdk.feedback.server.support.JsonHelper.booleanField(
                toggle.getBody(), "enabled"), "受理门（启用/停用）不受同源锁约束");
        ResponseEntity<String> sync = rest.exchange(baseUrl() + "/applications/src-iam", HttpMethod.PUT,
                jsonEntity(TOKEN_FULL, "{\"syncFromIam\":true,\"expectedRowVersion\":1}"), String.class);
        assertEquals(200, sync.getStatusCodeValue());
        assertEquals("IAM官方名-同源", io.github.surezzzzzz.sdk.feedback.server.support.JsonHelper.stringField(
                sync.getBody(), "displayName"), "显式重同步应抄回 IAM 官方名");
        // ⑥ 本表行全可编辑：改名=200
        ResponseEntity<String> rename = rest.exchange(baseUrl() + "/applications/src-manual", HttpMethod.PUT,
                jsonEntity(TOKEN_FULL, "{\"displayName\":\"本表新名\",\"expectedRowVersion\":0}"), String.class);
        assertEquals(200, rename.getStatusCodeValue());
        assertEquals("本表新名", io.github.surezzzzzz.sdk.feedback.server.support.JsonHelper.stringField(
                rename.getBody(), "displayName"));
        log.info("输出: 同源锁 409/受理门 200/重同步官方名/本表行改名 200");
    }

    /**
     * 可控档案桩（@Primary 压过 starter 降级实现）：提交链应在落库时快照
     * 提交人展示名（聚形态回访展示不再回退裸 subjectId）。
     */
    @TestConfiguration
    static class SubmitterProfileStubConfiguration {

        @Bean
        @Primary
        public SubmitterProfileProvider stubProfileProvider() {
            return subjectId -> new SubmissionContactHint(null, "桩展示名-" + subjectId);
        }

        /**
         * 目录桩：accessibleApplications 恒 null（hint 走散形态降级全量，与主矩阵既有
         * 断言一致）；catalogApplications 只含专用编码 src-iam（官方名与提交名故意不同，
         * 钉「登记自动抄录 + 同源锁 + 重同步」三断言），不污染主矩阵的 app-a/app-b。
         */
        @Bean
        @Primary
        public io.github.surezzzzzz.sdk.feedback.server.spi.AccessibleApplicationProvider stubAccessibleProvider() {
            return new io.github.surezzzzzz.sdk.feedback.server.spi.AccessibleApplicationProvider() {
                @Override
                public java.util.List<io.github.surezzzzzz.sdk.feedback.server.model.AccessibleApplication> accessibleApplications(
                        String subjectId) {
                    return null;
                }

                @Override
                public java.util.List<io.github.surezzzzzz.sdk.feedback.server.model.AccessibleApplication> catalogApplications() {
                    return java.util.Collections.singletonList(
                            io.github.surezzzzzz.sdk.feedback.server.model.AccessibleApplication.builder()
                                    .applicationCode("src-iam")
                                    .displayName("IAM官方名-同源")
                                    .build());
                }
            };
        }
    }
}
