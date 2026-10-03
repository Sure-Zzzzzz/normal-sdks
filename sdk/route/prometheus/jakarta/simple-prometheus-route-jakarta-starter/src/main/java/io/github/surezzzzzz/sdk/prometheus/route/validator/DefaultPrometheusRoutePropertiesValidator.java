package io.github.surezzzzzz.sdk.prometheus.route.validator;

import io.github.surezzzzzz.sdk.prometheus.route.configuration.SimplePrometheusRouteProperties;
import io.github.surezzzzzz.sdk.prometheus.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.prometheus.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.prometheus.route.exception.PrometheusRouteException;
import io.github.surezzzzzz.sdk.prometheus.route.model.PrometheusRouteAuthenticationType;
import io.github.surezzzzzz.sdk.prometheus.route.transport.PrometheusRouteUriFactory;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 默认 Route 配置校验器。
 *
 * @author surezzzzzz
 */
public class DefaultPrometheusRoutePropertiesValidator implements PrometheusRoutePropertiesValidator {

    private static final Pattern TARGET_KEY_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]*");

    @Override
    public void validate(SimplePrometheusRouteProperties properties) {
        if (properties == null || !properties.isEnable()) {
            return;
        }
        if (properties.getShutdownTimeoutMs() <= 0) {
            fail();
        }
        if (properties.getTargets() == null || properties.getTargets().isEmpty()) {
            fail();
        }
        for (Map.Entry<String, SimplePrometheusRouteProperties.TargetConfig> entry : properties.getTargets().entrySet()) {
            String targetKey = entry.getKey();
            if (targetKey == null || !TARGET_KEY_PATTERN.matcher(targetKey).matches()) {
                fail();
            }
            SimplePrometheusRouteProperties.TargetConfig target = entry.getValue();
            if (target == null) {
                fail();
            }
            PrometheusRouteUriFactory.normalizeBaseUri(target.getUrl());
            validateAuthentication(target.getAuthentication());
            validateHttp(target.getHttp());
        }
    }

    private void validateAuthentication(SimplePrometheusRouteProperties.AuthenticationConfig authentication) {
        if (authentication == null || authentication.getType() == null) {
            fail();
        }
        boolean hasUsername = hasText(authentication.getUsername());
        boolean hasPassword = hasText(authentication.getPassword());
        boolean hasToken = hasText(authentication.getToken());
        rejectControlCharacter(authentication.getUsername());
        rejectControlCharacter(authentication.getPassword());
        rejectControlCharacter(authentication.getToken());
        if (authentication.getType() == PrometheusRouteAuthenticationType.NONE
                && (hasUsername || hasPassword || hasToken)) {
            fail();
        }
        if (authentication.getType() == PrometheusRouteAuthenticationType.BASIC
                && (!hasUsername || !hasPassword || hasToken)) {
            fail();
        }
        if (authentication.getType() == PrometheusRouteAuthenticationType.BEARER
                && (!hasToken || hasUsername || hasPassword)) {
            fail();
        }
    }

    private void validateHttp(SimplePrometheusRouteProperties.HttpConfig http) {
        if (http == null || http.getConnectTimeoutMs() <= 0 || http.getSocketTimeoutMs() <= 0
                || http.getConnectionRequestTimeoutMs() <= 0 || http.getValidateAfterInactivityMs() <= 0
                || http.getMaxTotal() <= 0
                || http.getMaxPerRoute() <= 0 || http.getMaxResponseBodyBytes() <= 0
                || http.getMaxPerRoute() > http.getMaxTotal()) {
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
        throw new PrometheusRouteException(ErrorCode.TARGET_CONFIGURATION_ILLEGAL,
                ErrorMessage.TARGET_CONFIGURATION_ILLEGAL);
    }
}
