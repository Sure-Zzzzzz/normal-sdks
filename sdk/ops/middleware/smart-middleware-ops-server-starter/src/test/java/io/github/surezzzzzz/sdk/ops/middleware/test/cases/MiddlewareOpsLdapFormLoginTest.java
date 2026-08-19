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
        assertTrue(stylesheet.getBody().contains("width: min(100%, 1920px);"));
        assertTrue(stylesheet.getBody().contains("margin: 0 auto;"));
        assertTrue(stylesheet.getBody().contains("min-height: 100dvh;"));
        assertTrue(stylesheet.getBody().contains("@media (min-width: 1180px)"));
        String compactStylesheet = stylesheet.getBody().replaceAll("\\s+", " ");
        assertTrue(compactStylesheet.contains(".ops-shell { display: flex; min-height: 100vh; min-height: 100dvh; flex-direction: column; }"));
        assertTrue(compactStylesheet.contains(".ops-main { display: flex; min-height: 0; flex: 1; flex-direction: column; }"));
        assertTrue(compactStylesheet.contains(".ops-workspace.is-active { display: flex; min-height: 0; flex: 1; flex-direction: column; }"));
        assertTrue(compactStylesheet.contains(".ops-section[data-section-panel=\"console\"].is-active, .ops-section[data-section-panel=\"audit\"].is-active { display: flex; min-height: 0; flex: 1; flex-direction: column; }"));
        assertTrue(stylesheet.getBody().contains("@media (min-width: 1440px)"));
        assertTrue(stylesheet.getBody().contains("@media (min-width: 2560px)"));
        assertTrue(compactStylesheet.contains(".ops-main { padding-right: 40px; padding-left: 40px; }"));
        assertTrue(compactStylesheet.contains(".ops-workspace { width: min(100%, 2048px); }"));
        assertTrue(stylesheet.getBody().contains("@media (min-width: 3200px)"));
        assertTrue(stylesheet.getBody().contains("@media (max-width: 1179px)"));
        assertTrue(stylesheet.getBody().contains("@media (max-width: 767px)"));
        assertTrue(stylesheet.getBody().contains("@media (max-width: 479px)"));
        assertFalse(compactStylesheet.contains("body { min-width: 1180px;"));
        assertTrue(compactStylesheet.contains(".ops-table-wrap { overflow: auto;"));
        assertTrue(stylesheet.getBody().contains(".ops-console-panel-toggle"));
        assertTrue(stylesheet.getBody().contains(".ops-console-panel-toggle:focus-visible"));
        assertTrue(stylesheet.getBody().contains(".ops-console-panel-toggle[aria-expanded=\"true\"]"));
        assertTrue(stylesheet.getBody().contains(".ops-user-caret::before, .ops-console-panel-caret::before"));
        assertTrue(stylesheet.getBody().contains(".ops-topbar-actions"));
        assertTrue(stylesheet.getBody().contains(".ops-global-time-zone select"));
        assertTrue(stylesheet.getBody().contains(".ops-form-grid select, .ops-elasticsearch-context-fields select, .ops-audit-filter select"));
        assertTrue(stylesheet.getBody().contains("appearance: none;"));
        assertTrue(stylesheet.getBody().contains(".ops-result-surface"));
        assertTrue(stylesheet.getBody().contains(".ops-result.ops-result-surface {\n    max-height: none;\n    padding: 0;\n    overflow: visible;"));
        assertTrue(stylesheet.getBody().contains(".ops-status-card"));
        assertTrue(stylesheet.getBody().contains(".ops-status-card-note"));
        assertTrue(compactStylesheet.contains(".ops-pagination[hidden] { display: none; }"));
        assertTrue(stylesheet.getBody().contains(".ops-result-table"));
        assertTrue(stylesheet.getBody().contains(".ops-result-action"));
        assertTrue(stylesheet.getBody().contains(".ops-console-help ul"));
        assertTrue(stylesheet.getBody().contains(".ops-dsl-editor"));
        assertTrue(stylesheet.getBody().contains(".ops-dsl-suggestions"));
        assertTrue(stylesheet.getBody().contains(".ops-dsl-suggestion"));
        assertTrue(stylesheet.getBody().contains(".ops-elasticsearch-workbench"));
        assertTrue(stylesheet.getBody().contains("--elasticsearch-request-pane-width"));
        assertTrue(stylesheet.getBody().contains(".ops-elasticsearch-splitter"));
        assertTrue(stylesheet.getBody().contains("#elasticsearch-console-response-output"));
        assertTrue(stylesheet.getBody().contains("overflow: auto;"));
        assertTrue(stylesheet.getBody().contains("resize: none;"));
        assertTrue(stylesheet.getBody().contains("@container (max-width: 760px)"));
        assertTrue(stylesheet.getBody().contains(".ops-datasource-card .ops-datasource-detail"));
        assertFalse(stylesheet.getBody().contains("[data-workspace-panel=\"elasticsearch\"] .ops-datasource-card .ops-datasource-detail"));
        assertTrue(stylesheet.getBody().contains(".ops-table-wrap th:nth-child(1)"));
        assertTrue(stylesheet.getBody().contains(".ops-table-wrap th:nth-child(8)"));
        assertTrue(stylesheet.getBody().contains(".ops-table-wrap td:nth-child(6)"));
        assertTrue(stylesheet.getBody().contains(".ops-table-wrap td:nth-child(8)"));
        assertFalse(stylesheet.getBody().contains(".ops-table-wrap th:nth-child(9)"));
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
        assertTrue(script.getBody().contains("var auditTimeZone = 'beijing';"));
        assertTrue(script.getBody().contains("var auditTimeZoneStorageKey = 'middleware-ops-audit-time-zone:v1:'"));
        assertTrue(script.getBody().contains("var auditTimeZoneSelect = document.getElementById('ops-audit-time-zone');"));
        assertTrue(script.getBody().contains("function restoreAuditTimeZone()"));
        assertTrue(script.getBody().contains("window.localStorage.getItem(auditTimeZoneStorageKey)"));
        assertTrue(script.getBody().contains("window.localStorage.setItem(auditTimeZoneStorageKey, auditTimeZone)"));
        assertTrue(script.getBody().contains("function dateFromWallClock(value, timeZone)"));
        assertTrue(script.getBody().contains("function auditInputDateTime(value, timeZone)"));
        assertTrue(script.getBody().contains("function formatAuditTime(value)"));
        assertTrue(script.getBody().contains("timeZone: auditTimeZone === 'utc' ? 'UTC' : 'Asia/Shanghai'"));
        assertTrue(script.getBody().contains("实际查询范围（' + auditTimeZoneLabel()"));
        assertTrue(script.getBody().contains("renderAuditItems(workspace, items)"));
        assertTrue(script.getBody().contains("function setAuditTimeZone(value)"));
        assertTrue(script.getBody().contains("auditTimeZoneSelect.addEventListener('change'"));
        assertFalse(script.getBody().contains("controls.timeZone"));
        assertFalse(script.getBody().contains("item.resourceDigest"));
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
        assertTrue(script.getBody().contains("function fillRedisKey(key)"));
        assertTrue(script.getBody().contains("setConsolePanelExpanded('redis', 'key-read');"));
        assertTrue(script.getBody().contains("function renderRedisKeyDiscovery(content)"));
        assertTrue(script.getBody().contains("'填入精确 Key'"));
        assertTrue(script.getBody().contains("'/redis/datasources/' + encodeURIComponent(datasourceKey) + '/keys/discovery?'"));
        assertTrue(script.getBody().contains("document.getElementById('redis-key-discovery-console-form').addEventListener('submit'"));
        assertTrue(script.getBody().contains("'redis-key-discovery-console-result'"));
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
        assertTrue(script.getBody().contains("function renderElasticsearchDocuments(state, message)"));
        assertTrue(script.getBody().contains("'已返回 Elasticsearch 响应。'"));
        assertTrue(script.getBody().contains("JSON.stringify(state.documentData, null, 2)"));
        assertFalse(script.getBody().contains("size: 20"));
        assertFalse(script.getBody().contains("documentData.items"));
        assertFalse(script.getBody().contains("elasticsearch-console-result-pagination"));
        assertTrue(script.getBody().contains("function initializeElasticsearchWorkbenchSplitter()"));
        assertTrue(script.getBody().contains("setPointerCapture(event.pointerId)"));
        assertTrue(script.getBody().contains("splitter.releasePointerCapture(event.pointerId)"));
        assertTrue(script.getBody().contains("event.key === 'ArrowLeft'"));
        assertTrue(script.getBody().contains("event.key === 'ArrowRight'"));
        assertTrue(script.getBody().contains("function clearElasticsearchDocuments(message)"));
        assertFalse(script.getBody().contains("documentSelectedIndex"));
        assertTrue(script.getBody().contains("function loadElasticsearchFieldCapabilities(datasourceKey, index)"));
        assertTrue(script.getBody().contains("function renderElasticsearchDslSuggestions()"));
        assertTrue(script.getBody().contains("function applyElasticsearchDslSuggestion(index)"));
        assertTrue(script.getBody().contains("function restoreConsoleDrafts()"));
        assertTrue(script.getBody().contains("function clearConsoleDrafts()"));
        assertTrue(script.getBody().contains("function loadMysqlTableSuggestions(datasourceKey)"));
        assertTrue(script.getBody().contains("function loadMysqlColumnSuggestions(datasourceKey, table)"));
        assertTrue(script.getBody().contains("function clearMysqlSuggestions(state)"));
        assertTrue(script.getBody().contains("'/mysql/datasources/' + encodeURIComponent(datasourceKey) + '/tables'"));
        assertFalse(script.getBody().contains("'/mysql/datasources/' + encodeURIComponent(datasourceKey) + '/tables?'\n            + queryString({size: 200})"));
        assertTrue(script.getBody().contains("state.mysqlTableError = error.message + '，仍可手动输入精确表名。';"));
        assertTrue(script.getBody().contains("state.mysqlColumnError = error.message + '，仍可手动编辑受控 SELECT。';"));
        assertTrue(script.getBody().contains("'/tables/'\n            + encodeURIComponent(table) + '/columns'"));
        assertTrue(script.getBody().contains("kafka: 'topic-list'"));
        assertTrue(script.getBody().contains("content.columns, content.rows"));
        assertTrue(script.getBody().contains("button.setAttribute('aria-label', ariaLabel)"));
        assertTrue(script.getBody().contains("fillKafkaTopic(item.name);"));
        assertTrue(script.getBody().contains("fillKafkaGroup(item.groupId);"));
        assertTrue(script.getBody().contains("fillKafkaTopic(item.topic);"));
        assertFalse(script.getBody().contains("innerHTML"));
        assertFalse(script.getBody().contains("outerHTML"));
        assertFalse(script.getBody().contains("insertAdjacentHTML"));
        assertTrue(script.getBody().contains("window.localStorage.setItem(draftStorageKey"));
        assertTrue(script.getBody().contains("window.localStorage.removeItem(draftStorageKey)"));
        assertTrue(script.getBody().contains("restoreConsoleDrafts();"));
        assertFalse(script.getBody().contains("sessionStorage"));
        assertFalse(script.getBody().contains("indexedDB"));
        assertFalse(script.getBody().contains("nextCursor"));
        assertFalse(script.getBody().contains("topology"));
        assertFalse(script.getBody().contains("endpoint"));

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
        assertTrue(console.getBody().contains("class=\"ops-topbar-actions\""));
        assertTrue(console.getBody().contains("id=\"ops-audit-time-zone\""));
        assertTrue(console.getBody().contains("class=\"ops-global-time-zone\""));
        assertFalse(console.getBody().contains("id=\"elasticsearch-audit-time-zone\""));
        assertFalse(console.getBody().contains("id=\"redis-audit-time-zone\""));
        assertFalse(console.getBody().contains("id=\"kafka-audit-time-zone\""));
        assertFalse(console.getBody().contains("id=\"mysql-audit-time-zone\""));
        assertEquals(1, occurrences(console.getBody(), ">北京时间</option>"));
        assertEquals(1, occurrences(console.getBody(), ">UTC</option>"));
        assertTrue(console.getBody().contains("id=\"elasticsearch-audit-from-label\""));
        assertTrue(console.getBody().contains("id=\"mysql-audit-to-label\""));
        assertTrue(console.getBody().contains("data-datasource-context"));
        assertTrue(console.getBody().contains("id=\"elasticsearch-index-input\""));
        assertTrue(console.getBody().contains("id=\"elasticsearch-index-options\""));
        assertTrue(console.getBody().contains("id=\"elasticsearch-dsl-input\""));
        assertTrue(console.getBody().contains("id=\"elasticsearch-dsl-suggestions\""));
        assertTrue(console.getBody().contains("role=\"listbox\""));
        assertTrue(console.getBody().contains("id=\"elasticsearch-field-state\""));
        assertTrue(console.getBody().contains("id=\"ops-clear-drafts\""));
        assertTrue(console.getBody().contains("id=\"ops-draft-state\""));
        assertFalse(console.getBody().contains("data-elasticsearch-dsl-example"));
        assertTrue(console.getBody().contains("id=\"elasticsearch-console-workbench\""));
        assertTrue(console.getBody().contains("id=\"elasticsearch-console-splitter\""));
        assertTrue(console.getBody().contains("role=\"separator\""));
        assertTrue(console.getBody().contains("aria-orientation=\"vertical\""));
        assertTrue(console.getBody().contains("id=\"elasticsearch-console-response-output\" readonly"));
        assertTrue(console.getBody().contains("wrap=\"off\""));
        assertFalse(console.getBody().contains("id=\"elasticsearch-console-result-pagination\""));
        assertFalse(console.getBody().contains("id=\"elasticsearch-console-result-hits\""));
        assertFalse(console.getBody().contains("id=\"elasticsearch-console-result-document\""));
        assertTrue(console.getBody().contains("data-console-panel=\"query\""));
        assertTrue(console.getBody().contains("data-console-panel=\"key-discovery\""));
        assertTrue(console.getBody().contains("data-console-panel=\"key-read\""));
        assertTrue(console.getBody().contains("data-console-panel=\"topic-list\""));
        assertTrue(console.getBody().contains("data-console-panel=\"topic-runtime\""));
        assertTrue(console.getBody().contains("data-console-panel=\"group-list\""));
        assertTrue(console.getBody().contains("data-console-panel=\"group-lag\""));
        assertTrue(console.getBody().contains("data-console-panel=\"select\""));
        assertEquals(17, occurrences(console.getBody(), "data-console-panel-toggle="));
        assertEquals(17, occurrences(console.getBody(), "class=\"ops-console-panel-toggle\""));
        assertTrue(console.getBody().contains("aria-controls=\"redis-key-discovery-panel\" aria-expanded=\"true\""));
        assertTrue(console.getBody().contains("aria-controls=\"redis-key-read-panel\" aria-expanded=\"false\""));
        assertTrue(console.getBody().contains("aria-controls=\"mysql-select-panel\" aria-expanded=\"true\""));
        assertTrue(console.getBody().contains("aria-controls=\"kafka-topic-list-panel\" aria-expanded=\"true\""));
        assertTrue(console.getBody().contains("aria-controls=\"kafka-topic-runtime-panel\" aria-expanded=\"false\""));
        assertTrue(console.getBody().contains("id=\"redis-key-discovery-console-form\""));
        assertTrue(console.getBody().contains("id=\"redis-key-discovery-console-result\""));
        assertTrue(console.getBody().contains("id=\"redis-key-discovery-console-result-pagination\""));
        assertTrue(console.getBody().contains("前缀只能是字面量。结果仅是单次受限观察，没有续传，不保证完整、一致或时间点快照"));
        assertFalse(console.getBody().contains("redis-datasource-console-result-pagination"));
        assertFalse(console.getBody().contains("kafka-datasource-console-result-pagination"));
        assertEquals(16, occurrences(console.getBody(), "ops-result ops-result-surface"));
        assertTrue(console.getBody().contains("id=\"redis-datasource-console-result\""));
        assertTrue(console.getBody().contains("id=\"redis-summary-console-result\""));
        assertTrue(console.getBody().contains("id=\"kafka-datasource-console-result\""));
        assertTrue(console.getBody().contains("id=\"kafka-topic-list-console-result\""));
        assertTrue(console.getBody().contains("id=\"kafka-group-list-console-result\""));
        assertTrue(console.getBody().contains("id=\"kafka-topic-console-result\""));
        assertTrue(console.getBody().contains("id=\"kafka-lag-console-result\""));
        assertTrue(console.getBody().contains("id=\"mysql-status-console-result\""));
        assertTrue(console.getBody().contains("id=\"mysql-select-console-result\""));
        assertEquals(1, occurrences(console.getBody(), "class=\"mysql-suggestion-actions\""));
        assertEquals(2, occurrences(console.getBody(), "class=\"mysql-suggestion-action\""));
        String compactConsole = console.getBody().replaceAll("\\s+", " ");
        assertTrue(console.getBody().contains("id=\"mysql-query-table-input\""));
        assertTrue(compactConsole.contains("id=\"mysql-query-table-input\" list=\"mysql-query-table-options\" maxlength=\"256\" name=\"table\""));
        assertTrue(console.getBody().contains("id=\"mysql-query-table-options\""));
        assertTrue(console.getBody().contains("id=\"mysql-query-column-options\""));
        assertTrue(console.getBody().contains("aria-label=\"字段候选\""));
        assertTrue(console.getBody().contains("字段（可多选）"));
        assertTrue(console.getBody().contains("id=\"mysql-load-table-suggestions\""));
        assertTrue(console.getBody().contains("id=\"mysql-load-column-suggestions\""));
        assertTrue(console.getBody().contains("<strong>提交前规则</strong>"));
        assertTrue(console.getBody().contains("仅允许一条 SELECT；不允许注释、分号或用户输入 EXPLAIN、SHOW、DESCRIBE。"));
        assertTrue(console.getBody().contains("SELECT 必须显式选择当前表字段；不支持 *、函数或表达式。"));
        assertTrue(console.getBody().contains("WHERE 仅支持当前表字段与字面量的受限条件；ORDER BY 仅支持当前表字段。"));
        assertTrue(compactConsole.contains("候选仅辅助编写 SQL，最终以服务端校验为准。"));
        assertTrue(console.getBody().contains("服务端会对该 SELECT 执行 Explain；最终以服务端校验为准。"));
        assertFalse(console.getBody().contains("ops-kafka-interactive-result"));
        assertTrue(console.getBody().contains("<th>操作</th>"));
        assertFalse(console.getBody().contains("<th>能力</th>"));
        assertFalse(console.getBody().contains("<th>操作指纹</th>"));
        assertFalse(console.getBody().contains("<th>资源摘要</th>"));
        assertTrue(console.getBody().contains("<th>查询参数</th>"));
        assertFalse(console.getBody().contains("elasticsearch-summary-console-form"));
        assertFalse(console.getBody().contains("已配置数据源"));
        assertFalse(console.getBody().contains("安全状态说明"));
        assertFalse(console.getBody().contains("ops-overview-notice"));

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
