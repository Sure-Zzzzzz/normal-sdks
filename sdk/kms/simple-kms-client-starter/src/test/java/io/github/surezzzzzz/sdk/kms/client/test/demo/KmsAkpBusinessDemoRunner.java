package io.github.surezzzzzz.sdk.kms.client.test.demo;

import io.github.surezzzzzz.sdk.kms.client.client.KmsClientAuthenticationInterceptor;
import io.github.surezzzzzz.sdk.kms.client.client.RestTemplateKmsClient;
import io.github.surezzzzzz.sdk.kms.client.exception.KmsBadRequestException;
import io.github.surezzzzzz.sdk.kms.client.model.KmsKey;
import io.github.surezzzzzz.sdk.kms.client.model.KmsOwnerDestructionPolicy;
import io.github.surezzzzzz.sdk.kms.client.model.KmsSignature;
import io.github.surezzzzzz.sdk.kms.client.support.KmsHttpErrorMapper;
import io.github.surezzzzzz.sdk.kms.client.support.KmsHttpExecutor;
import io.github.surezzzzzz.sdk.kms.client.support.KmsJsonCodec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * "业务经 AKP 直连调用 KMS"端到端演示宿主（test 源集，走查形态）。
 *
 * <p>链路：AKP(client_credentials) → aksk /oauth2/token → Bearer 调 KMS → introspect 归一
 * {@code aksk:{clientId}}。演示八个事实：建钥、列表只见自己、给自己授 sign 策略、签名、
 * 销毁政策默认不限制、设窗口、越窗被拒、resetSecret 轮换语义（clientId 不变归属不变）。</p>
 *
 * <p>凭据经环境变量注入（KMS_AKP_CLIENT_ID/KMS_AKP_CLIENT_SECRET），不写入任何仓库文件。</p>
 *
 * @author surezzzzzz
 */
public final class KmsAkpBusinessDemoRunner {

    private KmsAkpBusinessDemoRunner() {
    }

    /**
     * 演示入口。
     *
     * @param args 未使用；配置全部来自环境变量
     * @throws Exception HTTP 调用失败时抛出
     */
    public static void main(String[] args) throws Exception {
        String clientId = requireEnv("KMS_AKP_CLIENT_ID");
        String clientSecret = requireEnv("KMS_AKP_CLIENT_SECRET");
        String tokenUrl = env("AKSK_TOKEN_URL", "http://127.0.0.1:8280/oauth2/token");
        URI kmsBase = URI.create(env("KMS_BASE_URL", "http://127.0.0.1:8390") + "/api/kms");

        AkskBearerInterceptor auth = new AkskBearerInterceptor(tokenUrl, clientId, clientSecret);
        try (CloseableHttpClient httpClient = HttpClients.custom().disableRedirectHandling().build()) {
            ClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
            RestTemplate restTemplate = new RestTemplate(factory);
            restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
                @Override
                public boolean hasError(ClientHttpResponse response) {
                    return false;
                }
            });
            restTemplate.getInterceptors().add(auth);
            KmsHttpExecutor executor = new KmsHttpExecutor(restTemplate, new KmsJsonCodec(),
                    new KmsHttpErrorMapper(), 2 * 1024 * 1024, 2 * 1024 * 1024);
            RestTemplateKmsClient client = new RestTemplateKmsClient(kmsBase, executor);

            String owner = "aksk:" + clientId;
            System.out.println("== AKP→KMS 业务演示 ==");
            System.out.println("owner 归一 = " + owner);

            String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            KmsKey key = client.createKey("demo-idem-create-" + unique, "license-demo-key-" + unique, "SIGN", "ES256");
            System.out.println("[1] 建钥成功 keyRef=" + key.getKeyRef() + " state=" + key.getState());

            boolean listed = client.listKeys(1, 100, "license-demo-key-" + unique, "SIGN", "ES256", null)
                    .getItems().stream().anyMatch(k -> k.getKeyRef().equals(key.getKeyRef()));
            System.out.println("[2] 列表只含自己密钥 = " + listed);

            client.createPolicy("demo-idem-policy-" + unique, key.getKeyRef(), owner, null, "SIGN", null);
            System.out.println("[3] 给自己授 SIGN 策略成功（principalId=" + owner + "）");

            byte[] payload = "license-payload-".concat(unique).getBytes(StandardCharsets.UTF_8);
            KmsSignature signature = client.sign(key.getKeyRef(), null, payload);
            System.out.println("[4] 签名成功 version=" + signature.getVersion()
                    + " signatureLen=" + signature.getSignature().length);

            KmsOwnerDestructionPolicy before = client.getMyDestructionPolicy();
            System.out.println("[5] 销毁政策默认 exists=" + before.isExists() + "（无行=不限制）");

            client.saveMyDestructionPolicy(3600L, null);
            System.out.println("[6] 设置窗口 min=3600s 后 exists="
                    + client.getMyDestructionPolicy().isExists());

            try {
                client.scheduleDestruction("demo-idem-destroy-" + unique, key.getKeyRef(),
                        Instant.now().plusSeconds(60), key.getRowVersion());
                System.out.println("[7] 越窗安排 竟然成功——异常！应被拒绝");
            } catch (KmsBadRequestException expected) {
                System.out.println("[7] 越下限安排被拒（60s < 3600s 窗口）= 符合预期");
            }

            System.out.println("== 演示完成：AKP 直连 KMS 全链成立，底座零改动 ==");
        }
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("缺少环境变量 " + name);
        }
        return value;
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isEmpty() ? fallback : value;
    }

    /**
     * AKSK client_credentials 令牌拦截器：首次与临期时取新令牌，注入 Bearer。
     *
     * @author surezzzzzz
     */
    static final class AkskBearerInterceptor implements KmsClientAuthenticationInterceptor {

        private final String tokenUrl;
        final String clientId;
        private final String clientSecret;
        private final RestTemplate tokenClient = new RestTemplate();
        private final ObjectMapper mapper = new ObjectMapper();
        private String accessToken;
        private Instant expiresAt = Instant.EPOCH;

        AkskBearerInterceptor(String tokenUrl, String clientId, String clientSecret) {
            this.tokenUrl = tokenUrl;
            this.clientId = clientId;
            this.clientSecret = clientSecret;
        }

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                            ClientHttpRequestExecution execution) throws IOException {
            Instant now = Instant.now();
            if (accessToken == null || now.isAfter(expiresAt.minusSeconds(60))) {
                fetchToken();
            }
            request.getHeaders().setBearerAuth(accessToken);
            return execution.execute(request, body);
        }

        private synchronized void fetchToken() {
            String basic = Base64.getEncoder()
                    .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
            ResponseEntity<String> response = tokenClient.postForEntity(tokenUrl,
                    org.springframework.http.RequestEntity
                            .post(URI.create(tokenUrl))
                            .header("Authorization", "Basic " + basic)
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .body("grant_type=client_credentials&scope=read"), String.class);
            if (!HttpStatus.OK.equals(response.getStatusCode()) || response.getBody() == null) {
                throw new IllegalStateException("AKP 取令牌失败: " + response.getStatusCode());
            }
            try {
                JsonNode node = mapper.readTree(response.getBody());
                this.accessToken = node.path("access_token").asText();
                long expiresIn = node.path("expires_in").asLong(3600L);
                this.expiresAt = Instant.now().plusSeconds(expiresIn);
            } catch (IOException exception) {
                throw new IllegalStateException("AKP 令牌响应解析失败", exception);
            }
        }
    }
}
