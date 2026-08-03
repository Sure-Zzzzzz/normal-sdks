package io.github.surezzzzzz.sdk.mysql.route.test.cases;

import io.github.surezzzzzz.sdk.mysql.route.configuration.SimpleMysqlRouteProperties;
import io.github.surezzzzzz.sdk.mysql.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.mysql.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.mysql.route.matcher.MySqlRoutePatternMatcher;
import io.github.surezzzzzz.sdk.mysql.route.validator.MySqlRoutePropertiesValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MySQL Route 配置校验测试。
 *
 * @author surezzzzzz
 */
@Slf4j
public class MySqlRoutePropertiesValidatorTest {

    private final MySqlRoutePropertiesValidator validator =
            new MySqlRoutePropertiesValidator(new MySqlRoutePatternMatcher());

    @BeforeEach
    public void logTestStart() {
        log.info("开始执行 MySQL Route 配置校验测试");
    }

    @Test
    public void shouldAcceptCompleteRouteOwnedDatasources() {
        assertDoesNotThrow(() -> validator.validate(validProperties()));
    }

    @Test
    public void shouldExcludeConnectionDetailsFromToString() {
        SimpleMysqlRouteProperties.DatasourceConfig datasource = datasource("test-user", "test-password");
        datasource.getHikari().put("connection-test-password", "test-value");

        String datasourceDescription = datasource.toString();
        log.info("数据源描述：{}", datasourceDescription);

        assertFalse(datasourceDescription.contains("test-user"));
        assertFalse(datasourceDescription.contains("test-password"));
        assertFalse(datasourceDescription.contains("example.invalid"));
        assertFalse(datasourceDescription.contains("connection-test-password"));
    }

    @Test
    public void shouldRejectMissingBlankOrUnknownPrimaryDatasource() {
        SimpleMysqlRouteProperties missingPrimary = validProperties();
        missingPrimary.setPrimaryDatasource(null);
        assertConfigInvalid(missingPrimary, "primary-datasource");

        SimpleMysqlRouteProperties blankPrimary = validProperties();
        blankPrimary.setPrimaryDatasource(" ");
        assertConfigInvalid(blankPrimary, "primary-datasource");

        SimpleMysqlRouteProperties unknownPrimary = validProperties();
        unknownPrimary.setPrimaryDatasource("test-missing");
        assertConfigInvalid(unknownPrimary, "primary-datasource");
    }

    @Test
    public void shouldRejectIncompleteDatasourceDefinitions() {
        SimpleMysqlRouteProperties missingDatasource = validProperties();
        missingDatasource.getDatasources().put("test-missing", null);
        assertConfigInvalid(missingDatasource, "url");

        SimpleMysqlRouteProperties invalidName = validProperties();
        invalidName.getDatasources().put("test.invalid", invalidName.getDatasources().remove("test-ops"));
        invalidName.setPrimaryDatasource("test-audit");
        assertConfigInvalid(invalidName, "datasource 名称");

        SimpleMysqlRouteProperties missingUrl = validProperties();
        missingUrl.getDatasources().get("test-ops").setUrl(" ");
        assertConfigInvalid(missingUrl, "url");

        SimpleMysqlRouteProperties missingUsername = validProperties();
        missingUsername.getDatasources().get("test-ops").setUsername(" ");
        assertConfigInvalid(missingUsername, "username");

        SimpleMysqlRouteProperties missingPassword = validProperties();
        missingPassword.getDatasources().get("test-ops").setPassword(" ");
        assertConfigInvalid(missingPassword, "password");

        SimpleMysqlRouteProperties missingDriver = validProperties();
        missingDriver.getDatasources().get("test-ops").setDriverClassName(" ");
        assertConfigInvalid(missingDriver, "driver-class-name");
    }

    @Test
    public void shouldRejectPrivilegedUsernameWithoutEchoingCredential() {
        String[] privilegedUsernames = {"root", "ROOT", "admin", "ADMIN"};
        for (String privilegedUsername : privilegedUsernames) {
            SimpleMysqlRouteProperties properties = validProperties();
            properties.getDatasources().get("test-ops").setUsername(privilegedUsername);

            ConfigurationException exception = assertThrows(ConfigurationException.class,
                    () -> validator.validate(properties));
            log.info("高风险账号校验结果：code={}，message={}", exception.getCode(), exception.getMessage());

            assertEquals(ErrorCode.CONFIG_INVALID, exception.getCode());
            assertTrue(exception.getMessage().contains("高风险连接账号"));
            assertFalse(exception.getMessage().contains(privilegedUsername));
        }
    }

    @Test
    public void shouldRejectBlankHikariPropertiesAndUnknownRuleDatasource() {
        SimpleMysqlRouteProperties invalidHikari = validProperties();
        invalidHikari.getDatasources().get("test-ops").getHikari().put(" ", "4");
        assertConfigInvalid(invalidHikari, "Hikari");

        SimpleMysqlRouteProperties blankHikariValue = validProperties();
        blankHikariValue.getDatasources().get("test-ops").getHikari().put("maximum-pool-size", " ");
        assertConfigInvalid(blankHikariValue, "Hikari");

        SimpleMysqlRouteProperties unknownRuleDatasource = validProperties();
        unknownRuleDatasource.getRules().get(0).setDatasource("test-missing");
        assertConfigInvalid(unknownRuleDatasource, "不存在");

        SimpleMysqlRouteProperties invalidRegex = validProperties();
        invalidRegex.getRules().get(0).setMatchType("regex");
        invalidRegex.getRules().get(0).setPattern("[");
        assertConfigInvalid(invalidRegex, "无法编译");
    }

    private void assertConfigInvalid(SimpleMysqlRouteProperties properties, String expectedDetail) {
        ConfigurationException exception = assertThrows(ConfigurationException.class,
                () -> validator.validate(properties));
        log.info("配置校验失败：code={}，message={}", exception.getCode(), exception.getMessage());
        assertEquals(ErrorCode.CONFIG_INVALID, exception.getCode());
        assertTrue(exception.getMessage().contains(expectedDetail));
    }

    private SimpleMysqlRouteProperties validProperties() {
        SimpleMysqlRouteProperties properties = new SimpleMysqlRouteProperties();
        properties.setPrimaryDatasource("test-ops");
        properties.getDatasources().put("test-ops", datasource("test-ops-user", "test-ops-password"));
        properties.getDatasources().put("test-audit", datasource("test-audit-user", "test-audit-password"));

        SimpleMysqlRouteProperties.RouteRule rule = new SimpleMysqlRouteProperties.RouteRule();
        rule.setPattern("test_order");
        rule.setDatasource("test-ops");
        properties.getRules().add(rule);
        return properties;
    }

    private SimpleMysqlRouteProperties.DatasourceConfig datasource(String username, String password) {
        SimpleMysqlRouteProperties.DatasourceConfig datasource = new SimpleMysqlRouteProperties.DatasourceConfig();
        datasource.setUrl("jdbc:mysql://example.invalid/test_order");
        datasource.setUsername(username);
        datasource.setPassword(password);
        return datasource;
    }
}
