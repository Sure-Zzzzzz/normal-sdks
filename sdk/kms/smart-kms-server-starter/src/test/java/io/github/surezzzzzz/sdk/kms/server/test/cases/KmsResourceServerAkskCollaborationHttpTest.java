package io.github.surezzzzzz.sdk.kms.server.test.cases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * KMS 组合式认证（AKSK 单认证链）外部 HTTP 验收。
 *
 * <p>只读取已启动服务的地址与验收身份凭据，不构建、不启动、不清理任何服务。
 * 环境变量缺失或服务不可达即失败——不做任何静默跳过。只能通过
 * {@code collaborationAcceptance} 任务运行；日常 {@code test} 任务不含本类。</p>
 *
 * <p>验收前提（见 LOCAL_TEST_COMMANDS.md）：AKSK Server 已独立启动，且已为 SERVICE 客户端
 * 预置 KMS 应用授权——apiPermissions 含 KMS scope 码、dataGrantDocument 含唯一的
 * ownerPrincipalId 维度 IN 约束；KMS 协作应用经 {@code collaborationKmsApp} 任务启动。</p>
 *
 * @author surezzzzzz
 */
@Tag("collaboration")
class KmsResourceServerAkskCollaborationHttpTest {

    private static final String AKSK_BASE_URL = requireEnv("AKSK_BASE_URL");
    private static final String KMS_BASE_URL = requireEnv("KMS_BASE_URL");
    private static final String AKSK_CLIENT_ID = requireEnv("AKSK_CLIENT_ID");
    private static final String AKSK_CLIENT_SECRET = requireEnv("AKSK_CLIENT_SECRET");
    private static final String AKSK_INTROSPECT_CLIENT_ID = requireEnv("AKSK_INTROSPECT_CLIENT_ID");
    private static final String AKSK_INTROSPECT_CLIENT_SECRET = requireEnv("AKSK_INTROSPECT_CLIENT_SECRET");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CREATE_KEY_BODY =
            "{\"keyAlias\":\"collab-bridge-key\",\"purpose\":\"SIGN\",\"algorithm\":\"ES256\"}";

    private static Response performResponse(HttpRequestBase request) throws Exception {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            try (CloseableHttpResponse response = client.execute(request)) {
                return new Response(response.getStatusLine().getStatusCode(),
                        EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
            }
        }
    }

    private static String perform(HttpRequestBase request) throws Exception {
        Response response = performResponse(request);
        assertThat(response.status).as("HTTP 调用失败，响应=" + response.body)
                .isGreaterThanOrEqualTo(200).isLessThan(300);
        return response.body;
    }

    private static int execute(HttpRequestBase request) throws Exception {
        return performResponse(request).status;
    }

    private static HttpPost jsonPost(String url, String body) {
        HttpPost request = new HttpPost(url);
        request.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
        return request;
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String basic(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString(
                (username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private static JsonNode readJson(String body) throws Exception {
        return OBJECT_MAPPER.readTree(body);
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            fail("协作验收需要环境变量 " + name + " 指向已独立启动的服务或已签发身份；"
                    + "终端未启动或授权未预置时不得运行本验收");
        }
        return value;
    }

    /**
     * 验证两个独立启动服务可达：AKSK JWKS 与 KMS 无认证 401 质询。
     */
    @Test
    void shouldReachBothIndependentlyStartedServices() throws Exception {
        assertThat(execute(new HttpGet(AKSK_BASE_URL + "/oauth2/jwks")))
                .as("AKSK Server 必须已独立启动").isGreaterThanOrEqualTo(200);
        assertThat(execute(jsonPost(KMS_BASE_URL + "/api/kms/keys", CREATE_KEY_BODY)))
                .as("KMS 协作应用必须已独立启动并以 401 质询未认证请求").isEqualTo(401);
    }

    /**
     * 验证 AKSK Bearer 经公共层内省与桥翻译贯通 KMS 全链：内省快照含 ownerPrincipalId DATA 授权
     * 与 KMS scope 码，建钥、合成主体精确 policy、签名、验签闭环。
     */
    @Test
    void shouldCompleteKmsChainThroughAkskBearer() throws Exception {
        String token = fetchToken();
        JsonNode authorization = introspect(token);
        String subjectId = authorization.get("subjectId").asText();
        String composedPrincipalId = "aksk:" + subjectId;
        String ownerPrincipalId = extractOwnerPrincipalId(authorization);
        List<String> apiPermissions = new ArrayList<String>();
        authorization.get("apiPermissions").forEach(permission -> apiPermissions.add(permission.asText()));
        assertThat(apiPermissions).as("AKSK 应用授权必须直接授予 KMS scope 码（无中间映射）")
                .contains("kms.key.manage", "kms.key.policy", "kms.sign", "kms.verify");
        assertThat(ownerPrincipalId).as("AKSK 应用授权必须随快照下发唯一 ownerPrincipalId 维度").isNotEmpty();

        String suffix = String.valueOf(System.currentTimeMillis());
        String keyRef = createKey(token, suffix);
        createExactPolicy(token, keyRef, "SIGN", composedPrincipalId, suffix + "-1");
        createExactPolicy(token, keyRef, "VERIFY", composedPrincipalId, suffix + "-2");

        HttpPost signRequest = jsonPost(KMS_BASE_URL + "/api/kms/crypto/signatures",
                "{\"keyRef\":\"" + keyRef + "\",\"input\":\"aGVsbG8\"}");
        signRequest.setHeader("Authorization", bearer(token));
        JsonNode signed = readJson(perform(signRequest));
        assertThat(signed.hasNonNull("signature")).as("合成主体精确 policy 下必须完成签名").isTrue();

        HttpPost verifyRequest = jsonPost(KMS_BASE_URL + "/api/kms/crypto/verifications",
                "{\"keyRef\":\"" + keyRef + "\",\"version\":1,\"input\":\"aGVsbG8\",\"signature\":\""
                        + signed.get("signature").asText() + "\"}");
        verifyRequest.setHeader("Authorization", bearer(token));
        JsonNode verified = readJson(perform(verifyRequest));
        assertThat(verified.get("valid").asBoolean()).as("验签必须闭环").isTrue();
    }

    /**
     * 验证无效凭据 fail-closed，无回退、无存在性泄露。
     */
    @Test
    void invalidCredentialsAreRejectedWithoutFallback() throws Exception {
        assertThat(execute(jsonPost(KMS_BASE_URL + "/api/kms/keys", CREATE_KEY_BODY)))
                .as("无认证必须 401").isEqualTo(401);
        HttpPost invalid = jsonPost(KMS_BASE_URL + "/api/kms/keys", CREATE_KEY_BODY);
        invalid.setHeader("Authorization", "Bearer not-a-token");
        assertThat(execute(invalid)).as("无效 Bearer 必须 401 且不回退").isEqualTo(401);
    }

    private String fetchToken() throws Exception {
        HttpPost request = new HttpPost(AKSK_BASE_URL + "/oauth2/token");
        request.setHeader("Authorization", basic(AKSK_CLIENT_ID, AKSK_CLIENT_SECRET));
        List<org.apache.http.NameValuePair> form = new ArrayList<org.apache.http.NameValuePair>();
        form.add(new BasicNameValuePair("grant_type", "client_credentials"));
        request.setEntity(new UrlEncodedFormEntity(form, StandardCharsets.UTF_8));
        JsonNode response = readJson(perform(request));
        assertThat(response.hasNonNull("access_token")).as("client_credentials 必须签发真 Token").isTrue();
        return response.get("access_token").asText();
    }

    /**
     * 直接内省业务 Token，取回授权快照验证 ownerPrincipalId DATA 授权与 KMS scope 码确实随
     * introspection 下发（纯 API 通路可达性），并取 subjectId 合成 policy 主体。
     */
    private JsonNode introspect(String token) throws Exception {
        HttpPost request = new HttpPost(AKSK_BASE_URL + "/oauth2/introspect");
        request.setHeader("Authorization", basic(AKSK_INTROSPECT_CLIENT_ID, AKSK_INTROSPECT_CLIENT_SECRET));
        List<org.apache.http.NameValuePair> form = new ArrayList<org.apache.http.NameValuePair>();
        form.add(new BasicNameValuePair("token", token));
        request.setEntity(new UrlEncodedFormEntity(form, StandardCharsets.UTF_8));
        JsonNode response = readJson(perform(request));
        assertThat(response.path("active").asBoolean(false))
                .as("内省必须返回 active 快照").isTrue();
        JsonNode authorization = response.get("aksk_authorization");
        assertThat(authorization != null && authorization.isObject())
                .as("内省响应必须包含 aksk_authorization 授权快照").isTrue();
        return authorization;
    }

    private String extractOwnerPrincipalId(JsonNode authorization) {
        JsonNode grants = authorization.path("dataGrantDocument").path("grants");
        List<String> ownerValues = new ArrayList<String>();
        if (grants.isArray()) {
            for (JsonNode grant : grants) {
                JsonNode constraints = grant.path("constraints");
                if (!constraints.isArray()) {
                    continue;
                }
                for (JsonNode constraint : constraints) {
                    if ("ownerPrincipalId".equals(constraint.path("dimension").asText())
                            && "IN".equals(constraint.path("operator").asText())) {
                        constraint.path("values").forEach(value -> ownerValues.add(value.asText()));
                    }
                }
            }
        }
        assertThat(ownerValues).as("ownerPrincipalId 维度约束必须唯一一致（fail-closed 契约）").hasSize(1);
        return ownerValues.get(0);
    }

    private String createKey(String token, String suffix) throws Exception {
        HttpPost request = jsonPost(KMS_BASE_URL + "/api/kms/keys", CREATE_KEY_BODY);
        request.setHeader("Authorization", bearer(token));
        request.setHeader("Idempotency-Key", "collab-key-" + suffix);
        String body = perform(request);
        JsonNode key = readJson(body);
        assertThat(key.hasNonNull("keyRef")).as("建钥必须成功，响应=" + body).isTrue();
        return key.get("keyRef").asText();
    }

    private void createExactPolicy(String token, String keyRef, String operation, String principalId,
                                   String idempotencyKey) throws Exception {
        HttpPost request = jsonPost(KMS_BASE_URL + "/api/kms/keys/" + keyRef + "/policies",
                "{\"principalId\":\"" + principalId + "\",\"keyVersion\":1,\"operation\":\"" + operation + "\"}");
        request.setHeader("Authorization", bearer(token));
        request.setHeader("Idempotency-Key", "collab-policy-" + idempotencyKey);
        Response response = performResponse(request);
        assertThat(response.status).as("合成主体精确 policy 必须创建成功，响应=" + response.body).isEqualTo(201);
    }

    private static final class Response {
        final int status;
        final String body;

        Response(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
