package io.github.surezzzzzz.sdk.auth.resource.server.test.cases;

import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceAuthenticationFailureCategory;
import io.github.surezzzzzz.sdk.auth.resource.server.support.BearerCredentialResolution;
import io.github.surezzzzzz.sdk.auth.resource.server.support.BearerCredentialResolver;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bearer凭据路由测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class BearerCredentialResolverTest {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String COOKIE_HEADER = "Cookie";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String SOURCE_ID = "iam";
    private static final String KEY_ID = "key-a";

    private final BearerCredentialResolver resolver = new BearerCredentialResolver();

    /**
     * 验证受限解析只从外层受保护头读取来源。
     */
    @Test
    void shouldResolveSourceFromBoundedOuterJoseHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AUTHORIZATION_HEADER, BEARER_PREFIX + token("{\"alg\":\"dir\",\"enc\":\"A256GCM\",\"kid\":\"iam/key-a\"}"));

        BearerCredentialResolution resolution = resolver.resolve(request);

        log.info("Bearer外层头来源解析结果: {}", resolution.isResolved());
        assertTrue(resolution.isResolved(), "合法受保护头必须解析唯一来源");
        assertEquals(SOURCE_ID, resolution.getCredential().getSourceId().getValue(), "来源必须来自kid命名空间");
    }

    /**
     * 验证路由不依赖令牌段数，后续协议有效性只能由已选择的Provider判定。
     */
    @Test
    void shouldResolveSourceWithoutUsingTokenSegmentCount() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"kid\":\"iam/key-a\"}".getBytes(StandardCharsets.UTF_8));
        request.addHeader(AUTHORIZATION_HEADER, BEARER_PREFIX + header + ".truncated");

        BearerCredentialResolution resolution = resolver.resolve(request);

        assertTrue(resolution.isResolved(), "路由只能读取受保护头，不能以令牌段数拒绝来源");
        assertEquals(SOURCE_ID, resolution.getCredential().getSourceId().getValue(),
                "残缺令牌只能被路由至kid声明的来源");
    }

    /**
     * 验证重复kid被拒绝，避免解析器选择其中一个值。
     */
    @Test
    void shouldRejectDuplicateKid() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AUTHORIZATION_HEADER, BEARER_PREFIX + token("{\"kid\":\"iam/key-a\",\"kid\":\"aksk/key-b\"}"));

        BearerCredentialResolution resolution = resolver.resolve(request);

        log.info("重复kid解析结果: {}", resolution.isResolved());
        assertFalse(resolution.isResolved(), "重复kid必须拒绝");
        assertEquals(ResourceAuthenticationFailureCategory.SOURCE_UNRECOGNIZED,
                resolution.getFailureCategory(), "重复kid不能选择来源");
    }

    /**
     * 验证缺失和格式错误的Authorization请求头不能进入Provider。
     */
    @Test
    void shouldRejectMissingOrMalformedAuthorizationHeader() {
        MockHttpServletRequest missingRequest = new MockHttpServletRequest();
        MockHttpServletRequest wrongSchemeRequest = new MockHttpServletRequest();
        wrongSchemeRequest.addHeader(AUTHORIZATION_HEADER, "Basic credential");
        MockHttpServletRequest blankTokenRequest = new MockHttpServletRequest();
        blankTokenRequest.addHeader(AUTHORIZATION_HEADER, BEARER_PREFIX);
        MockHttpServletRequest whitespaceTokenRequest = new MockHttpServletRequest();
        whitespaceTokenRequest.addHeader(AUTHORIZATION_HEADER, BEARER_PREFIX + "credential extra");

        assertEquals(ResourceAuthenticationFailureCategory.CREDENTIAL_MISSING,
                resolver.resolve(missingRequest).getFailureCategory(), "缺失Authorization必须拒绝");
        assertEquals(ResourceAuthenticationFailureCategory.CREDENTIAL_MALFORMED,
                resolver.resolve(wrongSchemeRequest).getFailureCategory(), "非Bearer方案必须拒绝");
        assertEquals(ResourceAuthenticationFailureCategory.CREDENTIAL_MALFORMED,
                resolver.resolve(blankTokenRequest).getFailureCategory(), "空Bearer必须拒绝");
        assertEquals(ResourceAuthenticationFailureCategory.CREDENTIAL_MALFORMED,
                resolver.resolve(whitespaceTokenRequest).getFailureCategory(), "含分隔空白的Bearer必须拒绝");
    }

    /**
     * 验证多个Bearer和Cookie组合均不能选择身份来源。
     */
    @Test
    void shouldRejectAmbiguousCredentialCarriers() {
        MockHttpServletRequest multipleBearerRequest = new MockHttpServletRequest();
        multipleBearerRequest.addHeader(AUTHORIZATION_HEADER, BEARER_PREFIX + token("{\"kid\":\"iam/key-a\"}"));
        multipleBearerRequest.addHeader(AUTHORIZATION_HEADER, BEARER_PREFIX + token("{\"kid\":\"aksk/key-b\"}"));

        BearerCredentialResolution multipleBearer = resolver.resolve(multipleBearerRequest);

        MockHttpServletRequest cookieAndBearerRequest = new MockHttpServletRequest();
        cookieAndBearerRequest.addHeader(COOKIE_HEADER, "session=opaque-value");
        cookieAndBearerRequest.addHeader(AUTHORIZATION_HEADER, BEARER_PREFIX + token("{\"kid\":\"iam/key-a\"}"));
        MockHttpServletRequest cookieOnlyRequest = new MockHttpServletRequest();
        cookieOnlyRequest.addHeader(COOKIE_HEADER, "session=opaque-value");
        MockHttpServletRequest multipleCookieRequest = new MockHttpServletRequest();
        multipleCookieRequest.addHeader(COOKIE_HEADER, "session=opaque-value");
        multipleCookieRequest.addHeader(COOKIE_HEADER, "other=opaque-value");

        BearerCredentialResolution cookieAndBearer = resolver.resolve(cookieAndBearerRequest);
        BearerCredentialResolution cookieOnly = resolver.resolve(cookieOnlyRequest);
        BearerCredentialResolution multipleCookie = resolver.resolve(multipleCookieRequest);

        log.info("多Bearer、Cookie组合与Cookie单独载体均被拒绝");
        assertEquals(ResourceAuthenticationFailureCategory.CREDENTIAL_AMBIGUOUS,
                multipleBearer.getFailureCategory(), "多个Bearer必须拒绝");
        assertEquals(ResourceAuthenticationFailureCategory.CREDENTIAL_AMBIGUOUS,
                cookieAndBearer.getFailureCategory(), "Cookie与Bearer组合必须拒绝");
        assertEquals(ResourceAuthenticationFailureCategory.CREDENTIAL_AMBIGUOUS,
                cookieOnly.getFailureCategory(), "Cookie单独作为认证载体必须拒绝");
        assertEquals(ResourceAuthenticationFailureCategory.CREDENTIAL_AMBIGUOUS,
                multipleCookie.getFailureCategory(), "多个Cookie请求头中的任一载体都必须拒绝");
    }

    /**
     * 验证非法格式、缺失kid和超限受保护头拒绝。
     */
    @Test
    void shouldRejectMalformedOrUnrouteableCredential() {
        MockHttpServletRequest malformedRequest = new MockHttpServletRequest();
        malformedRequest.addHeader(AUTHORIZATION_HEADER, BEARER_PREFIX + "not-a-jwe");
        MockHttpServletRequest missingKidRequest = new MockHttpServletRequest();
        missingKidRequest.addHeader(AUTHORIZATION_HEADER, BEARER_PREFIX + token("{\"alg\":\"dir\"}"));
        MockHttpServletRequest oversizedHeaderRequest = new MockHttpServletRequest();
        oversizedHeaderRequest.addHeader(AUTHORIZATION_HEADER, BEARER_PREFIX + token(headerWithSize(2048)));

        BearerCredentialResolution malformed = resolver.resolve(malformedRequest);
        BearerCredentialResolution missingKid = resolver.resolve(missingKidRequest);
        BearerCredentialResolution oversized = resolver.resolve(oversizedHeaderRequest);

        log.info("畸形、缺少kid与超限头均被拒绝");
        assertEquals(ResourceAuthenticationFailureCategory.SOURCE_UNRECOGNIZED, malformed.getFailureCategory(),
                "无可解析受保护头不得推断来源");
        assertEquals(ResourceAuthenticationFailureCategory.SOURCE_UNRECOGNIZED, missingKid.getFailureCategory(),
                "缺少kid不得选择来源");
        assertEquals(ResourceAuthenticationFailureCategory.SOURCE_UNRECOGNIZED, oversized.getFailureCategory(),
                "超限受保护头不得选择来源");
    }

    private String token(String header) {
        String encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(header.getBytes(StandardCharsets.UTF_8));
        return encodedHeader + ".encrypted-key.iv.cipher-text.authentication-tag";
    }

    private String headerWithSize(int size) {
        StringBuilder builder = new StringBuilder(size);
        builder.append('{').append("\"kid\":\"iam/");
        while (builder.length() < size - 2) {
            builder.append('a');
        }
        builder.append("\"}");
        return builder.toString();
    }
}
