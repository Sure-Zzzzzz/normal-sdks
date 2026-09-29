package io.github.surezzzzzz.sdk.auth.iam.adapter.aksk.collaboration.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.configuration.SimpleAkskServerProperties;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model.OwnerAuthorizationKey;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model.OwnerAuthorizationReadResult;
import io.github.surezzzzzz.sdk.auth.iam.adapter.aksk.collaboration.configuration.SimpleIamAkskCollaborationProperties;
import io.github.surezzzzzz.sdk.auth.iam.adapter.aksk.collaboration.service.IamOwnerAuthorizationProvider;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * IAM owner authorization provider 测试。
 *
 * <p>IAM 适配器只验证中性 provider 契约，不让 AKSK Server 测试依赖 IAM HTTP 实现。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
class IamOwnerAuthorizationProviderTest {

    @Test
    void validProjectionMustBeReturnedAsNeutralResult() {
        SimpleAkskServerProperties serverProperties = new SimpleAkskServerProperties();
        serverProperties.getOwnerAuthorization().setEnabled(Boolean.TRUE);
        serverProperties.getOwnerAuthorization().setOwnerSourceId("directory");
        SimpleIamAkskCollaborationProperties properties = new SimpleIamAkskCollaborationProperties();
        properties.setEnabled(Boolean.TRUE);
        properties.setTokenUri("https://iam.example/oauth2/token");
        properties.setBaseUri("https://iam.example");
        properties.setClientId("reader");
        properties.setClientSecret("secret");
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(once(), requestTo("https://iam.example/oauth2/token"))
                .andExpect(method(POST))
                .andRespond(withSuccess("{\"access_token\":\"token\",\"expires_in\":120}",
                        MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://iam.example/iam/internal/owner-authorization/resolve"))
                .andExpect(method(POST))
                .andRespond(withSuccess(resolveBody(), MediaType.APPLICATION_JSON));

        IamOwnerAuthorizationProvider provider = new IamOwnerAuthorizationProvider(
                serverProperties, properties, restTemplate);
        OwnerAuthorizationReadResult result = provider.resolve(
                new OwnerAuthorizationKey("directory", "subject_7Kq2m9X4"), Long.valueOf(9L));

        assertTrue(result.isActive());
        assertTrue(provider.isAvailable());
        assertTrue(result.toApplicationAuthorizationContext().isAdmitted());
        server.verify();
        log.info("中性 owner authorization provider resolve 通过：active={}", result.isActive());
    }

    @Test
    void httpEndpointMustBeAcceptedAsDeploymentChoice() {
        SimpleAkskServerProperties serverProperties = new SimpleAkskServerProperties();
        serverProperties.getOwnerAuthorization().setEnabled(Boolean.TRUE);
        serverProperties.getOwnerAuthorization().setOwnerSourceId("directory");
        SimpleIamAkskCollaborationProperties properties = new SimpleIamAkskCollaborationProperties();
        properties.setEnabled(Boolean.TRUE);
        properties.setTokenUri("http://iam.example/oauth2/token");
        properties.setBaseUri("http://iam.example");
        properties.setClientId("reader");
        properties.setClientSecret("secret");
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(once(), requestTo("http://iam.example/oauth2/token"))
                .andExpect(method(POST))
                .andRespond(withSuccess("{\"access_token\":\"token\",\"expires_in\":120}",
                        MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("http://iam.example/iam/internal/owner-authorization/resolve"))
                .andExpect(method(POST))
                .andRespond(withSuccess(resolveBody(), MediaType.APPLICATION_JSON));

        IamOwnerAuthorizationProvider provider = new IamOwnerAuthorizationProvider(
                serverProperties, properties, restTemplate);
        OwnerAuthorizationReadResult result = provider.resolve(
                new OwnerAuthorizationKey("directory", "subject_7Kq2m9X4"), Long.valueOf(9L));

        log.info("http 端点部署形态：available={}, active={}", provider.isAvailable(), result.isActive());
        assertTrue(provider.isAvailable(), "http 端点是受支持的部署形态，配置校验必须通过");
        assertTrue(result.isActive(), "http 端点下有效投影必须正常返回");
        server.verify();
    }

    @Test
    void missingDisplayNameMustFailClosed() {
        SimpleAkskServerProperties serverProperties = new SimpleAkskServerProperties();
        serverProperties.getOwnerAuthorization().setEnabled(Boolean.TRUE);
        serverProperties.getOwnerAuthorization().setOwnerSourceId("directory");
        SimpleIamAkskCollaborationProperties properties = new SimpleIamAkskCollaborationProperties();
        properties.setEnabled(Boolean.TRUE);
        properties.setTokenUri("https://iam.example/oauth2/token");
        properties.setBaseUri("https://iam.example");
        properties.setClientId("reader");
        properties.setClientSecret("secret");
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(once(), requestTo("https://iam.example/oauth2/token"))
                .andRespond(withSuccess("{\"access_token\":\"token\",\"expires_in\":120}",
                        MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://iam.example/iam/internal/owner-authorization/resolve"))
                .andRespond(withSuccess(resolveBody().replace(",\"ownerUsername\":\"user\"", ""),
                        MediaType.APPLICATION_JSON));

        IamOwnerAuthorizationProvider provider = new IamOwnerAuthorizationProvider(
                serverProperties, properties, restTemplate);
        assertFalse(provider.resolve(new OwnerAuthorizationKey("directory", "subject_7Kq2m9X4"), 9L).isActive());
        server.verify();
    }

    private static String resolveBody() {
        return "{\"active\":true,\"ownerSecurityEpoch\":5,\"applicationAuthorizationEpoch\":3,"
                + "\"ownerInheritedAccessEpoch\":2,\"projectionAccessEpoch\":4,\"resumeAfterSequence\":10,"
                + "\"ownerUsername\":\"user\",\"authorization\":{\"protocol\":\"simple-application-authorization\","
                + "\"version\":\"1.0\",\"subjectType\":\"HUMAN\",\"subjectId\":\"subject_7Kq2m9X4\","
                + "\"applicationCode\":\"demo\",\"admitted\":true,\"roles\":[],\"pagePermissions\":[],"
                + "\"apiPermissions\":[\"resource.read\"],\"dataGrantDocument\":null,"
                + "\"authorizationVersion\":1,\"manifestVersion\":\"v1\",\"manifestDigest\":\"digest\","
                + "\"issuedAt\":1000,\"expiresAt\":2000}}";
    }
}
