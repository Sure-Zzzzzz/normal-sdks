package io.github.surezzzzzz.sdk.auth.iam.resource.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.resource.server.configuration.SimpleIamResourceServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.resource.server.exception.IamResourceVerificationProtocolException;
import io.github.surezzzzzz.sdk.auth.iam.resource.server.support.HttpIamResourceTokenVerificationClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * IAM受控令牌验证HTTP客户端测试。
 *
 * @author surezzzzzz
 */
class HttpIamResourceTokenVerificationClientTest {

    @Test
    void shouldUseIndependentBasicCredentialsAndAcceptExactControlledResponse() {
        RestTemplate restTemplate = new RestTemplate();
        HttpIamResourceTokenVerificationClient client = new HttpIamResourceTokenVerificationClient(properties(), restTemplate);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://iam.example/iam/resource/tokens/verify"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic dmVyaWZpZXI6c2VjcmV0"))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"sub\":\"user-a\",\"iam_authorization\":{}}"));

        Map<String, Object> claims = client.verify("bearer-token");

        server.verify();
        assertEquals("user-a", claims.get("sub"), "受控响应只保留主体");
        assertEquals(2, claims.size(), "不得透传或接收额外验证响应字段");
    }

    @Test
    void shouldTreatUnauthorizedAsInactiveAndRejectUnexpectedResponseShape() {
        RestTemplate unauthorizedTemplate = new RestTemplate();
        HttpIamResourceTokenVerificationClient unauthorizedClient = new HttpIamResourceTokenVerificationClient(
                properties(), unauthorizedTemplate);
        MockRestServiceServer unauthorizedServer = MockRestServiceServer.bindTo(unauthorizedTemplate).build();
        unauthorizedServer.expect(requestTo("http://iam.example/iam/resource/tokens/verify"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertNull(unauthorizedClient.verify("bearer-token"), "IAM拒绝令牌必须映射为未认证");
        unauthorizedServer.verify();

        RestTemplate malformedTemplate = new RestTemplate();
        HttpIamResourceTokenVerificationClient malformedClient = new HttpIamResourceTokenVerificationClient(
                properties(), malformedTemplate);
        MockRestServiceServer malformedServer = MockRestServiceServer.bindTo(malformedTemplate).build();
        malformedServer.expect(requestTo("http://iam.example/iam/resource/tokens/verify"))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"sub\":\"user-a\",\"iam_authorization\":{},\"roles\":[]}"));

        assertThrows(IamResourceVerificationProtocolException.class,
                () -> malformedClient.verify("bearer-token"), "额外响应字段必须拒绝");
        malformedServer.verify();
    }

    private SimpleIamResourceServerProperties properties() {
        SimpleIamResourceServerProperties properties = new SimpleIamResourceServerProperties();
        properties.setVerificationEndpoint("http://iam.example/iam/resource/tokens/verify");
        properties.setClientId("verifier");
        properties.setClientSecret("secret");
        return properties;
    }
}
