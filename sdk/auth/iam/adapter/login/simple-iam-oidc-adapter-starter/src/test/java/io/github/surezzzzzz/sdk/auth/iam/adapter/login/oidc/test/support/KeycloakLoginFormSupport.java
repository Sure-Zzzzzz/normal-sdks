package io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.test.support;

import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 测试用 Keycloak 登录表单自动化支持。
 *
 * <p>以 JDK HttpURLConnection + CookieManager 驱动真实 Keycloak 授权码流程：
 * 打开授权地址 → 提交登录表单 → 跟随重定向直到回调地址，解析出 code 与 state。</p>
 *
 * @author surezzzzzz
 */
public final class KeycloakLoginFormSupport {

    private static final Pattern FORM_ACTION_PATTERN =
            Pattern.compile("(?i)<form[^>]+action=\"([^\"]+)\"");

    private static final int MAX_REDIRECTS = 6;

    /**
     * 最近一次 GET 的 Location（简单跟随链路用，仅测试单线程使用）
     */
    private static String currentLocation;

    private KeycloakLoginFormSupport() {
        throw new UnsupportedOperationException("测试支持类不允许实例化");
    }

    /**
     * 完成登录并返回回调查询参数（code、state）
     *
     * @param authorizeUrl IAM 生成的授权地址
     * @param username     Keycloak fixture 用户名
     * @param password     Keycloak fixture 密码
     * @return 回调地址查询参数
     */
    public static Map<String, String> completeLogin(String authorizeUrl, String username,
                                                    String password) throws Exception {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        String loginHtml = getToContent(authorizeUrl, cookies, 0);
        String formAction = extractFormAction(loginHtml, authorizeUrl);

        HttpURLConnection connection = open(formAction);
        connection.setRequestMethod("POST");
        connection.setInstanceFollowRedirects(false);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        applyCookies(formAction, cookies, connection);
        byte[] form = ("username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8)
                + "&credentialId=").getBytes(StandardCharsets.UTF_8);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(form);
        }
        cookies.put(URI.create(formAction), connection.getHeaderFields());
        int status = connection.getResponseCode();

        String location = connection.getHeaderField("Location");
        int depth = 0;
        while (status >= 300 && status < 400 && location != null && depth < MAX_REDIRECTS) {
            if (location.startsWith("http://localhost:18080")) {
                return UriComponentsBuilder.fromUriString(location)
                        .build().getQueryParams().toSingleValueMap();
            }
            status = getToStatus(location, cookies);
            location = currentLocation;
            depth++;
        }
        return failWith("Keycloak 登录后未跳转到回调地址，最后状态码 " + status);
    }

    private static String getToContent(String url, CookieManager cookies, int depth)
            throws Exception {
        if (depth > MAX_REDIRECTS) {
            throw new AssertionError("重定向层级过深");
        }
        HttpURLConnection connection = open(url);
        connection.setRequestMethod("GET");
        connection.setInstanceFollowRedirects(false);
        applyCookies(url, cookies, connection);
        int status = connection.getResponseCode();
        cookies.put(URI.create(url), connection.getHeaderFields());
        String location = connection.getHeaderField("Location");
        if (status >= 300 && status < 400 && StringUtils.hasText(location)) {
            return getToContent(absolute(location, url), cookies, depth + 1);
        }
        currentLocation = url;
        return readBody(connection);
    }

    private static int getToStatus(String url, CookieManager cookies) throws Exception {
        HttpURLConnection connection = open(url);
        connection.setRequestMethod("GET");
        connection.setInstanceFollowRedirects(false);
        applyCookies(url, cookies, connection);
        int status = connection.getResponseCode();
        cookies.put(URI.create(url), connection.getHeaderFields());
        currentLocation = connection.getHeaderField("Location");
        return status;
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection connection =
                (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("User-Agent", "sure-iam-oidc-e2e-test");
        return connection;
    }

    private static void applyCookies(String url, CookieManager cookies,
                                     HttpURLConnection connection) throws Exception {
        // Keycloak 26 的 AUTH_SESSION_ID 带 Secure 属性，JDK CookieStore.get(URI) 会对 http 请求
        // 过滤 Secure cookie；fixture 是 localhost 明文端口，浏览器语义（localhost 属 secure
        // context）应当照发，故绕开该过滤，自遍历 store 并按 path 前缀匹配
        String path = URI.create(url).getPath();
        StringBuilder value = new StringBuilder();
        for (java.net.HttpCookie cookie : cookies.getCookieStore().getCookies()) {
            String cookiePath = cookie.getPath() == null ? "/" : cookie.getPath();
            if (!path.startsWith(cookiePath)) {
                continue;
            }
            if (value.length() > 0) {
                value.append("; ");
            }
            value.append(cookie.getName()).append('=').append(cookie.getValue());
        }
        if (value.length() > 0) {
            connection.setRequestProperty("Cookie", value.toString());
        }
    }

    private static String extractFormAction(String html, String pageUrl) {
        Matcher matcher = FORM_ACTION_PATTERN.matcher(html);
        if (!matcher.find()) {
            throw new AssertionError("未在 Keycloak 登录页找到表单");
        }
        return absolute(matcher.group(1).replace("&amp;", "&"), pageUrl);
    }

    private static Map<String, String> failWith(String message) {
        throw new AssertionError(message);
    }

    private static String absolute(String location, String baseUrl) {
        if (location.startsWith("http://") || location.startsWith("https://")) {
            return location;
        }
        String origin = baseUrl.substring(0, baseUrl.indexOf('/', "https://".length()));
        return location.startsWith("/") ? origin + location : origin + "/" + location;
    }

    private static String readBody(HttpURLConnection connection) throws Exception {
        try (InputStream input = connection.getResponseCode() < 400
                ? connection.getInputStream() : connection.getErrorStream()) {
            if (input == null) {
                return "";
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = input.read(chunk)) >= 0) {
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
