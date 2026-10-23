package io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.service;

import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.constant.SimpleIamPortalGatewayAdaptorConstant;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.PortalApplicationRoute;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.ResolvedTarget;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.RouteKind;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.RouteSnapshot;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.RouteSnapshotBuildResult;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.RouteValidationReason;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.RouteValidationResult;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.TargetUrlPolicy;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.TrustedApplicationRoute;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.spi.DnsResolver;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 将 IAM Portal 路由投影构建为可整体替换的稳定快照。
 *
 * <p>该类只处理纯 Java 归一化和策略判断；DNS 解析通过 {@link DnsResolver} 注入。</p>
 *
 * @author surezzzzzz
 */
public class RouteSnapshotBuilder {

    private static final Comparator<TrustedApplicationRoute> ROUTE_ORDER =
            new Comparator<TrustedApplicationRoute>() {
                @Override
                public int compare(TrustedApplicationRoute left, TrustedApplicationRoute right) {
                    int value = left.getPublicPath().compareTo(right.getPublicPath());
                    if (value != 0) {
                        return value;
                    }
                    value = left.getKind().getCode().compareTo(right.getKind().getCode());
                    if (value != 0) {
                        return value;
                    }
                    value = left.getApplicationCode().compareTo(right.getApplicationCode());
                    if (value != 0) {
                        return value;
                    }
                    return left.getTarget().getIp().compareTo(right.getTarget().getIp());
                }
            };

    /**
     * 构建全量快照。任何一条路由不通过时都不返回部分快照。
     *
     * @param catalogRevision IAM 目录版本
     * @param policy          目标策略
     * @param routes          IAM Portal 路由投影
     * @param resolver        runtime 提供的 DNS 解析器
     * @return 全量快照或结构化拒绝结果
     */
    public RouteSnapshotBuildResult build(Long catalogRevision,
                                          TargetUrlPolicy policy,
                                          List<PortalApplicationRoute> routes,
                                          DnsResolver resolver) {
        if (catalogRevision == null || catalogRevision.longValue() < 0L
                || policy == null || resolver == null || routes == null) {
            return RouteSnapshotBuildResult.rejected(
                    RouteValidationResult.rejected(RouteValidationReason.DIRECTORY_INCOMPLETE));
        }

        List<TrustedApplicationRoute> normalized = new ArrayList<TrustedApplicationRoute>();
        Set<String> routePrefixes = new HashSet<String>();
        for (PortalApplicationRoute route : routes) {
            RouteValidationResult validation = validateRoute(route, policy, routePrefixes);
            if (!validation.isAccepted()) {
                return RouteSnapshotBuildResult.rejected(validation);
            }
            routePrefixes.add(route.getRoutePrefix());

            URI targetOrigin = parseTargetOrigin(route.getTargetOrigin());
            if (targetOrigin == null) {
                return RouteSnapshotBuildResult.rejected(
                        RouteValidationResult.rejected(RouteValidationReason.INVALID_TARGET_ORIGIN,
                                route.getApplicationCode()));
            }
            String targetScheme = targetOrigin.getScheme().toLowerCase(Locale.ROOT);
            int targetPort = effectivePort(targetOrigin.getPort());
            List<ResolvedTarget> targets = resolve(resolver, targetScheme, targetOrigin.getHost(), targetPort);
            if (!validTargets(targets, targetScheme, targetOrigin.getHost(), targetPort, policy)) {
                return RouteSnapshotBuildResult.rejected(
                        RouteValidationResult.rejected(RouteValidationReason.DNS_RESOLUTION_FAILED,
                                route.getApplicationCode()));
            }
            for (ResolvedTarget target : targets) {
                normalized.add(toPageRoute(route, target));
            }

            String apiPath = normalizePath(route.getApiBase());
            for (ResolvedTarget target : targets) {
                normalized.add(toApiRoute(route, target, apiPath));
            }
        }

        Collections.sort(normalized, ROUTE_ORDER);
        Map<String, String> routeOwners = new HashMap<String, String>();
        for (TrustedApplicationRoute route : normalized) {
            String previousOwner = routeOwners.get(route.getPublicPath());
            if (previousOwner != null && !previousOwner.equals(route.getApplicationCode())) {
                return RouteSnapshotBuildResult.rejected(RouteValidationResult.rejected(
                        RouteValidationReason.DUPLICATE_ROUTE, route.getApplicationCode()));
            }
            routeOwners.put(route.getPublicPath(), route.getApplicationCode());
        }
        for (int index = 0; index < normalized.size(); index++) {
            TrustedApplicationRoute left = normalized.get(index);
            for (int otherIndex = index + 1; otherIndex < normalized.size(); otherIndex++) {
                TrustedApplicationRoute right = normalized.get(otherIndex);
                if (left.getApplicationCode().equals(right.getApplicationCode())) {
                    continue;
                }
                if (sameOrChild(left.getPublicPath(), right.getPublicPath())
                        || sameOrChild(right.getPublicPath(), left.getPublicPath())) {
                    return RouteSnapshotBuildResult.rejected(RouteValidationResult.rejected(
                            RouteValidationReason.DUPLICATE_ROUTE, right.getApplicationCode()));
                }
            }
        }
        String canonical = canonical(catalogRevision.longValue(), policy.getVersion(), normalized);
        String digest = sha256(canonical);
        if (digest == null) {
            return RouteSnapshotBuildResult.rejected(
                    RouteValidationResult.rejected(RouteValidationReason.SNAPSHOT_BUILD_FAILED));
        }
        String targetDigest = digest.substring(0, Math.min(16, digest.length()));
        RouteSnapshot snapshot = RouteSnapshot.builder()
                .catalogRevision(catalogRevision.longValue())
                .policyVersion(policy.getVersion())
                .snapshotVersion(digest)
                .targetDigest(targetDigest)
                .routes(normalized)
                .build();
        return RouteSnapshotBuildResult.accepted(snapshot);
    }

    private RouteValidationResult validateRoute(PortalApplicationRoute route,
                                                TargetUrlPolicy policy,
                                                Set<String> existingPrefixes) {
        if (route == null || !hasText(route.getApplicationCode())) {
            return RouteValidationResult.rejected(RouteValidationReason.DIRECTORY_INCOMPLETE);
        }
        if (!validApplicationCode(route.getApplicationCode())) {
            return RouteValidationResult.rejected(RouteValidationReason.INVALID_APPLICATION_CODE,
                    route.getApplicationCode());
        }
        if (!validApplicationName(route.getApplicationName())) {
            return RouteValidationResult.rejected(RouteValidationReason.INVALID_APPLICATION_NAME,
                    route.getApplicationCode());
        }
        if (!Integer.valueOf(SimpleIamPortalGatewayAdaptorConstant.STATUS_ACTIVE).equals(route.getStatus())) {
            return RouteValidationResult.rejected(RouteValidationReason.INACTIVE_APPLICATION,
                    route.getApplicationCode());
        }
        if (!Boolean.TRUE.equals(route.getPortalEnabled())) {
            return RouteValidationResult.rejected(RouteValidationReason.PORTAL_DISABLED,
                    route.getApplicationCode());
        }
        if (route.getConfigVersion() == null || route.getConfigVersion().longValue() <= 0L) {
            return RouteValidationResult.rejected(RouteValidationReason.INVALID_CONFIG_VERSION,
                    route.getApplicationCode());
        }
        if (!SimpleIamPortalGatewayAdaptorConstant.FORWARD_AUTHORIZATION_NONE.equals(route.getForwardAuthorization())
                && !SimpleIamPortalGatewayAdaptorConstant.FORWARD_AUTHORIZATION_REQUEST
                .equals(route.getForwardAuthorization())) {
            return RouteValidationResult.rejected(RouteValidationReason.INVALID_FORWARD_POLICY,
                    route.getApplicationCode());
        }
        if (route.getForwardCookieNames() != null) {
            for (String cookieName : route.getForwardCookieNames()) {
                if (!validCookieName(cookieName)) {
                    return RouteValidationResult.rejected(RouteValidationReason.INVALID_COOKIE_NAME,
                            route.getApplicationCode());
                }
            }
        }
        if (!validPath(route.getRoutePrefix()) || "/".equals(route.getRoutePrefix())
                || route.getRoutePrefix().codePointCount(0, route.getRoutePrefix().length())
                > SimpleIamPortalGatewayAdaptorConstant.MAX_ROUTE_PREFIX_CODE_POINTS) {
            return RouteValidationResult.rejected(RouteValidationReason.INVALID_ROUTE_PREFIX,
                    route.getApplicationCode());
        }
        if (policy.isReservedPrefix(route.getRoutePrefix())) {
            return RouteValidationResult.rejected(RouteValidationReason.RESERVED_ROUTE_PREFIX,
                    route.getApplicationCode());
        }
        for (String existingPrefix : existingPrefixes) {
            if (sameOrChild(existingPrefix, route.getRoutePrefix())
                    || sameOrChild(route.getRoutePrefix(), existingPrefix)) {
                return RouteValidationResult.rejected(RouteValidationReason.ROUTE_PREFIX_CONFLICT,
                        route.getApplicationCode());
            }
        }
        if (!validPublicPath(route.getEntry())
                || !sameOrChild(route.getEntry(), route.getRoutePrefix())) {
            return RouteValidationResult.rejected(RouteValidationReason.INVALID_ENTRY,
                    route.getApplicationCode());
        }
        if (!validPublicPath(route.getApiBase())) {
            return RouteValidationResult.rejected(RouteValidationReason.INVALID_API_BASE,
                    route.getApplicationCode());
        }
        if (policy.isReservedPrefix(route.getApiBase())) {
            return RouteValidationResult.rejected(RouteValidationReason.RESERVED_API_BASE,
                    route.getApplicationCode());
        }
        URI targetOrigin = parseTargetOrigin(route.getTargetOrigin());
        if (targetOrigin == null) {
            return RouteValidationResult.rejected(RouteValidationReason.INVALID_TARGET_ORIGIN,
                    route.getApplicationCode());
        }
        int port = effectivePort(targetOrigin.getPort());
        if (!policy.isSchemeAllowed(targetOrigin.getScheme())) {
            return RouteValidationResult.rejected(RouteValidationReason.UNSUPPORTED_SCHEME,
                    route.getApplicationCode());
        }
        if (!policy.isPortAllowed(port)) {
            return RouteValidationResult.rejected(RouteValidationReason.INVALID_PORT,
                    route.getApplicationCode());
        }
        return RouteValidationResult.accepted();
    }

    private URI parseTargetOrigin(String value) {
        if (!hasText(value) || value.codePointCount(0, value.length())
                > SimpleIamPortalGatewayAdaptorConstant.MAX_PUBLIC_PATH_CODE_POINTS
                || hasUnsafeCharacters(value)) {
            return null;
        }
        try {
            URI uri = new URI(value);
            if (!uri.isAbsolute() || !"http".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null
                    || !isAsciiHost(uri.getHost())
                    || (uri.getRawAuthority() != null && uri.getRawAuthority().contains("%25"))
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                    || uri.getPort() > 65535) {
                return null;
            }
            return uri;
        } catch (URISyntaxException ex) {
            return null;
        }
    }

    private int effectivePort(int port) {
        if (port >= 0) {
            return port;
        }
        return SimpleIamPortalGatewayAdaptorConstant.PORT_HTTP;
    }

    private List<ResolvedTarget> resolve(DnsResolver resolver, String scheme, String host, int port) {
        try {
            List<ResolvedTarget> targets = resolver.resolve(scheme, host, port);
            return targets == null ? Collections.<ResolvedTarget>emptyList() : targets;
        } catch (RuntimeException ex) {
            return Collections.emptyList();
        }
    }

    private boolean validTargets(List<ResolvedTarget> targets,
                                 String scheme,
                                 String host,
                                 int port,
                                 TargetUrlPolicy policy) {
        if (targets.isEmpty()) {
            return false;
        }
        for (ResolvedTarget target : targets) {
            if (target == null || !hasText(target.getIp()) || !hasText(target.getHost())
                    || !hasText(target.getScheme()) || !scheme.equalsIgnoreCase(target.getScheme())
                    || !host.equalsIgnoreCase(target.getHost()) || target.getPort() != port
                    || !policy.isTargetAllowed(target.getIp())) {
                return false;
            }
        }
        return true;
    }

    private TrustedApplicationRoute toPageRoute(PortalApplicationRoute route,
                                                ResolvedTarget target) {
        return routeBuilder(route, RouteKind.PAGE, route.getRoutePrefix(), route.getRoutePrefix(), target);
    }

    private TrustedApplicationRoute toApiRoute(PortalApplicationRoute route,
                                               ResolvedTarget target,
                                               String path) {
        return routeBuilder(route, RouteKind.API, path, path, target);
    }

    private TrustedApplicationRoute routeBuilder(PortalApplicationRoute route,
                                                 RouteKind kind,
                                                 String publicPath,
                                                 String upstreamPath,
                                                 ResolvedTarget target) {
        TrustedApplicationRoute.TrustedApplicationRouteBuilder builder = TrustedApplicationRoute.builder()
                .applicationCode(route.getApplicationCode())
                .applicationName(route.getApplicationName())
                .kind(kind)
                .publicPath(publicPath)
                .upstreamPath(upstreamPath)
                .target(target)
                .upstreamHost(target.getHost())
                .configVersion(route.getConfigVersion())
                .forwardAuthorization(route.getForwardAuthorization());
        List<String> cookieNames = route.getForwardCookieNames() == null
                ? new ArrayList<String>() : new ArrayList<String>(route.getForwardCookieNames());
        Collections.sort(cookieNames);
        for (String cookieName : cookieNames) {
            builder.forwardCookieName(cookieName);
        }
        return builder.build();
    }

    private String normalizePath(String path) {
        if (!hasText(path)) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private boolean validPath(String path) {
        return hasText(path) && path.codePointCount(0, path.length())
                <= SimpleIamPortalGatewayAdaptorConstant.MAX_PUBLIC_PATH_CODE_POINTS
                && path.startsWith("/") && !path.endsWith("/")
                && !path.contains("//")
                && path.indexOf('?') < 0 && path.indexOf('#') < 0
                && !hasInvalidPath(path);
    }

    private boolean validPublicPath(String path) {
        if (!hasText(path) || !path.startsWith("/") || path.startsWith("//")) {
            return false;
        }
        if (path.codePointCount(0, path.length())
                > SimpleIamPortalGatewayAdaptorConstant.MAX_PUBLIC_PATH_CODE_POINTS) {
            return false;
        }
        if ("/".equals(path)) {
            return true;
        }
        String withoutTrailingSlash = path.endsWith("/")
                ? path.substring(0, path.length() - 1) : path;
        return validPath(withoutTrailingSlash);
    }

    private boolean validApplicationCode(String value) {
        return value != null && value.matches(SimpleIamPortalGatewayAdaptorConstant.APPLICATION_CODE_PATTERN);
    }

    private boolean validApplicationName(String value) {
        if (!hasText(value) || value.codePointCount(0, value.length())
                > SimpleIamPortalGatewayAdaptorConstant.MAX_APPLICATION_NAME_CODE_POINTS) {
            return false;
        }
        for (int index = 0; index < value.length();) {
            int codePoint = value.codePointAt(index);
            if (Character.isISOControl(codePoint)) {
                return false;
            }
            index += Character.charCount(codePoint);
        }
        return true;
    }

    private boolean validCookieName(String value) {
        if (!hasText(value)) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch <= 0x20 || ch >= 0x7f || "()<>@,;:\\[]?={} \"".indexOf(ch) >= 0) {
                return false;
            }
        }
        return true;
    }

    private boolean hasInvalidPath(String path) {
        if (path == null) {
            return false;
        }
        if (path.indexOf('\\') >= 0 || path.contains("//")) {
            return true;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.contains("%2f") || lower.contains("%5c") || lower.contains("%25")
                || lower.contains("%2e") || lower.contains("%00")
                || lower.contains("%3f") || lower.contains("%23")) {
            return true;
        }
        for (int i = 0; i < path.length(); i++) {
            char ch = path.charAt(i);
            if (Character.isISOControl(ch) || Character.isWhitespace(ch) || Character.isSpaceChar(ch)) {
                return true;
            }
            if (ch == '%') {
                if (i + 2 >= path.length() || !isHex(path.charAt(i + 1)) || !isHex(path.charAt(i + 2))) {
                    return true;
                }
                int decoded = Integer.parseInt(path.substring(i + 1, i + 3), 16);
                if (decoded <= 0x20 || decoded == 0x7f) {
                    return true;
                }
                i += 2;
            }
        }
        String[] segments = path.split("/", -1);
        for (String segment : segments) {
            if (".".equals(segment) || "..".equals(segment)) {
                return true;
            }
        }
        return false;
    }

    private boolean isHex(char ch) {
        return (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f')
                || (ch >= 'A' && ch <= 'F');
    }

    private boolean isAsciiHost(String host) {
        for (int index = 0; index < host.length(); index++) {
            char value = host.charAt(index);
            if (value > 0x7f || Character.isWhitespace(value) || Character.isISOControl(value)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasUnsafeCharacters(String value) {
        for (int index = 0; index < value.length();) {
            int codePoint = value.codePointAt(index);
            if (Character.isISOControl(codePoint) || Character.isWhitespace(codePoint)
                    || Character.isSpaceChar(codePoint)) {
                return true;
            }
            index += Character.charCount(codePoint);
        }
        return false;
    }

    private boolean sameOrChild(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private String canonical(long catalogRevision, String policyVersion,
                             List<TrustedApplicationRoute> routes) {
        StringBuilder builder = new StringBuilder();
        appendCanonicalField(builder, String.valueOf(catalogRevision));
        appendCanonicalField(builder, policyVersion);
        for (TrustedApplicationRoute route : routes) {
            ResolvedTarget target = route.getTarget();
            appendCanonicalField(builder, route.getApplicationCode());
            appendCanonicalField(builder, route.getApplicationName());
            appendCanonicalField(builder, String.valueOf(route.getConfigVersion()));
            appendCanonicalField(builder, route.getKind().getCode());
            appendCanonicalField(builder, route.getPublicPath());
            appendCanonicalField(builder, route.getUpstreamPath());
            appendCanonicalField(builder, route.getUpstreamHost());
            appendCanonicalField(builder, route.getForwardAuthorization());
            appendCanonicalList(builder, route.getForwardCookieNames());
            appendCanonicalField(builder, target.getScheme());
            appendCanonicalField(builder, target.getHost());
            appendCanonicalField(builder, target.getIp());
            appendCanonicalField(builder, String.valueOf(target.getPort()));
        }
        return builder.toString();
    }

    private void appendCanonicalField(StringBuilder builder, String value) {
        if (value == null) {
            builder.append("-1:");
            return;
        }
        builder.append(value.length()).append(':').append(value);
    }

    private void appendCanonicalList(StringBuilder builder, List<String> values) {
        if (values == null) {
            appendCanonicalField(builder, null);
            return;
        }
        appendCanonicalField(builder, String.valueOf(values.size()));
        for (String value : values) {
            appendCanonicalField(builder, value);
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                builder.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            return null;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
