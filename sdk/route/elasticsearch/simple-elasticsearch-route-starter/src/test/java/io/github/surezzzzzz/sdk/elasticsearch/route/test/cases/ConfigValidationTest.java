package io.github.surezzzzzz.sdk.elasticsearch.route.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteProperties;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 配置验证功能单元测试
 * 测试 SimpleElasticsearchRouteProperties 的配置验证逻辑
 *
 * @author Sure
 * @since 1.0.2
 */
@Slf4j
public class ConfigValidationTest {

    /**
     * 测试1：正常配置应该验证通过
     */
    @Test
    public void testValidConfig() {
        log.info("=== 测试正常配置验证 ===");

        SimpleElasticsearchRouteProperties properties = createValidProperties();

        // 应该不抛异常
        assertDoesNotThrow(() -> properties.init());

        log.info("=== 正常配置验证通过 ===");
    }

    /**
     * 测试2：数据源为空时应该抛异常
     */
    @Test
    public void testEmptySources() {
        log.info("=== 测试数据源为空时验证失败 ===");

        SimpleElasticsearchRouteProperties properties = new SimpleElasticsearchRouteProperties();
        properties.setEnable(true);
        properties.setSources(new HashMap<>());

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            properties.init();
        });

        Throwable cause = exception.getCause();
        assertNotNull(cause);
        String message = cause.getMessage();
        assertTrue(message.contains("sources"));
        log.info("异常信息: {}", message);

        log.info("=== 数据源为空验证测试通过 ===");
    }

    /**
     * 测试3：默认数据源不存在时应该抛异常
     */
    @Test
    public void testDefaultSourceNotFound() {
        log.info("=== 测试默认数据源不存在时验证失败 ===");

        SimpleElasticsearchRouteProperties properties = createValidProperties();
        properties.setDefaultSource("nonexistent");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            properties.init();
        });

        Throwable cause = exception.getCause();
        assertNotNull(cause);
        String message = cause.getMessage();
        assertTrue(message.contains("nonexistent"));
        log.info("异常信息: {}", message);

        log.info("=== 默认数据源不存在验证测试通过 ===");
    }

    /**
     * 测试4：hosts 和 urls 都为空时应该抛异常
     */
    @Test
    public void testEmptyHostsAndUrls() {
        log.info("=== 测试 hosts 和 urls 都为空时验证失败 ===");

        SimpleElasticsearchRouteProperties properties = createValidProperties();

        // 设置一个没有 hosts 和 urls 的数据源
        SimpleElasticsearchRouteProperties.DataSourceConfig config =
                new SimpleElasticsearchRouteProperties.DataSourceConfig();
        properties.getSources().put("test", config);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            properties.init();
        });

        Throwable cause = exception.getCause();
        assertNotNull(cause);
        String message = cause.getMessage();
        assertTrue(message.contains("hosts") || message.contains("urls"));
        log.info("异常信息: {}", message);

        log.info("=== hosts 和 urls 为空验证测试通过 ===");
    }

    /**
     * 测试5：connectTimeout <= 0 时应该抛异常
     */
    @Test
    public void testInvalidConnectTimeout() {
        log.info("=== 测试 connectTimeout <= 0 时验证失败 ===");

        SimpleElasticsearchRouteProperties properties = createValidProperties();
        properties.getSources().get("primary").setConnectTimeout(0);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            properties.init();
        });

        Throwable cause = exception.getCause();
        assertNotNull(cause);
        String message = cause.getMessage();
        assertTrue(message.contains("connectTimeout"));
        log.info("异常信息: {}", message);

        log.info("=== connectTimeout 验证测试通过 ===");
    }

    /**
     * 测试6：socketTimeout <= 0 时应该抛异常
     */
    @Test
    public void testInvalidSocketTimeout() {
        log.info("=== 测试 socketTimeout <= 0 时验证失败 ===");

        SimpleElasticsearchRouteProperties properties = createValidProperties();
        properties.getSources().get("primary").setSocketTimeout(-1);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            properties.init();
        });

        Throwable cause = exception.getCause();
        assertNotNull(cause);
        String message = cause.getMessage();
        assertTrue(message.contains("socketTimeout"));
        log.info("异常信息: {}", message);

        log.info("=== socketTimeout 验证测试通过 ===");
    }

    /**
     * 测试7：maxConnPerRoute > maxConnTotal 时应该抛异常
     */
    @Test
    public void testMaxConnPerRouteExceedsTotal() {
        log.info("=== 测试 maxConnPerRoute > maxConnTotal 时验证失败 ===");

        SimpleElasticsearchRouteProperties properties = createValidProperties();
        properties.getSources().get("primary").setMaxConnTotal(10);
        properties.getSources().get("primary").setMaxConnPerRoute(20);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            properties.init();
        });

        Throwable cause = exception.getCause();
        assertNotNull(cause);
        String message = cause.getMessage();
        assertTrue(message.contains("maxConnPerRoute") && message.contains("maxConnTotal"));
        log.info("异常信息: {}", message);

        log.info("=== maxConnPerRoute 验证测试通过 ===");
    }

    /**
     * 测试8：设置了 proxyPort 但没有 proxyHost 时应该抛异常
     */
    @Test
    public void testProxyPortWithoutHost() {
        log.info("=== 测试设置 proxyPort 但没有 proxyHost 时验证失败 ===");

        SimpleElasticsearchRouteProperties properties = createValidProperties();
        properties.getSources().get("primary").setProxyPort(8080);
        properties.getSources().get("primary").setProxyHost(null);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            properties.init();
        });

        Throwable cause = exception.getCause();
        assertNotNull(cause);
        String message = cause.getMessage();
        assertTrue(message.contains("proxyPort") && message.contains("proxyHost"));
        log.info("异常信息: {}", message);

        log.info("=== proxyPort 验证测试通过 ===");
    }

    /**
     * 测试9：keepAliveStrategy <= 0 时应该抛异常
     */
    @Test
    public void testInvalidKeepAliveStrategy() {
        log.info("=== 测试 keepAliveStrategy <= 0 时验证失败 ===");

        SimpleElasticsearchRouteProperties properties = createValidProperties();
        properties.getSources().get("primary").setKeepAliveStrategy(0);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            properties.init();
        });

        Throwable cause = exception.getCause();
        assertNotNull(cause);
        String message = cause.getMessage();
        assertTrue(message.contains("keepAliveStrategy"));
        log.info("异常信息: {}", message);

        log.info("=== keepAliveStrategy 验证测试通过 ===");
    }

    /**
     * 测试10：无效的 URL 格式时应该抛异常
     */
    @Test
    public void testInvalidUrlFormat() {
        log.info("=== 测试无效 URL 格式时验证失败 ===");

        SimpleElasticsearchRouteProperties properties = createValidProperties();
        // 使用包含非法字符的 URL（空格是非法的）
        properties.getSources().get("primary").setUrls("http1111://invalid url with spaces");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            properties.init();
        });

        Throwable cause = exception.getCause();
        assertNotNull(cause);
        String message = cause.getMessage();
        assertTrue(message.contains("URL") || message.contains("格式"));
        log.info("异常信息: {}", message);

        log.info("=== URL 格式验证测试通过 ===");
    }

    /**
     * 测试11：路由规则 pattern 为空时应该抛异常
     */
    @Test
    public void testEmptyRulePattern() {
        log.info("=== 测试路由规则 pattern 为空时验证失败 ===");

        SimpleElasticsearchRouteProperties properties = createValidProperties();

        SimpleElasticsearchRouteProperties.RouteRule rule =
                new SimpleElasticsearchRouteProperties.RouteRule();
        rule.setPattern("");
        rule.setDatasource("primary");
        rule.setType("exact");

        properties.getRules().add(rule);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            properties.init();
        });

        Throwable cause = exception.getCause();
        assertNotNull(cause);
        String message = cause.getMessage();
        assertTrue(message.contains("pattern"));
        log.info("异常信息: {}", message);

        log.info("=== pattern 为空验证测试通过 ===");
    }

    /**
     * 测试12：路由规则引用不存在的数据源时应该抛异常
     */
    @Test
    public void testRuleReferencesNonexistentDatasource() {
        log.info("=== 测试路由规则引用不存在的数据源时验证失败 ===");

        SimpleElasticsearchRouteProperties properties = createValidProperties();

        SimpleElasticsearchRouteProperties.RouteRule rule =
                new SimpleElasticsearchRouteProperties.RouteRule();
        rule.setPattern("test_*");
        rule.setDatasource("nonexistent");
        rule.setType("wildcard");

        properties.getRules().add(rule);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            properties.init();
        });

        Throwable cause = exception.getCause();
        assertNotNull(cause);
        String message = cause.getMessage();
        assertTrue(message.contains("nonexistent"));
        log.info("异常信息: {}", message);

        log.info("=== 规则引用不存在的数据源验证测试通过 ===");
    }

    /**
     * 测试13：无效的匹配类型时应该抛异常
     */
    @Test
    public void testInvalidMatchType() {
        log.info("=== 测试无效匹配类型时验证失败 ===");

        SimpleElasticsearchRouteProperties properties = createValidProperties();

        SimpleElasticsearchRouteProperties.RouteRule rule =
                new SimpleElasticsearchRouteProperties.RouteRule();
        rule.setPattern("test_*");
        rule.setDatasource("primary");
        rule.setType("invalid-type");

        properties.getRules().add(rule);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            properties.init();
        });

        Throwable cause = exception.getCause();
        assertNotNull(cause);
        String message = cause.getMessage();
        assertTrue(message.contains("invalid-type") && message.contains("匹配类型"));
        log.info("异常信息: {}", message);

        log.info("=== 匹配类型验证测试通过 ===");
    }

    /**
     * 测试14：priority 超出范围时应该抛异常
     */
    @Test
    public void testInvalidPriority() {
        log.info("=== 测试 priority 超出范围时验证失败 ===");

        SimpleElasticsearchRouteProperties properties = createValidProperties();

        SimpleElasticsearchRouteProperties.RouteRule rule =
                new SimpleElasticsearchRouteProperties.RouteRule();
        rule.setPattern("test_*");
        rule.setDatasource("primary");
        rule.setType("wildcard");
        rule.setPriority(10001);  // 超出范围

        properties.getRules().add(rule);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            properties.init();
        });

        Throwable cause = exception.getCause();
        assertNotNull(cause);
        String message = cause.getMessage();
        assertTrue(message.contains("priority"));
        log.info("异常信息: {}", message);

        log.info("=== priority 验证测试通过 ===");
    }

    /**
     * 测试15：正则表达式语法错误时应该抛异常
     */
    @Test
    public void testInvalidRegex() {
        log.info("=== 测试正则表达式语法错误时验证失败 ===");

        SimpleElasticsearchRouteProperties properties = createValidProperties();

        SimpleElasticsearchRouteProperties.RouteRule rule =
                new SimpleElasticsearchRouteProperties.RouteRule();
        rule.setPattern("[invalid(");
        rule.setDatasource("primary");
        rule.setType("regex");
        rule.setPriority(100);

        properties.getRules().add(rule);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            properties.init();
        });

        Throwable cause = exception.getCause();
        assertNotNull(cause);
        String message = cause.getMessage();
        assertTrue(message.contains("正则表达式") || message.contains("语法错误"));
        log.info("异常信息: {}", message);

        log.info("=== 正则表达式验证测试通过 ===");
    }

    /**
     * 测试16：重复的 exact 规则时应该抛异常
     */
    @Test
    public void testDuplicateExactRules() {
        log.info("=== 测试重复的 exact 规则时验证失败 ===");

        SimpleElasticsearchRouteProperties properties = createValidProperties();

        SimpleElasticsearchRouteProperties.RouteRule rule1 =
                new SimpleElasticsearchRouteProperties.RouteRule();
        rule1.setPattern("test_index");
        rule1.setDatasource("primary");
        rule1.setType("exact");
        rule1.setPriority(100);

        SimpleElasticsearchRouteProperties.RouteRule rule2 =
                new SimpleElasticsearchRouteProperties.RouteRule();
        rule2.setPattern("test_index");
        rule2.setDatasource("primary");
        rule2.setType("exact");
        rule2.setPriority(200);

        properties.getRules().add(rule1);
        properties.getRules().add(rule2);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            properties.init();
        });

        Throwable cause = exception.getCause();
        assertNotNull(cause);
        String message = cause.getMessage();
        assertTrue(message.contains("重复"));
        log.info("异常信息: {}", message);

        log.info("=== 重复 exact 规则验证测试通过 ===");
    }

    /**
     * 测试17：URL 解析 - urls 字段（带协议）
     */
    @Test
    public void testUrlResolutionWithProtocol() {
        log.info("=== 测试 URL 解析（urls 字段，带协议）===");

        SimpleElasticsearchRouteProperties.DataSourceConfig config =
                new SimpleElasticsearchRouteProperties.DataSourceConfig();
        config.setUrls("http://localhost:9200,https://localhost:9201");

        List<String> urls = config.getResolvedUrls();
        assertEquals(2, urls.size());
        assertEquals("http://localhost:9200", urls.get(0));
        assertEquals("https://localhost:9201", urls.get(1));

        log.info("解析结果: {}", urls);
        log.info("=== URL 解析测试通过 ===");
    }

    /**
     * 测试18：URL 解析 - hosts 字段（无协议，useSsl=false）
     */
    @Test
    public void testHostResolutionWithoutSsl() {
        log.info("=== 测试 hosts 解析（无协议，useSsl=false）===");

        SimpleElasticsearchRouteProperties.DataSourceConfig config =
                new SimpleElasticsearchRouteProperties.DataSourceConfig();
        config.setHosts("localhost:9200,localhost:9201");
        config.setUseSsl(false);

        List<String> urls = config.getResolvedUrls();
        assertEquals(2, urls.size());
        assertEquals("http://localhost:9200", urls.get(0));
        assertEquals("http://localhost:9201", urls.get(1));

        log.info("解析结果: {}", urls);
        log.info("=== hosts 解析测试通过 ===");
    }

    /**
     * 测试19：URL 解析 - hosts 字段（无协议，useSsl=true）
     */
    @Test
    public void testHostResolutionWithSsl() {
        log.info("=== 测试 hosts 解析（无协议，useSsl=true）===");

        SimpleElasticsearchRouteProperties.DataSourceConfig config =
                new SimpleElasticsearchRouteProperties.DataSourceConfig();
        config.setHosts("localhost:9200");
        config.setUseSsl(true);

        List<String> urls = config.getResolvedUrls();
        assertEquals(1, urls.size());
        assertEquals("https://localhost:9200", urls.get(0));

        log.info("解析结果: {}", urls);
        log.info("=== hosts SSL 解析测试通过 ===");
    }

    /**
     * 测试20：URL 解析 - hosts 字段（已包含协议，忽略 useSsl）
     */
    @Test
    public void testHostResolutionWithProtocol() {
        log.info("=== 测试 hosts 解析（已包含协议）===");

        SimpleElasticsearchRouteProperties.DataSourceConfig config =
                new SimpleElasticsearchRouteProperties.DataSourceConfig();
        config.setHosts("http://localhost:9200,https://localhost:9201");
        config.setUseSsl(true);  // 应该被忽略

        List<String> urls = config.getResolvedUrls();
        assertEquals(2, urls.size());
        assertEquals("http://localhost:9200", urls.get(0));
        assertEquals("https://localhost:9201", urls.get(1));

        log.info("解析结果: {}", urls);
        log.info("=== hosts 带协议解析测试通过 ===");
    }

    /**
     * 创建一个合法的配置对象
     */
    private SimpleElasticsearchRouteProperties createValidProperties() {
        SimpleElasticsearchRouteProperties properties = new SimpleElasticsearchRouteProperties();
        properties.setEnable(true);
        properties.setDefaultSource("primary");
        properties.setSources(new HashMap<>());
        properties.setRules(new ArrayList<>());

        // 创建主数据源
        SimpleElasticsearchRouteProperties.DataSourceConfig primary =
                new SimpleElasticsearchRouteProperties.DataSourceConfig();
        primary.setUrls("http://localhost:9200");
        primary.setConnectTimeout(5000);
        primary.setSocketTimeout(60000);
        primary.setMaxConnTotal(100);
        primary.setMaxConnPerRoute(10);
        primary.setKeepAliveStrategy(300);

        properties.getSources().put("primary", primary);

        return properties;
    }
}
