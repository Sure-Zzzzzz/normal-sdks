package io.github.surezzzzzz.sdk.kms.server.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException;
import io.github.surezzzzzz.sdk.kms.server.configuration.SmartKmsServerProperties;
import io.github.surezzzzzz.sdk.kms.server.exception.KmsPayloadTooLargeException;
import io.github.surezzzzzz.sdk.kms.server.exception.KmsUnauthenticatedException;
import io.github.surezzzzzz.sdk.kms.server.service.KmsManagementIdempotencyResult;
import io.github.surezzzzzz.sdk.kms.server.service.KmsPrincipalResolver;
import io.github.surezzzzzz.sdk.kms.server.service.KmsRequestContext;
import io.github.surezzzzzz.sdk.kms.server.support.KmsHttpJson;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * KMS REST 控制器公共安全边界。
 *
 * @author surezzzzzz
 */
abstract class KmsHttpControllerSupport {

    static final String JSON = "application/json";
    static final String JSON_UTF8 = "application/json;charset=UTF-8";
    static final String REQUEST_CONTEXT_ATTRIBUTE = KmsHttpControllerSupport.class.getName() + ".requestContext";
    private final KmsPrincipalResolver principalResolver;
    private final SmartKmsServerProperties properties;

    KmsHttpControllerSupport(KmsPrincipalResolver principalResolver, SmartKmsServerProperties properties) {
        this.principalResolver = principalResolver;
        this.properties = properties;
    }

    private static int encodedBase64urlLength(int maximumBytes) {
        if (maximumBytes < 0) {
            throw new KmsValidationException();
        }
        return ((maximumBytes + 2) / 3) * 4;
    }

    KmsRequestContext context(HttpServletRequest request) {
        KmsRequestContext context = principalResolver.resolve(request);
        if (context == null || context.getPrincipal() == null || context.getRequestId() == null) {
            throw new KmsUnauthenticatedException();
        }
        request.setAttribute(REQUEST_CONTEXT_ATTRIBUTE, context);
        return context;
    }

    ObjectNode object(String body, String... fields) {
        return KmsHttpJson.parseObject(body, fields);
    }

    String text(ObjectNode object, String field, boolean required) {
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) {
            if (required) {
                throw new KmsValidationException();
            }
            return null;
        }
        if (!value.isTextual()) {
            throw new KmsValidationException();
        }
        return value.textValue();
    }

    Integer integer(ObjectNode object, String field, boolean required) {
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) {
            if (required) {
                throw new KmsValidationException();
            }
            return null;
        }
        if (!value.canConvertToInt()) {
            throw new KmsValidationException();
        }
        return value.intValue();
    }

    Long longValue(ObjectNode object, String field, boolean required) {
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) {
            if (required) {
                throw new KmsValidationException();
            }
            return null;
        }
        if (!value.canConvertToLong()) {
            throw new KmsValidationException();
        }
        return value.longValue();
    }

    Instant instant(ObjectNode object, String field, boolean required) {
        String value = text(object, field, required);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            throw new KmsValidationException();
        }
    }

    byte[] base64url(ObjectNode object, String field, boolean required, int maximumBytes) {
        String value = text(object, field, required);
        if (value == null) {
            return null;
        }
        if (value.indexOf('=') >= 0) {
            throw new KmsValidationException();
        }
        if (value.length() > encodedBase64urlLength(maximumBytes)) {
            throw new KmsPayloadTooLargeException();
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            if (decoded.length > maximumBytes) {
                throw new KmsPayloadTooLargeException();
            }
            return decoded;
        } catch (KmsPayloadTooLargeException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new KmsValidationException();
        }
    }

    byte[] signingInput(ObjectNode object, String field, boolean required) {
        return base64url(object, field, required, cryptoLimit(properties.getCrypto().getMaxSigningInputBytes()));
    }

    byte[] signature(ObjectNode object, String field, boolean required) {
        return base64url(object, field, required, cryptoLimit(properties.getCrypto().getMaxSignatureBytes()));
    }

    byte[] plaintext(ObjectNode object, String field, boolean required) {
        return base64url(object, field, required, cryptoLimit(properties.getCrypto().getMaxPlaintextBytes()));
    }

    byte[] aad(ObjectNode object, String field, boolean required) {
        return base64url(object, field, required, cryptoLimit(properties.getCrypto().getMaxAadBytes()));
    }

    byte[] envelope(ObjectNode object, String field, boolean required) {
        return base64url(object, field, required, cryptoLimit(properties.getCrypto().getMaxEnvelopeBytes()));
    }

    int pageDefaultSize() {
        if (properties.getPage() == null || properties.getPage().getDefaultSize() == null
                || properties.getPage().getDefaultSize().intValue() < 1) {
            throw new KmsValidationException();
        }
        return properties.getPage().getDefaultSize().intValue();
    }

    int pageMaxSize(int defaultSize) {
        if (properties.getPage() == null || properties.getPage().getMaxSize() == null
                || properties.getPage().getMaxSize().intValue() < defaultSize) {
            throw new KmsValidationException();
        }
        return properties.getPage().getMaxSize().intValue();
    }

    private int cryptoLimit(Integer maximumBytes) {
        if (properties.getCrypto() == null || maximumBytes == null || maximumBytes.intValue() < 1) {
            throw new KmsValidationException();
        }
        return maximumBytes.intValue();
    }

    String base64url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    String canonicalRequest(String endpoint, ObjectNode body) {
        return endpoint + "\n" + KmsHttpJson.canonical(body);
    }

    ResponseEntity<String> idempotent(KmsManagementIdempotencyResult result) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(result.getStatus());
        if (result.getLocation() != null) {
            builder.header(HttpHeaders.LOCATION, result.getLocation());
        }
        if (result.getResponseBody() == null) {
            return builder.build();
        }
        return builder.contentType(MediaType.parseMediaType(JSON_UTF8)).body(result.getResponseBody());
    }

    ResponseEntity<String> json(int status, Map<String, Object> body) {
        return ResponseEntity.status(status).contentType(MediaType.parseMediaType(JSON_UTF8))
                .body(KmsHttpJson.write(body));
    }

    ResponseEntity<String> jsonWithHeader(int status, Map<String, Object> body, String headerName,
                                          String headerValue) {
        return ResponseEntity.status(status).contentType(MediaType.parseMediaType(JSON_UTF8))
                .header(headerName, headerValue).body(KmsHttpJson.write(body));
    }

    ResponseEntity<String> jsonArrayWithHeader(int status, java.util.List<Map<String, Object>> body,
                                               String headerName, String headerValue) {
        return ResponseEntity.status(status).contentType(MediaType.parseMediaType(JSON_UTF8))
                .header(headerName, headerValue).body(KmsHttpJson.write(body));
    }

    Map<String, Object> map() {
        return new LinkedHashMap<String, Object>();
    }
}
