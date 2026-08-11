package io.github.surezzzzzz.sdk.ops.middleware.test.cases;

import io.github.surezzzzzz.sdk.ops.middleware.test.SmartMiddlewareOpsServerTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Windows AD LDAP 表单登录与共享会话测试。
 *
 * @author surezzzzzz
 */
@SpringBootTest(classes = SmartMiddlewareOpsServerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MiddlewareOpsLdapFormLoginTest {

    private static final Pattern CSRF_TOKEN = Pattern.compile("name=\\\"_csrf\\\" value=\\\"([^\\\"]+)\\\"");

    @Value("${io.github.surezzzzzz.sdk.ops.middleware.test.ldap.user-password}")
    private String ldapUserPassword;

    @Value("${io.github.surezzzzzz.sdk.ops.middleware.ui-base-path}")
    private String uiBasePath;

    @Value("${io.github.surezzzzzz.sdk.ops.middleware.api-base-path}")
    private String apiBasePath;

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @Test
    void shouldAuthenticateWithFormLoginAndReuseSessionForApi() {
        ResponseEntity<String> root = restTemplate.getForEntity(url("/"), String.class);
        assertEquals(302, root.getStatusCodeValue());
        assertEquals(uiBasePath + "/login", root.getHeaders().getLocation().getPath());
        assertNull(root.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE));

        ResponseEntity<String> favicon = restTemplate.getForEntity(uiUrl("/favicon.svg"), String.class);
        assertEquals(200, favicon.getStatusCodeValue());
        assertTrue(favicon.getHeaders().getContentType().includes(MediaType.valueOf("image/svg+xml")));
        assertNull(favicon.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE));

        ResponseEntity<String> stylesheet = restTemplate.getForEntity(uiUrl("/console.css"), String.class);
        assertEquals(200, stylesheet.getStatusCodeValue());
        assertTrue(stylesheet.getHeaders().getContentType().includes(MediaType.valueOf("text/css")));
        assertTrue(stylesheet.getBody().contains(".ops-sidebar"));
        assertTrue(stylesheet.getBody().contains("width: min(100%, 1920px); margin: 0 auto;"));
        assertTrue(stylesheet.getBody().contains("min-height: 100dvh;"));
        assertTrue(stylesheet.getBody().contains("@media (min-width: 1180px)"));
        assertTrue(stylesheet.getBody().contains(".ops-shell { display: flex; min-height: 100vh; min-height: 100dvh; flex-direction: column; }"));
        assertTrue(stylesheet.getBody().contains(".ops-main { display: flex; min-height: 0; flex: 1; flex-direction: column; }"));
        assertTrue(stylesheet.getBody().contains(".ops-workspace.is-active { display: flex; min-height: 0; flex: 1; flex-direction: column; }"));
        assertTrue(stylesheet.getBody().contains(".ops-section[data-section-panel=\"console\"].is-active, .ops-section[data-section-panel=\"audit\"].is-active { display: flex; min-height: 0; flex: 1; flex-direction: column; }"));
        assertTrue(stylesheet.getBody().contains("@media (min-width: 1440px)"));
        assertTrue(stylesheet.getBody().contains("@media (min-width: 2560px)"));
        assertTrue(stylesheet.getBody().contains(".ops-main { padding-right: 40px; padding-left: 40px; }"));
        assertTrue(stylesheet.getBody().contains(".ops-workspace { width: min(100%, 2048px); }"));
        assertTrue(stylesheet.getBody().contains("@media (min-width: 3200px)"));
        assertTrue(stylesheet.getBody().contains("@media (max-width: 1179px)"));
        assertTrue(stylesheet.getBody().contains("@media (max-width: 767px)"));
        assertTrue(stylesheet.getBody().contains("@media (max-width: 479px)"));
        assertFalse(stylesheet.getBody().contains("body { min-width: 1180px;"));
        assertTrue(stylesheet.getBody().contains(".ops-table-wrap { overflow: auto;"));
        assertTrue(stylesheet.getBody().contains(".ops-console-panel-toggle"));
        assertTrue(stylesheet.getBody().contains(".ops-console-panel-toggle:focus-visible"));
        assertTrue(stylesheet.getBody().contains(".ops-console-panel-toggle[aria-expanded=\"true\"]"));
        assertTrue(stylesheet.getBody().contains(".ops-result-surface"));
        assertTrue(stylesheet.getBody().contains(".ops-status-card"));
        assertTrue(stylesheet.getBody().contains(".ops-status-card-note"));
        assertTrue(stylesheet.getBody().contains(".ops-pagination[hidden] { display: none; }"));
        assertTrue(stylesheet.getBody().contains(".ops-result-table"));
        assertTrue(stylesheet.getBody().contains(".ops-result-action"));
        assertTrue(stylesheet.getBody().contains(".ops-datasource-card .ops-datasource-detail"));
        assertFalse(stylesheet.getBody().contains("[data-workspace-panel=\"elasticsearch\"] .ops-datasource-card .ops-datasource-detail"));
        assertTrue(stylesheet.getBody().contains(".ops-table-wrap th:nth-child(1)"));
        assertFalse(stylesheet.getBody().contains("\nth:nth-child(1) { width: 150px;"));

        ResponseEntity<String> script = restTemplate.getForEntity(uiUrl("/console.js"), String.class);
        assertEquals(200, script.getStatusCodeValue());
        assertTrue(script.getHeaders().getContentType().includes(MediaType.valueOf("application/javascript")));
        assertTrue(script.getBody().contains("var workspaceNames = ['elasticsearch', 'redis', 'kafka', 'mysql'];"));
        assertTrue(script.getBody().contains("selectedDatasourceKey"));
        assertTrue(script.getBody().contains("auditPendingSnapshot"));
        assertTrue(script.getBody().contains("auditRequested"));
        assertTrue(script.getBody().contains("loadElasticsearchIndices"));
        assertTrue(script.getBody().contains("loadElasticsearchDocuments"));
        assertTrue(script.getBody().contains("auditContext(workspace, item)"));
        assertTrue(script.getBody().contains("auditOperation(item.capability)"));
        assertTrue(script.getBody().contains("ELASTICSEARCH_DOCUMENT_QUERY: '查询文档'"));
        assertTrue(script.getBody().contains("版本状态：已验证一致"));
        assertTrue(script.getBody().contains("版本状态：已验证不一致"));
        assertTrue(script.getBody().contains("版本状态：已探测，未配置期望版本"));
        assertTrue(script.getBody().contains("版本状态：尚未探测"));
        assertFalse(script.getBody().contains("一致或未探测"));
        assertTrue(script.getBody().contains("controls.range.value = 'custom'"));
        assertTrue(script.getBody().contains("AbortController"));
        assertTrue(script.getBody().contains("var defaultConsolePanels"));
        assertTrue(script.getBody().contains("function setConsolePanelExpanded(workspace, panelName)"));
        assertTrue(script.getBody().contains("function fillKafkaTopic(topic)"));
        assertTrue(script.getBody().contains("function fillKafkaGroup(groupId)"));
        assertTrue(script.getBody().contains("function renderResultTable(id, caption, headers, items, appendRow)"));
        assertTrue(script.getBody().contains("function renderStatusCard(id, title, fields, note)"));
        assertTrue(script.getBody().contains("普通只读保护限制普通账号写入"));
        assertTrue(script.getBody().contains("强制只读保护会同时限制具备更高权限的会话"));
        assertFalse(script.getBody().contains("resetConsolePanel(workspace);\n        if (workspace === 'kafka')"));
        assertTrue(script.getBody().contains("function renderStructuredResult(id, content)"));
        assertTrue(script.getBody().contains("function renderKafkaDatasourceDiagnostics(content)"));
        assertTrue(script.getBody().contains("'诊断原因：' + tagText(detail.diagnosticReason)"));
        assertTrue(script.getBody().contains("'/redis/datasources/overview'"));
        assertTrue(script.getBody().contains("'/kafka/datasources/overview'"));
        assertTrue(script.getBody().contains("'/overview-status'"));
        assertTrue(script.getBody().contains("'连接状态：' + booleanValue(detail.connected, '已连接', '未连接')"));
        assertTrue(script.getBody().contains("'强制只读保护：' + booleanValue(detail.superReadOnly, '已开启', '未开启')"));
        assertTrue(script.getBody().contains("function renderKafkaTopicList(content)"));
        assertTrue(script.getBody().contains("function renderKafkaGroupList(content)"));
        assertTrue(script.getBody().contains("function renderMysqlSelect(content)"));
        assertTrue(script.getBody().contains("function renderKafkaTopicRuntime(content)"));
        assertTrue(script.getBody().contains("function renderKafkaLag(content)"));
        assertTrue(script.getBody().contains("kafka: 'topic-list'"));
        assertTrue(script.getBody().contains("content.columns, content.rows"));
        assertTrue(script.getBody().contains("button.setAttribute('aria-label', ariaLabel)"));
        assertTrue(script.getBody().contains("fillKafkaTopic(item.name);"));
        assertTrue(script.getBody().contains("fillKafkaGroup(item.groupId);"));
        assertTrue(script.getBody().contains("fillKafkaTopic(item.topic);"));
        assertFalse(script.getBody().contains("innerHTML"));
        assertFalse(script.getBody().contains("outerHTML"));
        assertFalse(script.getBody().contains("insertAdjacentHTML"));
        assertFalse(script.getBody().contains("localStorage"));
        assertFalse(script.getBody().contains("sessionStorage"));
        assertFalse(script.getBody().contains("indexedDB"));

        ResponseEntity<String> uiUnauthenticated = restTemplate.getForEntity(uiUrl(""), String.class);
        assertEquals(302, uiUnauthenticated.getStatusCodeValue());
        assertEquals(uiBasePath + "/login", uiUnauthenticated.getHeaders().getLocation().getPath());
        assertNull(uiUnauthenticated.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE));

        ResponseEntity<String> apiUnauthenticated = restTemplate.getForEntity(apiUrl("/elasticsearch/catalog"), String.class);
        assertEquals(401, apiUnauthenticated.getStatusCodeValue());
        assertTrue(apiUnauthenticated.getHeaders().getContentType().includes(MediaType.APPLICATION_JSON));

        ResponseEntity<String> loginPage = restTemplate.getForEntity(uiUrl("/login"), String.class);
        assertEquals(200, loginPage.getStatusCodeValue());
        assertNotNull(loginPage.getBody());
        assertTrue(loginPage.getBody().contains("Windows AD 账号登录"));
        String csrfToken = csrfToken(loginPage.getBody());
        String anonymousSession = session(loginPage.getHeaders());

        ResponseEntity<String> login = restTemplate.exchange(uiUrl("/login"), HttpMethod.POST,
                loginRequest(csrfToken, anonymousSession, ldapUserPassword), String.class);
        assertEquals(302, login.getStatusCodeValue());
        assertEquals(uiBasePath, login.getHeaders().getLocation().getPath());
        String authenticatedSession = session(login.getHeaders());
        assertNotNull(authenticatedSession);
        assertFalse(authenticatedSession.equals(anonymousSession));

        ResponseEntity<String> console = restTemplate.exchange(uiUrl(""), HttpMethod.GET,
                sessionRequest(authenticatedSession), String.class);
        assertEquals(200, console.getStatusCodeValue());
        assertTrue(console.getBody().contains("多维中间件运维平台"));
        assertTrue(console.getBody().contains("ops-user"));
        assertTrue(console.getBody().contains("ops-user-menu"));
        assertTrue(console.getBody().contains("class=\"ops-user-avatar\""));
        assertTrue(console.getBody().contains(">O</span>"));
        assertTrue(console.getBody().contains("data-audit-max-range-days=\"90\""));
        assertTrue(console.getBody().contains("favicon.svg"));
        assertTrue(console.getBody().contains("data-workspace-panel=\"elasticsearch\""));
        assertTrue(console.getBody().contains("data-workspace-panel=\"redis\""));
        assertTrue(console.getBody().contains("data-workspace-panel=\"kafka\""));
        assertTrue(console.getBody().contains("data-workspace-panel=\"mysql\""));
        assertTrue(console.getBody().contains("id=\"elasticsearch-audit-effective\""));
        assertTrue(console.getBody().contains("id=\"redis-audit-effective\""));
        assertTrue(console.getBody().contains("id=\"kafka-audit-effective\""));
        assertTrue(console.getBody().contains("id=\"mysql-audit-effective\""));
        assertTrue(console.getBody().contains("data-datasource-context"));
        assertTrue(console.getBody().contains("id=\"elasticsearch-index-input\""));
        assertTrue(console.getBody().contains("id=\"elasticsearch-index-options\""));
        assertTrue(console.getBody().contains("id=\"elasticsearch-console-result-hits\""));
        assertTrue(console.getBody().contains("id=\"elasticsearch-console-result-document\""));
        assertTrue(console.getBody().contains("data-console-panel=\"query\""));
        assertTrue(console.getBody().contains("data-console-panel=\"key-read\""));
        assertTrue(console.getBody().contains("data-console-panel=\"topic-list\""));
        assertTrue(console.getBody().contains("data-console-panel=\"topic-runtime\""));
        assertTrue(console.getBody().contains("data-console-panel=\"group-list\""));
        assertTrue(console.getBody().contains("data-console-panel=\"group-lag\""));
        assertTrue(console.getBody().contains("data-console-panel=\"select\""));
        assertEquals(10, occurrences(console.getBody(), "data-console-panel-toggle="));
        assertEquals(10, occurrences(console.getBody(), "class=\"ops-console-panel-toggle\""));
        assertTrue(console.getBody().contains("aria-controls=\"mysql-select-panel\" aria-expanded=\"true\""));
        assertTrue(console.getBody().contains("aria-controls=\"kafka-topic-list-panel\" aria-expanded=\"true\""));
        assertTrue(console.getBody().contains("aria-controls=\"kafka-topic-runtime-panel\" aria-expanded=\"false\""));
        assertFalse(console.getBody().contains("redis-datasource-console-result-pagination"));
        assertFalse(console.getBody().contains("kafka-datasource-console-result-pagination"));
        assertEquals(9, occurrences(console.getBody(), "ops-result ops-result-surface"));
        assertTrue(console.getBody().contains("id=\"redis-datasource-console-result\""));
        assertTrue(console.getBody().contains("id=\"redis-summary-console-result\""));
        assertTrue(console.getBody().contains("id=\"kafka-datasource-console-result\""));
        assertTrue(console.getBody().contains("id=\"kafka-topic-list-console-result\""));
        assertTrue(console.getBody().contains("id=\"kafka-group-list-console-result\""));
        assertTrue(console.getBody().contains("id=\"kafka-topic-console-result\""));
        assertTrue(console.getBody().contains("id=\"kafka-lag-console-result\""));
        assertTrue(console.getBody().contains("id=\"mysql-status-console-result\""));
        assertTrue(console.getBody().contains("id=\"mysql-select-console-result\""));
        assertFalse(console.getBody().contains("ops-kafka-interactive-result"));
        assertTrue(console.getBody().contains("<th>操作</th>"));
        assertFalse(console.getBody().contains("<th>能力</th>"));
        assertEquals(4, occurrences(console.getBody(), "<th>操作指纹</th>"));
        assertFalse(console.getBody().contains("<th>资源摘要</th>"));
        assertTrue(console.getBody().contains("<th>查询参数</th>"));
        assertFalse(console.getBody().contains("elasticsearch-summary-console-form"));
        assertFalse(console.getBody().contains("已配置数据源"));

        ResponseEntity<String> catalog = restTemplate.exchange(apiUrl("/elasticsearch/catalog"), HttpMethod.GET,
                sessionRequest(authenticatedSession), String.class);
        assertEquals(200, catalog.getStatusCodeValue());
        assertTrue(catalog.getHeaders().getContentType().includes(MediaType.APPLICATION_JSON));
        assertEquals("no-store", catalog.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));

        ResponseEntity<String> postWithoutCsrf = restTemplate.exchange(apiUrl("/elasticsearch/catalog"), HttpMethod.POST,
                sessionRequest(authenticatedSession), String.class);
        assertEquals(403, postWithoutCsrf.getStatusCodeValue());

        ResponseEntity<String> logout = restTemplate.exchange(uiUrl("/logout"), HttpMethod.POST,
                loginRequest(csrfToken(console.getBody()), authenticatedSession, null), String.class);
        assertEquals(302, logout.getStatusCodeValue());
        assertEquals(uiBasePath + "/login", logout.getHeaders().getLocation().getPath());
        assertNull(logout.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE));

        ResponseEntity<String> invalidated = restTemplate.exchange(apiUrl("/elasticsearch/catalog"), HttpMethod.GET,
                sessionRequest(authenticatedSession), String.class);
        assertEquals(401, invalidated.getStatusCodeValue());
    }

    @Test
    void shouldRejectWrongPasswordThroughFormLogin() {
        ResponseEntity<String> loginPage = restTemplate.getForEntity(uiUrl("/login"), String.class);
        ResponseEntity<String> response = restTemplate.exchange(uiUrl("/login"), HttpMethod.POST,
                loginRequest(csrfToken(loginPage.getBody()), session(loginPage.getHeaders()), "wrong-password"), String.class);

        assertEquals(302, response.getStatusCodeValue());
        assertEquals(uiBasePath + "/login", response.getHeaders().getLocation().getPath());
        assertTrue(response.getHeaders().getLocation().getQuery().contains("error"));
        assertNull(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE));
    }

    private HttpEntity<MultiValueMap<String, String>> loginRequest(String csrfToken, String session, String password) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("_csrf", csrfToken);
        if (password != null) {
            body.add("username", "ops-user");
            body.add("password", password);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set(HttpHeaders.COOKIE, session);
        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<Void> sessionRequest(String session) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, session);
        return new HttpEntity<>(headers);
    }

    private String csrfToken(String body) {
        Matcher matcher = CSRF_TOKEN.matcher(body);
        assertTrue(matcher.find());
        return matcher.group(1);
    }

    private String session(HttpHeaders headers) {
        String value = headers.getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(value);
        return value.substring(0, value.indexOf(';'));
    }

    private String apiUrl(String path) {
        return url(apiBasePath + path);
    }

    private String uiUrl(String path) {
        return url(uiBasePath + path);
    }

    private int occurrences(String value, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }
}
