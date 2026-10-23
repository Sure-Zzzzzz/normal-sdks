package io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model;

import lombok.Getter;

/**
 * 路由投影被接受或拒绝的结构化原因。
 *
 * @author surezzzzzz
 */
@Getter
public enum RouteValidationReason {
    ACCEPTED("accepted"),
    EMPTY_DIRECTORY("empty-directory"),
    INACTIVE_APPLICATION("inactive-application"),
    PORTAL_DISABLED("portal-disabled"),
    INVALID_ROUTE_PREFIX("invalid-route-prefix"),
    RESERVED_ROUTE_PREFIX("reserved-route-prefix"),
    RESERVED_API_BASE("reserved-api-base"),
    ROUTE_PREFIX_CONFLICT("route-prefix-conflict"),
    INVALID_ENTRY("invalid-entry"),
    INVALID_API_BASE("invalid-api-base"),
    INVALID_TARGET_ORIGIN("invalid-target-origin"),
    UNSUPPORTED_SCHEME("unsupported-scheme"),
    INVALID_HOST("invalid-host"),
    USERINFO_NOT_ALLOWED("userinfo-not-allowed"),
    QUERY_NOT_ALLOWED("query-not-allowed"),
    FRAGMENT_NOT_ALLOWED("fragment-not-allowed"),
    INVALID_PATH("invalid-path"),
    INVALID_PORT("invalid-port"),
    DNS_RESOLUTION_FAILED("dns-resolution-failed"),
    TARGET_NOT_ALLOWED("target-not-allowed"),
    REVISION_MISMATCH("revision-mismatch"),
    DIRECTORY_INCOMPLETE("directory-incomplete"),
    DUPLICATE_ROUTE("duplicate-route"),
    INVALID_APPLICATION_CODE("invalid-application-code"),
    INVALID_APPLICATION_NAME("invalid-application-name"),
    INVALID_CONFIG_VERSION("invalid-config-version"),
    INVALID_FORWARD_POLICY("invalid-forward-policy"),
    INVALID_COOKIE_NAME("invalid-cookie-name"),
    SNAPSHOT_BUILD_FAILED("snapshot-build-failed");

    private final String code;

    RouteValidationReason(String code) {
        this.code = code;
    }
}
