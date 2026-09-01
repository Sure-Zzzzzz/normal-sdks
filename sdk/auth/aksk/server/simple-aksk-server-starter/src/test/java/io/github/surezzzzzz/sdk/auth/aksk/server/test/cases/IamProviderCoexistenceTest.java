package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.AkskApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.ClientManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.test.SimpleAkskServerTestApplication;
import io.github.surezzzzzz.sdk.auth.aksk.server.test.helper.ApplicationAuthorizationTestHelper;
import io.github.surezzzzzz.sdk.auth.aksk.server.test.helper.JwtTokenTestHelper;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceAuthenticationFailureCategory;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.resource.core.model.*;
import io.github.surezzzzzz.sdk.auth.resource.core.spi.ResourceAuthenticationAdapter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 并存形态（admin.enabled=true + IAM Provider）集成测试。
 * <p>
 * 用真 RSA 签名的 kid=iam/* token 验证 SPI 注册的第二 Adapter 与 server 自有 JWE Adapter
 * 经公共层 kid 路由并存（IAM 校验端点属网络边界，替身只剪该边界；签名验证走真链）。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleAkskServerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Import(IamProviderCoexistenceTest.IamTestAdapterConfiguration.class)
class IamProviderCoexistenceTest {

    private static final KeyPair IAM_KEY_PAIR = generateKeyPair();

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ClientManagementService clientManagementService;

    @Autowired
    private OAuth2RegisteredClientEntityRepository clientRepository;

    @Autowired
    private AkskApplicationAuthorizationRepository applicationAuthorizationRepository;

    private String selfIssuedJweToken;

    private static String signIamToken(String kid) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("iam-e2e-service")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build(), claims);
        jwt.sign(new RSASSASigner(IAM_KEY_PAIR.getPrivate()));
        return jwt.serialize();
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException("生成测试RSA密钥对失败", exception);
        }
    }

    @BeforeEach
    void setup() {
        ClientInfoResponse bootstrapClient = clientManagementService.createPlatformClient(
                "Coexistence Bootstrap Client", Arrays.asList("/api/client"));
        ApplicationAuthorizationTestHelper.grantManagementAuthorization(
                applicationAuthorizationRepository, bootstrapClient);
        selfIssuedJweToken = JwtTokenTestHelper.getTokenByClientCredentials(
                restTemplate, port, bootstrapClient.getClientId(),
                bootstrapClient.getClientSecret(), "/api/client");
    }

    @AfterEach
    void cleanupData() {
        clientRepository.deleteAll();
    }

    @Test
    void selfIssuedJweStillAcceptedAlongsideIamAdapter() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/client"), HttpMethod.GET,
                JwtTokenTestHelper.createAuthEntity(selfIssuedJweToken), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(), "并存形态下自签JWE应照常被自有Adapter认证");
    }

    @Test
    void iamKidTokenRoutedToIamAdapter() throws Exception {
        String iamToken = signIamToken("iam/e2e-test");

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/client"), HttpMethod.GET,
                JwtTokenTestHelper.createAuthEntity(iamToken), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(), "kid=iam/*的token应路由到IAM Adapter并放行");
    }

    @Test
    void unknownKidTokenMustBeRejected() throws Exception {
        String unknownToken = signIamToken("unknown/e2e-test");

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/client"), HttpMethod.GET,
                JwtTokenTestHelper.createAuthEntity(unknownToken), String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(), "未注册来源的kid必须被kid路由拒绝");
    }

    @Test
    void anonymousRequestMustBeRejected() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/client"), HttpMethod.GET, HttpEntity.EMPTY, String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(), "无凭据请求必须401");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    /**
     * 测试用 IAM Provider Adapter（sourceId=iam，真 RSA 验签，授权文档在 Adapter 侧构造——
     * 等价 IAM 校验端点返回的授权快照）。
     * <p>
     * {@code @Profile("!e2e")}：双身份 e2e（LOCAL_DUAL_IDENTITY_E2E）以 {@code e2e} profile
     * 外插真 iam-resource 时，本替身让位——组件扫描会把嵌套配置一并拾取，若同时装配
     * 两个 sourceId=iam 的 Adapter，AdapterRegistry 会拒绝启动（认证来源重复）。
     */
    @TestConfiguration
    @Profile("!e2e")
    static class IamTestAdapterConfiguration {

        @Bean
        public ResourceAuthenticationAdapter iamTestResourceAuthenticationAdapter() {
            return new IamTestResourceAuthenticationAdapter();
        }
    }

    static final class IamTestResourceAuthenticationAdapter implements ResourceAuthenticationAdapter {

        private static final ResourceAuthenticationSourceId SOURCE_ID =
                new ResourceAuthenticationSourceId("iam");

        @Override
        public ResourceAuthenticationSourceId sourceId() {
            return SOURCE_ID;
        }

        @Override
        public ResourceAuthenticationResult authenticate(ResourceCredential credential) {
            if (!(credential instanceof BearerResourceCredential)
                    || !SOURCE_ID.equals(credential.getSourceId())) {
                return ResourceAuthenticationResult.rejected(
                        ResourceAuthenticationFailureCategory.CREDENTIAL_MALFORMED);
            }
            try {
                SignedJWT jwt = SignedJWT.parse(((BearerResourceCredential) credential).getToken());
                if (!jwt.verify(new RSASSAVerifier((RSAPublicKey) IAM_KEY_PAIR.getPublic()))) {
                    return ResourceAuthenticationResult.rejected(
                            ResourceAuthenticationFailureCategory.SIGNATURE_OR_DECRYPTION_FAILED);
                }
                return ResourceAuthenticationResult.authenticated(
                        new VerifiedResourcePrincipal(SOURCE_ID, ResourceSubjectType.SERVICE,
                                "iam-e2e-service"),
                        managementAuthorization());
            } catch (Exception exception) {
                return ResourceAuthenticationResult.rejected(
                        ResourceAuthenticationFailureCategory.SIGNATURE_OR_DECRYPTION_FAILED);
            }
        }

        private ApplicationAuthorizationContext managementAuthorization() {
            return new ApplicationAuthorizationContext(
                    SimpleApplicationAuthorizationConstant.PROTOCOL,
                    SimpleApplicationAuthorizationConstant.VERSION,
                    ApplicationAuthorizationSubjectType.SERVICE,
                    "iam-e2e-service",
                    "aksk-server",
                    Boolean.TRUE,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Arrays.asList("akskClient:create", "akskClient:read", "akskClient:update",
                            "akskClient:delete", "akskToken:read", "akskToken:update", "akskToken:delete",
                            "akskApplicationAuthorization:create", "akskApplicationAuthorization:read",
                            "akskApplicationAuthorization:update", "akskApplicationAuthorization:revoke"),
                    new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL,
                            SimpleDataPermissionConstant.VERSION,
                            Arrays.asList(
                                    new DataGrant("akskClient",
                                            Arrays.asList("create", "read", "update", "delete"),
                                            true, Collections.emptyList()),
                                    new DataGrant("akskToken",
                                            Arrays.asList("read", "update", "delete"),
                                            true, Collections.emptyList()),
                                    new DataGrant("akskApplicationAuthorization",
                                            Arrays.asList("create", "read", "update", "revoke"),
                                            true, Collections.emptyList()))),
                    1L,
                    "iam-e2e-manifest",
                    "iam-e2e-manifest-digest",
                    Instant.now(),
                    Instant.now().plusSeconds(300));
        }
    }
}
