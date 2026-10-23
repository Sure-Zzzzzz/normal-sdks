package io.github.surezzzzzz.sdk.iam.feign.client.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.surezzzzzz.sdk.iam.client.constant.SimpleIamClientConstant;
import io.github.surezzzzzz.sdk.iam.client.exception.IamClientProtocolException;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * IAM Portal 可信应用路由投影 wire 模型（1.3.8，Jakarta 宿主）。
 *
 * @author surezzzzzz
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Slf4j
public class IamPortalApplicationResponse {

    public String applicationCode;
    public String applicationName;
    public Integer status;
    public Boolean portalEnabled;
    public String routePrefix;
    public String entry;
    public String apiBase;
    public String targetOrigin;
    public Long configVersion;
    public String forwardAuthorization;
    public List<String> forwardCookieNames;

    public IamPortalApplicationResponse() {
    }

    /**
     * 仅在解码 IAM 机器目录响应时执行 wire 契约校验，不改变调用方的 Jackson 全局配置。
     */
    @JsonCreator
    public IamPortalApplicationResponse(
            @JsonProperty("applicationCode") Object applicationCode,
            @JsonProperty("applicationName") Object applicationName,
            @JsonProperty("status") Object status,
            @JsonProperty("portalEnabled") Object portalEnabled,
            @JsonProperty("routePrefix") Object routePrefix,
            @JsonProperty("entry") Object entry,
            @JsonProperty("apiBase") Object apiBase,
            @JsonProperty("targetOrigin") Object targetOrigin,
            @JsonProperty("configVersion") Object configVersion,
            @JsonProperty("forwardAuthorization") Object forwardAuthorization,
            @JsonProperty("forwardCookieNames") Object forwardCookieNames) {
        this.applicationCode = requireApplicationCode(applicationCode);
        this.applicationName = requireApplicationName(applicationName);
        this.status = Integer.valueOf((int) requireInteger(status, 1L, 1L, "status"));
        if (!Boolean.TRUE.equals(portalEnabled)) {
            throw protocolError("portalEnabled");
        }
        this.portalEnabled = Boolean.TRUE;
        this.routePrefix = requireRoutePrefix(routePrefix);
        this.targetOrigin = requireTargetOrigin(targetOrigin);
        this.entry = requireEntry(entry, this.routePrefix);
        this.apiBase = requireApiBase(apiBase);
        this.configVersion = Long.valueOf(requireInteger(configVersion, 1L, Long.MAX_VALUE, "configVersion"));
        if (!(forwardAuthorization instanceof String)
                || !SimpleIamClientConstant.FORWARD_AUTHORIZATION_REQUEST.equals(forwardAuthorization)) {
            throw protocolError("forwardAuthorization");
        }
        this.forwardAuthorization = (String) forwardAuthorization;
        if (!(forwardCookieNames instanceof List) || !((List<?>) forwardCookieNames).isEmpty()) {
            throw protocolError("forwardCookieNames");
        }
        this.forwardCookieNames = new ArrayList<String>();
    }

    private static String requireApplicationCode(Object value) {
        if (!(value instanceof String)) {
            throw protocolError("applicationCode");
        }
        String code = (String) value;
        int length = code.codePointCount(0, code.length());
        if (length < 1 || length > 64) {
            throw protocolError("applicationCode");
        }
        for (int index = 0; index < code.length(); index++) {
            char character = code.charAt(index);
            if (!(character >= 'A' && character <= 'Z')
                    && !(character >= 'a' && character <= 'z')
                    && !(character >= '0' && character <= '9')
                    && character != '_' && character != '-') {
                throw protocolError("applicationCode");
            }
        }
        return code;
    }

    private static String requireApplicationName(Object value) {
        if (!(value instanceof String)) {
            throw protocolError("applicationName");
        }
        String name = (String) value;
        int length = name.codePointCount(0, name.length());
        if (length < 1 || length > 128 || !hasNonWhitespace(name)) {
            throw protocolError("applicationName");
        }
        return name;
    }

    private static String requireRoutePrefix(Object value) {
        if (!(value instanceof String)) {
            throw protocolError("routePrefix");
        }
        String path = (String) value;
        int length = path.codePointCount(0, path.length());
        if (length < 2 || length > 128 || !path.startsWith("/") || path.startsWith("//")
                || path.endsWith("/")) {
            throw protocolError("routePrefix");
        }
        validateRelativePath(path, "routePrefix");
        return path;
    }

    private static String requireEntry(Object value, String routePrefix) {
        if (!(value instanceof String) || ((String) value).isEmpty()) {
            throw protocolError("entry");
        }
        String entry = (String) value;
        if (!entry.startsWith("/") || entry.startsWith("//")) {
            throw protocolError("entry");
        }
        validateRelativePath(entry, "entry");
        if (!entry.equals(routePrefix) && !entry.startsWith(routePrefix + "/")) {
            throw protocolError("entry");
        }
        return entry;
    }

    private static String requireApiBase(Object value) {
        if (!(value instanceof String)) {
            throw protocolError("apiBase");
        }
        String apiBase = (String) value;
        if (!apiBase.startsWith("/")) {
            throw protocolError("apiBase");
        }
        validateRelativePath(apiBase, "apiBase");
        return apiBase;
    }

    private static String requireTargetOrigin(Object value) {
        if (!(value instanceof String) || ((String) value).isEmpty()) {
            throw protocolError("targetOrigin");
        }
        String targetOrigin = (String) value;
        validateHttpOrigin(parseUri(targetOrigin, "targetOrigin"), "targetOrigin");
        return targetOrigin;
    }

    private static void validateRelativePath(String path, String field) {
        if (path.startsWith("//") || containsWhitespaceOrControl(path) || path.indexOf('\\') >= 0) {
            throw protocolError(field);
        }
        URI uri = parseUri(path, field);
        if (uri.isAbsolute() || uri.getRawAuthority() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw protocolError(field);
        }
        validateRawPath(uri.getRawPath(), field);
    }

    private static void validateHttpOrigin(URI uri, String field) {
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
            throw protocolError(field);
        }
    }

    private static URI parseUri(String value, String field) {
        try {
            return new URI(value);
        } catch (URISyntaxException exception) {
            throw protocolError(field);
        }
    }

    private static void validateRawPath(String path, String field) {
        if (path == null) {
            return;
        }
        if (path.contains("//")) {
            throw protocolError(field);
        }
        String[] segments = path.split("/", -1);
        for (String segment : segments) {
            String comparable = segment.toLowerCase(Locale.ROOT).replace("%2e", ".");
            if (".".equals(comparable) || "..".equals(comparable)) {
                throw protocolError(field);
            }
            for (int index = 0; index + 2 < segment.length(); index++) {
                if (segment.charAt(index) == '%') {
                    int high = Character.digit(segment.charAt(index + 1), 16);
                    int low = Character.digit(segment.charAt(index + 2), 16);
                    int decoded = high * 16 + low;
                    if (decoded == '/' || decoded == '\\') {
                        throw protocolError(field);
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

    private static boolean hasNonWhitespace(String value) {
        for (int index = 0; index < value.length(); ) {
            int codePoint = value.codePointAt(index);
            if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) {
                return true;
            }
            index += Character.charCount(codePoint);
        }
        return false;
    }

    private static long requireInteger(Object value, long minimum, long maximum, String field) {
        if (!(value instanceof Number)) {
            throw protocolError(field);
        }
        Number number = (Number) value;
        long result;
        if (number instanceof Integer || number instanceof Long
                || number instanceof Short || number instanceof Byte) {
            result = number.longValue();
        } else if (number instanceof java.math.BigInteger) {
            java.math.BigInteger bigInteger = (java.math.BigInteger) number;
            if (bigInteger.compareTo(java.math.BigInteger.valueOf(minimum)) < 0
                    || bigInteger.compareTo(java.math.BigInteger.valueOf(maximum)) > 0) {
                throw protocolError(field);
            }
            result = bigInteger.longValue();
        } else {
            throw protocolError(field);
        }
        if (result < minimum || result > maximum) {
            throw protocolError(field);
        }
        return result;
    }

    private static IamClientProtocolException protocolError(String field) {
        log.debug("IAM Portal 机器目录响应违反 wire 契约，字段={}", field);
        return new IamClientProtocolException();
    }
}
