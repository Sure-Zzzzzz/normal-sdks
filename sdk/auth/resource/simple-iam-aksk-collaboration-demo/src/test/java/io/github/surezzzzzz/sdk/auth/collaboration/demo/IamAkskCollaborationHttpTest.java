package io.github.surezzzzzz.sdk.auth.collaboration.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * IAM 与 AKSK 协作外部 HTTP 验收。
 *
 * <p>只读取已启动服务的地址与验收身份凭据，不构建、不启动、不清理任何服务。
 * 环境变量缺失或服务不可达即失败——不做任何静默跳过。
 * 只能通过 {@code collaborationAcceptance} 任务运行；日常 {@code test} 任务不含本类。</p>
 *
 * <p>覆盖说明：AKSK 服务身份链路为完整真实断言（client_credentials 换真 Token、
 * kid 路由、真实 introspection、API + DATA 双权限、真实结果集与越权边界）。
 * IAM 人员身份链路为 PKCE 授权码全流程真实断言（login session → authorize →
 * code → token，真 JWE token 经 kid 路由进 IAM verify 在线回调）。</p>
 *
 * <p>验收身份通过 AKSK Admin 接口创建（见
 * {@code sdk/auth/aksk/client/simple-aksk-client-core/LOCAL_TEST_COMMANDS.md}）：
 * 受限身份只授予 order 的 t1∧d1 DATA grant；种子身份授予 order 全量 grant，
 * 负责 setup/teardown 的业务数据，避免验收直接触碰业务库。</p>
 *
 * @author surezzzzzz
 */
@Tag("collaboration")
class IamAkskCollaborationHttpTest {

    private static final String IAM_BASE_URL = requireEnv("IAM_BASE_URL");
    /** 可选的 IAM 第二实例：IAM 以分布式多实例部署时显式提供，验收探测其作为协作终端的可达性 */
    private static final String IAM_SECONDARY_BASE_URL = optionalEnv("IAM_SECONDARY_BASE_URL");
    private static final String AKSK_BASE_URL = requireEnv("AKSK_BASE_URL");
    private static final String RESOURCE_BASE_URL = requireEnv("RESOURCE_BASE_URL");
    private static final String RESTRICTED_CLIENT_ID = requireEnv("AKSK_RESTRICTED_CLIENT_ID");
    private static final String RESTRICTED_CLIENT_SECRET = requireEnv("AKSK_RESTRICTED_CLIENT_SECRET");
    private static final String SEED_CLIENT_ID = requireEnv("AKSK_SEED_CLIENT_ID");
    private static final String SEED_CLIENT_SECRET = requireEnv("AKSK_SEED_CLIENT_SECRET");
    private static final String IAM_LOGIN_USERNAME = requireEnv("IAM_LOGIN_USERNAME");
    private static final String IAM_LOGIN_PASSWORD = requireEnv("IAM_LOGIN_PASSWORD");
    /** PKCE 公共客户端（clientType=PUBLIC，authenticationMethods=none），redirect_uri 与注册值一致，仅用于接收 302 回调，无需真实可达 */
    private static final String IAM_PKCE_CLIENT_ID = requireEnv("IAM_PKCE_CLIENT_ID");
    private static final String IAM_PKCE_REDIRECT_URI = "https://resource.example.test/callback";

    private static final String ORDER_PREFIX = "collab-e2e-";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final List<Long> createdOrderIds = new ArrayList<Long>();

    @BeforeEach
    void seedOrdersWithFullGrantIdentity() throws Exception {
        cleanupSeedOrders();
        createOrder("t1", "d1");
        createOrder("t1", "d2");
        createOrder("t2", "d1");
        createOrder("t2", "d2");
    }

    @AfterEach
    void cleanupSeedOrders() throws Exception {
        String token = seedToken();
        JsonNode orders = readJson(get(RESOURCE_BASE_URL + "/api/orders", bearer(token)));
        for (JsonNode order : orders) {
            String orderNo = order.get("orderNo").asText();
            Long id = order.get("id").asLong();
            if (orderNo.startsWith(ORDER_PREFIX)) {
                int status = execute(new HttpDelete(RESOURCE_BASE_URL + "/api/orders/" + id), bearer(token));
                assertThat(status).as("种子身份清理订单 %s", orderNo).isEqualTo(200);
            }
        }
        createdOrderIds.clear();
    }

    @Test
    void shouldReachAllThreeIndependentlyStartedServices() throws Exception {
        assertThat(execute(new HttpGet(AKSK_BASE_URL + "/oauth2/jwks"))).isGreaterThanOrEqualTo(200);
        assertThat(get(RESOURCE_BASE_URL + "/public/health")).isEqualTo("ok");
        assertThat(execute(new HttpGet(IAM_BASE_URL))).isGreaterThanOrEqualTo(200);
        if (IAM_SECONDARY_BASE_URL != null) {
            assertThat(execute(new HttpGet(IAM_SECONDARY_BASE_URL)))
                    .as("IAM 分布式第二实例 %s 应与主实例同为可达协作终端", IAM_SECONDARY_BASE_URL)
                    .isGreaterThanOrEqualTo(200);
        }
    }

    @Test
    void restrictedIdentitySeesOnlyGrantedRows() throws Exception {
        String token = restrictedToken();
        JsonNode orders = readJson(get(RESOURCE_BASE_URL + "/api/orders", bearer(token)));

        List<String> orderNos = new ArrayList<String>();
        orders.forEach(order -> orderNos.add(order.get("orderNo").asText()));
        assertThat(orderNos).as("t1∧d1 grant 只能看见一条种子数据").containsExactly(ORDER_PREFIX + "t1-d1");
    }

    @Test
    void restrictedIdentityCannotCrossDataBoundary() throws Exception {
        String token = restrictedToken();
        JsonNode seedOrders = readJson(get(RESOURCE_BASE_URL + "/api/orders", bearer(seedToken())));
        Long outsideId = null;
        for (JsonNode order : seedOrders) {
            if ((ORDER_PREFIX + "t2-d1").equals(order.get("orderNo").asText())) {
                outsideId = order.get("id").asLong();
            }
        }
        assertThat(outsideId).as("种子数据必须包含 t2-d1 用于越界断言").isNotNull();

        int detail = execute(new HttpGet(RESOURCE_BASE_URL + "/api/orders/" + outsideId), bearer(token));
        assertThat(detail).as("越界 detail 一律 404，不泄露存在性").isEqualTo(404);

        int delete = execute(new HttpDelete(RESOURCE_BASE_URL + "/api/orders/" + outsideId), bearer(token));
        assertThat(delete).as("越界 delete 一律 404").isEqualTo(404);

        int create = execute(jsonPost(RESOURCE_BASE_URL + "/api/orders",
                "{\"tenantId\":\"t2\",\"departmentId\":\"d1\",\"orderNo\":\""
                        + ORDER_PREFIX + "out\",\"amount\":10.00}"), bearer(token));
        assertThat(create).as("越界 create 必须被拒绝").isEqualTo(403);
    }

    @Test
    void invalidCredentialsAreRejectedWithoutFallback() throws Exception {
        assertThat(execute(new HttpGet(RESOURCE_BASE_URL + "/api/orders"))).isEqualTo(401);
        assertThat(execute(new HttpGet(RESOURCE_BASE_URL + "/api/orders"), "Bearer not-a-token")).isEqualTo(401);

        String iamKidHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"kid\":\"iam/forged\",\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        assertThat(execute(new HttpGet(RESOURCE_BASE_URL + "/api/orders"),
                "Bearer " + iamKidHeader + ".e30.")).isEqualTo(401);

        String akskKidHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"kid\":\"aksk/forged\",\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        assertThat(execute(new HttpGet(RESOURCE_BASE_URL + "/api/orders"),
                "Bearer " + akskKidHeader + ".e30.")).isEqualTo(401);
    }

    private String restrictedToken() throws Exception {
        return fetchToken(RESTRICTED_CLIENT_ID, RESTRICTED_CLIENT_SECRET);
    }

    /**
     * IAM 人员身份走 PKCE 授权码换真 Token，与 AKSK 服务身份调用同一业务接口。
     *
     * <p>IAM Server verify 端点把应用授权投影（apiPermissions + dataGrantDocument）原样
     * 下发进 iam_authorization；本用例验证投影里的 t1∧d1 grant 在 DATA 层真实收口。</p>
     */
    @Test
    void iamLoginTokenCallsOrderApi() throws Exception {
        String token = iamLoginToken();

        JsonNode orders = readJson(get(RESOURCE_BASE_URL + "/api/orders", bearer(token)));
        List<String> orderNos = new ArrayList<String>();
        orders.forEach(order -> orderNos.add(order.get("orderNo").asText()));
        assertThat(orderNos).as("IAM 登录 Token 按投影授权（t1∧d1）只能看见一条种子数据")
                .containsExactly(ORDER_PREFIX + "t1-d1");
    }

    /**
     * IAM 投影授权的 DATA 边界反证：越界 detail/delete 一律 404、越界 create 403。
     */
    @Test
    void iamProjectionGrantCannotCrossDataBoundary() throws Exception {
        String token = iamLoginToken();

        JsonNode seedOrders = readJson(get(RESOURCE_BASE_URL + "/api/orders", bearer(seedToken())));
        Long outsideId = null;
        for (JsonNode order : seedOrders) {
            if ((ORDER_PREFIX + "t2-d1").equals(order.get("orderNo").asText())) {
                outsideId = order.get("id").asLong();
            }
        }
        assertThat(outsideId).as("种子数据必须包含 t2-d1 用于 IAM 越界断言").isNotNull();

        int detail = execute(new HttpGet(RESOURCE_BASE_URL + "/api/orders/" + outsideId), bearer(token));
        assertThat(detail).as("IAM 投影越界 detail 一律 404，不泄露存在性").isEqualTo(404);

        int delete = execute(new HttpDelete(RESOURCE_BASE_URL + "/api/orders/" + outsideId), bearer(token));
        assertThat(delete).as("IAM 投影越界 delete 一律 404").isEqualTo(404);

        int create = execute(jsonPost(RESOURCE_BASE_URL + "/api/orders",
                "{\"tenantId\":\"t2\",\"departmentId\":\"d1\",\"orderNo\":\""
                        + ORDER_PREFIX + "iam-out\",\"amount\":10.00}"), bearer(token));
        assertThat(create).as("IAM 投影越界 create 必须被拒绝").isEqualTo(403);
    }

    /**
     * PKCE 全流程：csrf → login（建立 session）→ authorize（S256）→ 提取 code → token 交换。
     *
     * <p>csrf/login/authorize 必须共用同一 cookie store（同一 JSESSIONID session）；
     * redirect_uri 只出现在 Location 头，不发起真实回调，故无需可达。</p>
     */
    private String iamLoginToken() throws Exception {
        String codeVerifier = randomCodeVerifier();
        String codeChallenge = base64UrlSha256(codeVerifier);

        try (CloseableHttpClient client = HttpClients.custom()
                .setDefaultCookieStore(new BasicCookieStore())
                .disableRedirectHandling()
                .build()) {
            JsonNode csrf = readJson(get(client, IAM_BASE_URL + "/iam/web/auth/csrf"));
            HttpPost login = jsonPost(IAM_BASE_URL + "/iam/web/auth/login",
                    "{\"username\":\"" + IAM_LOGIN_USERNAME + "\",\"password\":\"" + IAM_LOGIN_PASSWORD + "\"}");
            login.addHeader(csrf.get("headerName").asText(), csrf.get("token").asText());
            Response loginResponse = perform(client, login);
            assertThat(loginResponse.status).as("IAM login 必须成功，响应=%s", loginResponse.body).isEqualTo(200);

            HttpGet authorize = new HttpGet(IAM_BASE_URL + "/oauth2/authorize?response_type=code"
                    + "&client_id=" + IAM_PKCE_CLIENT_ID
                    + "&redirect_uri=" + URLEncoder.encode(IAM_PKCE_REDIRECT_URI, "UTF-8")
                    + "&scope=profile"
                    + "&code_challenge=" + codeChallenge
                    + "&code_challenge_method=S256");
            String code;
            try (CloseableHttpResponse response = client.execute(authorize)) {
                assertThat(response.getStatusLine().getStatusCode())
                        .as("已登录 session 的 authorize 应 302 回调").isEqualTo(302);
                code = queryParam(response.getFirstHeader("Location").getValue(), "code");
                EntityUtils.consume(response.getEntity());
            }
            assertThat(code).as("authorize 回调必须携带授权码").isNotEmpty();

            HttpPost tokenRequest = new HttpPost(IAM_BASE_URL + "/oauth2/token");
            List<org.apache.http.NameValuePair> form = new ArrayList<org.apache.http.NameValuePair>();
            form.add(new BasicNameValuePair("grant_type", "authorization_code"));
            form.add(new BasicNameValuePair("client_id", IAM_PKCE_CLIENT_ID));
            form.add(new BasicNameValuePair("code", code));
            form.add(new BasicNameValuePair("redirect_uri", IAM_PKCE_REDIRECT_URI));
            form.add(new BasicNameValuePair("code_verifier", codeVerifier));
            tokenRequest.setEntity(new UrlEncodedFormEntity(form, StandardCharsets.UTF_8));
            JsonNode tokenResponse = readJson(perform(client, tokenRequest).body);
            assertThat(tokenResponse.hasNonNull("access_token")).as("PKCE 授权码必须换出真 Token：%s", tokenResponse).isTrue();
            return tokenResponse.get("access_token").asText();
        }
    }

    private static String randomCodeVerifier() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String base64UrlSha256(String value) throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)));
    }

    private static String queryParam(String uri, String name) {
        for (String pair : uri.substring(uri.indexOf('?') + 1).split("&")) {
            int equalsIndex = pair.indexOf('=');
            if (equalsIndex > 0 && name.equals(pair.substring(0, equalsIndex))) {
                return pair.substring(equalsIndex + 1);
            }
        }
        return null;
    }

    private String seedToken() throws Exception {
        return fetchToken(SEED_CLIENT_ID, SEED_CLIENT_SECRET);
    }

    private String fetchToken(String clientId, String clientSecret) throws Exception {
        HttpPost request = new HttpPost(AKSK_BASE_URL + "/oauth2/token");
        request.setHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8)));
        List<org.apache.http.NameValuePair> form = new ArrayList<org.apache.http.NameValuePair>();
        form.add(new BasicNameValuePair("grant_type", "client_credentials"));
        request.setEntity(new UrlEncodedFormEntity(form, StandardCharsets.UTF_8));
        JsonNode response = readJson(get(request));
        assertThat(response.hasNonNull("access_token")).as("client_credentials 必须签发真 Token").isTrue();
        return response.get("access_token").asText();
    }

    private void createOrder(String tenantId, String departmentId) throws Exception {
        String token = seedToken();
        HttpPost request = jsonPost(RESOURCE_BASE_URL + "/api/orders",
                "{\"tenantId\":\"" + tenantId + "\",\"departmentId\":\"" + departmentId
                        + "\",\"orderNo\":\"" + ORDER_PREFIX + tenantId + "-" + departmentId
                        + "\",\"amount\":10.00}");
        request.setHeader("Authorization", bearer(token));
        Response response = perform(request);
        assertThat(response.status).as("种子身份必须能写入 %s/%s，响应=%s", tenantId, departmentId, response.body).isEqualTo(200);
    }

    private static HttpPost jsonPost(String url, String body) {
        HttpPost request = new HttpPost(url);
        request.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
        return request;
    }

    private static final class Response {
        final int status;
        final String body;

        Response(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    private static Response perform(HttpRequestBase request) throws Exception {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            return perform(client, request);
        }
    }

    private static Response perform(CloseableHttpClient client, HttpRequestBase request) throws Exception {
        try (CloseableHttpResponse response = client.execute(request)) {
            return new Response(response.getStatusLine().getStatusCode(),
                    EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
        }
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            fail("协作验收需要环境变量 " + name + " 指向已独立启动的服务或已签发身份；"
                    + "三终端未启动或身份未按 Admin 流程创建时不得运行本验收");
        }
        return value;
    }

    private static String optionalEnv(String name) {
        String value = System.getenv(name);
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static String get(String url) throws Exception {
        return get(new HttpGet(url));
    }

    private static String get(String url, String authorization) throws Exception {
        HttpGet request = new HttpGet(url);
        if (authorization != null) {
            request.setHeader("Authorization", authorization);
        }
        return get(request);
    }

    private static String get(HttpRequestBase request) throws Exception {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            return get(client, request);
        }
    }

    private static String get(CloseableHttpClient client, HttpRequestBase request) throws Exception {
        try (CloseableHttpResponse response = client.execute(request)) {
            return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        }
    }

    private static String get(CloseableHttpClient client, String url) throws Exception {
        return get(client, new HttpGet(url));
    }

    private static int execute(HttpRequestBase request) throws Exception {
        return execute(request, null);
    }

    private static int execute(HttpRequestBase request, String authorization) throws Exception {
        if (authorization != null) {
            request.setHeader("Authorization", authorization);
        }
        if (request instanceof HttpEntityEnclosingRequestBase
                && request.getFirstHeader("Content-Type") == null) {
            request.setHeader("Content-Type", ContentType.APPLICATION_JSON.toString());
        }
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            try (CloseableHttpResponse response = client.execute(request)) {
                EntityUtils.consume(response.getEntity());
                return response.getStatusLine().getStatusCode();
            }
        }
    }

    private static JsonNode readJson(String body) throws Exception {
        return OBJECT_MAPPER.readTree(body);
    }
}
