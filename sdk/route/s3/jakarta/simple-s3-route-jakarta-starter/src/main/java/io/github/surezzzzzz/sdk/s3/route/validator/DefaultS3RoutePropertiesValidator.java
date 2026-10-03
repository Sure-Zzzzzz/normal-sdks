package io.github.surezzzzzz.sdk.s3.route.validator;

import io.github.surezzzzzz.sdk.s3.route.annotation.SimpleS3RouteComponent;
import io.github.surezzzzzz.sdk.s3.route.configuration.SimpleS3RouteProperties;
import io.github.surezzzzzz.sdk.s3.route.constant.*;
import io.github.surezzzzzz.sdk.s3.route.exception.S3RouteException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 默认 Route 配置校验器。
 *
 * @author surezzzzzz
 */
@SimpleS3RouteComponent
public class DefaultS3RoutePropertiesValidator implements S3RoutePropertiesValidator {

    private static final Pattern TARGET_KEY_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]*");

    private static final Pattern REGION_PATTERN = Pattern.compile("[a-z0-9]([a-z0-9-]*[a-z0-9])?");

    @Override
    public void validate(SimpleS3RouteProperties properties) {
        if (properties == null || !properties.isEnable()) {
            return;
        }
        if (properties.getShutdownTimeoutMs() <= 0) {
            fail();
        }
        if (properties.getTargets() == null || properties.getTargets().isEmpty()) {
            fail();
        }
        for (Map.Entry<String, SimpleS3RouteProperties.TargetConfig> entry : properties.getTargets().entrySet()) {
            String targetKey = entry.getKey();
            if (targetKey == null || !TARGET_KEY_PATTERN.matcher(targetKey).matches()) {
                fail();
            }
            SimpleS3RouteProperties.TargetConfig target = entry.getValue();
            if (target == null) {
                fail();
            }
            URI endpointUri = parseEndpoint(target.getEndpoint());
            validateEndpoint(endpointUri);
            validateSignerType(target.getSignerType());
            validateTrustedCaFile(target.getTrustedCaFile(), endpointUri);
            validateRegion(target.getRegion());
            validateAuthentication(target.getAuthentication());
            validateClient(target.getClient());
        }
    }

    private void validateEndpoint(URI uri) {
        String scheme = uri.getScheme();
        if (!SimpleS3RouteConstant.HTTP_SCHEME.equals(scheme) && !SimpleS3RouteConstant.HTTPS_SCHEME.equals(scheme)) {
            fail();
        }
        if (!hasText(uri.getHost())) {
            fail();
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            fail();
        }
        String path = uri.getRawPath();
        if (path != null && !path.isEmpty() && !SimpleS3RouteConstant.ROOT_PATH.equals(path)) {
            fail();
        }
    }

    private void validateSignerType(S3RouteSignerType signerType) {
        if (signerType == null) {
            fail();
        }
    }

    private void validateTrustedCaFile(String trustedCaFile, URI endpointUri) {
        if (!hasText(trustedCaFile)) {
            return;
        }
        rejectControlCharacter(trustedCaFile);
        if (!SimpleS3RouteConstant.HTTPS_SCHEME.equals(endpointUri.getScheme())) {
            throw new S3RouteException(ErrorCode.TARGET_CONFIGURATION_ILLEGAL,
                    ErrorMessage.TRUSTED_CA_ENDPOINT_NOT_HTTPS);
        }
    }

    private URI parseEndpoint(String endpoint) {
        if (!hasText(endpoint)) {
            fail();
        }
        try {
            return new URI(endpoint);
        } catch (URISyntaxException exception) {
            throw new S3RouteException(ErrorCode.TARGET_CONFIGURATION_ILLEGAL,
                    ErrorMessage.TARGET_CONFIGURATION_ILLEGAL);
        }
    }

    private void validateRegion(String region) {
        if (region == null || !REGION_PATTERN.matcher(region).matches()) {
            fail();
        }
    }

    private void validateAuthentication(SimpleS3RouteProperties.AuthenticationConfig authentication) {
        if (authentication == null || authentication.getType() == null) {
            fail();
        }
        boolean hasAccessKey = hasText(authentication.getAccessKey());
        boolean hasSecretKey = hasText(authentication.getSecretKey());
        boolean hasSessionToken = hasText(authentication.getSessionToken());
        rejectControlCharacter(authentication.getAccessKey());
        rejectControlCharacter(authentication.getSecretKey());
        rejectControlCharacter(authentication.getSessionToken());
        if (authentication.getType() == S3RouteAuthenticationType.NONE
                && (hasAccessKey || hasSecretKey || hasSessionToken)) {
            fail();
        }
        if (authentication.getType() == S3RouteAuthenticationType.ACCESS_KEY
                && (!hasAccessKey || !hasSecretKey)) {
            fail();
        }
    }

    private void validateClient(SimpleS3RouteProperties.ClientConfig client) {
        if (client == null || client.getConnectTimeoutMs() <= 0 || client.getSocketTimeoutMs() <= 0
                || client.getMaxConnections() <= 0 || client.getRequestTimeoutMs() < 0
                || client.getClientExecutionTimeoutMs() < 0 || client.getConnectionMaxIdleMs() <= 0
                || client.getConnectionTtlMs() < -1L) {
            fail();
        }
    }

    private void rejectControlCharacter(String value) {
        if (value == null) {
            return;
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                fail();
            }
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void fail() {
        throw new S3RouteException(ErrorCode.TARGET_CONFIGURATION_ILLEGAL,
                ErrorMessage.TARGET_CONFIGURATION_ILLEGAL);
    }
}
