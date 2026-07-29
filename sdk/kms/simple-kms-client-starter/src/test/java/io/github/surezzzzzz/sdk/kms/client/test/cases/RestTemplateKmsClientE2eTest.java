package io.github.surezzzzzz.sdk.kms.client.test.cases;

import io.github.surezzzzzz.sdk.kms.client.client.KmsClientAuthenticationInterceptor;
import io.github.surezzzzzz.sdk.kms.client.client.RestTemplateKmsClient;
import io.github.surezzzzzz.sdk.kms.client.model.KmsKey;
import io.github.surezzzzzz.sdk.kms.client.model.KmsPublicKey;
import io.github.surezzzzzz.sdk.kms.client.model.KmsSigningResult;
import io.github.surezzzzzz.sdk.kms.client.port.DefaultTenantSignerPort;
import io.github.surezzzzzz.sdk.kms.client.port.TenantSignerPort;
import io.github.surezzzzzz.sdk.kms.client.support.KmsHttpErrorMapper;
import io.github.surezzzzzz.sdk.kms.client.support.KmsHttpExecutor;
import io.github.surezzzzzz.sdk.kms.client.support.KmsJsonCodec;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RestTemplate KMS Client 调用固定 Spring Boot 2.7.9 发布版 Server 的端到端测试。
 *
 * <p>测试只读取启动夹具发布的 loopback 地址，不解析 Server、Core、JDBC、MySQL 或表结构类型；
 * 因此每个受支持的 Client Spring Boot 版本均执行相同远程 HTTP 契约。</p>
 *
 * @author surezzzzzz
 */
class RestTemplateKmsClientE2eTest {

    private static final String SERVER_FILE_PROPERTY = "kms.e2e.server.file";
    private static final String BASE_URL_PROPERTY = "baseUrl";

    private String tenantId;
    private String principalId;
    private String requestId;
    private URI apiBaseUri;

    /**
     * 从固定 Server 临时清单读取 API 根地址，并生成本用例专属的测试身份标识。
     *
     * @throws IOException 临时清单无法读取时抛出
     */
    @BeforeEach
    void setUp() throws IOException {
        String unique = UUID.randomUUID().toString().replace("-", "");
        tenantId = "client-e2e-" + unique;
        principalId = "client-e2e-principal-" + unique;
        requestId = "client-e2e-request-" + unique;
        apiBaseUri = URI.create(readBaseUrl() + "/api/v1/kms");
    }

    @Test
    void shouldCallReleasedServerForManagementSigningAndPublicKeyLifecycle() throws Exception {
        String unique = UUID.randomUUID().toString().replace("-", "");
        try (CloseableHttpClient httpClient = HttpClients.custom().disableRedirectHandling().build()) {
            RestTemplateKmsClient client = client(httpClient);
            KmsKey created = client.createKey("client-e2e-create-signing-" + unique, "client-e2e-signing-" + unique,
                    "SIGN", "ES256");
            KmsKey replayed = client.createKey("client-e2e-create-signing-" + unique, "client-e2e-signing-" + unique,
                    "SIGN", "ES256");
            assertEquals(created.getKeyRef(), replayed.getKeyRef(), "同一幂等键必须重放同一逻辑密钥");
            assertEquals(Integer.valueOf(1), created.getActiveVersion(), "首次创建必须激活版本一");

            client.createPolicy("client-e2e-sign-policy-" + unique, created.getKeyRef(), principalId, Integer.valueOf(1),
                    "SIGN", null);
            client.createPolicy("client-e2e-verify-policy-" + unique, created.getKeyRef(), principalId, Integer.valueOf(1),
                    "VERIFY", null);
            client.createPolicy("client-e2e-public-policy-" + unique, created.getKeyRef(), principalId, null,
                    "READ_PUBLIC_KEY", null);

            byte[] signingInput = "license-payload".getBytes(StandardCharsets.UTF_8);
            TenantSignerPort signerPort = new DefaultTenantSignerPort(client);
            KmsSigningResult signing = signerPort.sign(created.getKeyRef(), Integer.valueOf(1), signingInput);
            assertEquals(Integer.valueOf(1), signing.getVersion(), "端口必须返回 KMS 实际签名版本");
            assertEquals("ES256", signing.getAlgorithm(), "端口必须返回固定 JOSE 算法");
            assertTrue(client.verify(created.getKeyRef(), Integer.valueOf(1), signingInput, signing.getSignature()),
                    "真实 Server 必须验证 Client 返回的 ES256 签名");

            KmsKey rotated = client.rotateKey("client-e2e-rotate-signing-" + unique, created.getKeyRef(),
                    created.getRowVersion());
            assertEquals(Integer.valueOf(2), rotated.getActiveVersion(), "轮换必须激活版本二");
            List<KmsPublicKey> publicKeys = client.listPublicKeys(created.getKeyRef());
            assertEquals(Integer.valueOf(2), Integer.valueOf(publicKeys.size()), "轮换后必须返回 ACTIVE 与 RETIRED 公钥");
            assertTrue(containsVersionState(publicKeys, Integer.valueOf(1), "RETIRED"), "版本一必须转为 RETIRED");
            assertTrue(containsVersionState(publicKeys, Integer.valueOf(2), "ACTIVE"), "版本二必须为 ACTIVE");
        }
    }

    @Test
    void shouldCallReleasedServerForAesGcmEncryptionRoundTrip() throws Exception {
        String unique = UUID.randomUUID().toString().replace("-", "");
        try (CloseableHttpClient httpClient = HttpClients.custom().disableRedirectHandling().build()) {
            RestTemplateKmsClient client = client(httpClient);
            KmsKey created = client.createKey("client-e2e-create-encryption-" + unique,
                    "client-e2e-encryption-" + unique, "ENCRYPT", "AES_256_GCM");
            client.createPolicy("client-e2e-encrypt-policy-" + unique, created.getKeyRef(), principalId, Integer.valueOf(1),
                    "ENCRYPT", null);
            client.createPolicy("client-e2e-decrypt-policy-" + unique, created.getKeyRef(), principalId, Integer.valueOf(1),
                    "DECRYPT", null);

            byte[] plaintext = "client-e2e-plaintext".getBytes(StandardCharsets.UTF_8);
            byte[] aad = "client-e2e-aad".getBytes(StandardCharsets.UTF_8);
            byte[] envelope = client.encrypt(created.getKeyRef(), plaintext, aad);
            assertNotEquals(Integer.valueOf(0), Integer.valueOf(envelope.length), "真实 Server 必须返回版本化 envelope");
            assertArrayEquals(plaintext, client.decrypt(envelope, aad), "AES-GCM 必须经 Client 与真实 Server 无损往返");
        }
    }

    /**
     * 为固定发布版 Server 创建专属 Client；关闭默认状态抛错，由执行器统一映射，且禁用重定向。
     *
     * @param httpClient 仅本用例持有的 HTTP 传输
     * @return 配置完成的 Client
     */
    private RestTemplateKmsClient client(CloseableHttpClient httpClient) {
        RestTemplate restTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false;
            }
        });
        restTemplate.getInterceptors().add(authenticationInterceptor());
        KmsHttpExecutor executor = new KmsHttpExecutor(restTemplate, new KmsJsonCodec(), new KmsHttpErrorMapper(),
                2 * 1024 * 1024, 2 * 1024 * 1024);
        return new RestTemplateKmsClient(apiBaseUri, executor);
    }

    /**
     * 构造只在本 E2E 中使用的认证拦截器，不输出任何测试身份头的值。
     *
     * @return 测试认证拦截器
     */
    private KmsClientAuthenticationInterceptor authenticationInterceptor() {
        return (request, body, execution) -> {
            request.getHeaders().set("X-Test-Tenant", tenantId);
            request.getHeaders().set("X-Test-Principal", principalId);
            request.getHeaders().set("X-Test-Request-Id", requestId);
            return execution.execute(request, body);
        };
    }

    private String readBaseUrl() throws IOException {
        String filePath = System.getProperty(SERVER_FILE_PROPERTY);
        if (filePath == null || filePath.trim().isEmpty()) {
            fail("缺少固定 KMS E2E Server 临时清单路径");
        }
        File serverFile = new File(filePath);
        if (!serverFile.isFile()) {
            fail("固定 KMS E2E Server 临时清单不存在");
        }
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(serverFile)) {
            properties.load(input);
        }
        String baseUrl = properties.getProperty(BASE_URL_PROPERTY);
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            fail("固定 KMS E2E Server 临时清单不合法");
        }
        return baseUrl;
    }

    private boolean containsVersionState(List<KmsPublicKey> publicKeys, Integer version, String state) {
        for (KmsPublicKey publicKey : publicKeys) {
            if (version.equals(publicKey.getVersion()) && state.equals(publicKey.getState())) {
                return true;
            }
        }
        return false;
    }
}
