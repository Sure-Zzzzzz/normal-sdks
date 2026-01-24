package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.configuration.SimpleAkskServerProperties;
import io.github.surezzzzzz.sdk.auth.aksk.server.test.SimpleAkskServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Admin Logout测试
 * <p>
 * 测试Admin登出功能是否正常工作
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleAkskServerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class AdminLogoutTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SimpleAkskServerProperties properties;

    @Test
    void testLogoutFunctionality() {
        log.info("测试Admin logout功能");

        String baseUrl = "http://localhost:" + port;
        String loginUrl = baseUrl + "/admin/login";
        String adminUrl = baseUrl + "/admin";
        String logoutUrl = baseUrl + "/admin/logout";

        // Step 1: 访问登录页面,获取CSRF token
        log.info("Step 1: 访问登录页面获取CSRF token");
        ResponseEntity<String> loginPageResponse = restTemplate.getForEntity(loginUrl, String.class);
        assertEquals(HttpStatus.OK, loginPageResponse.getStatusCode(), "登录页面应该可访问");

        String csrfToken = extractCsrfToken(loginPageResponse.getBody());
        assertNotNull(csrfToken, "应该能从登录页面提取CSRF token");
        log.info("提取到CSRF token: {}", csrfToken);

        List<String> cookies = loginPageResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
        String sessionCookie = extractSessionCookie(cookies);
        assertNotNull(sessionCookie, "应该能获取到session cookie");
        log.info("获取到session cookie: {}", sessionCookie);

        // Step 2: 提交登录表单
        log.info("Step 2: 提交登录表单");
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        loginHeaders.add(HttpHeaders.COOKIE, sessionCookie);

        MultiValueMap<String, String> loginForm = new LinkedMultiValueMap<>();
        loginForm.add("username", properties.getAdmin().getUsername());
        loginForm.add("password", properties.getAdmin().getPassword());
        loginForm.add("_csrf", csrfToken);

        HttpEntity<MultiValueMap<String, String>> loginRequest = new HttpEntity<>(loginForm, loginHeaders);

        // 使用exchange而不是postForEntity,以便处理重定向
        ResponseEntity<String> loginResponse = restTemplate.exchange(
                loginUrl,
                HttpMethod.POST,
                loginRequest,
                String.class
        );

        log.info("登录响应状态: {}", loginResponse.getStatusCode());

        // 登录成功后会重定向到/admin,状态码可能是302或200
        assertTrue(
                loginResponse.getStatusCode() == HttpStatus.FOUND ||
                        loginResponse.getStatusCode() == HttpStatus.OK,
                "登录应该成功"
        );

        // 更新session cookie(登录后可能会更新)
        List<String> loginCookies = loginResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (loginCookies != null && !loginCookies.isEmpty()) {
            String newSessionCookie = extractSessionCookie(loginCookies);
            if (newSessionCookie != null) {
                sessionCookie = newSessionCookie;
                log.info("更新session cookie: {}", sessionCookie);
            }
        }

        // Step 3: 访问admin页面,验证已登录
        log.info("Step 3: 访问admin页面验证已登录");
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.add(HttpHeaders.COOKIE, sessionCookie);

        HttpEntity<Void> adminRequest = new HttpEntity<>(adminHeaders);
        ResponseEntity<String> adminResponse = restTemplate.exchange(
                adminUrl,
                HttpMethod.GET,
                adminRequest,
                String.class
        );

        assertEquals(HttpStatus.OK, adminResponse.getStatusCode(), "登录后应该能访问admin页面");
        assertTrue(adminResponse.getBody().contains("AKSK管理"), "admin页面应该包含标题");
        log.info("成功访问admin页面");

        // Step 4: 从admin页面提取CSRF token用于logout
        log.info("Step 4: 从admin页面提取CSRF token");
        String logoutCsrfToken = extractCsrfToken(adminResponse.getBody());
        assertNotNull(logoutCsrfToken, "应该能从admin页面提取CSRF token");
        log.info("提取到logout CSRF token: {}", logoutCsrfToken);

        // Step 5: 执行logout
        log.info("Step 5: 执行logout");
        HttpHeaders logoutHeaders = new HttpHeaders();
        logoutHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        logoutHeaders.add(HttpHeaders.COOKIE, sessionCookie);

        MultiValueMap<String, String> logoutForm = new LinkedMultiValueMap<>();
        logoutForm.add("_csrf", logoutCsrfToken);

        HttpEntity<MultiValueMap<String, String>> logoutRequest = new HttpEntity<>(logoutForm, logoutHeaders);

        ResponseEntity<String> logoutResponse = restTemplate.exchange(
                logoutUrl,
                HttpMethod.POST,
                logoutRequest,
                String.class
        );

        log.info("Logout响应状态: {}", logoutResponse.getStatusCode());

        // Logout成功后会重定向到/login?logout
        assertTrue(
                logoutResponse.getStatusCode() == HttpStatus.FOUND ||
                        logoutResponse.getStatusCode() == HttpStatus.OK,
                "Logout应该成功"
        );

        // Step 6: 尝试再次访问admin页面,应该被重定向到登录页面
        log.info("Step 6: 验证logout后无法访问admin页面");
        ResponseEntity<String> adminAfterLogoutResponse = restTemplate.exchange(
                adminUrl,
                HttpMethod.GET,
                adminRequest,
                String.class
        );

        // Logout后访问admin应该返回302重定向到登录页面,或者401未授权
        assertTrue(
                adminAfterLogoutResponse.getStatusCode() == HttpStatus.FOUND ||
                        adminAfterLogoutResponse.getStatusCode() == HttpStatus.UNAUTHORIZED,
                "Logout后访问admin应该被重定向或返回401,实际状态: " + adminAfterLogoutResponse.getStatusCode()
        );

        log.info("Admin logout功能测试通过");
    }

    /**
     * 从HTML中提取CSRF token
     */
    private String extractCsrfToken(String html) {
        if (html == null) {
            return null;
        }

        // 匹配: <input type="hidden" name="_csrf" value="xxx" />
        Pattern pattern = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(html);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    /**
     * 从Set-Cookie headers中提取JSESSIONID cookie
     */
    private String extractSessionCookie(List<String> cookies) {
        if (cookies == null) {
            return null;
        }

        for (String cookie : cookies) {
            if (cookie.startsWith("JSESSIONID=")) {
                // 只取JSESSIONID部分,不包括其他属性
                int semicolonIndex = cookie.indexOf(';');
                if (semicolonIndex > 0) {
                    return cookie.substring(0, semicolonIndex);
                }
                return cookie;
            }
        }

        return null;
    }
}
