package io.github.surezzzzzz.sdk.iam.resttemplate.client;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.surezzzzzz.sdk.iam.client.IamPortalApplicationClient;
import io.github.surezzzzzz.sdk.iam.client.constant.SimpleIamClientConstant;
import io.github.surezzzzzz.sdk.iam.client.exception.IamClientProtocolException;
import io.github.surezzzzzz.sdk.iam.client.model.IamPortalApplicationPage;
import io.github.surezzzzzz.sdk.iam.client.model.IamPortalApplicationRoute;
import io.github.surezzzzzz.sdk.iam.resttemplate.client.annotation.SimpleIamClientRestTemplateComponent;
import io.github.surezzzzzz.sdk.iam.resttemplate.client.configuration.SimpleIamClientRestTemplateProperties;
import io.github.surezzzzzz.sdk.iam.resttemplate.client.support.IamClientHttpSupport;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * IAM Portal 机器路由目录 RestTemplate 实现（server 1.3.8，Jakarta 宿主）。
 *
 * <p>只复用 AKSK 底座提供的认证与传输，不在 IAM client 内自行获取凭据或拼接认证头。</p>
 *
 * @author surezzzzzz
 */
@SimpleIamClientRestTemplateComponent
public class IamPortalApplicationRestTemplateClient implements IamPortalApplicationClient {

    private final IamClientHttpSupport http;

    /**
     * 创建 Portal 可信应用路由投影客户端。
     *
     * @param akskClientRestTemplate AKSK 底座认证 RestTemplate
     * @param properties             IAM client 配置
     */
    public IamPortalApplicationRestTemplateClient(
            @Qualifier("akskClientRestTemplate") RestTemplate akskClientRestTemplate,
            SimpleIamClientRestTemplateProperties properties) {
        this.http = new IamClientHttpSupport(akskClientRestTemplate, properties.getBaseUrl());
    }

    private static void requireObject(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new IamClientProtocolException();
        }
    }

    private static JsonNode requiredArray(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) {
            throw new IamClientProtocolException();
        }
        return value;
    }

    private static String requiredText(JsonNode node, String field, int minimumLength, int maximumLength) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw new IamClientProtocolException();
        }
        String text = value.textValue();
        int length = text.codePointCount(0, text.length());
        if (text.trim().isEmpty() || length < minimumLength || length > maximumLength) {
            throw new IamClientProtocolException();
        }
        return text;
    }

    private static String requiredEntry(JsonNode node) {
        String entry = requiredText(node, SimpleIamClientConstant.FIELD_ENTRY, 1, Integer.MAX_VALUE);
        if (!entry.startsWith("/") || entry.startsWith("//")) {
            throw new IamClientProtocolException();
        }
        validateRelativePath(entry);
        return entry;
    }

    private static String requiredApiBase(JsonNode node) {
        String apiBase = requiredText(node, SimpleIamClientConstant.FIELD_API_BASE, 1, Integer.MAX_VALUE);
        if (!apiBase.startsWith("/")) {
            throw new IamClientProtocolException();
        }
        validateRelativePath(apiBase);
        return apiBase;
    }

    private static String requiredTargetOrigin(JsonNode node) {
        String targetOrigin = requiredText(node, SimpleIamClientConstant.FIELD_TARGET_ORIGIN, 1, Integer.MAX_VALUE);
        URI uri = parseUri(targetOrigin);
        validateHttpOrigin(uri);
        return targetOrigin;
    }

    private static void validateRelativePath(String path) {
        if (path.startsWith("//") || containsWhitespaceOrControl(path) || path.indexOf('\\') >= 0) {
            throw new IamClientProtocolException();
        }
        URI uri = parseUri(path);
        if (uri.isAbsolute() || uri.getRawAuthority() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IamClientProtocolException();
        }
        validateRawPath(uri.getRawPath());
    }

    private static void validateHttpOrigin(URI uri) {
        String scheme = uri.getScheme();
        String authority = uri.getRawAuthority();
        int port = uri.getPort();
        if (!uri.isAbsolute() || scheme == null
                || !"http".equalsIgnoreCase(scheme)
                || authority == null || uri.getHost() == null || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !isAsciiHost(uri.getHost())
                || authority.contains("%25")
                || authority.endsWith(":") || port == 0 || port > 65535
                || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())) {
            throw new IamClientProtocolException();
        }
    }

    private static URI parseUri(String value) {
        try {
            return new URI(value);
        } catch (URISyntaxException exception) {
            throw new IamClientProtocolException();
        }
    }

    private static void validateRawPath(String path) {
        if (path == null) {
            return;
        }
        if (path.contains("//")) {
            throw new IamClientProtocolException();
        }
        String[] segments = path.split("/", -1);
        for (String segment : segments) {
            String comparable = segment.toLowerCase(Locale.ROOT).replace("%2e", ".");
            if (".".equals(comparable) || "..".equals(comparable)) {
                throw new IamClientProtocolException();
            }
            for (int index = 0; index + 2 < segment.length(); index++) {
                if (segment.charAt(index) == '%') {
                    int high = Character.digit(segment.charAt(index + 1), 16);
                    int low = Character.digit(segment.charAt(index + 2), 16);
                    if (high < 0 || low < 0 || high * 16 + low == '/' || high * 16 + low == '\\') {
                        throw new IamClientProtocolException();
                    }
                    index += 2;
                }
            }
        }
    }

    private static boolean isAsciiHost(String host) {
        for (int index = 0; index < host.length(); index++) {
            char value = host.charAt(index);
            if (value > 0x7f || Character.isWhitespace(value) || Character.isISOControl(value)) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsWhitespaceOrControl(String value) {
        for (int index = 0; index < value.length(); ) {
            int codePoint = value.codePointAt(index);
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)
                    || Character.isISOControl(codePoint)) {
                return true;
            }
            index += Character.charCount(codePoint);
        }
        return false;
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new IamClientProtocolException();
        }
        return value.textValue();
    }

    private static int requiredInt(JsonNode node, String field, int minimum) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IamClientProtocolException();
        }
        int result = value.intValue();
        if (result < minimum) {
            throw new IamClientProtocolException();
        }
        return result;
    }

    private static long requiredLong(JsonNode node, String field, long minimum) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            throw new IamClientProtocolException();
        }
        long result = value.longValue();
        if (result < minimum) {
            throw new IamClientProtocolException();
        }
        return result;
    }

    private static boolean requiredBoolean(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isBoolean()) {
            throw new IamClientProtocolException();
        }
        return value.booleanValue();
    }

    @Override
    public IamPortalApplicationPage listApplications(Integer page, Integer size, Long catalogRevision) {
        IamClientHttpSupport.Query query = new IamClientHttpSupport.Query()
                .add(SimpleIamClientConstant.QUERY_PAGE, page)
                .add(SimpleIamClientConstant.QUERY_SIZE, size)
                .add(SimpleIamClientConstant.QUERY_CATALOG_REVISION, catalogRevision);
        JsonNode node = http.get(query, SimpleIamClientConstant.RESOURCE_PORTAL,
                SimpleIamClientConstant.RESOURCE_APPLICATIONS);
        requireObject(node);
        List<IamPortalApplicationRoute> items = new ArrayList<IamPortalApplicationRoute>();
        JsonNode itemNode = requiredArray(node, SimpleIamClientConstant.FIELD_ITEMS);
        for (JsonNode item : itemNode) {
            requireObject(item);
            String applicationCode = requiredText(item, "applicationCode", 1, 64);
            if (!applicationCode.matches("[A-Za-z0-9_-]{1,64}")) {
                throw new IamClientProtocolException();
            }
            String applicationName = requiredText(item, "applicationName", 1, 128);
            int status = requiredInt(item, "status", 1);
            if (status != 1) {
                throw new IamClientProtocolException();
            }
            boolean portalEnabled = requiredBoolean(item, SimpleIamClientConstant.FIELD_PORTAL_ENABLED);
            if (!portalEnabled) {
                throw new IamClientProtocolException();
            }
            String routePrefix = requiredText(item, SimpleIamClientConstant.FIELD_ROUTE_PREFIX, 2, 128);
            if (!routePrefix.startsWith("/") || routePrefix.startsWith("//") || routePrefix.endsWith("/")) {
                throw new IamClientProtocolException();
            }
            validateRelativePath(routePrefix);
            String targetOrigin = requiredTargetOrigin(item);
            String entry = requiredEntry(item);
            if (!entry.equals(routePrefix) && !entry.startsWith(routePrefix + "/")) {
                throw new IamClientProtocolException();
            }
            String apiBase = requiredApiBase(item);
            long configVersion = requiredLong(item, SimpleIamClientConstant.FIELD_CONFIG_VERSION, 1L);
            String forwardAuthorization = requiredText(item,
                    SimpleIamClientConstant.FIELD_FORWARD_AUTHORIZATION, 1, Integer.MAX_VALUE);
            if (!SimpleIamClientConstant.FORWARD_AUTHORIZATION_REQUEST.equals(forwardAuthorization)) {
                throw new IamClientProtocolException();
            }
            JsonNode cookieNode = requiredArray(item, SimpleIamClientConstant.FIELD_FORWARD_COOKIE_NAMES);
            if (!cookieNode.isEmpty()) {
                throw new IamClientProtocolException();
            }
            items.add(IamPortalApplicationRoute.builder()
                    .applicationCode(applicationCode)
                    .applicationName(applicationName)
                    .status(Integer.valueOf(status))
                    .portalEnabled(Boolean.valueOf(portalEnabled))
                    .routePrefix(routePrefix)
                    .entry(entry)
                    .apiBase(apiBase)
                    .targetOrigin(targetOrigin)
                    .configVersion(Long.valueOf(configVersion))
                    .forwardAuthorization(forwardAuthorization)
                    .forwardCookieNames(new ArrayList<String>())
                    .build());
        }
        return IamPortalApplicationPage.builder()
                .catalogRevision(Long.valueOf(requiredLong(node, SimpleIamClientConstant.FIELD_CATALOG_REVISION, 1L)))
                .totalElements(requiredLong(node, SimpleIamClientConstant.FIELD_TOTAL_ELEMENTS, 0L))
                .page(requiredInt(node, SimpleIamClientConstant.FIELD_PAGE, 0))
                .size(requiredInt(node, SimpleIamClientConstant.FIELD_SIZE, 1))
                .items(items)
                .build();
    }
}
