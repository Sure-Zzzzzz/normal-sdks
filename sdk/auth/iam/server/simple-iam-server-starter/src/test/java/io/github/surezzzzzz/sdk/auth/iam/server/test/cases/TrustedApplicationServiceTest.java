package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationClientRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.UpdateTrustedApplicationClientRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.response.TrustedApplicationClientResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.response.TrustedApplicationClientSecretResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.response.TrustedApplicationCreatedResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.service.TrustedApplicationClientService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.TrustedApplicationService;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 可信应用及其 OAuth2 客户端服务测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
class TrustedApplicationServiceTest {

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String applicationCode = "trusted-app-" + suffix;
    private final String clientId = applicationCode + "-web";
    private final String clientSecret = "ClientSecret@" + suffix;

    private Long applicationId;

    @Autowired
    private TrustedApplicationService trustedApplicationService;

    @Autowired
    private TrustedApplicationClientService trustedApplicationClientService;

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    @AfterEach
    void cleanup() {
        if (applicationId != null) {
            trustedApplicationService.deleteApplication(applicationId);
            applicationId = null;
        }
    }

    @Test
    @DisplayName("创建应用应带初始客户端，密钥随响应一次性回显且详情不回显")
    void testCreateApplicationWithInitialClient() {
        TrustedApplicationCreatedResponse response = createApplication(createConfidentialClientRequest());
        TrustedApplicationClientResponse client = trustedApplicationClientService.getClient(applicationId, clientId);
        RegisteredClient stored = registeredClientRepository.findByClientId(clientId);

        log.info("创建可信应用：applicationId={}, applicationCode={}, clientId={}",
                response.getApplication().getId(), response.getApplication().getApplicationCode(), client.getClientId());
        assertEquals(applicationCode, response.getApplication().getApplicationCode(), "应用编码应一致");
        assertEquals(1, response.getApplication().getClientCount(), "创建时应关联一个初始客户端");
        assertEquals(clientSecret, response.getInitialClientSecret(), "自传密钥应随创建响应一次性回显");
        assertEquals(clientId, client.getClientId(), "初始客户端ID应一致");
        assertTrue(client.getSecretPresent(), "机密客户端应标记存在密钥");
        assertNotNull(stored, "客户端应已存储");
        assertNotEquals(clientSecret, stored.getClientSecret(), "密钥必须加密存储");
    }

    @Test
    @DisplayName("应用下新增客户端仅返回一次明文密钥")
    void testAddClientReturnsSecretOnce() {
        createApplication(createPublicClientRequest());
        String secondClientId = applicationCode + "-service";
        CreateTrustedApplicationClientRequest request = createConfidentialClientRequest();
        request.setClientId(secondClientId);

        TrustedApplicationClientSecretResponse secretResponse = trustedApplicationClientService.addClient(applicationId, request);
        TrustedApplicationClientResponse response = trustedApplicationClientService.getClient(applicationId, secondClientId);

        log.info("新增应用客户端：applicationId={}, clientId={}", applicationId, secondClientId);
        assertEquals(secondClientId, secretResponse.getClientId(), "创建响应应包含客户端ID");
        assertEquals(clientSecret, secretResponse.getClientSecret(), "创建响应应仅此一次返回明文密钥");
        assertTrue(response.getSecretPresent(), "详情应仅标记密钥存在");
        assertEquals(2, trustedApplicationClientService.listClients(applicationId).size(), "应用应关联两个客户端");
    }

    @Test
    @DisplayName("更新客户端应去重重定向地址并保留密钥")
    void testUpdateClientPreservesSecret() {
        createApplication(createConfidentialClientRequest());
        RegisteredClient before = registeredClientRepository.findByClientId(clientId);
        UpdateTrustedApplicationClientRequest request = new UpdateTrustedApplicationClientRequest();
        request.setClientName("可信应用管理测试更新");
        request.setRedirectUris(Arrays.asList("https://example.com/callback2", "https://example.com/callback2"));
        request.setScopes(Arrays.asList("openid", "profile"));
        request.setRequireConsent(true);

        TrustedApplicationClientResponse response = trustedApplicationClientService
                .updateClient(applicationId, clientId, request);
        RegisteredClient after = registeredClientRepository.findByClientId(clientId);

        log.info("更新应用客户端：clientId={}, redirectUris={}", clientId, response.getRedirectUris());
        assertEquals("可信应用管理测试更新", response.getClientName(), "客户端名称应更新");
        assertEquals(Collections.singletonList("https://example.com/callback2"), response.getRedirectUris(),
                "重定向地址应去重");
        assertTrue(response.getScopes().contains("profile"), "更新后的 scope 应包含 profile");
        assertTrue(response.getRequireConsent(), "应启用授权确认");
        assertEquals(before.getClientSecret(), after.getClientSecret(), "更新不应改变已加密密钥");
    }

    @Test
    @DisplayName("公共客户端应无密钥并强制 PKCE")
    void testCreatePublicClient() {
        createApplication(createPublicClientRequest());
        TrustedApplicationClientResponse response = trustedApplicationClientService.getClient(applicationId, clientId);
        RegisteredClient stored = registeredClientRepository.findByClientId(clientId);

        log.info("创建公共客户端：clientId={}, requireProofKey={}", clientId, response.getRequireProofKey());
        assertEquals("PUBLIC", response.getClientType(), "客户端类型应为公共客户端");
        assertTrue(response.getRequireProofKey(), "公共客户端必须启用 PKCE");
        assertFalse(response.getSecretPresent(), "公共客户端不得存储密钥");
        assertNull(stored.getClientSecret(), "公共客户端不得存储密钥");
        assertEquals(Collections.singleton(ClientAuthenticationMethod.NONE), stored.getClientAuthenticationMethods(),
                "公共客户端仅允许 none 认证方式");
    }

    @Test
    @DisplayName("可信应用图标必须使用内置编码")
    void testRejectUnsupportedIcon() {
        CreateTrustedApplicationRequest request = new CreateTrustedApplicationRequest();
        request.setApplicationCode(applicationCode);
        request.setApplicationName("可信应用图标测试");
        request.setIcon("https://example.com/icon.svg");
        request.setInitialClient(createPublicClientRequest());

        SimpleIamServerException exception = assertThrows(SimpleIamServerException.class,
                () -> trustedApplicationService.createApplication(request));

        assertEquals(ErrorCode.TRUSTED_APPLICATION_ICON_INVALID, exception.getErrorCode(),
                "外部图标地址必须被拒绝");
    }

    @Test
    @DisplayName("公共客户端携带密钥应被拒绝")
    void testRejectInvalidClientPolicy() {
        CreateTrustedApplicationClientRequest publicWithSecret = createPublicClientRequest();
        publicWithSecret.setClientSecret(clientSecret);

        SimpleIamServerException publicException = assertThrows(SimpleIamServerException.class,
                () -> createApplication(publicWithSecret));

        log.info("客户端策略拒绝错误码：public={}", publicException.getErrorCode());
        assertEquals(ErrorCode.TRUSTED_APPLICATION_CLIENT_POLICY_INVALID, publicException.getErrorCode(),
                "公共客户端不得携带密钥");
    }

    @Test
    @DisplayName("机密客户端留空密钥应由服务端生成并一次性回显")
    void testConfidentialClientSecretGeneratedWhenBlank() {
        CreateTrustedApplicationClientRequest request = createConfidentialClientRequest();
        request.setClientSecret(null);
        TrustedApplicationCreatedResponse response = createApplication(request);
        RegisteredClient stored = registeredClientRepository.findByClientId(clientId);

        log.info("服务端生成密钥：applicationId={}, clientId={}", applicationId, clientId);
        assertNotNull(response.getInitialClientSecret(), "留空密钥应由服务端生成并回显");
        assertTrue(response.getInitialClientSecret().length() >= 32, "生成密钥应具备足够熵");
        assertNotEquals(response.getInitialClientSecret(), stored.getClientSecret(), "生成密钥必须加密存储");
        assertTrue(trustedApplicationClientService.getClient(applicationId, clientId).getSecretPresent(),
                "详情应仅标记密钥存在");
    }

    @Test
    @DisplayName("非法重定向地址和机器凭证授权类型应被拒绝")
    void testRejectInvalidRedirectUriAndMachineGrantType() {
        CreateTrustedApplicationClientRequest invalidRedirect = createConfidentialClientRequest();
        invalidRedirect.setRedirectUris(Collections.singletonList("/relative/callback"));
        SimpleIamServerException redirectException = assertThrows(SimpleIamServerException.class,
                () -> createApplication(invalidRedirect));

        CreateTrustedApplicationClientRequest machineGrant = createConfidentialClientRequest();
        machineGrant.setGrantTypes(Arrays.asList("authorization_code", "client_credentials"));
        SimpleIamServerException grantException = assertThrows(SimpleIamServerException.class,
                () -> createApplication(machineGrant));

        log.info("客户端输入拒绝错误码：redirectUri={}, grantType={}",
                redirectException.getErrorCode(), grantException.getErrorCode());
        assertEquals(ErrorCode.TRUSTED_APPLICATION_REDIRECT_URI_INVALID, redirectException.getErrorCode(),
                "相对重定向地址必须被拒绝");
        assertEquals(ErrorCode.TRUSTED_APPLICATION_GRANT_TYPE_NOT_ALLOWED, grantException.getErrorCode(),
                "机器凭证授权类型必须被拒绝");
    }

    @Test
    @DisplayName("不存在的应用不得创建孤儿客户端")
    void testRejectClientForMissingApplication() {
        SimpleIamServerException exception = assertThrows(SimpleIamServerException.class,
                () -> trustedApplicationClientService.addClient(Long.MAX_VALUE, createConfidentialClientRequest()));

        assertEquals(ErrorCode.TRUSTED_APPLICATION_NOT_FOUND, exception.getErrorCode(),
                "不存在的应用必须拒绝客户端创建");
        assertNull(registeredClientRepository.findByClientId(clientId), "不得写入孤儿客户端");
    }

    @Test
    @DisplayName("删除应用应级联删除其下全部客户端")
    void testDeleteApplicationCascadesClients() {
        createApplication(createPublicClientRequest());
        String secondClientId = applicationCode + "-service";
        CreateTrustedApplicationClientRequest secondClient = createConfidentialClientRequest();
        secondClient.setClientId(secondClientId);
        trustedApplicationClientService.addClient(applicationId, secondClient);
        Long deletedApplicationId = applicationId;

        trustedApplicationService.deleteApplication(deletedApplicationId);
        applicationId = null;

        log.info("删除可信应用：applicationId={}, clientIds=[{}, {}]", deletedApplicationId, clientId, secondClientId);
        assertNull(registeredClientRepository.findByClientId(clientId), "初始客户端应被级联删除");
        assertNull(registeredClientRepository.findByClientId(secondClientId), "新增客户端应被级联删除");
        assertThrows(SimpleIamServerException.class,
                () -> trustedApplicationService.getApplication(deletedApplicationId), "应用应无法再读取");
    }

    private TrustedApplicationCreatedResponse createApplication(CreateTrustedApplicationClientRequest initialClient) {
        CreateTrustedApplicationRequest request = new CreateTrustedApplicationRequest();
        request.setApplicationCode(applicationCode);
        request.setApplicationName("可信应用管理测试");
        request.setInitialClient(initialClient);
        TrustedApplicationCreatedResponse response = trustedApplicationService.createApplication(request);
        applicationId = response.getApplication().getId();
        return response;
    }

    private CreateTrustedApplicationClientRequest createConfidentialClientRequest() {
        CreateTrustedApplicationClientRequest request = createBaseClientRequest();
        request.setClientType("CONFIDENTIAL");
        request.setClientSecret(clientSecret);
        request.setAuthenticationMethods(Collections.singletonList("client_secret_basic"));
        return request;
    }

    private CreateTrustedApplicationClientRequest createPublicClientRequest() {
        CreateTrustedApplicationClientRequest request = createBaseClientRequest();
        request.setClientType("PUBLIC");
        request.setAuthenticationMethods(Collections.singletonList("none"));
        return request;
    }

    private CreateTrustedApplicationClientRequest createBaseClientRequest() {
        CreateTrustedApplicationClientRequest request = new CreateTrustedApplicationClientRequest();
        request.setClientId(clientId);
        request.setClientName("可信应用管理测试客户端");
        request.setRedirectUris(Collections.singletonList("https://example.com/callback"));
        request.setScopes(Collections.singletonList("openid"));
        request.setGrantTypes(Collections.singletonList("authorization_code"));
        return request;
    }
}
