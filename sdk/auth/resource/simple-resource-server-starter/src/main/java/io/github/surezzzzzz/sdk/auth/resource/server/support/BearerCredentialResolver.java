package io.github.surezzzzzz.sdk.auth.resource.server.support;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceAuthenticationFailureCategory;
import io.github.surezzzzzz.sdk.auth.resource.core.model.BearerResourceCredential;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationSourceId;
import io.github.surezzzzzz.sdk.auth.resource.server.constant.SimpleResourceServerStarterConstant;

import javax.servlet.http.HttpServletRequest;
import java.util.Base64;
import java.util.Enumeration;

/**
 * 单一Bearer凭据解析器。
 *
 * @author surezzzzzz
 */
public final class BearerCredentialResolver {

    private static final ObjectMapper JOSE_HEADER_MAPPER = createJoseHeaderMapper();

    private static ObjectMapper createJoseHeaderMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
        return mapper;
    }

    /**
     * 解析单一Bearer凭据。
     *
     * @param request HTTP请求
     * @return 解析结果
     */
    public BearerCredentialResolution resolve(HttpServletRequest request) {
        Enumeration<String> cookieHeaders = request.getHeaders(SimpleResourceServerStarterConstant.HEADER_COOKIE);
        if (cookieHeaders != null) {
            while (cookieHeaders.hasMoreElements()) {
                String cookieHeader = cookieHeaders.nextElement();
                if (cookieHeader != null && !cookieHeader.trim().isEmpty()) {
                    return BearerCredentialResolution.rejected(ResourceAuthenticationFailureCategory.CREDENTIAL_AMBIGUOUS);
                }
            }
        }
        Enumeration<String> headerValues = request.getHeaders(SimpleResourceServerStarterConstant.HEADER_AUTHORIZATION);
        if (headerValues == null || !headerValues.hasMoreElements()) {
            return BearerCredentialResolution.rejected(ResourceAuthenticationFailureCategory.CREDENTIAL_MISSING);
        }
        String headerValue = headerValues.nextElement();
        if (headerValues.hasMoreElements() || headerValue == null) {
            return BearerCredentialResolution.rejected(ResourceAuthenticationFailureCategory.CREDENTIAL_AMBIGUOUS);
        }
        String prefix = SimpleResourceServerStarterConstant.AUTHORIZATION_SCHEME_BEARER
                + SimpleResourceServerStarterConstant.HEADER_VALUE_SEPARATOR;
        if (!headerValue.startsWith(prefix) || headerValue.length() == prefix.length()
                || headerValue.indexOf(SimpleResourceServerStarterConstant.HEADER_VALUE_SEPARATOR, prefix.length()) >= 0) {
            return BearerCredentialResolution.rejected(ResourceAuthenticationFailureCategory.CREDENTIAL_MALFORMED);
        }
        String token = headerValue.substring(prefix.length());
        ResourceAuthenticationSourceId sourceId = resolveSourceId(token);
        if (sourceId == null) {
            return BearerCredentialResolution.rejected(ResourceAuthenticationFailureCategory.SOURCE_UNRECOGNIZED);
        }
        return BearerCredentialResolution.resolved(new BearerResourceCredential(sourceId, token));
    }

    private ResourceAuthenticationSourceId resolveSourceId(String token) {
        int firstSeparatorIndex = token.indexOf(SimpleResourceServerStarterConstant.JOSE_SEGMENT_SEPARATOR);
        String encodedHeader = firstSeparatorIndex < 0 ? token : token.substring(0, firstSeparatorIndex);
        if (encodedHeader.length() == 0 || encodedHeader.length()
                > SimpleResourceServerStarterConstant.MAX_JOSE_HEADER_ENCODED_CHARACTER_COUNT) {
            return null;
        }
        try {
            byte[] headerBytes = Base64.getUrlDecoder().decode(encodedHeader);
            if (headerBytes.length > SimpleResourceServerStarterConstant.MAX_JOSE_HEADER_BYTE_COUNT) {
                return null;
            }
            JsonNode header = JOSE_HEADER_MAPPER.readTree(headerBytes);
            if (header == null || !header.isObject()) {
                return null;
            }
            JsonNode kidNode = header.get(SimpleResourceServerStarterConstant.JOSE_HEADER_FIELD_KID);
            if (kidNode == null || !kidNode.isTextual()) {
                return null;
            }
            String kid = kidNode.textValue();
            if (kid.length() == 0 || kid.codePointCount(0, kid.length())
                    > SimpleResourceServerStarterConstant.MAX_JOSE_KID_LENGTH) {
                return null;
            }
            int separatorIndex = kid.indexOf(SimpleResourceServerStarterConstant.KID_SOURCE_SEPARATOR);
            if (separatorIndex <= 0 || separatorIndex != kid.lastIndexOf(SimpleResourceServerStarterConstant.KID_SOURCE_SEPARATOR)
                    || separatorIndex == kid.length() - 1) {
                return null;
            }
            return new ResourceAuthenticationSourceId(kid.substring(0, separatorIndex));
        } catch (Exception exception) {
            return null;
        }
    }
}
