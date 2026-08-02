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
    public void shouldAcceptIndependentNestedTargets() {
        assertDoesNotThrow(() -> validator.validate(validProperties()));
    }

    @Test
    public void shouldExcludeConnectionDetailsFromToString() {
        SimpleMysqlRouteProperties.DatasourceConfig datasource =
                datasource("test_ops", "test-ops-user", "test-ops-password");
        SimpleMysqlRouteProperties.ClusterConfig cluster = new SimpleMysqlRouteProperties.ClusterConfig();
        cluster.setHost("test-host.internal");
        cluster.getConnectionProperties().put("connection-password", "test-connection-password");
        cluster.getDatasources().put("ops", datasource);

        String datasourceDescription = datasource.toString();
        String clusterDescription = cluster.toString();

        assertFalse(datasourceDescription.contains("test-ops-user"));
        assertFalse(datasourceDescription.contains("test-ops-password"));
        assertFalse(clusterDescription.contains("test-host.internal"));
        assertFalse(clusterDescription.contains("test-connection-password"));
        assertFalse(clusterDescription.contains("test-ops-user"));
        assertFalse(clusterDescription.contains("test-ops-password"));
    }

    @Test
    public void shouldRejectMissingTargetCredentialAndDuplicateDatabaseBinding() {
        SimpleMysqlRouteProperties missingDatasource = validProperties();
        missingDatasource.getClusters().get("test-cluster-a").getDatasources().put("missing", null);
        ConfigurationException datasourceException = assertThrows(ConfigurationException.class,
                () -> validator.validate(missingDatasource));
        assertEquals(ErrorCode.CONFIG_INVALID, datasourceException.getCode());

        SimpleMysqlRouteProperties missingUsername = validProperties();
        missingUsername.getClusters().get("test-cluster-a").getDatasources().get("ops").setUsername(" ");
        ConfigurationException usernameException = assertThrows(ConfigurationException.class,
                () -> validator.validate(missingUsername));
        assertEquals(ErrorCode.CONFIG_INVALID, usernameException.getCode());
        assertTrue(usernameException.getMessage().contains("username"));

        SimpleMysqlRouteProperties missingPassword = validProperties();
        missingPassword.getClusters().get("test-cluster-a").getDatasources().get("ops").setPassword(" ");
        ConfigurationException passwordException = assertThrows(ConfigurationException.class,
                () -> validator.validate(missingPassword));
        assertEquals(ErrorCode.CONFIG_INVALID, passwordException.getCode());
        assertTrue(passwordException.getMessage().contains("password"));

        SimpleMysqlRouteProperties duplicate = validProperties();
        duplicate.getClusters().get("test-cluster-a").getDatasources().put("duplicate",
                datasource("test_ops", "test-duplicate-user", "test-duplicate-password"));
        ConfigurationException duplicateException = assertThrows(ConfigurationException.class,
                () -> validator.validate(duplicate));
        assertEquals(ErrorCode.CONFIG_INVALID, duplicateException.getCode());

        SimpleMysqlRouteProperties duplicateGeneratedKey = new SimpleMysqlRouteProperties();
        SimpleMysqlRouteProperties.ClusterConfig firstCluster = new SimpleMysqlRouteProperties.ClusterConfig();
        firstCluster.setHost("example.invalid");
        firstCluster.getDatasources().put("b.ops",
                datasource("test_first", "test-first-user", "test-first-password"));
        duplicateGeneratedKey.getClusters().put("test-cluster-a", firstCluster);
        SimpleMysqlRouteProperties.ClusterConfig secondCluster = new SimpleMysqlRouteProperties.ClusterConfig();
        secondCluster.setHost("example.invalid");
        secondCluster.getDatasources().put("ops",
                datasource("test_second", "test-second-user", "test-second-password"));
        duplicateGeneratedKey.getClusters().put("test-cluster-a.b", secondCluster);
        ConfigurationException generatedKeyException = assertThrows(ConfigurationException.class,
                () -> validator.validate(duplicateGeneratedKey));
        assertEquals(ErrorCode.CONFIG_INVALID, generatedKeyException.getCode());
    }

    @Test
    public void shouldRejectUnknownGeneratedRuleTargetAndInvalidPattern() {
        SimpleMysqlRouteProperties unknownTarget = validProperties();
        unknownTarget.getRules().get(0).setDatasourceKey("test-unknown.ops");
        ConfigurationException unknownTargetException = assertThrows(ConfigurationException.class,
                () -> validator.validate(unknownTarget));
        assertEquals(ErrorCode.CONFIG_INVALID, unknownTargetException.getCode());

        SimpleMysqlRouteProperties invalidRegex = validProperties();
        invalidRegex.getRules().get(0).setMatchType("regex");
        invalidRegex.getRules().get(0).setPattern("[");
        ConfigurationException invalidRegexException = assertThrows(ConfigurationException.class,
                () -> validator.validate(invalidRegex));
        assertEquals(ErrorCode.CONFIG_INVALID, invalidRegexException.getCode());
    }

    private SimpleMysqlRouteProperties validProperties() {
        SimpleMysqlRouteProperties properties = new SimpleMysqlRouteProperties();
        SimpleMysqlRouteProperties.ClusterConfig cluster = new SimpleMysqlRouteProperties.ClusterConfig();
        cluster.setHost("example.invalid");
        cluster.getDatasources().put("ops", datasource("test_ops", "test-ops-user", "test-ops-password"));
        cluster.getDatasources().put("audit", datasource("test_audit", "test-audit-user", "test-audit-password"));
        properties.getClusters().put("test-cluster-a", cluster);

        SimpleMysqlRouteProperties.RouteRule rule = new SimpleMysqlRouteProperties.RouteRule();
        rule.setPattern("test_order");
        rule.setDatasourceKey("test-cluster-a.ops");
        properties.getRules().add(rule);
        return properties;
    }

    private SimpleMysqlRouteProperties.DatasourceConfig datasource(String database, String username, String password) {
        SimpleMysqlRouteProperties.DatasourceConfig datasource = new SimpleMysqlRouteProperties.DatasourceConfig();
        datasource.setDatabase(database);
        datasource.setUsername(username);
        datasource.setPassword(password);
        return datasource;
    }
}
