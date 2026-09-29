package io.github.surezzzzzz.sdk.auth.iam.adapter.aksk.collaboration.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.configuration.SimpleAkskServerProperties;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model.OwnerAuthorizationKey;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model.OwnerAuthorizationReadResult;
import io.github.surezzzzzz.sdk.auth.iam.adapter.aksk.collaboration.configuration.SimpleIamAkskCollaborationProperties;
import io.github.surezzzzzz.sdk.auth.iam.adapter.aksk.collaboration.service.IamOwnerAuthorizationProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * IAM owner authorization provider 测试。
 *
 * <p>IAM 适配器只验证中性 provider 契约，不让 AKSK Server 测试依赖 IAM HTTP 实现。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
class IamOwnerAuthorizationProviderTest {

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

    @Test
    void ownerSubjectIdBeyondIamWireLimitMustFailClosedBeforeHttpRequest() {
        SimpleAkskServerProperties serverProperties = serverProperties();
        SimpleIamAkskCollaborationProperties properties = httpsProperties();
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        IamOwnerAuthorizationProvider provider = new IamOwnerAuthorizationProvider(
                serverProperties, properties, restTemplate);

        String subjectId = new String(new char[65]).replace('\0', 's');
        OwnerAuthorizationReadResult result = provider.resolve(
                new OwnerAuthorizationKey("directory", subjectId), Long.valueOf(9L));

        assertFalse(result.isActive(), "超出 IAM wire 上限时不得把无效主体发送到身份源");
        server.verify();
    }

    @Test
    void inactiveResolveMustRetainResumeSequence() {
        SimpleAkskServerProperties serverProperties = serverProperties();
        SimpleIamAkskCollaborationProperties properties = httpsProperties();
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(once(), requestTo("https://iam.example/oauth2/token"))
                .andRespond(withSuccess("{\"access_token\":\"token\",\"expires_in\":120}",
                        MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://iam.example/iam/internal/owner-authorization/resolve"))
                .andRespond(withSuccess("{\"active\":false,\"resumeAfterSequence\":31}",
                        MediaType.APPLICATION_JSON));

        IamOwnerAuthorizationProvider provider = new IamOwnerAuthorizationProvider(
                serverProperties, properties, restTemplate);
        OwnerAuthorizationReadResult result = provider.resolve(
                new OwnerAuthorizationKey("directory", "subject_7Kq2m9X4"), Long.valueOf(9L));

        assertFalse(result.isActive());
        assertEquals(Long.valueOf(31L), result.getResumeAfterSequence(),
                "失效投影仍要携带安全恢复位点，避免修复时跳过变更日志");
        server.verify();
    }

    @Test
    void unauthorizedCachedReaderTokenMustBeRenewedBeforeRetry() {
        SimpleAkskServerProperties serverProperties = serverProperties();
        SimpleIamAkskCollaborationProperties properties = httpsProperties();
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(once(), requestTo("https://iam.example/oauth2/token"))
                .andRespond(withSuccess("{\"access_token\":\"expired-token\",\"expires_in\":120}",
                        MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://iam.example/iam/internal/owner-authorization/resolve"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(once(), requestTo("https://iam.example/oauth2/token"))
                .andRespond(withSuccess("{\"access_token\":\"renewed-token\",\"expires_in\":120}",
                        MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://iam.example/iam/internal/owner-authorization/resolve"))
                .andRespond(withSuccess(resolveBody(), MediaType.APPLICATION_JSON));

        IamOwnerAuthorizationProvider provider = new IamOwnerAuthorizationProvider(
                serverProperties, properties, restTemplate);
        OwnerAuthorizationReadResult result = provider.resolve(
                new OwnerAuthorizationKey("directory", "subject_7Kq2m9X4"), Long.valueOf(9L));

        assertTrue(result.isActive(), "缓存 reader token 已被拒绝时，重试必须重新换取 token");
        server.verify();
    }

    @Test
    void changePullMustRetryAndPreserveIamServerTime() {
        SimpleAkskServerProperties serverProperties = serverProperties();
        SimpleIamAkskCollaborationProperties properties = httpsProperties();
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(once(), requestTo("https://iam.example/oauth2/token"))
                .andRespond(withSuccess("{\"access_token\":\"stream-token\",\"expires_in\":120}",
                        MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://iam.example/iam/internal/owner-authorization/changes/pull"))
                .andRespond(withServerError());
        server.expect(once(), requestTo("https://iam.example/iam/internal/owner-authorization/changes/pull"))
                .andRespond(withSuccess(changePullBody(), MediaType.APPLICATION_JSON));

        IamOwnerAuthorizationProvider provider = new IamOwnerAuthorizationProvider(
                serverProperties, properties, restTemplate);
        io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model.OwnerAuthorizationChangePage result =
                provider.pullChanges(Long.valueOf(4L), 100);

        assertTrue(result.isAvailable());
        assertEquals(1, result.getChanges().size());
        assertEquals(Instant.parse("2026-09-29T03:00:00Z"), result.getServerTime(),
                "投影租约必须使用 IAM 返回的服务时间，而不是本地调用时钟");
        server.verify();
    }

    @Test
    void inconsistentChangeWatermarksMustFailClosed() {
        SimpleAkskServerProperties serverProperties = serverProperties();
        SimpleIamAkskCollaborationProperties properties = httpsProperties();
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(once(), requestTo("https://iam.example/oauth2/token"))
                .andRespond(withSuccess("{\"access_token\":\"stream-token\",\"expires_in\":120}",
                        MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://iam.example/iam/internal/owner-authorization/changes/pull"))
                .andRespond(withSuccess("{\"resyncRequired\":false,\"lowWatermark\":6,\"highWatermark\":5,"
                                + "\"serverTime\":\"2026-09-29T03:00:00Z\",\"changes\":[]}",
                        MediaType.APPLICATION_JSON));

        IamOwnerAuthorizationProvider provider = new IamOwnerAuthorizationProvider(
                serverProperties, properties, restTemplate);

        assertFalse(provider.pullChanges(Long.valueOf(4L), 100).isAvailable(),
                "最高水位低于最低可用水位时，变更页不能推进本地投影");
        server.verify();
    }

    private static SimpleAkskServerProperties serverProperties() {
        SimpleAkskServerProperties properties = new SimpleAkskServerProperties();
        properties.getOwnerAuthorization().setEnabled(Boolean.TRUE);
        properties.getOwnerAuthorization().setOwnerSourceId("directory");
        return properties;
    }

    private static SimpleIamAkskCollaborationProperties httpsProperties() {
        SimpleIamAkskCollaborationProperties properties = new SimpleIamAkskCollaborationProperties();
        properties.setEnabled(Boolean.TRUE);
        properties.setTokenUri("https://iam.example/oauth2/token");
        properties.setBaseUri("https://iam.example");
        properties.setClientId("reader");
        properties.setClientSecret("secret");
        return properties;
    }

    private static String changePullBody() {
        return "{\"resyncRequired\":false,\"lowWatermark\":1,\"highWatermark\":5,"
                + "\"serverTime\":\"2026-09-29T03:00:00Z\",\"changes\":[{\"sourceSequence\":5,"
                + "\"eventId\":\"event-5\",\"changeType\":\"OWNER_APPLICATION_PROJECTION\","
                + "\"payload\":{\"active\":true}}]}";
    }
}
